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
│   │   ├── load-balancer
│   │   ├── root-context
│   │   ├── root-context-push-url
│   │   ├── root-to-custom-context
│   │   ├── root-to-custom-context-push-url
│   │   ├── servlet-mapping
│   │   └── servlet-mapping-push-url
│   ├── http
│   │   ├── custom-context
│   │   ├── custom-context-push-url
│   │   ├── custom-to-root-context
│   │   ├── custom-to-root-context-push-url
│   │   ├── custom-to-root-context-servlet-mapping
│   │   ├── load-balancer
│   │   ├── root-context
│   │   ├── root-context-legacy
│   │   ├── root-context-push-url
│   │   ├── root-to-custom-context
│   │   ├── root-to-custom-context-push-url
│   │   ├── servlet-mapping
│   │   └── servlet-mapping-push-url
│   ├── https
│   │   └── root-context
│   └── ajp-https
│       └── root-context
├── nginx
│   ├── http
│   │   ├── custom-context
│   │   ├── custom-context-push-url
│   │   ├── custom-to-root-context
│   │   ├── custom-to-root-context-push-url
│   │   ├── custom-to-root-context-servlet-mapping
│   │   ├── load-balancer
│   │   ├── multiple-root-context
│   │   ├── multiple-root-context-issue-15399
│   │   ├── root-context
│   │   ├── root-context-push-url
│   │   ├── root-to-custom-context
│   │   ├── root-to-custom-context-push-url
│   │   ├── servlet-mapping
│   │   └── servlet-mapping-push-url
│   └── https
│       └── root-context
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
and the Hello Hilla buttons, parameterized over `WEBSOCKET_XHR` and `WEBSOCKET`
push transports. It's the fastest way to verify that a given proxy scenario
actually works end to end.

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
| `multiple-root-context` | both `http://localhost:9090/ui1/` and `/ui2/` |
| `https/*`, `ajp-https/*` | `https://localhost:9443/` |

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

## nginx Notes

By default, the websocket connection will be closed if the proxied server does
not transmit any data within 60 seconds. Vaadin PUSH is configured to
periodically send heartbeat messages over WebSocket every 60 seconds, so the
connection should not be closed. 
If the default is not working correctly, the timeout can be increased with the
`proxy_read_timeout` directive.