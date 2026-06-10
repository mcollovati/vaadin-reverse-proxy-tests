#!/usr/bin/env bash
# Emit the GitHub Actions test matrix from scenarios.tsv as a single-line JSON
# array, suitable for `strategy.matrix.include` via fromJson().
#
# Each element:
#   {
#     "scenario": "apache-httpd/http/root-context",  # proxy/scenario key
#     "scheme":   "http",                            # http | https
#     "port":     "9090",                            # 9090 (http) | 9443 (https)
#     "paths":    ["/"]                              # proxy-relative URL paths
#   }
#
# Optional arg $1 is a filter (extended regex) matched against the scenario
# key; only matching rows are emitted. No filter (or empty) = all scenarios.
#
#   scripts/gen-matrix.sh                 # every scenario
#   scripts/gen-matrix.sh nginx           # only nginx/* scenarios
#   scripts/gen-matrix.sh 'http/root'     # regex match on the key
#
# Requires jq for safe JSON encoding.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
catalog="$repo_root/scenarios.tsv"
filter="${1:-}"

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

    # Pipe-separated: key | description | paths | short
    IFS='|' read -r key _description paths _short <<< "$line"
    key="$(trim "$key")"
    paths="$(trim "$paths")"
    [[ -z $key ]] && continue

    if [[ -n $filter ]]; then
        printf '%s' "$key" | grep -Eq "$filter" || continue
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

printf '%s\n' "${entries[@]}" | jq -sc '.'
