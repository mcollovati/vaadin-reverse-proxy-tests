# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

This repo is **not** a deployable application. It is a collection of quick docker-compose
scenarios that exercise a single Vaadin/Spring Boot test app (`my-app/`) sitting behind
several reverse-proxy configurations (Apache HTTPD over HTTP and AJP, NGINX over HTTP,
Traefik over HTTP, HAProxy over HTTP).
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
- `traefik/{http,https,labels}/<scenario>/` — `docker-compose.yml` + `vaadin.yml` (the
  scenario's dynamic config), against a shared `traefik/traefik.yml` (static: entryPoints
  plus the file provider) mounted read-only. Deliberately the same split as Apache's
  `httpd.conf` + `vaadin.conf`, and deliberately not Docker labels: `gen-readmes.sh`
  inlines the proxy config file, and a label-configured scenario has none.
  `traefik/labels/root-context` is the single exception, documenting the idiom, and the
  only compose file in the repo that mounts the Docker socket.
- `haproxy/{http,https}/<scenario>/` — `docker-compose.yml` plus either a shared file from
  `haproxy/templates/` or a scenario-local `vaadin.cfg`. HAProxy has no `include`
  directive, so the split is done with mounts: the shared `haproxy/haproxy-base.cfg`
  becomes `conf.d/00-base.cfg`, the scenario config becomes `conf.d/10-vaadin.cfg`, and
  the container runs `haproxy -f /usr/local/etc/haproxy/conf.d`, which concatenates the
  directory in lexical order. Base and scenario only parse as a pair. The base carries two
  `defaults` sections because a named one holding `http-request` rules cannot be inherited
  by frontends *and* backends; scenarios write `frontend … from vaadin-front` and
  `backend … from vaadin`.
- `integration-tests/` — standalone Maven module with a Playwright smoke suite
  (`BaseIT`, `AboutViewIT`, `HelloFlowIT`, `HelloHillaIT`). Driven by `run-test.sh`
  against an already-running scenario; parameterized over the push transports named
  by `-Dit.push.transports`.
- `scripts/` — `gen-matrix.sh` (catalogue → CI matrix), `ci-run-chunk.sh` (the body of the
  CI test job: brings up, probes, tests and tears down each scenario in a chunk),
  `gen-readmes.sh`, `check-catalog.sh`, `check-compression.sh`. `lint.yml` shellchecks all
  of them, which is why CI logic lives here rather than inline in the workflow.

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
  gets a `.<route>` suffix. Available, but **no scenario sets it**: every sticky-session
  scenario has the proxy synthesize its own affinity cookie instead, and HAProxy's
  `load-balancer-cookie-prefix` decorates `JSESSIONID` without reading Tomcat's suffix.
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
  WebSocket-only proxy block in the Apache and NGINX trees. Not in `traefik/` or
  `haproxy/`: both relay an upgrade on whatever path it arrives on, so each config there
  is identical to its sibling and the identical config is the finding. In `haproxy/` the
  scenario mounts the sibling's *file*, not a copy of it. Note the public path —
  `VAADIN_PUSH_URL` resolves against the context, so under `custom-context` the browser
  asks for `/app/VAADIN/push`, not `/VAADIN/push`.
- `load-balancer` — two `vaadin-1` / `vaadin-2` backends, sticky sessions via a `ROUTEID`
  cookie that the proxy synthesizes itself in every tree. `TOMCAT_JVMROUTE` exists in
  `my-app` but no scenario currently sets it: Apache's balancer reads its own route id
  rather than Tomcat's, so nothing needs the suffix.
- `load-balancer-cookie-prefix` (HAProxy only) — the same two backends made sticky the
  other way round, with `cookie JSESSIONID prefix`: the proxy prepends its server id to
  Tomcat's session id on the way out (`JSESSIONID=v1~ABC123`) and strips it on the way
  in. The only scenario in the repo where a proxy rewrites the session id itself, which
  is why it earns a place beside `load-balancer`. `prefix` mode does **not** consume
  `TOMCAT_JVMROUTE` — the two are independent mechanisms that merely compose.
