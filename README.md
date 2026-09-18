# Vaadin Application behind reverse proxy

[![Vaadin](https://img.shields.io/badge/dynamic/xml?url=https%3A%2F%2Fraw.githubusercontent.com%2Fmcollovati%2Fvaadin-reverse-proxy-tests%2Fmain%2Fmy-app%2Fpom.xml&query=%2F%2A%5Blocal-name%28%29%3D%27project%27%5D%2F%2A%5Blocal-name%28%29%3D%27properties%27%5D%2F%2A%5Blocal-name%28%29%3D%27vaadin.version%27%5D&label=Vaadin&color=00b4f0)](my-app/pom.xml)
[![Apache HTTPD](https://github.com/mcollovati/vaadin-reverse-proxy-tests/actions/workflows/apache.yml/badge.svg)](https://github.com/mcollovati/vaadin-reverse-proxy-tests/actions/workflows/apache.yml)
[![NGINX](https://github.com/mcollovati/vaadin-reverse-proxy-tests/actions/workflows/nginx.yml/badge.svg)](https://github.com/mcollovati/vaadin-reverse-proxy-tests/actions/workflows/nginx.yml)
[![Traefik](https://github.com/mcollovati/vaadin-reverse-proxy-tests/actions/workflows/traefik.yml/badge.svg)](https://github.com/mcollovati/vaadin-reverse-proxy-tests/actions/workflows/traefik.yml)
[![HAProxy](https://github.com/mcollovati/vaadin-reverse-proxy-tests/actions/workflows/haproxy.yml/badge.svg)](https://github.com/mcollovati/vaadin-reverse-proxy-tests/actions/workflows/haproxy.yml)
[![Lint](https://github.com/mcollovati/vaadin-reverse-proxy-tests/actions/workflows/lint.yml/badge.svg)](https://github.com/mcollovati/vaadin-reverse-proxy-tests/actions/workflows/lint.yml)

A collection of quick and dirty configurations to test Vaadin application behind
a reverse proxy.

WARNING: this repository does not contain complete configurations nor best practices.
The aim is to set up a Vaadin application behind a reverse proxy in an easy and quick way in order to ensure that main functionalities are working correctly. 
Configurations are not completely tested at the moment

Docker and docker-compose are used to set up environments.

Vaadin application is based on Spring Boot with embedded Tomcat.
An AJP connector is configured when `tomcat.ajp.port` is set;
use `tomcat.ajp.address` to bind the connector to the correct interface (
e.g `0.0.0.0` or `::`).

The repository
contains [docker compose](https://docs.docker.com/compose/reference/)
configuration files for the following scenarios, each one in its own
subdirectory:

* Vaadin application on ROOT context
* Vaadin application on ROOT context with custom PUSH url (`/VAADIN/push`)
* Vaadin application on custom servlet mapping (`/ui/*`)
* Vaadin application on custom servlet mapping (`/ui/*`) with custom PUSH
  url (`/VAADIN/push`)
* Two Vaadin application on ROOT context mapped by the reverse proxy with 
  different prefixes (`/ui1/` and `/ui2`)
* Any of the above with PUSH over Server-Sent Events instead of WebSocket,
  through a proxy configured with no WebSocket support at all (the `*-sse`
  directories — see [Server-Sent Events push](#server-sent-events-push))
 

The scenarios are replicated for the following reverse proxy configuration:

* Apache HTTPD
* Apache HTTPD with AJP
* Apache HTTPD over HTTPS (root-context only)
* Apache HTTPD with AJP and HTTPS termination (root-context only)
* NGINX
* NGINX over HTTPS (root-context only)
* Traefik
* Traefik over HTTPS (root-context and root-context-sse)
* Traefik configured with Docker labels (root-context only)
* HAProxy
* HAProxy over HTTPS (root-context and root-context-sse)

```
├── apache-httpd
│   ├── ajp
│   │   ├── custom-context
│   │   ├── custom-context-push-url
│   │   ├── custom-to-root-context
│   │   ├── custom-to-root-context-push-url
│   │   ├── custom-to-root-context-servlet-mapping
│   │   ├── custom-to-root-context-servlet-mapping-sse
│   │   ├── load-balancer
│   │   ├── root-context
│   │   ├── root-context-push-url
│   │   ├── root-context-push-url-sse
│   │   ├── root-context-sse
│   │   ├── root-to-custom-context
│   │   ├── root-to-custom-context-push-url
│   │   ├── root-to-custom-context-servlet-mapping
│   │   ├── servlet-mapping
│   │   └── servlet-mapping-push-url
│   ├── ajp-https
│   │   └── root-context
│   ├── http
│   │   ├── custom-context
│   │   ├── custom-context-push-url
│   │   ├── custom-context-sse
│   │   ├── custom-to-root-context
│   │   ├── custom-to-root-context-push-url
│   │   ├── custom-to-root-context-servlet-mapping
│   │   ├── custom-to-root-context-sse
│   │   ├── load-balancer
│   │   ├── load-balancer-sse
│   │   ├── root-context
│   │   ├── root-context-legacy
│   │   ├── root-context-push-url
│   │   ├── root-context-push-url-sse
│   │   ├── root-context-sse
│   │   ├── root-to-custom-context
│   │   ├── root-to-custom-context-push-url
│   │   ├── root-to-custom-context-servlet-mapping
│   │   ├── root-to-custom-context-sse
│   │   ├── servlet-mapping
│   │   ├── servlet-mapping-push-url
│   │   └── servlet-mapping-sse
│   └── https
│       ├── root-context
│       └── root-context-sse
├── nginx
│   ├── http
│   │   ├── custom-context
│   │   ├── custom-context-push-url
│   │   ├── custom-context-sse
│   │   ├── custom-to-root-context
│   │   ├── custom-to-root-context-push-url
│   │   ├── custom-to-root-context-servlet-mapping
│   │   ├── custom-to-root-context-sse
│   │   ├── load-balancer
│   │   ├── load-balancer-sse
│   │   ├── multiple-root-context
│   │   ├── root-context
│   │   ├── root-context-push-url
│   │   ├── root-context-push-url-sse
│   │   ├── root-context-sse
│   │   ├── root-to-custom-context
│   │   ├── root-to-custom-context-push-url
│   │   ├── root-to-custom-context-servlet-mapping
│   │   ├── root-to-custom-context-sse
│   │   ├── servlet-mapping
│   │   ├── servlet-mapping-push-url
│   │   └── servlet-mapping-sse
│   └── https
│       ├── root-context
│       └── root-context-sse
├── traefik
│   ├── traefik.yml (shared static config)
│   ├── http
│   │   ├── custom-context
│   │   ├── custom-context-push-url
│   │   ├── custom-context-sse
│   │   ├── custom-to-root-context
│   │   ├── custom-to-root-context-forwarded-prefix
│   │   ├── custom-to-root-context-push-url
│   │   ├── custom-to-root-context-servlet-mapping
│   │   ├── custom-to-root-context-sse
│   │   ├── load-balancer
│   │   ├── load-balancer-sse
│   │   ├── multiple-root-context
│   │   ├── root-context
│   │   ├── root-context-push-url
│   │   ├── root-context-push-url-sse
│   │   ├── root-context-sse
│   │   ├── root-to-custom-context
│   │   ├── root-to-custom-context-push-url
│   │   ├── root-to-custom-context-servlet-mapping
│   │   ├── root-to-custom-context-sse
│   │   ├── servlet-mapping
│   │   ├── servlet-mapping-push-url
│   │   └── servlet-mapping-sse
│   ├── https
│   │   ├── root-context
│   │   └── root-context-sse
│   └── labels
│       └── root-context
├── haproxy
│   ├── haproxy-base.cfg (shared base, mounted as conf.d/00-base.cfg)
│   ├── templates (config files shared by several scenarios)
│   │   ├── add-prefix.cfg
│   │   ├── passthrough.cfg
│   │   ├── passthrough-sse.cfg
│   │   ├── servlet-mapping.cfg
│   │   └── strip-prefix.cfg
│   ├── http
│   │   ├── custom-context
│   │   ├── custom-context-push-url
│   │   ├── custom-context-sse
│   │   ├── custom-to-root-context
│   │   ├── custom-to-root-context-push-url
│   │   ├── custom-to-root-context-servlet-mapping
│   │   ├── custom-to-root-context-sse
│   │   ├── load-balancer
│   │   ├── load-balancer-cookie-prefix
│   │   ├── load-balancer-sse
│   │   ├── multiple-root-context
│   │   ├── root-context
│   │   ├── root-context-push-url
│   │   ├── root-context-push-url-sse
│   │   ├── root-context-sse
│   │   ├── root-to-custom-context
│   │   ├── root-to-custom-context-push-url
│   │   ├── root-to-custom-context-servlet-mapping
│   │   ├── root-to-custom-context-sse
│   │   ├── servlet-mapping
│   │   ├── servlet-mapping-push-url
│   │   └── servlet-mapping-sse
│   └── https
│       ├── root-context
│       └── root-context-sse
│
└── my-app (VAADIN Application)
```

## Run a scenario

### Interactive launcher

The fastest path is the [`gum`](https://github.com/charmbracelet/gum)-based TUI:

```
./run-scenario.sh
```

It prompts for a reverse proxy, then a scenario, shows the scenario README in
a pager, and starts `docker compose up`. Press `Ctrl-C` to stop the
containers; you will be asked whether to also `docker compose down -v`. Pass
`--compose-down` to skip the prompt and always tear down.

By default the scenario runs `vaadin/my-app:latest`. To pick a different image
tag (for example to compare two Vaadin versions side by side), pass
`--app-version <tag>`:

```
./run-scenario.sh --app-version 25.1 apache-httpd/http/root-context
```

The flag sets the `MY_APP_VERSION` environment variable consumed by every
`docker-compose.yml` (`image: vaadin/my-app:${MY_APP_VERSION:-latest}`), so it
also works with plain `docker compose`:

```
MY_APP_VERSION=25.1 docker compose up
```

See [Use local Vaadin SNAPSHOT](#use-local-vaadin-snapshot) for how to produce
the tagged images.

### Manual

To test a configuration enter the specific directory and run `docker compose up`
.

Vaadin application will be reachable at `http://localhost:8080`, whereas the
proxy server can be accessed at `http://localhost:9090` (or
`https://localhost:9443` for the HTTPS scenarios under `apache-httpd/https`,
`apache-httpd/ajp-https`, and `nginx/https`).

If you change the Vaadin application (`my-app`), remember to rebuild the docker
image by typing `docker compose build`.

To destroy the containers created during the tests type `docker compose down`

### Per-scenario READMEs

Each scenario directory has its own `README.md` (auto-generated). The
descriptions live in [`scenarios.tsv`](./scenarios.tsv); regenerate the
READMEs with `scripts/gen-readmes.sh` after editing it or after changing the
underlying proxy config files.

That file is also the CI catalogue: its `tier` column decides whether a scenario
runs on every pull request or only on the weekly sweep. See
[Continuous integration](#continuous-integration).

## Run the smoke tests

The [`integration-tests/`](./integration-tests) module contains a Playwright
smoke suite that exercises the About view image, the Hello World Flow buttons
and the Hello Hilla buttons, parameterized over the push transports named by
`-Dit.push.transports` (`WEBSOCKET_XHR,WEBSOCKET` by default). It's the fastest
way to verify that a given proxy scenario actually works end to end.

The typical workflow uses two terminals: `run-scenario.sh` to bring up the
proxy + app stack, and `run-test.sh` to drive the suite against it.

```bash
# Terminal 1 — bring up a scenario
./run-scenario.sh apache-httpd/http/custom-context

# Terminal 2 — run the smoke tests against the running scenario
./run-test.sh http://localhost:9090/app/
```

`./run-test.sh <base-url>` invokes `mvn verify` in `integration-tests/` with
`-Dapp.base.url=<base-url>` and does not touch the running app or proxy. The
trailing slash in the URL matters — view paths are resolved relative to it.

The base URL must match what the scenario exposes; see the `paths` column in
[`scenarios.tsv`](./scenarios.tsv). Common shapes:

| Scenario | Test base URL |
|---|---|
| `root-context*`, `root-to-custom-context*`, `load-balancer` | `http://localhost:9090/` |
| `custom-context*`, `custom-to-root-context*` | `http://localhost:9090/app/` |
| `servlet-mapping*` | `http://localhost:9090/ui/` |
| `custom-to-root-context-servlet-mapping` | `http://localhost:9090/app/ui/` |
| `root-to-custom-context-servlet-mapping` | `http://localhost:9090/ui/` |
| `multiple-root-context` | both `http://localhost:9090/ui1/` and `/ui2/` |
| `https/*`, `ajp-https/*` | `https://localhost:9443/` |

### Sweeping every scenario

`./run-all.sh` brings each scenario up, waits for it, runs the suite
against every path it exposes, tears it down and prints a summary. Scenarios
share the same ports, so it is strictly sequential — the GitHub Actions matrix
does the same work in parallel on separate runners.

It does not build the app image; build it first and pass the tag.

```
docker build my-app -t vaadin/my-app:latest
./run-all.sh --exclude '\-sse$'      # the 44 WebSocket scenarios

docker build my-app --build-arg VAADIN_VERSION=25.4-SNAPSHOT -t vaadin/my-app:sse
./run-all.sh --app-version sse '\-sse$'   # the 19 SSE scenarios
```

`--dry-run` lists what would run without starting anything. Like the CI job, the
push transport comes from the scenario name: `*-sse` scenarios run over
`SERVER_SENT_EVENTS` with `-Dit.websocket=false`, everything else over the
WebSocket transports.

To smoke-test the raw app (no proxy) on `:8080`, build and start it directly:

```bash
cd my-app
./mvnw clean package -DskipTests
java -jar target/myapp-1.0-SNAPSHOT.jar &
./run-test.sh http://localhost:8080/
```

The first run downloads Chromium (~1 min, cached under `~/.cache/ms-playwright`).

### Continuous integration

The same suite runs on GitHub Actions. The logic lives in a single reusable
workflow; the rest are thin callers that decide *when* it runs.

| Workflow | Runs on | Scope |
|---|---|---|
| `_scenarios.yml` | called by the others | every step: matrix, image build, readiness waits, tests, summary |
| `apache.yml` | PR + push to `main` touching `apache-httpd/**` or anything shared; manual | the Apache tree |
| `nginx.yml` | PR + push to `main` touching `nginx/**` or anything shared; manual | the NGINX tree |
| `traefik.yml` | PR + push to `main` touching `traefik/**` or anything shared; manual | the Traefik tree |
| `haproxy.yml` | PR + push to `main` touching `haproxy/**` or anything shared; manual | the HAProxy tree |
| `all.yml` | weekly (Mondays 03:17 UTC); manual | every proxy tree at once, full catalogue |
| `lint.yml` | PR + push to `main` | actionlint, shellcheck, hadolint, catalogue/README consistency |

A PR that only touches one proxy tree runs only that tree's jobs. Anything
shared — `my-app/`, `integration-tests/`, `scripts/`, `scenarios.tsv`, `tls/` —
triggers every tree.

#### Two tiers, and several scenarios per job

Running all 112 scenarios on every event, one job each, cost ~125 jobs and
around five runner-hours per change. Two things cut that to ~20 jobs without
dropping a scenario from the catalogue:

**The `tier` column in `scenarios.tsv`.** A pull request or a push runs the
`smoke` tier — one scenario per distinct *mechanism* in a tree: rewrite
direction, servlet mapping, load balancing, TLS, a transport that cannot
upgrade. 35 rows of 112. The other 77 are variations whose breakage a smoke row
in the same tree would almost certainly catch too, and they run on the weekly
sweep and on `workflow_dispatch`. When adding a scenario that tests something no
other row in its tree does, mark it `smoke`.

**Chunking.** One job runs several scenarios in sequence rather than one each.
Measured on a real run, a per-scenario job spent ~87s on setup — checkout, the
image tarball, the JDK, Playwright's browser and its system libraries — against
~61s of actual scenario work, so over half of every job was overhead paid 112
times. `scripts/gen-matrix.sh` groups the catalogue into chunks of 8 within a
proxy tree; `scripts/ci-run-chunk.sh` brings each scenario up, probes it, tests
it, tears it down and moves on. It keeps going after a failure — which
scenarios broke is the whole output of this repo — and the job goes red at the
end if any did.

To get back to one job per scenario when bisecting a flake, dispatch a workflow
with `chunk_size: 1`.

```bash
# the full catalogue, on demand
gh workflow run all.yml

# one tree, every scenario, one job each
gh workflow run apache.yml -f tier=all -f chunk_size=1
```

Each run ends with a per-proxy pass/fail table in the workflow summary. A failed
scenario puts its failed assertions and the last 40 lines of proxy and app logs
straight into that summary — the job name no longer names the culprit, so this
matters more than it did — and uploads a Playwright trace (open it with
`npx playwright show-trace <file>`), the failsafe reports and the full compose
logs as an artifact. Passing scenarios upload nothing but a one-line result row.
The report job cross-checks the rows against the expected scenario count, so a
job killed mid-chunk cannot turn a run green.

Proxy images are pinned (see `.github/proxy-images.txt`) and shipped to the test
jobs in the same tarball as the app image, so no job pulls from Docker Hub. This
matters beyond speed: with `httpd:latest` a red run could be a Vaadin regression
or an Apache upgrade, with no way to tell after the fact. A per-tree run ships
only its own tree's image. Override one for a local sweep without editing
anything:

```bash
HTTPD_IMAGE=httpd:2.4.67 docker compose up
```

## Use local Vaadin SNAPSHOT

To use local Vaadin SNAPSHOTS you must build the application locally and then
build the docker image.

Go to `my-app` folder and build the application for production

```
mvn clean package -DskipTests
```

Then build the docker image and tag it as `vaadin/my-app`

```
docker build -f Dockerfile_localBuild -t vaadin/my-app .
```

### Multiple Vaadin versions side by side

To compare scenarios across Vaadin versions, build one image per version and
tag each with the version. The `vaadin.version` Maven property selects the
Vaadin BOM:

```
cd my-app
mvn clean package -DskipTests -Dvaadin.version=25.1
docker build -f Dockerfile_localBuild -t vaadin/my-app:25.1 .

mvn clean package -DskipTests -Dvaadin.version=25.2-SNAPSHOT
docker build -f Dockerfile_localBuild -t vaadin/my-app:25.2 .
```

Then pick a tag at run time:

```
./run-scenario.sh --app-version 25.1 apache-httpd/http/root-context
./run-scenario.sh --app-version 25.2 apache-httpd/http/root-context
```

### Building an SSE-capable image

The Server-Sent Events push transport landed in
[vaadin/flow#24484](https://github.com/vaadin/flow/pull/24484), merged to Flow
`main`, so it ships in the `25.3` line and later. The pom now pins
`25.3.0-rc1`, and `my-app/src/main/resources/vaadin-featureflags.properties`
sets `com.vaadin.experimental.ssePushTransport=true`, so **the default build
already carries the transport** — the app logs it among its enabled feature
previews at startup. The `*-sse` scenarios need no special image.

An explicit build is still useful for testing the transport against a different
Flow:

```
docker build my-app --build-arg VAADIN_VERSION=25.4-SNAPSHOT -t vaadin/my-app:sse
./run-scenario.sh --app-version sse nginx/http/root-context-sse
```

### Overriding the Flow version

`flow.version` imports `flow-bom` on top of the platform BOM, so the app can be
built against a Flow branch snapshot without moving the whole platform. Use it
to test a Flow PR, or when a platform snapshot has not yet picked up a Flow
change you need:

```
docker build my-app --build-arg VAADIN_VERSION=25.4-SNAPSHOT \
                    --build-arg FLOW_VERSION=25.4.some-branch-SNAPSHOT \
                    -t vaadin/my-app:branch
```

The same pair works for a local build (`mvn clean package -DskipTests
-Dvaadin.version=… -Dflow.version=…`, then `docker build -f
Dockerfile_localBuild`). CI exposes both as the `vaadin_version` and
`flow_version` workflow inputs.

## Server-Sent Events push

The `*-sse` scenarios proxy Vaadin PUSH over Server-Sent Events instead of
WebSocket. Their proxy configuration deliberately has **no** WebSocket support
at all — no `upgrade=websocket`, no `ws://` worker, no `Upgrade`/`Connection`
headers — which is the case the transport exists for: a proxy that blocks or
drops WebSocket but passes plain HTTP streaming.

Two properties of the transport drive the configuration:

- The browser receives messages over an `EventSource`, an ordinary GET that
  streams `text/event-stream`. Nothing has to be upgraded.
- Client-to-server messages are sent as **XHR POSTs to the push URL**, because
  Flow only sets `alwaysXhrToServer` for `WEBSOCKET_XHR`. A rule that points the
  push path at a `ws://` worker therefore breaks both directions.

The backends in these scenarios run with `VAADIN_PUSH_TRANSPORT=SERVER_SENT_EVENTS`,
which also sets the *fallback* transport to SSE. Without that, Atmosphere
quietly degrades to long polling when a proxy blocks the event stream, and a
broken configuration would look like a working one.

### Two Flow behaviours that shape how push is configured here

**1. Changing the transport needs a reconnect — documented, not a bug.**
`PushConfiguration.setTransport` states: *"Note that the new transport type will
not be used until the push channel is disconnected and reconnected if already
active."* The client copies the transport into the Atmosphere configuration in
`AtmospherePushConnection.init()` and watches only `pushMode` for changes.

`MainLayout.switchTransport` therefore does the documented reconnect: it sets
the transport, then toggles push off and on across two client round-trips (the
`executeJs` hop). Both toggles inside one response would not work — the value
would be unchanged as far as the client is concerned, so no change event fires.

**2. A `UIInitListener` cannot override the `@Push` transport.** In the
client-routing bootstrap, `BootstrapContext.getPageConfigurationAnnotation`
finds no `@Push` during `BootstrapHandler.createAndInitUI`, so the AppShell
annotation is applied later by `JavaScriptBootstrapHandler.createAndInitUI` via
`AppShellRegistry.modifyPushConfiguration` — *after* `UIInitEvent` has fired.
A transport set from a UI init listener is therefore overwritten with the
annotation's value. The *fallback* transport survives, since that method only
touches push mode and transport, so push connects over WebSocket and reaches the
intended transport only after Atmosphere exhausts its retries.

This one looks like an ordering defect rather than intended behaviour, and it is
worth reporting. The app sidesteps it: `Application` carries no `@Push`
annotation, push is enabled with `vaadin.pushMode = automatic` in
`application.properties`, and `modifyPushConfiguration` is a no-op when there is
no annotation to apply — so the transport chosen in the UI init listener
survives and the first push connection uses it.

### Hilla reactive endpoints do not work over SSE

Hilla's push is a separate Atmosphere connection on `/HILLA/push` that Flow's
`PushConfiguration` does not control, and
[`FluxConnection.ts`](https://github.com/vaadin/hilla/blob/main/packages/ts/frontend/src/FluxConnection.ts)
subscribes with `transport: 'websocket'` **and** `fallbackTransport: 'websocket'`.
Hilla push is therefore WebSocket-only, with no fallback and no SSE option.

So an SSE-only proxy carries Flow push but cannot carry Hilla `Flux` endpoints.
Ordinary Hilla browser-callables are unaffected — they are plain HTTP requests.
`HelloHillaIT` keeps its non-push assertions and skips the streaming ones when
`-Dit.websocket=false`, which is what the `*-sse` scenarios pass.

### Expected failures in the `*-sse` scenarios

Selecting `WEBSOCKET` or `WEBSOCKET_XHR` in the drawer of an `*-sse` scenario
fails with `Error during WebSocket handshake: Unexpected response code: 501`.
That is the scenario working as intended. `Upgrade` and `Connection` are
hop-by-hop headers that nginx drops unless a location re-adds them, and the SSE
templates deliberately do not, so the backend sees a request claiming the
WebSocket transport without an actual upgrade and Atmosphere answers 501
`Websocket protocol not supported` (`AsynchronousProcessor.action`).

### Debugging a buffering proxy

The app exposes `/sse-probe`, a plain event stream (no Vaadin involved) that
emits one tick every 500 ms for 30 seconds:

```
curl -N http://localhost:9090/sse-probe   # through the proxy
curl -N http://localhost:8080/sse-probe   # straight to the app
```

Ticks must trickle in one at a time. If they arrive in a burst at the end, the
proxy is buffering the response — on nginx that means `proxy_buffering off` is
missing, on Apache AJP that `flushpackets=on` is missing.

### Running the tests over SSE

The transport-parameterized tests take their transport list from
`it.push.transports`:

```
./run-test.sh http://localhost:9090/ -- -Dit.push.transports=SERVER_SENT_EVENTS
```

## Compression

Every proxy in this repo runs with compression on — `mod_deflate` on Apache,
`gzip on` on nginx — because that is how a real deployment runs, and a config
that only works with compression disabled is not one anyone can copy. What none
of them compress is a **streamed** response.

Vaadin PUSH answers with `text/event-stream` over SSE and with `text/plain`
over streaming and long polling (`PushHandler` sets that content type when the
connection is established), so neither type is in `gzip_types` /
`AddOutputFilterByType`. Apache additionally skips compression for any request
that asks for `text/event-stream` and for the push endpoints, whatever they
answer with:

```apache
SetEnvIfNoCase Accept      text/event-stream          no-gzip=1
SetEnvIfNoCase Request_URI "/(VAADIN|HILLA)/push"     no-gzip=1
```

Compressing a stream does *not* stall it by itself: both `mod_deflate` and
nginx's gzip filter flush per event, so the ticks still trickle in as long as
`proxy_buffering off` (nginx) and `flushpackets=on` (AJP) are in place. Two
things make it a bad idea anyway:

- a deflate context stays allocated for the whole life of every push
  connection, which is the whole life of every open browser tab;
- Atmosphere writes 2000 bytes of padding when an SSE connection opens,
  precisely so that a buffering intermediary is forced to flush. Compressed,
  those 2000 identical characters become a couple of dozen bytes and the
  padding stops doing its job — so the setup breaks the moment another
  buffering hop appears in front of the proxy.

### Checking it on a running scenario

```
scripts/check-compression.sh http://localhost:9090/
scripts/check-compression.sh https://localhost:9443/   # https scenarios
```

It asserts that an ordinary HTML response *is* compressed (otherwise the rest
proves nothing), that `/sse-probe` is *not*, and that its ticks still arrive one
at a time.

## Apache HTTPD Notes

For simplicity, the proxy configuration are set in a `<Location>` section, so
the `ProxyPass` directives obtains the path from the `<Location>`, 
e.g. `ProxyPass http://vaadin:8080/`.
If the configuration has to be used in other sections, the path should be
explicitly specified, e.g. `ProxyPass /app/ http://vaadin:8080/app/`.

However, usage of `RewriteRule` in Location is discouraged and should be avoided.
From the Apache HTTPS documentation:

> Although rewrite rules are syntactically permitted in `<Location>` and `<Files>`
> sections (including their regular expression counterparts), this should never
> be necessary and is unsupported. A likely feature to break in these contexts
> is relative substitutions.

For the mentioned reason, the example that requires rewrite rules do not make
use of `<Location>` directive.

Only `Location`, `Content-Location` and `URI` headers in the HTTP response
will be rewritten. Apache httpd will not rewrite other response headers,
nor will it by default rewrite URL references inside HTML pages.
This means that if the proxied content contains absolute URL references,
they will bypass the proxy. To rewrite HTML content to match the proxy,
you must load and enable `mod_proxy_html`.

By default, the websocket connection will be closed if the proxied server does
not transmit any data within 60 seconds. Vaadin PUSH is configured to
periodically send heartbeat messages over WebSocket every 60 seconds, so the
connection should not be closed.
If the default is not working correctly, the timeout can be increased setting the
`timeout` parameter in the `ProxyPass` directive (e.g. `ProxyPass / http://vaadin:8080/ upgrade=websocket timeout=90`)
or by configuring the `ProxyTimeout` directive (e.g. `ProxyTimeout 90`).
The shared `apache-httpd/httpd.conf` sets `ProxyTimeout 300` so every scenario
sits clear of that edge.

`mod_proxy_ajp` buffers the response body by default, which holds back a
streaming response such as Server-Sent Events. Every AJP worker in this repo
therefore carries `flushpackets=on`.

## nginx Notes

By default, the websocket connection will be closed if the proxied server does
not transmit any data within 60 seconds. Vaadin PUSH is configured to
periodically send heartbeat messages over WebSocket every 60 seconds, so the
connection should not be closed. 
If the default is not working correctly, the timeout can be increased with the
`proxy_read_timeout` directive.

nginx buffers proxied responses and talks HTTP/1.0 upstream unless told
otherwise. Neither matters for a WebSocket tunnel, but both break a streaming
HTTP response: without `proxy_http_version 1.1` and `proxy_buffering off`, an
SSE event stream never reaches the browser incrementally. Every template in this
repo sets both, plus an explicit `proxy_read_timeout 300s`.
## Traefik Notes

Traefik is in this repo for one reason above the others: it is the only proxy
here that **rewrites nothing on the way out**. There is no `ProxyPassReverse`,
no `ProxyPassReverseCookiePath`, no `proxy_redirect` and no
`proxy_cookie_path`. Whatever the other two trees quietly repair in a response
has to be arranged some other way — by telling the backend the truth on the way
in, by configuring the app, or by documenting the breakage. That is also how
every Kubernetes ingress behaves, so the scenarios that fail here are worth as
much as the ones that pass.

Configuration is split the way Apache's is: a shared static
[`traefik/traefik.yml`](./traefik/traefik.yml) with the entryPoints and the
file provider, mounted read-only, plus one `vaadin.yml` of dynamic
configuration per scenario. The Docker socket is not mounted anywhere except
[`traefik/labels/root-context`](./traefik/labels/root-context), which exists to
document the label idiom and says why the rest of the tree does not use it.

Pin the version with `TRAEFIK_VERSION`, the way `MY_APP_VERSION` pins the app:

```
TRAEFIK_VERSION=v3.6 docker compose up
```

`v3.7` is a floor as well as a default. Between v3.0 and v3.3.4 the `compress`
middleware stalled a `text/event-stream` response outright
([traefik#11583](https://github.com/traefik/traefik/pull/11583)), which would
break every `*-sse` scenario here.

### There is no WebSocket configuration, and that cuts both ways

Traefik upgrades on any route when the client asks, and proxies plain HTTP
otherwise. Nothing in this tree configures that — compare Apache's
`upgrade=websocket` and NGINX's `map $http_upgrade` plus
`Upgrade`/`Connection` headers.

Two consequences:

* The `*-push-url` scenarios need no rule for the relocated endpoint, so each
  one's config is identical to its sibling. The scenarios are kept anyway: they
  still exercise `VAADIN_PUSH_URL` end to end, and the identical config is the
  finding. Note that `VAADIN_PUSH_URL` is resolved against the **context**, so
  under `custom-context` the browser asks for `/app/VAADIN/push` rather than
  `/VAADIN/push`.
* The `*-sse` scenarios cannot simply omit upgrade support the way their Apache
  and NGINX siblings do. They have to refuse it, with a `headers` middleware
  that deletes the client's `Upgrade` and `Connection` headers (an empty value
  removes a header rather than setting it empty). Atmosphere then answers
  `501 Websocket protocol not supported`, while the same request sent straight
  to the backend still completes a handshake.

### `PathPrefix` is a raw string prefix

``PathPrefix(`/app`)`` also matches `/application`. Every context-prefix rule
in this tree is therefore written as ``PathPrefix(`/app/`) || Path(`/app`)``,
which matches the prefix and its bare form and nothing else.

`stripPrefix` leaves `/` rather than an empty path when the request is for the
bare prefix, so no redirect rule is needed to add a trailing slash — unlike the
NGINX `multiple-root-context` template, which rewrites `^(/ui1)$` to add one.

### Compression is a denylist here

Every NGINX template lists the types to compress (`gzip_types`); Traefik's
`compress` middleware compresses everything except what
`excludedContentTypes` names. The streamed types have to be listed explicitly,
in every scenario:

```yaml
compress:
  compress:
    excludedContentTypes:
      - text/event-stream   # SSE push
      - text/plain          # streaming and long polling
```

Leaving them out does not merely compress a stream — `minResponseBodyBytes`
defaults to 1024, so the middleware holds the response until a kilobyte has
accumulated, which for a push channel can be a long time.

### What the missing response rewriting costs, scenario by scenario

* `custom-to-root-context` answers `/app/test-redirect` with a `Location` that
  has lost the prefix. Kept as-is, deliberately.
  `custom-to-root-context-forwarded-prefix` is the same proxy configuration
  plus `SERVER_FORWARD_HEADERS_STRATEGY=FRAMEWORK` on the backend, which reads
  the `X-Forwarded-Prefix` that `stripPrefix` already sends and gets the
  redirect right. Comparing the two is the point.
* `root-to-custom-context` needs three things: the session cookie scoped to the
  public path with `SERVER_SERVLET_SESSION_COOKIE_PATH`, an
  `X-Forwarded-Prefix: /` middleware (`addPrefix` sends no such header of its
  own), and `FRAMEWORK`. The cookie path alone is not enough — it governs only
  the servlet container's session cookie, while Vaadin's `csrfToken` cookie
  stays on `/app`, and Hilla cannot read a cookie the browser never sends, so
  every endpoint call comes back `401`.
* `multiple-root-context` cannot lean on distinct session cookie names alone,
  because both apps issue a `csrfToken` under the same name and would clobber
  each other's. Each app is told its public prefix instead.

### Timeouts are deliberately absent

The other two trees carry `ProxyTimeout 300` and `proxy_read_timeout 300s`
because their defaults cut an idle stream. Traefik's documented default
`readTimeout` is 60s and covers "the maximum duration for reading the entire
request, including the body", which looks like the same problem and is what
reports of streams severed at exactly 60s point at
([traefik#10652](https://github.com/traefik/traefik/issues/10652)).

Measured on 3.7.13, neither shape is actually cut: a request whose body
trickles in over 75s returns 200 at 76s, and an idle WebSocket is still open
after 100s, through a proxy with no timeout configuration at all. Vaadin's PUSH
heartbeat is every 60s, so a push connection is never idle that long in the
first place. If a cut does show up, `readTimeout` and `idleTimeout` of 300s per
entryPoint is the fix.

### HTTP/2 on the TLS entryPoint

`traefik/https/*` is served over HTTP/2, which the NGINX sibling
(`listen 443 ssl`) is not. WebSocket upgrades still happen over HTTP/1.1, since
HTTP/2 forbids the `Connection` and `Upgrade` headers; browsers open a separate
HTTP/1.1 connection for them. Worth knowing when reproducing anything by hand:
`curl` negotiates h2 and will silently drop an `Upgrade` header unless it is
given `--http1.1`.

On an upgrade Traefik sends `X-Forwarded-Proto: wss` rather than `https`.
Tomcat's `RemoteIpValve` only treats `https` as secure, so `request.isSecure()`
is false for that request; PUSH works regardless, because the client builds the
push URL from the page load, which does carry `https`.

## HAProxy Notes

HAProxy is here because it is the engine under OpenShift's router and
`haproxy-ingress`, so it is where a lot of enterprise Vaadin deployments
actually land — and because it is the only proxy in this repo with **no
`<Location>` / `location` construct at all**. A frontend accepts every path and
a backend carries every request it was given, which changes what a scenario
even is here: several families collapse onto a single config file, and a
published prefix has to be *enforced* rather than declared.

Configuration is split the way Apache's is, but the mechanism is different
because HAProxy has no `include` directive. Each scenario mounts two files into
the same directory —

```
haproxy/haproxy-base.cfg   -> conf.d/00-base.cfg
<the scenario's config>    -> conf.d/10-vaadin.cfg
```

— and the container runs `haproxy -f /usr/local/etc/haproxy/conf.d`, which
concatenates the directory in lexical order. Base and scenario only parse as a
pair. Pin the version with `HAPROXY_IMAGE`, the way `MY_APP_VERSION` pins the
app:

```
HAPROXY_IMAGE=haproxy:3.1 docker compose up
```

### 24 scenarios, 16 config files

Five files under [`haproxy/templates/`](./haproxy/templates) are mounted by
more than one scenario, and that sharing is a finding rather than a convenience:

| file | mounted by |
|---|---|
| `passthrough.cfg` | `root-context`, `root-context-push-url`, `custom-context`, `custom-context-push-url` |
| `passthrough-sse.cfg` | `root-context-sse`, `root-context-push-url-sse`, `custom-context-sse` |
| `strip-prefix.cfg` | `custom-to-root-context`, `custom-to-root-context-push-url` |
| `add-prefix.cfg` | `root-to-custom-context`, `root-to-custom-context-push-url` |
| `servlet-mapping.cfg` | `servlet-mapping`, `servlet-mapping-push-url` |

A context path needs no rule because nothing here is scoped to a path, and a
relocated PUSH endpoint needs none because an HTTP/1.1 upgrade is relayed on
whichever path the client asks to upgrade. The Apache and NGINX siblings each
carry a second block for `VAADIN_PUSH_URL` only because that is where
`upgrade=websocket` / the `Upgrade` header lived. Note that `VAADIN_PUSH_URL`
is resolved against the **context**, so under `custom-context` the browser asks
for `/app/VAADIN/push`.

### The `*-sse` scenarios deny the upgrade, they do not omit it

Apache and NGINX refuse an upgrade by *omission*: `Upgrade` and `Connection`
are hop-by-hop headers that neither forwards unless a directive puts them back,
and the `*-sse` configs simply do not. HAProxy relays an upgrade with no
configuration at all and has no keyword to switch that off, so the refusal is
written out:

```
http-request deny deny_status 501 if { req.hdr(Upgrade) -m found }
```

The browser console shows the same thing either way
(`Error during WebSocket handshake: Unexpected response code: 501`), but the
501 comes from the proxy rather than from Atmosphere. Traefik has the same
problem and solves it a third way, by deleting the client's `Upgrade` header.

### Rewriting a response means raw regexes, and two of them bite

There is no `ProxyPassReverse`, no `ProxyPassReverseCookiePath`, no
`proxy_redirect` and no `proxy_cookie_path`. The prefix-translating scenarios
put the public URL back with `http-response replace-header` over `Location` and
`Set-Cookie`, which works — and has two traps that a config check cannot see:

* **A literal space in a character class breaks the config parser** before the
  regex engine ever sees it: `(.*;[ ]*[Pp]ath=)` is rejected with
  `missing terminating ] for character class`. Write `\s`.
* **Tomcat writes `Path=/app`, with no trailing slash.** A rule anchored on
  `/app/` matches nothing, `replace-header` silently does nothing, and the
  browser at `/` stops sending the session cookie back — every view then times
  out. The rules in this tree are anchored on the *end* of the header value for
  that reason.

A rewrite that matches nothing looks exactly like a rewrite that was never
needed, so both templates carry a comment saying so.

### `http-request` rules run before `use_backend`

This decides the shape of `multiple-root-context`, where two apps are published
under `/ui1` and `/ui2`:

* A prefix strip in the frontend would erase the prefix the routing ACL matches
  on, and every request answers `503` with `vaadin-in/<NOSRV>` in the log. Each
  backend therefore strips its own prefix, which a backend is allowed to do.
* An unconditional `http-request deny` meant as "nothing outside the two
  prefixes exists" denies the two prefixes as well, for the same reason. It has
  to carry the condition.

The same ordering rule is why the two `*-servlet-mapping` context translations
list their rewrite rules in opposite orders — most specific first in one
direction, general first in the other — with a comment in each explaining which
and why.

### Compression is an allowlist, and other things the base carries

`compression type` names the types to compress, like NGINX's `gzip_types` and
unlike Traefik's denylist, so `text/event-stream` (SSE push) and `text/plain`
(streaming and long polling) stay out by simply not being named. A backslash
does **not** continue a directive, so the list stays on one line.

Two more things live in the shared base rather than in every scenario:

* `timeout tunnel`, which no other proxy here has an equivalent for. Once a
  connection has been upgraded it stops being a request, and this timeout
  governs it in place of `timeout client`/`timeout server`. Vaadin's PUSH
  heartbeat is every 60s so neither value is ever reached; they are written
  down because HAProxy's own default is no timeout at all.
* `X-Forwarded-Proto`, derived from the bind's TLS state with
  `%[ssl_fc,iif(https,http)]`. That one expression is correct for every
  scenario at once, which is why `haproxy/https/*` needs no header rule where
  the Apache and NGINX siblings each set one by hand.

### TLS reuses the existing fixture under a second name

`bind ssl crt` takes a PEM that may carry the key, and when it does not it
loads `<crt>.key` from beside it. So `haproxy/https/*` mounts
`tls/localhost.key` as `localhost.crt.key` and no combined PEM has to be
generated. The bind offers HTTP/2 through ALPN by default, as Traefik's TLS
entryPoint does and unlike NGINX's `listen 443 ssl`; `curl` therefore needs
`--http1.1` to express an upgrade by hand.

### No AJP, and no `root-context-legacy`

HAProxy speaks HTTP/1.x, HTTP/2, FastCGI and raw TCP. There is no AJP mux in
the codebase and none has ever been proposed, so the 17 AJP scenarios have no
counterpart here. `root-context-legacy` exists to contrast Apache's
pre-2.4.47 `RewriteRule`-on-`Upgrade` idiom with `ProxyPass … upgrade=websocket`;
HAProxy has only ever had one way to relay an upgrade, so the port would be a
byte-identical copy of `root-context`.
