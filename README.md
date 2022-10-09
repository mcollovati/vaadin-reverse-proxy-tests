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

The scenarios are replicated for the following reverse proxy configuration:

* Apache HTTPD
* Apache HTTPD with AJP
* NGINX

```
├── apache-httpd
│   ├── ajp
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
│       ├── servlet-mapping
│       └── servlet-mapping-push-url
│
└── my-app (VAADIN Application)
```

## Run a scenario

To test a configuration enter the specific directory and run `docker-compose up`
.

Vaadin application will be reachable at `http://localhost:8080`, whereas the
proxy server can be accessed at `http://localhost:9090`.

If you change the Vaadin application (`my-app`), remember to rebuild the docker
image by typing `docker-compose build`.

To destroy the containers created during the tests type `docker-compose down`

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