- `multiple-root-context*` — two independent apps mapped under different prefixes (no
  `SERVER_SERVLET_CONTEXT_PATH` on the backend): NGINX rewrite rules, `stripPrefix`
  in `traefik/`, or a per-backend `replace-path` in `haproxy/`. Distinct session cookie
  *names* are not enough, because both apps issue a `csrfToken` under the same name:
  Apache, NGINX and HAProxy rescope it by rewriting the `Set-Cookie` path, Traefik cannot
  and tells each app its public prefix instead.
- `*-sse` — same scenario but with PUSH over Server-Sent Events
  (`VAADIN_PUSH_TRANSPORT=SERVER_SENT_EVENTS`) through a proxy that cannot upgrade a
  connection. In the Apache and NGINX trees that is achieved by omission: no
  `upgrade=websocket`, no `ws://` worker, no `RewriteRule` on the `Upgrade` header.
  Traefik and HAProxy have no such omission available — both relay an upgrade with no
  configuration at all — so those scenarios **refuse** instead: Traefik with a `headers`
  middleware that deletes the client's `Upgrade` and `Connection` headers, HAProxy with
  `http-request deny deny_status 501 if { req.hdr(Upgrade) -m found }`. Same semantics,
  three mechanisms; when adding an `*-sse` scenario, check which one the tree calls for.
  Where a tree refuses, the 501 comes from the proxy rather than from Atmosphere.
  The transport needs no special app image any more: the pom pins 25.3.0-rc1 and
  `my-app/src/main/resources/vaadin-featureflags.properties` enables
  `com.vaadin.experimental.ssePushTransport`, so the default build has it. CI still gates
  these behind the `include_sse` workflow input. Selecting a WebSocket transport in one
  of them fails with a 501 — that is the point of the scenario, not a bug, and it was
  verified through all three mechanisms.
  Hilla `Flux` endpoints cannot work in these either: Hilla push is WebSocket-only
  (`FluxConnection` sets transport and fallbackTransport both to `websocket`), so
  the ITs skip those assertions via `-Dit.websocket=false`.
- `*-forwarded-prefix` (Traefik only) — same proxy config as its sibling, plus
  `SERVER_FORWARD_HEADERS_STRATEGY=FRAMEWORK` on the backend so Spring rebuilds the
  context path from `X-Forwarded-Prefix`. The only place in the repo using that strategy
  to repair what a proxy cannot rewrite on the way out.
- `root-context-legacy` (Apache HTTP only) — kept for comparison with older config style.

When adding a new scenario, mirror an existing sibling: a `docker-compose.yml` that mounts
the shared `httpd.conf` (Apache), a template (NGINX), the shared `traefik.yml`
(Traefik) or the shared `haproxy-base.cfg` (HAProxy), plus the proxy-specific config file.
Then add a row to `scenarios.tsv` and run `scripts/gen-readmes.sh` — the catalogue drives the
CI matrix, the per-scenario READMEs and `run-scenario.sh`'s picker, and a scenario missing
from it simply never runs. `scripts/check-catalog.sh` enforces both directions and runs in CI.
The row's last column is its `tier`: `smoke` if it is the only row in its tree testing that
mechanism (so every pull request runs it), `full` if a `smoke` row in the same tree would
almost certainly catch the same breakage (so it runs on the weekly sweep). Most new rows are
`full` — the smoke tier is 35 of 112 and is meant to stay that size. `check-catalog.sh`
rejects a missing or misspelled tier, because that failure is otherwise silent: the row just
quietly stops running on PRs.
Reference the proxy image as `${HTTPD_IMAGE:-httpd:2.4.68}` / `${NGINX_IMAGE:-nginx:1.31.6}`
/ `traefik:${TRAEFIK_VERSION:-v3.7}` / `${HAPROXY_IMAGE:-haproxy:3.2.23}` rather than a
bare tag, and keep `.github/proxy-images.txt` in step. For Traefik that tag is a floor as
well as a pin: `compress` stalled `text/event-stream` outright before 3.3.5.

## Continuous integration

`.github/workflows/_scenarios.yml` holds every step (matrix generation, image build,
readiness waits, tests, summary) and is called by thin per-proxy workflows — `apache.yml`,
`nginx.yml`, `traefik.yml`, `haproxy.yml` — that carry only triggers and path filters, plus
`all.yml` for the weekly and manual full-matrix sweep, and `lint.yml`. A new proxy tree needs
a new caller, not a change to `_scenarios.yml`.

