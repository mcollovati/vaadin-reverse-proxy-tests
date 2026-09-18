#!/usr/bin/env bash
# Run one matrix chunk: every scenario in $CHUNK, one after another, in a single
# CI job.
#
# This is the body of the `test` job in .github/workflows/_scenarios.yml. It
# lives here rather than inline so shellcheck covers it — lint.yml runs
# `shellcheck -x -S warning scripts/*.sh` — and so the loop is readable.
#
# A chunk keeps going after a scenario fails. Which scenarios broke is the whole
# output of this repository, and stopping at the first one would hide the rest
# behind it. The exit status is non-zero if *any* scenario failed, so the job
# still goes red.
#
# Inputs (environment):
#   CHUNK            JSON array of {scenario, scheme, port, paths} — one matrix
#                    entry's `scenarios`, as produced by scripts/gen-matrix.sh.
#   MY_APP_VERSION   Image tag the compose files resolve ${MY_APP_VERSION} to.
#
# Outputs (in the working directory):
#   results.jsonl            one row per scenario, whatever happened to it.
#   failure-artifacts/<id>/  failsafe reports, traces and compose logs, for the
#                            failed scenarios only.
#   $GITHUB_STEP_SUMMARY     an inline report per failure.

# Deliberately no `-e`: a failing scenario is data, not an abort. Every step
# below is checked explicitly instead.
set -uo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root" || exit 1

: "${CHUNK:?CHUNK is not set}"
command -v jq >/dev/null || { echo "jq is not installed" >&2; exit 1; }

results="$repo_root/results.jsonl"
artifacts="$repo_root/failure-artifacts"
: > "$results"
rm -rf "$artifacts"

summary="${GITHUB_STEP_SUMMARY:-/dev/null}"
failed=0

# --- one scenario ------------------------------------------------------------

