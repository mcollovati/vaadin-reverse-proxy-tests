# Design: manually-dispatched integration-test GitHub workflow

Date: 2026-06-10

## Goal

Add a GitHub Actions workflow that runs the Playwright integration suite
(`integration-tests/`) against every reverse-proxy scenario in `scenarios.tsv`.
The workflow is triggered manually (`workflow_dispatch`) and builds the
`my-app` image once, then exercises each scenario in a parallel matrix.

## Background (current state)

- `scenarios.tsv` — canonical catalog. Pipe-separated:
  `proxy/scenario | description | paths | short`. 45 scenario rows.
  `paths` is a comma-separated list of proxy-relative URL paths (e.g. `/`,
  `/app/`, `/ui/`, or `/ui1/,/ui2/` for `multiple-root-context`).
- `run-scenario.sh` — interactive launcher; **not** used by CI.
- `run-test.sh <base-url> [-- mvn-args…]` — runs the Playwright suite via
  `./mvnw -B verify -Dapp.base.url=<base-url>` in `integration-tests/`.
  Assumes the stack is already running.
- `integration-tests/` — JUnit 5 + Playwright (Java) failsafe module.
  `BaseIT` launches headless Chromium, reads `app.base.url`, and sets
  `ignoreHTTPSErrors` for `https://` URLs.
- `my-app/Dockerfile` — full Maven build inside the image, resolving from
  public repos. Builds the pom-pinned Vaadin version (currently 25.1.3).
  Does **not** currently accept a Vaadin-version build-arg.
- Every scenario `docker-compose.yml` references
  `image: vaadin/my-app:${MY_APP_VERSION:-latest}` and exposes the proxy on
  `:9090` (http) or `:9443` (https / ajp-https), app direct on `:8080`.

## Decisions

| Question | Decision |
|---|---|
| Which scenarios | All 45, as a parallel matrix (`fail-fast: false`), with an optional filter input. |
| Image build | Build the `my-app` image once in a setup job; share to matrix jobs via `docker save` + `upload-artifact` + `docker load`. |
| Image tag | `vaadin/my-app:<vaadin_version>` when `vaadin_version` is set, else `vaadin/my-app:latest`. The same tag drives `MY_APP_VERSION` for compose. |
| Vaadin version | Optional `vaadin_version` dispatch input → passed as `--build-arg VAADIN_VERSION`. Empty = pom default. Requires a small Dockerfile change. |
| Java version | `java_version` dispatch input (default `21`) → drives the test-runner JDK (`setup-java`), the Maven build stage (`maven:3-eclipse-temurin-<v>`) and the runtime stage (`eclipse-temurin:<v>`) via `--build-arg JAVA_VERSION`. |
| Matrix source | A committed `scripts/gen-matrix.sh` parses `scenarios.tsv` → JSON matrix (reusable locally; not inline in the workflow). |
| Scenario filter | Keep a `scenario_filter` dispatch input (substring/regex); empty = all. |

## Workflow structure

File: `.github/workflows/integration-tests.yml`. `on: workflow_dispatch`.

### Inputs
- `vaadin_version` — string, default `""`. Forwarded to the image build as
  `--build-arg VAADIN_VERSION=<value>`. Empty leaves the pom default.
- `scenario_filter` — string, default `""`. Substring/regex applied to the
  `proxy/scenario` key to narrow the matrix. Empty = all scenarios.
- `java_version` — string, default `"21"`. Used for `setup-java` in the test
  job and forwarded to the image build as `--build-arg JAVA_VERSION`.

### Image tag resolution
A single value used by both `build` and `test`:
`tag = inputs.vaadin_version != "" ? inputs.vaadin_version : "latest"`.
Computed once (e.g. as a `prepare` job output `image_tag`) so both jobs agree.

### Job `prepare`
- Runs `scripts/gen-matrix.sh` (passing `scenario_filter`) and sets a job
  output `matrix` = JSON array of objects:
  `{ scenario, scheme, port, paths: ["…", …] }`.
  - `scheme`/`port`: `https`/`9443` if the scenario dir contains `https` or
    `ajp-https`, else `http`/`9090`.
  - `paths`: the `paths` column split on `,`.
- Also exposes the `image_tag` output (see above).
- Fast job; `build` and `test` depend on it.

