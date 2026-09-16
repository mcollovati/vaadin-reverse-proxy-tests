#!/usr/bin/env bash
# Check that a running scenario compresses what it should and leaves streams alone.
#
# Every proxy config in this repo has compression turned on, because that is how
# a real deployment runs. The risk is compressing a streamed response: the push
# connection would then carry a deflate context for its whole life, and the
# 2000-byte padding Atmosphere writes when an SSE connection opens — the padding
# that exists to force a buffering intermediary to flush — shrinks to a couple
# of dozen bytes.
#
# Three assertions, against a scenario that is already up:
#   1. an ordinary HTML response IS compressed (so the test is not vacuous)
#   2. the event stream is NOT compressed
#   3. the event stream still trickles in, one tick at a time
#
# Usage: scripts/check-compression.sh <base-url>
#        scripts/check-compression.sh http://localhost:9090/
#        scripts/check-compression.sh https://localhost:9443/

set -euo pipefail

base_url="${1:-}"
if [[ -z $base_url ]]; then
    echo "Usage: $0 <base-url>   (trailing slash required)" >&2
    exit 2
fi
[[ $base_url == */ ]] || base_url="$base_url/"

# -k: the https scenarios use the self-signed cert in tls/.
curl_opts=(-sS -k --max-time 20)

failures=0
pass() { printf '  \033[32mok\033[0m   %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m %s\n' "$1"; failures=$((failures + 1)); }

# Pull one header out of a `curl -D` dump.
dump_header() {
    tr -d '\r' < "$1" | awk -v h="$2" 'BEGIN{IGNORECASE=1} tolower($0) ~ "^" h ":" {
              sub(/^[^:]*: */, ""); print; exit }'
}

# Read one response header from a URL that answers and closes, like the HTML
# page. Not usable on /sse-probe: that response never ends on its own, so curl
# would sit there until --max-time on every call. The probe's headers come off
# the streaming request below instead.
header() {
    local url="$1" name="$2" dump err out
    dump="$(mktemp)"
    err="$(mktemp)"
    if ! curl -sS -k --max-time 10 -o /dev/null -D "$dump" \
            -H 'Accept-Encoding: gzip' "$url" 2>"$err" && [[ ! -s $dump ]]; then
        cat "$err" >&2
    fi
    out=$(dump_header "$dump" "$name")
    rm -f "$dump" "$err"
    printf '%s\n' "$out"
}

echo "Base URL: $base_url"

# 1. Compression is actually on.
encoding=$(header "$base_url" content-encoding)
if [[ $encoding == gzip ]]; then
    pass "HTML response is compressed (Content-Encoding: gzip)"
else
    fail "HTML response is not compressed (Content-Encoding: ${encoding:-none});"
    echo "       compression is off, so the stream checks below prove nothing"
fi

# 2 + 3. The event stream. /sse-probe is registered at the context root, which
# is not always where the base URL points: a servlet-mapping scenario appends
# /ui to it, and the context root itself may sit at the origin (root-context)
# or behind a proxy prefix (custom-to-root-context puts it under /app). So walk
# the base URL's path up one segment at a time, ending at the origin, and use
# the first level that answers with an event stream.
#
# One request per candidate does all three jobs. curl writes the -D dump before
# the first byte of the body, so the same connection that times the ticks also
# says what the content type and the encoding were — and --compressed decodes
# the stream for the tick reader while leaving the raw Content-Encoding in the
# dump. Asking for those headers separately is what used to make this script
# slow: each such request landed on a live stream and burned the full
# --max-time before curl gave up.
origin=$(sed -E 's#^(https?://[^/]+).*#\1#' <<<"$base_url")
path="${base_url#"$origin"}"
path="${path%/}"

probe_url=""
probe_encoding=""
ticks=0
first=0
last=0

while :; do
    candidate="$origin$path/sse-probe"
    dump="$(mktemp)"

    # The probe emits a tick every 500ms. Buffered anywhere along the way, the
    # ticks all land at once at the end instead of trickling in. A candidate
    # that is not the probe answers and closes, so this loop just falls through
    # with no ticks. Breaking out closes the pipe under curl, which is why its
    # stderr is discarded.
    start=$(date +%s%N)
    ticks=0
    while IFS= read -r line; do
        [[ $line == data:* ]] || continue
        now=$(( ($(date +%s%N) - start) / 1000000 ))
        [[ $ticks -eq 0 ]] && first=$now
        last=$now
        ticks=$((ticks + 1))
        [[ $ticks -ge 6 ]] && break
    done < <(curl "${curl_opts[@]}" -N --compressed -D "$dump" "$candidate" 2>/dev/null)

    if [[ $(dump_header "$dump" content-type) == text/event-stream* ]]; then
        probe_url="$candidate"
        probe_encoding=$(dump_header "$dump" content-encoding)
        rm -f "$dump"
        break
    fi
    rm -f "$dump"

    # An empty path means the origin itself was the candidate just tried.
    [[ -n $path ]] || break
    path="${path%/*}"
done

if [[ -z $probe_url ]]; then
    fail "no /sse-probe endpoint reachable from $base_url"
else
    echo "Probe URL: $probe_url"
    if [[ -z $probe_encoding ]]; then
        pass "event stream is not compressed"
    else
        fail "event stream is compressed (Content-Encoding: $probe_encoding)"
    fi

    if [[ $ticks -lt 6 ]]; then
        fail "event stream stopped early ($ticks of 6 ticks received)"
    elif [[ $((last - first)) -lt 1000 ]]; then
        fail "event stream is buffered (6 ticks arrived within $((last - first))ms, expected ~500ms apart)"
    else
        pass "event stream is not buffered (6 ticks over $((last - first))ms, ~500ms apart; first at ${first}ms)"
    fi
fi
echo
if [[ $failures -eq 0 ]]; then
    echo "All checks passed."
else
    echo "$failures check(s) failed."
    exit 1
fi
