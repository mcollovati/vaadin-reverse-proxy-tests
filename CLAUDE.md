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
- `integration-tests/` — IntelliJ module placeholder only; no actual tests yet.

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
- `TOMCAT_AJP_PORT` / `TOMCAT_AJP_ADDRESS` / `TOMCAT_AJP_SECRET` — `TomcatConfig.AJP` adds an
  AJP connector iff `tomcat.ajp.port` is set. `secretRequired=false` is forced when the secret
  is blank, otherwise the proxy must send a matching `secret=` on `ProxyPass`.
- `TOMCAT_JVMROUTE` — `TomcatConfig.JvmRoute` sets the engine `jvmRoute` so the session id
  gets a `.<route>` suffix; used by sticky-session load-balancer scenarios.
- `APP_NAME` — title shown in `MainLayout`, used by load balancer scenarios to distinguish
  which backend served the request.

`Application.java` also registers `/test-redirect` → `/hello-flow` to verify the proxy
preserves context paths through redirects. Any scenario reachable at `<proxy>/test-redirect`
should land on the Hello World Flow view at the proxy-relative URL.

## my-app development (without proxy)

```
cd my-app
./mvnw                                       # spring-boot:run, default goal
./mvnw clean package -Pproduction            # production jar in target/
./mvnw verify -Pit                           # integration tests profile (currently empty)
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
- `root-context-legacy` (Apache HTTP only) — kept for comparison with older config style.

When adding a new scenario, mirror an existing sibling: a `docker-compose.yml` that mounts
the shared `httpd.conf` (Apache) or a template (NGINX), plus the proxy-specific config file.

## Caveats picked up from existing configs

- Apache `RewriteRule` inside `<Location>` is officially unsupported; scenarios that need
  rewrites (e.g. AJP WebSocket upgrade) deliberately put rules at server scope, not in
  `<Location>`. Don't move them.
- `mod_proxy_html` is **not** loaded in the shared `httpd.conf`, so absolute URLs embedded
  in HTML bypass the proxy. Test pages and configs avoid relying on response-body rewriting.
- Apache's default WebSocket idle timeout is 60s; Vaadin PUSH heartbeats every 60s, which
  is right at the edge. If a scenario shows random push disconnects, raise `ProxyTimeout`
  or add `timeout=` to `ProxyPass` rather than chasing the symptom in app code. Same idea
  for NGINX `proxy_read_timeout`.
- Hilla endpoints are served from `/HILLA/*` and `/connect/*` regardless of
  `vaadin.url-mapping`. The `servlet-mapping*` configs need explicit `ProxyPassMatch` rules
  for those paths — don't assume the Vaadin URL mapping covers them.
