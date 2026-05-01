#!/usr/bin/env bash
# Regenerate the self-signed TLS cert used by HTTPS scenarios.
#
# The cert is for `localhost` (CN + SAN) with a 10-year validity. It is mounted
# read-only into Apache HTTPD and NGINX containers in:
#   apache-httpd/https/*, apache-httpd/ajp-https/*, nginx/https/*
#
# Browsers will warn about the self-signed CA — that is expected. The Playwright
# smoke suite skips cert validation when the base URL is https://.

set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

openssl req -x509 -newkey rsa:2048 \
    -keyout localhost.key -out localhost.crt \
    -sha256 -days 3650 -nodes \
    -subj "/CN=localhost" \
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

echo
echo "Wrote $(pwd)/localhost.crt and $(pwd)/localhost.key"
