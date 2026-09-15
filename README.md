# Vaadin Application behind reverse proxy

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

`scripts/run-all.sh` brings each scenario up, waits for it, runs the suite
against every path it exposes, tears it down and prints a summary. Scenarios
share the same ports, so it is strictly sequential — the GitHub Actions matrix
does the same work in parallel on separate runners.

It does not build the app image; build it first and pass the tag.

```
docker build my-app -t vaadin/my-app:latest
scripts/run-all.sh --exclude '\-sse$'      # the 44 WebSocket scenarios

docker build my-app --build-arg VAADIN_VERSION=25.4-SNAPSHOT -t vaadin/my-app:sse
scripts/run-all.sh --app-version sse '\-sse$'   # the 19 SSE scenarios
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
`main`, so it ships in the `25.3-SNAPSHOT` and `25.4-SNAPSHOT` lines. The `*-sse`
scenarios need an image built from one of those — the pom's pinned version
predates the transport.

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