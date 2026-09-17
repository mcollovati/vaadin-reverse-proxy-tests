# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

This repo is **not** a deployable application. It is a collection of quick docker-compose
scenarios that exercise a single Vaadin/Spring Boot test app (`my-app/`) sitting behind
several reverse-proxy configurations (Apache HTTPD over HTTP and AJP, NGINX over HTTP).
The goal is to verify that Vaadin Flow + Hilla works correctly through each proxy
configuration — context paths, custom servlet mappings, custom PUSH URLs, load balancing,
multi-app routing. README.md is the canonical map of scenarios.

## Repository layout

- `my-app/` — the Vaadin 25 / Spring Boot 4 / Java 21 application that every scenario builds
  and proxies to. Built once and reused across all scenarios via Docker image tag `vaadin/my-app`.
- `apache-httpd/{http,ajp}/<scenario>/` — each leaf has `docker-compose.yml` + `vaadin.conf`.
  All scenarios share `apache-httpd/httpd.conf` (mounted read-only). That shared `httpd.conf`
  has `Include conf/extra/httpd-vaadin-*.conf`, so each scenario's `vaadin.conf` is mounted as
  `httpd-vaadin-http.conf` and picked up automatically.
- `nginx/http/<scenario>/` — `docker-compose.yml` + either an env var pointing at a shared
  template in `nginx/templates/`, or a scenario-local `default.conf.template`. NGINX templates
  are interpolated by the official image from `${VAADIN_PATH}`-style env vars.
- `integration-tests/` — standalone Maven module with a Playwright smoke suite
  (`BaseIT`, `AboutViewIT`, `HelloFlowIT`, `HelloHillaIT`). Driven by `run-test.sh`
  against an already-running scenario; parameterized over the push transports named
  by `-Dit.push.transports`.

## Running scenarios

```
cd <scenario-dir>          # e.g. apache-httpd/http/root-context
docker compose up          # builds my-app image on first run
docker compose build       # rebuild after changing my-app sources
docker compose down
```

Vaadin app is exposed on `http://localhost:8080`, the proxy on `http://localhost:9090`.
The proxy (port 9090) is the URL under test; 8080 is for direct comparison.

### Building my-app from a local Vaadin SNAPSHOT

`my-app/Dockerfile` does a full Maven build inside the image and only resolves from public
repos. To use a local Vaadin snapshot, build the jar on the host first, then build the
slim image that just copies it in:

```
cd my-app
mvn clean package -DskipTests          # produces target/myapp-1.0-SNAPSHOT.jar
docker build -f Dockerfile_localBuild -t vaadin/my-app .
```

After this, `docker compose up --no-build` reuses the locally tagged image.

### Selecting an image tag per scenario

Every `docker-compose.yml` references the app as
`image: vaadin/my-app:${MY_APP_VERSION:-latest}`, so multiple Vaadin versions can coexist
locally as different tags. Build them with `-Dvaadin.version=<X.Y>`:

```
cd my-app
mvn clean package -DskipTests -Dvaadin.version=25.1
docker build -f Dockerfile_localBuild -t vaadin/my-app:25.1 .
```

Then pick which one to run, either via the launcher (`./run-scenario.sh --app-version 25.1 …`)
or by setting the env var directly (`MY_APP_VERSION=25.1 docker compose up`). The default
remains `latest`, so existing invocations are unchanged.

## How `my-app` adapts to each scenario

Scenarios change the app's behavior through environment variables / Spring properties — the
Java code is the same image everywhere. Key knobs:

- `SERVER_SERVLET_CONTEXT_PATH=/app` — Spring Boot context path (used by `custom-context*`).
- `VAADIN_URL_MAPPING=/ui/*` — Vaadin servlet mapping (used by `servlet-mapping*`). When
  set, `Application.publicImagesAliasFilter` forwards `/<mapping>/icons/...` and
  `/<mapping>/images/...` back to the unmapped path so static assets resolve.
- `VAADIN_PUSH_URL=/VAADIN/push` — overrides the default push endpoint (used by `*-push-url`).
- `VAADIN_PUSH_TRANSPORT=SERVER_SENT_EVENTS` — `PushTransportConfigurer` sets both the
  transport and the *fallback* transport on every UI (used by `*-sse`). Setting the
  fallback is the point: otherwise Atmosphere silently degrades to long polling when a
  proxy blocks the transport, and a broken config looks like a working one.
