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
* NGINX

```
├── apache-httpd
│   ├── ajp
│   │   ├── root-context
│   │   ├── root-context
│   │   ├── root-context-push-url
│   │   ├── servlet-mapping
│   │   └── servlet-mapping-push-url
│   └── http
│       ├── root-context
│       ├── root-context-push-url
│       ├── servlet-mapping
│       └── servlet-mapping-push-url
├── nginx
│   └── http
│       ├── root-context
│       ├── root-context-push-url
│       ├── multiple-root-context
│       ├── servlet-mapping
│       └── servlet-mapping-push-url
│
└── my-app (VAADIN Application)
```

## Run a scenario

To test a configuration enter the specific directory and run `docker compose up`
.

Vaadin application will be reachable at `http://localhost:8080`, whereas the
proxy server can be accessed at `http://localhost:9090`.

If you change the Vaadin application (`my-app`), remember to rebuild the docker
image by typing `docker compose build`.

To destroy the containers created during the tests type `docker compose down`

## Use local Vaadin SNAPSHOT

To use local Vaadin SNAPSHOTS you must build the application locally and then
build the docker image.

Go to `my-app` folder and build the application for production

```
mvn clean package -DskipTests -Pproduction 
```

Then build the docker image and tag it as `vaadin/my-app`

```
docker build -f Dockerfile_localBuild -t vaadin/my-app .
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