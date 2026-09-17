#!/usr/bin/env bash
# Check that scenarios.tsv and the scenario directories on disk agree.
#
# scenarios.tsv is the single source of truth for three things: the CI matrix
# (scripts/gen-matrix.sh), the per-scenario READMEs (scripts/gen-readmes.sh) and
# run-scenario.sh's picker. Drift in either direction is silent — a scenario
# missing from the catalogue simply never runs and never appears anywhere, and a
# catalogue row with no directory behind it fails only once CI tries to start it.
#
# Usage: scripts/check-catalog.sh
# Emits GitHub Actions ::error:: annotations; exits non-zero on any problem.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
catalog="$repo_root/scenarios.tsv"

[[ -f $catalog ]] || { echo "scenarios.tsv not found at $catalog" >&2; exit 1; }

# Every top-level directory that may hold scenarios. Absent ones are skipped, so
# this list can name the proxy trees before they exist.
proxy_trees=(apache-httpd nginx haproxy traefik)

trim() { local s="$1"; s="${s#"${s%%[![:space:]]*}"}"; s="${s%"${s##*[![:space:]]}"}"; printf '%s' "$s"; }

fail=0
err() { echo "::error::$*"; fail=1; }

cd "$repo_root"

# --- catalogue -> disk -------------------------------------------------------
declare -A seen=()
while IFS= read -r raw_line; do
    line="${raw_line%$'\r'}"
    [[ -z ${line// } ]] && continue
    [[ ${line:0:1} == "#" ]] && continue

    IFS='|' read -r key _description paths _short <<< "$line"
    key="$(trim "$key")"
    paths="$(trim "$paths")"
    [[ -z $key ]] && continue

    if [[ -n ${seen[$key]:-} ]]; then
        err "scenarios.tsv lists '$key' more than once"
    fi
    seen[$key]=1

    [[ -f $key/docker-compose.yml ]] \
        || err "scenarios.tsv lists '$key' but $key/docker-compose.yml does not exist"

    # The paths column drives every URL CI builds. An empty or relative value
    # yields a nonsense URL that only shows up as a timeout in the wait loop.
    if [[ -z $paths ]]; then
        err "'$key' has an empty paths column"
    else
        IFS=',' read -ra parts <<< "$paths"
        for part in "${parts[@]}"; do
            part="$(trim "$part")"
            [[ $part == /* ]] \
                || err "'$key' has a path that is not absolute: '$part'"
        done
    fi
done < "$catalog"

# --- disk -> catalogue -------------------------------------------------------
for tree in "${proxy_trees[@]}"; do
    [[ -d $tree ]] || continue
    while IFS= read -r compose; do
        dir="${compose%/docker-compose.yml}"
        [[ -n ${seen[$dir]:-} ]] \
            || err "$dir has a docker-compose.yml but is not listed in scenarios.tsv"
    done < <(find "$tree" -name docker-compose.yml | sort)
done

if [[ $fail -eq 0 ]]; then
    echo "scenarios.tsv: ${#seen[@]} scenario(s), all consistent with the tree."
fi
exit "$fail"