- `TOMCAT_AJP_PORT` / `TOMCAT_AJP_ADDRESS` / `TOMCAT_AJP_SECRET` — `TomcatConfig.AJP` adds an
  AJP connector iff `tomcat.ajp.port` is set. `secretRequired=false` is forced when the secret
  is blank, otherwise the proxy must send a matching `secret=` on `ProxyPass`.
- `TOMCAT_JVMROUTE` — `TomcatConfig.JvmRoute` sets the engine `jvmRoute` so the session id
  gets a `.<route>` suffix; used by sticky-session load-balancer scenarios.
- `APP_NAME` — title shown in `MainLayout`, used by load balancer scenarios to distinguish
  which backend served the request.

`Application.java` also registers `/sse-probe`, a plain non-Vaadin event stream emitting
one tick every 500 ms. `curl -N <proxy-url>sse-probe` is the quickest way to tell whether
a proxy buffers a streaming response — ticks must trickle in, not arrive in a burst.

`Application.java` also registers `/test-redirect` → `/hello-flow` to verify the proxy
preserves context paths through redirects. Any scenario reachable at `<proxy>/test-redirect`
should land on the Hello World Flow view at the proxy-relative URL.

## my-app development (without proxy)

```
cd my-app
./mvnw                                       # spring-boot:run, default goal
./mvnw clean package -Pproduction            # production jar in target/
./mvnw verify -Pit                           # `it` profile; the real suite lives in integration-tests/
```

Java debug port is wired to 5684 via the spring-boot-maven-plugin's `jvmArguments`.

## Naming conventions for scenarios

Directory names encode what the scenario tests — internalize these before writing or
modifying configs:

The `*-to-*-context` names follow an **X-to-Y = proxy at X, backend at Y** pattern.

