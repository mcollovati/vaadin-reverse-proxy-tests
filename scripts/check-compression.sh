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

# Read one response header. The catch is that this is also used on /sse-probe,
# which streams for 30s: curl downloads the body whatever -o points at, so it
# gets cut short by --max-time every time, and piping the header dump does not
# help — an awk that exits on the match leaves curl streaming into a pipe
# nobody reads until the timeout, then exiting non-zero and taking the whole
# script down with it. So the dump goes to a file, which curl writes before the
# first byte of the body, and a cut-short request is only an error when it
# yielded no headers at all.
header() {
    local url="$1" name="$2" dump err
    dump="$(mktemp)"
    err="$(mktemp)"
    if ! curl -sS -k --max-time 10 -o /dev/null -D "$dump" \
            -H 'Accept-Encoding: gzip' "$url" 2>"$err" && [[ ! -s $dump ]]; then
        cat "$err" >&2
    fi
    tr -d '\r' < "$dump" | awk -v h="$name" 'BEGIN{IGNORECASE=1} tolower($0) ~ "^" h ":" {
              sub(/^[^:]*: */, ""); print; exit }'
    rm -f "$dump" "$err"
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

# 2 + 3. The event stream. /sse-probe is registered at the context root, so for
# a scenario whose base URL sits under a servlet mapping it lives at the origin
# instead; try both and use whichever answers with an event stream.
probe_url=""
for candidate in "${base_url}sse-probe" "$(sed -E 's#^(https?://[^/]+)/.*#\1#' <<<"$base_url")/sse-probe"; do
    if [[ $(header "$candidate" content-type) == text/event-stream* ]]; then
        probe_url="$candidate"
        break
    fi
done

if [[ -z $probe_url ]]; then
    fail "no /sse-probe endpoint reachable from $base_url"
else
    echo "Probe URL: $probe_url"
    encoding=$(header "$probe_url" content-encoding)
    if [[ -z $encoding ]]; then
        pass "event stream is not compressed"
    else
        fail "event stream is compressed (Content-Encoding: $encoding)"
    fi

    # The probe emits a tick every 500ms. Buffered anywhere along the way, the
    # ticks all land at once at the end instead of trickling in.
    start=$(date +%s%N)
    first=0
    last=0
    ticks=0
    while IFS= read -r line; do
        [[ $line == data:* ]] || continue
        now=$(( ($(date +%s%N) - start) / 1000000 ))
        [[ $ticks -eq 0 ]] && first=$now
        last=$now
        ticks=$((ticks + 1))
        # Breaking out closes the pipe under curl, which is why its stderr is
        # discarded below.
        [[ $ticks -ge 6 ]] && break
    done < <(curl "${curl_opts[@]}" -N --compressed "$probe_url" 2>/dev/null)

    if [[ $ticks -lt 6 ]]; then
        fail "only $ticks ticks received from the probe"
    elif [[ $((last - first)) -lt 1000 ]]; then
        fail "6 ticks arrived within $((last - first))ms — the response is buffered"
    else
        pass "6 ticks trickled in over $((last - first))ms (first at ${first}ms)"
    fi
fi

echo
if [[ $failures -eq 0 ]]; then
    echo "All checks passed."
else
    echo "$failures check(s) failed."
    exit 1
fi
