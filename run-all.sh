#!/usr/bin/env bash
# Run the integration-tests suite against every scenario in scenarios.tsv, one
# at a time, and print a pass/fail summary.
#
# Scenarios all publish on the same ports (9090/9443 for the proxy, 8080/7070
# for the backends), so they cannot overlap — this is strictly sequential and
# takes a while. It is the local equivalent of the GitHub Actions matrix, which
# runs the same scenarios in parallel on separate runners.
#
# The app image is NOT built here; build it first and pass the tag:
#
#   docker build my-app -t vaadin/my-app:latest
#   ./run-all.sh
#
#   docker build my-app --build-arg VAADIN_VERSION=25.4-SNAPSHOT -t vaadin/my-app:sse
#   ./run-all.sh --app-version sse '\-sse$'
#
# Like the CI job, each scenario's push transport is derived from its name: the
# *-sse scenarios run over SERVER_SENT_EVENTS with WebSocket declared
# unavailable (so the Hilla push assertions are skipped — Hilla's FluxConnection
# is WebSocket-only), everything else over the WebSocket transports.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$repo_root"

usage() {
    cat <<EOF
Usage: $0 [options] [filter-regex]
       $0 --help

  filter-regex        Extended regex matched against the scenario key; only
                      matching scenarios run. Omit to run all of them.

  --app-version <tag> Image tag to run (vaadin/my-app:<tag>). Default: latest.
  --exclude <regex>   Skip scenarios whose key matches this regex.
  --dry-run           List the scenarios that would run, then exit.
  -h, --help          Show this help.

  Examples:
    $0                              # every scenario
    $0 nginx                        # only nginx/*
    $0 --app-version sse '\\-sse\$'   # only the SSE scenarios
    $0 --exclude '\\-sse\$'           # everything except the SSE scenarios
EOF
}

app_version="latest"
filter=""
exclude=""
dry_run=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        -h|--help)      usage; exit 0 ;;
        --dry-run)      dry_run=1 ;;
        --app-version)
            [[ $# -ge 2 ]] || { echo "--app-version requires a tag" >&2; exit 2; }
            app_version="$2"; shift ;;
        --app-version=*) app_version="${1#--app-version=}" ;;
        --exclude)
            [[ $# -ge 2 ]] || { echo "--exclude requires a regex" >&2; exit 2; }
            exclude="$2"; shift ;;
        --exclude=*)    exclude="${1#--exclude=}" ;;
        -*)
            echo "unknown argument: $1" >&2; usage >&2; exit 2 ;;
        *)
            [[ -z $filter ]] || { echo "unexpected argument: $1" >&2; exit 2; }
            filter="$1" ;;
    esac
    shift
done

command -v jq >/dev/null || { echo "jq is not installed" >&2; exit 1; }
export MY_APP_VERSION="$app_version"

matrix="$(scripts/gen-matrix.sh "$filter" "$exclude")"
count="$(echo "$matrix" | jq length)"
[[ $count -gt 0 ]] || { echo "no scenarios matched" >&2; exit 1; }

if [[ $dry_run -eq 1 ]]; then
    echo "$matrix" | jq -r '.[] | "\(.scenario)  ->  \(.scheme)://localhost:\(.port)\(.paths|join(" "))"'
    echo "($count scenario(s), image tag: $app_version)"
    exit 0
fi

compose_file=""
cleanup() {
    [[ -n $compose_file ]] && docker compose -f "$compose_file" down -v >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

passed=(); failed=()

echo "Running $count scenario(s) with vaadin/my-app:$app_version"
while IFS=$'\t' read -r scenario scheme port paths; do
    echo
    echo "=============================================================="
    echo "  $scenario"
    echo "=============================================================="

    # Mirror the CI job: the scenario's own name decides the transport.
    case "$scenario" in
        *-sse) transports="SERVER_SENT_EVENTS"; websocket="false" ;;
        *)     transports="WEBSOCKET_XHR,WEBSOCKET"; websocket="true" ;;
    esac

    compose_file="$scenario/docker-compose.yml"
    docker compose -f "$compose_file" up -d --no-build

    scenario_ok=1
    for path in $paths; do
        url="$scheme://localhost:$port$path"

        printf 'Waiting for %s ' "$url"
        ready=0
        for _ in $(seq 1 60); do
            if curl -fsk -o /dev/null "$url"; then ready=1; break; fi
            printf '.'; sleep 2
        done
        echo
        if [[ $ready -ne 1 ]]; then
            echo "timed out waiting for $url" >&2
            scenario_ok=0
            continue
        fi

        # Sticky-cookie load balancers round-robin the first cookie-less
        # request, so a fresh browser session can land on a backend the single
        # readiness probe never touched. Warm every backend before testing.
        for _ in $(seq 1 20); do curl -fsk -o /dev/null "$url" || true; done

        if ! ./run-test.sh "$url" -- -Dit.push.transports="$transports" \
                                    -Dit.websocket="$websocket"; then
            scenario_ok=0
        fi
    done

    if [[ $scenario_ok -eq 1 ]]; then passed+=("$scenario"); else failed+=("$scenario"); fi

    docker compose -f "$compose_file" down -v >/dev/null 2>&1 || true
    compose_file=""
done < <(echo "$matrix" | jq -r '.[] | [.scenario, .scheme, .port, (.paths|join(" "))] | @tsv')

echo
echo "=============================================================="
echo "  ${#passed[@]} passed, ${#failed[@]} failed"
echo "=============================================================="
for s in "${failed[@]:-}"; do [[ -n $s ]] && echo "  FAILED  $s"; done

[[ ${#failed[@]} -eq 0 ]]