- **A pull request or push runs the `smoke` tier only; the full catalogue runs weekly from
  `all.yml` and on `workflow_dispatch`.** All 112 scenarios on every event, one job each,
  was ~125 jobs and ~5 runner-hours per change. Don't restore that by widening the smoke
  tier scenario by scenario — if a tree needs more coverage on PRs, say why in the row's
  tier and keep the rest `full`.
- **One job runs a chunk of scenarios, not one.** Measured on run 35261791814, a
  per-scenario job spent ~87s on setup (checkout, image tarball, `docker load`, JDK,
  Playwright browser + system libs) against ~61s of scenario work. `scripts/gen-matrix.sh`
  emits `{name, proxy, scenarios[]}` chunked to 8 within a tree; `scripts/ci-run-chunk.sh`
  is the job body. It deliberately has no `set -e`: a failing scenario is data, and stopping
  at the first one would hide every scenario behind it. It also clears
  `integration-tests/target/{failsafe-reports,traces}` between scenarios — failsafe does not,
  and stale reports would be collected as the next scenario's evidence.
- Chunks never span proxy trees, so a job loads only its own tree's proxy image and the
  report job can keep grouping rows by tree.
- Proxy images are pinned and shipped to the test jobs inside the same tarball as the app
  image, so no job pulls from Docker Hub. An unpinned proxy makes a red run ambiguous —
  Vaadin regression, or an Apache upgrade? — which defeats the purpose of the repo.
  `.github/proxy-images.txt` is two columns, `<tree> <image>`: the tree column is what lets
  a per-proxy run ship one image instead of four, and it means a new tree still needs no
  change to `_scenarios.yml`.
- `scripts/check-compression.sh` runs **after** the integration tests and with
  `if: always()`, never before them. As a gate in front it could skip the whole suite:
  runs 35136594973 and 35138856877 were 63/63 jobs red with not one test executed.
- Backend containers are identified in `ci-run-chunk.sh`'s readiness wait by their image
  (`vaadin/my-app`), not by a `vaadin*` service name. A new tree that names its app service
  something else would otherwise get a wait step that silently succeeds without waiting.
- Each *scenario* writes one `results.jsonl` row, whatever happened to it; the `report` job
  aggregates them into a per-proxy table in the run summary and fails if any row is missing,
  so a cancelled or crashed job cannot turn a run green. That check matters more with
  several scenarios per job, not less: a job killed mid-chunk loses every row it had not
  written yet. The expected count is the scenario count, never the job count.
- Third-party actions are pinned to a commit SHA with the version in a trailing comment
  (`uses: actions/checkout@d23441a… # v6.1.0`); Dependabot updates both. `lint.yml`
  rejects any `uses:` that is not a 40-character SHA, local `./.github/workflows/…` refs
  excepted. The actionlint installer is pinned the same way — fetched from a tag, not
  `main`, and told which version to download.

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
- Traefik rewrites **nothing** in a response — no `ProxyPassReverse`, no
  `ProxyPassReverseCookiePath`, no `proxy_redirect`, no `proxy_cookie_path`. Anything the
  other two trees repair on the way out has to be arranged on the way in
  (`X-Forwarded-Prefix` plus `SERVER_FORWARD_HEADERS_STRATEGY=FRAMEWORK`), configured in
  the app (`SERVER_SERVLET_SESSION_COOKIE_PATH`), or documented as broken. The session
  cookie property is not a substitute for the forwarded prefix: it governs only the
  servlet container's cookie, while Vaadin's `csrfToken` cookie keeps the context path
  and Hilla then answers 401 to every endpoint call. `addPrefix` sends no
  `X-Forwarded-Prefix` of its own — only `stripPrefix` does — so where the proxy adds a
  prefix the header is set by hand with a `headers` middleware, and `"/"` is the value
  for "the public prefix is the root": `""` **removes** the header instead.
- Traefik's `PathPrefix` is a raw string prefix, so `` PathPrefix(`/app`) `` also matches
  `/application`. Every context-prefix rule in `traefik/` is
  `` PathPrefix(`/app/`) || Path(`/app`) `` — don't shorten it. `stripPrefix` leaves `/`
  rather than an empty path for the bare prefix, so no trailing-slash redirect is needed,
  and a `prefixes` list is a set of alternatives rather than a chain: stripping two
  segments means naming `"/app/ui"` as one string.