# Nothing may reach the proxy before the backends accept connections. A proxy
# that gets a refused connection remembers it: nginx takes the peer out of
# rotation for fail_timeout, Apache for `retry` seconds, and with two backends
# that verdict outlives the readiness probe below, which a single healthy
# backend (or, in nginx, a single clean worker process) is enough to satisfy.
# The published backend ports come from the compose file itself, so a container
# that has not started yet cannot be missed.
#
# Backends are identified by their image rather than by a `vaadin*` service
# name: a new proxy tree that names its app service something else would
# otherwise get a step that silently waits for nothing and passes.
wait_for_backends() {
    local compose_file="$1" ports port ok
    ports=$(docker compose -f "$compose_file" config --format json \
        | jq -r '.services | to_entries[]
                 | select((.value.image // "") | startswith("vaadin/my-app"))
                 | (.value.ports // [])[]
                 | select(.published != null) | .published | tostring')
    if [[ -z $ports ]]; then
        echo "::error::No published backend ports found in $compose_file"
        return 1
    fi
    for port in $ports; do
        echo "Waiting for backend port $port ..."
        ok=0
        for _ in $(seq 1 120); do
            if (exec 3<>/dev/tcp/127.0.0.1/"$port") 2>/dev/null; then ok=1; break; fi
            sleep 1
        done
        if [[ $ok -ne 1 ]]; then
            echo "::error::Timed out waiting for backend port $port"
            return 1
        fi
    done
}

# Require consecutive successes. A single 200 is not enough for a load balancer:
# it only proves one backend is up, and a backend that was refused earlier stays
# blacklisted by the proxy for its fail_timeout, so requests keep returning 502
# after the app has started listening.
wait_for_proxy() {
    local url ok streak fail=0
    for url in "$@"; do
        echo "Waiting for $url ..."
        ok=0
        streak=0
        for _ in $(seq 1 90); do
            if curl -fsk -o /dev/null "$url"; then
                streak=$((streak + 1))
                if [[ $streak -ge 3 ]]; then ok=1; break; fi
            else
                streak=0
            fi
            sleep 1
        done
        if [[ $ok -ne 1 ]]; then
            echo "::error::Timed out waiting for $url"
            fail=1
        fi
    done
    return "$fail"
}

# Sticky-cookie load balancers (nginx `sticky cookie`, Apache balancer)
# round-robin the first, cookie-less request, so a fresh browser session can
# land on a backend the readiness probe above never touched. Under CI contention
# that cold backend's first Vaadin render can exceed Playwright's 5s assertion
# timeout, failing the suite. Issue a burst of cookie-less requests per path so
# round-robin warms every backend's JVM before the tests run. No-op overhead for
# single-backend scenarios.
warm_backends() {
    local url
    for url in "$@"; do
        echo "Warming $url ..."
        for _ in $(seq 1 20); do curl -fsk -o /dev/null "$url" || true; done
    done
}

report_failure() {
    local scenario="$1" id="$2" it="$3" comp="$4" transport="$5"
    local dir="$artifacts/$id"
    mkdir -p "$dir"
    cp -r integration-tests/target/failsafe-reports "$dir/" 2>/dev/null || true
    cp -r integration-tests/target/traces "$dir/" 2>/dev/null || true
    mv compose-logs.txt "$dir/" 2>/dev/null || true

    # Put the evidence in the job summary rather than in an artifact nobody
    # downloads: the first question on a red run is always "which one, and why",
    # and clicking through to a log to find out is the slowest possible answer.
    # It matters more now that one job carries several scenarios — the job name
    # no longer names the culprit.
    {
        echo "### ❌ \`$scenario\`"
        echo
        echo "tests: \`$it\` · compression: \`$comp\` · transport: \`$transport\`"
        echo
        echo "<details><summary>failed assertions</summary>"
        echo
        echo '```'
        grep -hA5 -E '^(Failures|Tests in error|\[ERROR\].*IT)' \
            "$dir"/failsafe-reports/*.txt 2>/dev/null | head -60 || echo "(no failsafe report)"
        echo '```'
        echo
        echo "</details>"
        echo
        echo "<details><summary>last 40 proxy/app log lines</summary>"
        echo
        echo '```'
        tail -n 40 "$dir/compose-logs.txt" 2>/dev/null || echo "(no compose logs)"
        echo '```'
        echo
        echo "</details>"
        echo
        echo "[⬇ full artifacts](${GITHUB_SERVER_URL:-}/${GITHUB_REPOSITORY:-}/actions/runs/${GITHUB_RUN_ID:-}#artifacts)"
        echo
    } >> "$summary"
}

run_scenario() {
    local scenario="$1" scheme="$2" port="$3" paths_json="$4"
    local compose_file="$scenario/docker-compose.yml"
    local id transport websocket
    local it="skipped" comp="skipped" status="fail" duration=0
    local urls=() path start

    id="${scenario//\//-}"

    # A scenario connects over the transport its own compose file configures:
    # the *-sse ones set VAADIN_PUSH_TRANSPORT=SERVER_SENT_EVENTS, the rest
    # bootstrap over WebSocket.
    #
    # The *-sse proxies cannot upgrade a connection, so Hilla's reactive
    # endpoints cannot work there: FluxConnection subscribes with transport and
    # fallbackTransport both 'websocket'. Those assertions are skipped.
    case "$scenario" in
        *-sse) transport="SERVER_SENT_EVENTS"; websocket="false" ;;
        *)     transport="WEBSOCKET_XHR,WEBSOCKET"; websocket="true" ;;
    esac

    while IFS= read -r path; do
        urls+=("$scheme://localhost:$port$path")
    done < <(printf '%s' "$paths_json" | jq -r '.[]')

    echo "::group::$scenario"

    # Reports and traces from the previous scenario in this chunk are still in
    # target/: failsafe does not clean between runs and run-test.sh does not ask
    # it to. Left in place they would be collected as this scenario's evidence.
    rm -rf integration-tests/target/failsafe-reports integration-tests/target/traces

    if docker compose -f "$compose_file" up -d --no-build \
        && wait_for_backends "$compose_file" \
        && wait_for_proxy "${urls[@]}"; then

        warm_backends "${urls[@]}"

        it="success"
        start=$SECONDS
        for url in "${urls[@]}"; do
            if ! ./run-test.sh "$url" -- \
                -Dit.push.transports="$transport" -Dit.websocket="$websocket"; then
                it="failure"
            fi
        done
        duration=$((SECONDS - start))

        # Every proxy config runs with compression on; what must never be
        # compressed is a streamed response. Asserts that an ordinary HTML
        # response is gzipped, that /sse-probe is not, and that its ticks still
        # arrive one at a time rather than in a burst at the end.
        #
        # This runs *after* the tests, and runs even when they failed. As a gate
        # in front of them it could skip the entire suite: runs 35136594973 and
        # 35138856877 were 63/63 jobs red with not one integration test
        # executed. "Compression broke the stream" and "the app does not work
        # through this proxy" are separate findings and neither may hide the
        # other.
        comp="success"
        for url in "${urls[@]}"; do
            if ! scripts/check-compression.sh "$url"; then
                comp="failure"
            fi
        done
    fi

    [[ $it == "success" && $comp == "success" ]] && status="pass"

    if [[ $status != "pass" ]]; then
        docker compose -f "$compose_file" logs --no-color > compose-logs.txt 2>&1 || true
        report_failure "$scenario" "$id" "$it" "$comp" "$transport"
        failed=$((failed + 1))
    fi

    docker compose -f "$compose_file" down -v || true

    jq -nc \
        --arg scenario "$scenario" \
        --arg transport "$transport" \
        --arg status "$status" \
        --arg tests "$it" \
        --arg compression "$comp" \
        --argjson duration "$duration" \
        '{scenario:$scenario, transport:$transport, status:$status,
          tests:$tests, compression:$compression, duration:$duration}' \
        >> "$results"

    echo "::endgroup::"
    echo "$scenario: $status (tests=$it compression=$comp ${duration}s)"
}

# --- the chunk ---------------------------------------------------------------

total="$(printf '%s' "$CHUNK" | jq length)"
[[ $total -gt 0 ]] || { echo "::error::CHUNK is empty"; exit 1; }
echo "Running $total scenario(s) in this job."

while IFS=$'\t' read -r scenario scheme port paths_json; do
    run_scenario "$scenario" "$scheme" "$port" "$paths_json"
done < <(printf '%s' "$CHUNK" \
    | jq -r '.[] | [.scenario, .scheme, .port, (.paths | tojson)] | @tsv')

echo
echo "Chunk finished: $((total - failed))/$total passed."
[[ $failed -eq 0 ]] || echo "::error::$failed scenario(s) failed in this job"
exit $(( failed > 0 ? 1 : 0 ))