### Job `build`
- `needs: [prepare]` (to read `image_tag`).
- `docker build my-app/ -t vaadin/my-app:<image_tag>` with
  `--build-arg VAADIN_VERSION=${{ inputs.vaadin_version }}`.
- `docker save vaadin/my-app:<image_tag> | gzip > my-app-image.tar.gz`.
- `upload-artifact` the tarball (retention: short, e.g. 1 day).

### Job `test`
- `needs: [prepare, build]`.
- `strategy: { fail-fast: false, matrix: { include: ${{ fromJson(needs.prepare.outputs.matrix) }} } }`.
- Steps per matrix entry:
  1. `checkout`.
  2. `download-artifact` the image tarball; `docker load`.
  3. Set up Temurin JDK 21; cache `~/.m2` and `~/.cache/ms-playwright`.
  4. `MY_APP_VERSION=<image_tag> docker compose -f <scenario>/docker-compose.yml up -d --no-build`.
  5. Readiness gate: for each base URL, poll `curl -fsk <base-url>` until HTTP
     200 or a ~120s timeout (fail the job on timeout).
  6. For each base URL: `./run-test.sh <base-url>`
     (`multiple-root-context` runs twice — `/ui1/` and `/ui2/`).
  7. `if: always()`:
     - capture `docker compose … logs` to a file;
     - upload failsafe reports (`integration-tests/target/failsafe-reports`)
       and any Playwright traces/screenshots + the compose log as an artifact
       named after the scenario (slashes replaced);
     - `docker compose -f <scenario>/docker-compose.yml down -v`.

Base URL construction: `<scheme>://localhost:<port><path>` for each `path`.

## Required code change outside `.github/`

`my-app/Dockerfile`:
```dockerfile
ARG JAVA_VERSION=21
FROM maven:3-eclipse-temurin-${JAVA_VERSION} AS build
ARG VAADIN_VERSION=
...
RUN mvn clean package -DskipTests ${VAADIN_VERSION:+-Dvaadin.version=$VAADIN_VERSION}
...
FROM eclipse-temurin:${JAVA_VERSION}
```
The global `ARG JAVA_VERSION` (declared before the first `FROM`) is in scope
for both `FROM` lines; default `21` keeps the current base images. `RUN` is in
shell form, so the `${VAR:+…}` expansion works — default empty `VAADIN_VERSION`
→ no `-Dvaadin.version` flag → unchanged behavior for existing local/CI builds.

## New script: `scripts/gen-matrix.sh`

- Reads `scenarios.tsv` (skips `#` comment lines).
- Optional arg `$1` = filter (substring/regex) on the `proxy/scenario` key.
- Emits a single-line JSON array suitable for `fromJson` in the matrix
  `include`. Each element: `{ "scenario": "...", "scheme": "...",
  "port": "...", "paths": ["...", ...] }`.
- Pure bash + standard tooling; `jq` used for safe JSON encoding.
- Reusable locally for debugging the matrix (`scripts/gen-matrix.sh nginx`).

## Known risks / mitigations

- **Playwright system libs** on `ubuntu-latest`: `Playwright.create()`
  auto-downloads Chromium, but the browser may need OS packages. Verify on a
  real run; if missing, add a step to install Playwright deps (e.g. the
  Playwright CLI `install-deps`, or `apt-get` the documented libs) before the
  test step.
- **HTTPS scenarios**: readiness poll uses `curl -k`; `BaseIT` already trusts
  the self-signed cert.
- **Image artifact size**: Temurin 21 + app jar, gzipped; acceptable for a
  single save/load per run.
- **Port conflicts**: each matrix job is its own runner, so fixed ports
  `9090`/`9443`/`8080` never collide across scenarios.

## Verification approach

- Run `scripts/gen-matrix.sh` locally and confirm it emits valid JSON for all
  45 rows (and that the filter narrows correctly). Validate with
  `jq empty`.
- Run one representative scenario end to end locally
  (`docker compose up --no-build` → readiness poll → `run-test.sh` → `down`)
  to prove the per-job sequence before trusting CI.
- First real CI dispatch confirms the Playwright/browser environment and the
  artifact round-trip.

## Out of scope

- Triggering on push/PR (manual dispatch only).
- Pushing the image to a registry (GHCR); intra-run artifact sharing is enough.
- Adding new integration tests or scenarios.
