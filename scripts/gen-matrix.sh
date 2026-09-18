#!/usr/bin/env bash
# Emit the GitHub Actions test matrix from scenarios.tsv as a single-line JSON
# array, suitable for `strategy.matrix.include` via fromJson().
#
# Scenarios are grouped into *chunks*: one matrix entry runs several scenarios
# sequentially in one job. The per-job fixed cost — checkout, downloading and
# loading the image tarball, the JDK, Playwright's browser and its system
# libraries — measured ~87s against ~61s of actual scenario work, so one job per
# scenario spent well over half its time on setup, 112 times per sweep. Chunking
# pays that once per chunk instead.
#
# Each element:
#   {
#     "name":      "apache-httpd 1/5",   # job name; unique, human-readable
#     "proxy":     "apache-httpd",       # proxy tree the chunk belongs to
#     "scenarios": [                     # run in order, one after another
#       {
#         "scenario": "apache-httpd/http/root-context",
#         "scheme":   "http",            # http | https
#         "port":     "9090",            # 9090 (http) | 9443 (https)
#         "paths":    ["/"]              # proxy-relative URL paths
#       },
#       ...
#     ]
#   }
#
# Chunks never span proxy trees: a job then needs only its own tree's proxy
# image, and the report job groups result rows by tree anyway.
#
# Usage:
#   scripts/gen-matrix.sh [--tier smoke|full|all] [--chunk-size N] [filter] [exclude]
#
#   --tier        Which catalogue tier to include. `smoke` takes only the rows
#                 marked smoke; `full` and `all` take every row (a `full` row is
#                 *also* run by the full sweep, it is not a separate set).
#                 Default: all.
#   --chunk-size  Scenarios per job. 1 restores one job per scenario, which is
#                 what you want when bisecting a flake. Default: 8.
#   filter        Extended regex matched against the scenario key; only matching
#                 rows are emitted. Empty = all.
#   exclude       Second extended regex, applied after `filter`, dropping matches.
#
#   scripts/gen-matrix.sh                             # every scenario
#   scripts/gen-matrix.sh --tier smoke                # what a PR runs
#   scripts/gen-matrix.sh --chunk-size 1 nginx        # one nginx job per scenario
#   scripts/gen-matrix.sh '' '\-sse$'                 # everything except the SSE ones
#
# The exclusion exists because the *-sse scenarios need an app image built
# against a Flow version that has the SSE push transport; a run that did not
# build one has to skip them.
#
# Requires jq for safe JSON encoding.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
catalog="$repo_root/scenarios.tsv"

tier="all"
chunk_size=8

while [[ $# -gt 0 ]]; do
    case "$1" in
        --tier)       tier="${2:-}"; shift 2 ;;
        --chunk-size) chunk_size="${2:-}"; shift 2 ;;
        --)           shift; break ;;
        -*)           echo "unknown option: $1" >&2; exit 2 ;;
        *)            break ;;
    esac
done

filter="${1:-}"
exclude="${2:-}"

case "$tier" in
    smoke|full|all) ;;
    *) echo "--tier must be smoke, full or all (got '$tier')" >&2; exit 2 ;;
esac
[[ $chunk_size =~ ^[1-9][0-9]*$ ]] \
    || { echo "--chunk-size must be a positive integer (got '$chunk_size')" >&2; exit 2; }

[[ -f $catalog ]] || { echo "scenarios.tsv not found at $catalog" >&2; exit 1; }
command -v jq >/dev/null || { echo "jq is not installed" >&2; exit 1; }

# Scheme/port for a proxy directory: HTTPS variants serve on 9443, all else 9090.
scheme_for() { case "$1" in */https|*/ajp-https) echo https ;; *) echo http ;; esac; }
port_for()   { case "$1" in */https|*/ajp-https) echo 9443 ;; *) echo 9090 ;; esac; }

trim() { local s="$1"; s="${s#"${s%%[![:space:]]*}"}"; s="${s%"${s##*[![:space:]]}"}"; printf '%s' "$s"; }

entries=()
while IFS= read -r raw_line; do
    line="${raw_line%$'\r'}"
    [[ -z ${line// } ]] && continue
    [[ ${line:0:1} == "#" ]] && continue

    # Pipe-separated: key | description | paths | short | tier
    IFS='|' read -r key _description paths _short row_tier <<< "$line"
    key="$(trim "$key")"
    paths="$(trim "$paths")"
    row_tier="$(trim "$row_tier")"
    [[ -z $key ]] && continue

    # An unmarked row would silently vanish from the smoke tier, which is the
    # one failure mode this column can have. Treat it as a hard error; the same
    # rule is enforced ahead of time by scripts/check-catalog.sh.
    case "$row_tier" in
        smoke|full) ;;
        *) echo "'$key' has an invalid tier: '$row_tier' (expected smoke or full)" >&2; exit 1 ;;
    esac
    [[ $tier == smoke && $row_tier != smoke ]] && continue

    if [[ -n $filter ]]; then
        printf '%s' "$key" | grep -Eq "$filter" || continue
    fi
    if [[ -n $exclude ]] && printf '%s' "$key" | grep -Eq "$exclude"; then
        continue
    fi

    local_proxy_dir="${key%/*}"
    scheme="$(scheme_for "$local_proxy_dir")"
    port="$(port_for "$local_proxy_dir")"

    # Build a JSON array of trimmed, comma-separated paths.
    paths_json="$(printf '%s' "$paths" | jq -R 'split(",") | map(gsub("^\\s+|\\s+$";""))')"

    entries+=("$(jq -nc \
        --arg scenario "$key" \
        --arg scheme "$scheme" \
        --arg port "$port" \
        --argjson paths "$paths_json" \
        '{scenario:$scenario, scheme:$scheme, port:$port, paths:$paths}')")
done < "$catalog"

# No matches is a legitimate result here (the caller decides whether an empty
# matrix is an error), but `printf '%s\n' "${entries[@]}"` on an empty array
# under `set -u` is not.
if [[ ${#entries[@]} -eq 0 ]]; then
    echo '[]'
    exit 0
fi

# Group by proxy tree, then cut each tree into chunks of at most $chunk_size,
# preserving catalogue order. The chunk count is what $chunk_size really fixes;
# the size is then levelled across that many chunks, so a tree of 25 at size 8
# yields 7/7/7/4 rather than 8/8/8/1 and the slowest job in the wave — which is
# what sets the wall clock — is as short as it can be. A tree that fits in one
# chunk keeps its bare name; otherwise the name carries an "i/n" suffix so job
# names stay unique and say how much of the tree they cover.
printf '%s\n' "${entries[@]}" | jq -sc --argjson n "$chunk_size" '
    group_by(.scenario | split("/")[0])
    | map(
        . as $tree
        | ($tree[0].scenario | split("/")[0]) as $proxy
        | ($tree | length) as $len
        | (($len + $n - 1) / $n | floor) as $total
        | (($len + $total - 1) / $total | floor) as $size
        | [ range(0; $total) as $i
            | { name: ($proxy + (if $total > 1 then " \($i + 1)/\($total)" else "" end)),
                proxy: $proxy,
                scenarios: $tree[$i * $size : ($i + 1) * $size] } ]
      )
    | flatten
'