- `root-context` — app at `/`, proxy at `/`.
- `custom-context` — app at `/app`, proxy preserves the same path.
- `root-to-custom-context` — proxy at `/`, app on `/app` context (the public URL stays at root and the proxy adds the `/app` prefix when forwarding to the backend).
- `custom-to-root-context` — proxy at `/app/`, app at root `/` (the proxy strips the `/app` prefix before forwarding to the backend).
- `servlet-mapping` — app at `/` but Vaadin servlet on `/ui/*`; Hilla resources stay on root
  (see comment in `apache-httpd/http/servlet-mapping/vaadin.conf` and Hilla issue #289).
- `*-push-url` — same scenario but with `VAADIN_PUSH_URL` overridden, requiring an extra
  WebSocket-only proxy block.
- `load-balancer` — two `vaadin-1` / `vaadin-2` backends, sticky sessions via `jvmRoute`
  (Apache) or a synthesized `ROUTEID` cookie.
- `multiple-root-context*` — two independent apps mapped under different prefixes by NGINX
  using rewrite rules (no `SERVER_SERVLET_CONTEXT_PATH` on the backend).
- `*-sse` — same scenario but with PUSH over Server-Sent Events
  (`VAADIN_PUSH_TRANSPORT=SERVER_SENT_EVENTS`), and a proxy config that deliberately has
  **no** WebSocket support: no `upgrade=websocket`, no `ws://` worker, no `RewriteRule`
  on the `Upgrade` header. Needs an app image whose Flow has the SSE transport —
  Vaadin 25.3-SNAPSHOT / 25.4-SNAPSHOT or later, since the pom's pinned version predates
  it. CI gates them behind the `include_sse` workflow input. Selecting a WebSocket
  transport in one of these fails with a 501 from Atmosphere — that is the point of the
  scenario, not a bug.
  Hilla `Flux` endpoints cannot work in these either: Hilla push is WebSocket-only
  (`FluxConnection` sets transport and fallbackTransport both to `websocket`), so
  the ITs skip those assertions via `-Dit.websocket=false`.
- `root-context-legacy` (Apache HTTP only) — kept for comparison with older config style.

When adding a new scenario, mirror an existing sibling: a `docker-compose.yml` that mounts
the shared `httpd.conf` (Apache) or a template (NGINX), plus the proxy-specific config file.
Then add a row to `scenarios.tsv` and run `scripts/gen-readmes.sh` — the catalogue drives the
CI matrix, the per-scenario READMEs and `run-scenario.sh`'s picker, and a scenario missing
from it simply never runs. `scripts/check-catalog.sh` enforces both directions and runs in CI.
Reference the proxy image as `${HTTPD_IMAGE:-httpd:2.4.68}` / `${NGINX_IMAGE:-nginx:1.31.6}`
rather than a bare tag, and keep `.github/proxy-images.txt` in step.

## Continuous integration

`.github/workflows/_scenarios.yml` holds every step (matrix generation, image build,
readiness waits, tests, summary) and is called by thin per-proxy workflows — `apache.yml`,
`nginx.yml` — that carry only triggers and path filters, plus `all.yml` for a manual
full-matrix sweep and `lint.yml`. A new proxy tree needs a new caller, not a change to
`_scenarios.yml`.

- Proxy images are pinned and shipped to the test jobs inside the same tarball as the app
  image, so no job pulls from Docker Hub. An unpinned proxy makes a red run ambiguous —
  Vaadin regression, or an Apache upgrade? — which defeats the purpose of the repo.
- `scripts/check-compression.sh` runs **after** the integration tests and with
  `if: always()`, never before them. As a gate in front it could skip the whole suite:
  runs 35136594973 and 35138856877 were 63/63 jobs red with not one test executed.
- Backend containers are identified in the readiness wait by their image
  (`vaadin/my-app`), not by a `vaadin*` service name. A new tree that names its app service
  something else would otherwise get a wait step that silently succeeds without waiting.
- Each job writes one `results.jsonl` row; the `report` job aggregates them into a
  per-proxy table in the run summary and fails if any row is missing, so a cancelled or
  crashed job cannot turn a run green.

## Caveats picked up from existing configs

- Apache `RewriteRule` inside `<Location>` is officially unsupported; scenarios that need
  rewrites (e.g. AJP WebSocket upgrade) deliberately put rules at server scope, not in
  `<Location>`. Don't move them.
- `mod_proxy_html` is **not** loaded in the shared `httpd.conf`, so absolute URLs embedded
  in HTML bypass the proxy. Test pages and configs avoid relying on response-body rewriting.
- Apache's default WebSocket idle timeout is 60s; Vaadin PUSH heartbeats every 60s, which
  is right at the edge. The shared `httpd.conf` now sets `ProxyTimeout 300`, and every
  NGINX template sets `proxy_read_timeout 300s`, so this should no longer bite.
- Anything that buffers a response body breaks SSE push. `mod_proxy_ajp` buffers unless
  the worker carries `flushpackets=on`, and NGINX buffers and talks HTTP/1.0 upstream
  unless the location sets `proxy_buffering off` and `proxy_http_version 1.1`. All of
  these are set repo-wide — don't remove them when copying a config.
- Compression is **on** repo-wide (`mod_deflate` in the shared `httpd.conf`, `gzip on` in
  every NGINX template) but never over a stream. `text/event-stream` (SSE push) and
  `text/plain` (streaming/long polling — `PushHandler` sets that content type on the push
  response) are deliberately absent from `gzip_types` / `AddOutputFilterByType`, and Apache
  additionally sets `no-gzip` for requests that `Accept: text/event-stream` and for
  `/(VAADIN|HILLA)/push`. Compressing a stream does not stall it — both compressors flush
  per event — but it holds a deflate context open for the life of every push connection and
  it shrinks Atmosphere's 2000-byte SSE padding, the padding whose whole job is to force a
  buffering intermediary to flush, to a couple of dozen bytes.
  `scripts/check-compression.sh <base-url>` checks a running scenario: HTML compressed,
  `/sse-probe` not compressed, ticks still trickling.
- A `ProxyPass` to a `ws://` worker serves WebSocket **only**. Use
  `http://… upgrade=websocket` instead (httpd ≥ 2.4.47), which upgrades only when the
  client asks and proxies plain HTTP otherwise — SSE and long polling both need that,
  since they POST client-to-server messages to the push URL.
- Hilla endpoints are served from `/HILLA/*` and `/connect/*` regardless of
  `vaadin.url-mapping`. The `servlet-mapping*` configs need explicit `ProxyPassMatch` rules
  for those paths — don't assume the Vaadin URL mapping covers them.
- An NGINX `upstream` without a `zone` keeps peer health state **per worker process**. A
  backend that refused a connection while it was booting stays blacklisted (`max_fails=1`,
  `fail_timeout=10s` by default) only in the workers that saw the refusal, so a readiness
  probe answered by a clean worker proves nothing and a later request can still come back
  502 `no live upstreams`. Both `load-balancer*` templates declare
  `zone application_balancer 64k;` — keep it when copying one. The same startup race arms
  Apache's balancer (`retry`, 60s by default), so CI waits for every published backend port
  to accept a connection before any request reaches the proxy.