- Traefik's `compress` middleware is a **denylist** where `gzip_types` is an allowlist,
  and it does not skip `text/event-stream` on its own. With `minResponseBodyBytes`
  defaulting to 1024 an un-excluded stream is held until a kilobyte accumulates, so every
  scenario names `text/event-stream` and `text/plain` in `excludedContentTypes`.
- `traefik/traefik.yml` deliberately sets no `respondingTimeouts`, unlike
  `ProxyTimeout 300` and `proxy_read_timeout 300s`. The docs make that look wrong —
  `readTimeout` defaults to 60s and covers the whole request — but measured on 3.7.13
  neither a 75s trickled request body nor an idle WebSocket held for 100s is cut. The
  reasoning is in the file; don't add values without re-measuring.
- Traefik's TLS entryPoint serves HTTP/2, which the NGINX `listen 443 ssl` sibling does
  not, and it sends `X-Forwarded-Proto: wss` (not `https`) on a WebSocket upgrade. PUSH
  works anyway, because the client derives the push URL from the page load. When
  reproducing an upgrade by hand, `curl` must be given `--http1.1`: over h2 the
  `Connection` and `Upgrade` headers are forbidden and silently dropped, which makes a
  broken config look fine.
- An NGINX `upstream` without a `zone` keeps peer health state **per worker process**. A
  backend that refused a connection while it was booting stays blacklisted (`max_fails=1`,
  `fail_timeout=10s` by default) only in the workers that saw the refusal, so a readiness
  probe answered by a clean worker proves nothing and a later request can still come back
  502 `no live upstreams`. Both `load-balancer*` templates declare
  `zone application_balancer 64k;` — keep it when copying one. The same startup race arms
  Apache's balancer (`retry`, 60s by default), so CI waits for every published backend port
  to accept a connection before any request reaches the proxy.
- HAProxy has no `<Location>` / `location` construct: a frontend accepts every path and a
  backend carries every request it was given. A published prefix therefore has to be
  enforced (`http-request deny deny_status 404 unless is_app`), not declared, and four
  families of scenario collapse onto a single shared file under `haproxy/templates/` —
  including the whole `*-push-url` family, which mounts its sibling's file verbatim.
- HAProxy's `http-request` ruleset runs **before** `use_backend`. A `replace-path` in the
  frontend therefore erases the prefix the routing ACL matches on (`multiple-root-context`
  answers 503 `vaadin-in/<NOSRV>` that way), and an unconditional `http-request deny`
  denies the very paths the `use_backend` rules were meant to accept. Put the prefix strip
  in the backend and give the deny a condition. The same ordering rule is why the two
  `*-servlet-mapping` translations list their rewrites in opposite orders: specific first
  where the prefix is stripped, general first where it is added.
- Rewriting a response in `haproxy/` means raw `http-response replace-header` regexes over
  `Location` and `Set-Cookie`; there is no `proxy_cookie_path` equivalent. Two traps, both
  invisible to `haproxy -c`: a literal space in a character class is split by the config
  tokenizer before the regex engine sees it (`[ ]` → `missing terminating ]`; write `\s`),
  and Tomcat writes `Path=/app` with **no trailing slash**, so a rule anchored on `/app/`
  matches nothing and the browser silently stops sending the session cookie. Anchor on the
  end of the header value.
- A backslash does not continue a directive in a HAProxy config — the next line is parsed
  as a new keyword — so `compression type` and friends stay on one line. That list is an
  allowlist like NGINX's `gzip_types`, so the streamed types stay out by not being named.
- `haproxy/haproxy-base.cfg` carries `timeout tunnel`, which no other tree has an
  equivalent for: after an upgrade the connection stops being a request and that timeout
  governs it instead of `timeout client`/`timeout server`. It also sets
  `X-Forwarded-Proto` from `%[ssl_fc,iif(https,http)]`, which is correct for every
  scenario at once and is why `haproxy/https/*` needs no header rule of its own.
- `bind ssl crt <file>` loads the key from `<file>.key` when the PEM does not carry one,
  so `haproxy/https/*` mounts the repo's `tls/localhost.key` as `localhost.crt.key` and
  no combined PEM is generated. That bind offers HTTP/2 through ALPN by default, with the
  same `curl --http1.1` caveat as Traefik's.
