#!/usr/bin/env bash
# Run the integration-tests Playwright smoke tests against a given base URL.
#
# Assumes the app is already running (e.g. via docker compose for a proxy
# scenario, or `java -jar` for the raw app on :8080).

set -euo pipefail

usage() {
    cat <<EOF
Usage: $0 <base-url>
       $0 --help

  <base-url>  Base URL of the app under test (mandatory). Trailing slash
              required, since tests resolve view paths relative to it.

  Examples:
    $0 http://localhost:8080/
    $0 http://localhost:9090/
    $0 http://localhost:9090/app/
    $0 http://localhost:9090/ui/
EOF
}

if [[ ${1:-} == "--help" || ${1:-} == "-h" ]]; then
    usage
    exit 0
fi

if [[ $# -ne 1 ]]; then
    usage >&2
    exit 2
fi

base_url="$1"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cd "$repo_root/integration-tests"
exec ./mvnw -B verify -Dapp.base.url="$base_url"
