#!/usr/bin/env bash
# Run the integration-tests Playwright smoke tests against a given base URL.
#
# Assumes the app is already running (e.g. via docker compose for a proxy
# scenario, or `java -jar` for the raw app on :8080).

set -euo pipefail

usage() {
    cat <<EOF
Usage: $0 [-i|--interactive] <base-url> [-- mvn-args…]
       $0 --help

  <base-url>          Base URL of the app under test (mandatory). Trailing
                      slash required, since tests resolve view paths
                      relative to it.
  -i, --interactive   Pick which tests to run from a fuzzy-filter list
                      (Tab to multi-select). Requires \`gum\`. Selections
                      are forwarded as -Dit.test=Class[#method],…
  --                  Everything after this is forwarded verbatim to mvnw,
                      e.g. extra -D flags or -P profiles.

  Examples:
    $0 http://localhost:8080/
    $0 http://localhost:9090/app/
    $0 http://localhost:9090/ -- -Dfoo=bar -X
    $0 -i http://localhost:9090/
    $0 -i http://localhost:9090/ -- -Pdebug
EOF
}

interactive=0
base_url=""
mvn_args=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        -h|--help)        usage; exit 0 ;;
        -i|--interactive) interactive=1 ;;
        --)               shift; mvn_args+=("$@"); break ;;
        -*)
            echo "unknown argument: $1" >&2
            usage >&2
            exit 2 ;;
        *)
            if [[ -n $base_url ]]; then
                echo "unexpected positional argument: $1" >&2
                exit 2
            fi
            base_url="$1"
            ;;
    esac
    shift
done

if [[ -z $base_url ]]; then
    usage >&2
    exit 2
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$repo_root/integration-tests"

if [[ $interactive -eq 1 ]]; then
    command -v gum >/dev/null \
        || { echo "gum is not installed (https://github.com/charmbracelet/gum)" >&2; exit 1; }

    items=()
    while IFS= read -r path; do
        cls="${path##*/}"
        cls="${cls%.java}"
        items+=("$cls")
        while IFS= read -r method; do
            [[ -n $method ]] && items+=("$cls#$method")
        done < <(awk '
            /@Test|@ParameterizedTest/ { take=1; next }
            take && /^[[:space:]]*@/   { next }
            take {
                if (match($0, /void[[:space:]]+[A-Za-z_][A-Za-z0-9_]*[[:space:]]*\(/)) {
                    s = substr($0, RSTART, RLENGTH)
                    sub(/^void[[:space:]]+/, "", s)
                    sub(/[[:space:]]*\(.*/, "", s)
                    print s
                }
                take=0
            }
        ' "$path")
    done < <(find src/test/java -name '*IT.java' ! -name 'BaseIT.java' | sort)

    [[ ${#items[@]} -gt 0 ]] || { echo "no *IT.java tests discovered under src/test/java" >&2; exit 1; }

    if ! selection=$(printf '%s\n' "${items[@]}" \
            | gum filter --no-limit \
                --header "Tests (Tab to multi-select, Enter to confirm, Esc to cancel):" \
                --placeholder "Filter tests…"); then
        exit 0
    fi
    [[ -n $selection ]] || exit 0
    selection_csv=$(printf '%s\n' "$selection" | paste -sd,)
    mvn_args+=("-Dit.test=$selection_csv")
fi

exec ./mvnw -B verify -Dapp.base.url="$base_url" "${mvn_args[@]}"
