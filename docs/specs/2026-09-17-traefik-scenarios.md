# Plan: Traefik reverse-proxy scenarios

Date: 2026-09-17
Issue: [#17](https://github.com/mcollovati/vaadin-reverse-proxy-tests/issues/17)
Branch: `feat/traefik`

## Goal

Add a `traefik/` proxy tree beside `apache-httpd/` and `nginx/`: **25 scenarios**
(22 `traefik/http`, 2 `traefik/https`, 1 `traefik/labels`), configured through the
**file provider** (`traefik.yml` + per-scenario `vaadin.yml`), with the Docker socket
mounted in exactly one scenario.

The tree's reason to exist: Traefik is the only proxy here with **no response-header
rewriting at all** — no `ProxyPassReverse`, no `ProxyPassReverseCookiePath`, no
`proxy_redirect`, no `proxy_cookie_path`. Everything the other two trees repair on the
way out has to be either told to the backend on the way in (`X-Forwarded-Prefix`),
configured away in the app, or documented as broken. That is how every Kubernetes
ingress behaves, so the breakage is the deliverable.

## Repo state: what the issue assumed vs. what is true on `main`

The issue predates 8794a1e (`ci: make the workflow run by itself…`). Four of its wiring
notes are stale, one in our favour:

| Issue says | Actual state on `main` |
|---|---|
| "Needs a small `run-all.sh:111` fix first, so PR 1 writes the tag literally" | **No fix needed.** `run-all.sh:117-124` already resolves any `${VAR:-default}` image ref generically. Write `image: "traefik:${TRAEFIK_VERSION:-v3.7}"` from the first commit. |
| "the backend-wait step at `integration-tests.yml:170` selects services with `startswith("vaadin")`" | **Retired.** `_scenarios.yml:215-221` selects backends by image `vaadin/my-app`. Two constraints remain: every traefik compose must tag the backend `vaadin/my-app:${MY_APP_VERSION:-latest}` *and* publish a host port, or the step errors with `No published backend ports found`. |
| `scenarios.tsv` + `run-scenario.sh` + `gen-readmes.sh` need wiring | Still true, plus: `scripts/check-catalog.sh:31` **already lists `traefik`** in `proxy_trees`, so a scenario directory without a catalogue row fails `lint.yml` immediately. Directories and rows must land in the same commit. |
| "The CI matrix generalises already" | True, and more so than stated. `gen-matrix.sh` derives scheme/port from the `*/https` suffix, so `traefik/https` → 9443 and `traefik/http`, `traefik/labels` → 9090 with no change. `_scenarios.yml:148-152` keys the push transport and `it.websocket` off `endsWith(scenario, '-sse')`, so the SSE family inherits `SERVER_SENT_EVENTS` + `-Dit.websocket=false` for free. |

New work CI does need: `.github/workflows/traefik.yml` (a copy of `nginx.yml` with
`proxy: traefik` and traefik path filters) and `traefik:v3.7` in
`.github/proxy-images.txt`. That file is global, so the Apache and NGINX build jobs will
also pull and ship the Traefik image (~50 MB per tarball). Not worth splitting the list
per proxy yet.

## Decisions

Recommendations for the six items the issue puts up for sign-off, plus the fallback if
the empirical check in PR 1 goes the other way.

| # | Decision | Recommendation | Fallback |
|---|---|---|---|
| 1 | File provider vs. Docker labels | **File provider.** `gen-readmes.sh` inlines *the proxy config file*; with labels there is no file and the three-way comparison this repo exists for collapses. Labels also cannot express entryPoints or TLS. | — (structural; everything hangs off it) |
| 2 | Rescuing `root-to-custom-context*` | **Measured: the cookie path alone is not enough.** It fixes `JSESSIONID` but not Vaadin's `csrfToken`, which stays `Path=/app` and makes every Hilla call 401. The working combination is `SERVER_SERVLET_SESSION_COOKIE_PATH: /` **plus** a `headers` middleware sending `X-Forwarded-Prefix: /` **plus** `FRAMEWORK` — verified 6/6 including Hilla. | None needed; the family is fully salvageable, which beats the issue's "ship it broken or drop 4 scenarios". |
| 3 | Add `custom-to-root-context-forwarded-prefix` | **Add it — verified end to end.** The filter-order tie resolves the *wrong* way by default (`Location` loses the prefix); with `HIGHEST_PRECEDENCE + 10` it comes back correct in both directions. The `my-app` change is required, not optional. | — |
| 4 | `*-sse` means "deliberately refuses the upgrade" | **Amend CLAUDE.md:132-142.** Apache and NGINX `*-sse` configs *lack* upgrade support; Traefik upgrades unconditionally, so support has to be actively removed with a `headers` middleware that blanks `Upgrade`/`Connection`. Same semantics, different mechanism. | — |
| 5 | No AJP, no `root-context-legacy` | **Agreed.** No AJP transport in Traefik (`url:` takes `http://`, `https://`, `h2c://` only), and exactly one way to upgrade, so there is no legacy style to contrast. | — |
| 6 | No entry-point timeouts | **Write none — the issue was right, for a different reason.** The docs made this look wrong (`readTimeout` defaults to 60s and is documented as covering "the entire request, including the body"), but on 3.7.13 neither shape is cut: a 75s trickled body returns 200 at 76s, and an idle WebSocket survives 100s through a default-timeout proxy. Write a comment citing the measurement instead of a value. | If a cut ever appears, `readTimeout: 300s` / `idleTimeout: 300s` per entryPoint mirrors the other two trees. |

### A `my-app` change decision 3 depends on (verified statically, 2026-09-17)

Spring Boot 4.1.1 registers `ForwardedHeaderFilter` at
`FilterRegistrationBean.setOrder(-2147483648)` — `Ordered.HIGHEST_PRECEDENCE`
(`spring-boot-web-server-4.1.1.jar`,
`ServletWebServerConfiguration.forwardedHeaderFilter`). `Application.redirectTest`
(`my-app/src/main/java/com/example/application/Application.java:54-55`) uses the *same*
order, so which filter runs first is decided by bean ordering, not by intent — a coin
flip that decides whether `/test-redirect` sees the rewritten context path.

Fix: give `redirectTest` `Ordered.HIGHEST_PRECEDENCE + 10`. Safe for the existing trees —
no other scenario sets `FRAMEWORK`, so `ForwardedHeaderFilter` is not even registered
there and nothing else competes at that order. Small, in `my-app`, and worth doing
regardless of the Traefik tree.

## Scenario inventory (25)

23 port straight across from NGINX; 2 are Traefik-only. 18 catalogue rows are N/A
(17 AJP + `root-context-legacy`).

**`traefik/http` (22)** — the 21 `nginx/http` scenarios plus one:

`root-context`, `root-context-sse`, `root-context-push-url`, `root-context-push-url-sse`,
`custom-context`, `custom-context-sse`, `custom-context-push-url`,
`root-to-custom-context`, `root-to-custom-context-sse`, `root-to-custom-context-push-url`,
`custom-to-root-context`, `custom-to-root-context-sse`, `custom-to-root-context-push-url`,
`custom-to-root-context-servlet-mapping`, `root-to-custom-context-servlet-mapping`,
`servlet-mapping`, `servlet-mapping-sse`, `servlet-mapping-push-url`, `load-balancer`,
`load-balancer-sse`, `multiple-root-context`,
**`custom-to-root-context-forwarded-prefix`** (Traefik-only).

**`traefik/https` (2)** — `root-context`, `root-context-sse`.

**`traefik/labels` (1)** — `root-context`. The documented exception: the label idiom, and
the only compose file that mounts `/var/run/docker.sock` (read-only).

### The `*-push-url` family collapses

Traefik upgrades transparently on every route, so a relocated PUSH endpoint needs no rule
of its own: each `*-push-url` `vaadin.yml` is byte-identical to its sibling. Keep the
scenarios — they exercise `VAADIN_PUSH_URL` end to end, and the byte-identical config *is*
the finding. Say so in the `scenarios.tsv` description, the way
`apache-httpd/http/root-context-push-url-sse` already does.

## Config skeletons

### `traefik/traefik.yml` (shared, mounted read-only — mirrors `apache-httpd/httpd.conf`)

```yaml
# No timeouts here, unlike ProxyTimeout 300 (apache-httpd/httpd.conf) and
# proxy_read_timeout 300s (every NGINX template). The docs suggest there should
# be: readTimeout defaults to 60s and is documented as "the maximum duration
# for reading the entire request, including the body", which is what the 60s
# severing reports (traefik/traefik#10652, #13820) point at. Measured on
# 3.7.13, neither shape is cut — a 75s trickled request body returns 200 at
# 76s, and an idle WebSocket is still open after 100s. Vaadin's PUSH heartbeat
# is every 60s, so nothing here is ever idle that long anyway. Adding values
# would be decoration; if a cut ever shows up, readTimeout/idleTimeout 300s
# per entryPoint is the fix.
entryPoints:
  web:
    address: ":80"
  websecure:
    address: ":443"

providers:
  file:
    directory: /etc/traefik/dynamic
    watch: true

log:
  level: INFO
accessLog: {}
```

### `traefik/http/root-context/` — the shape every scenario follows

`docker-compose.yml`:

```yaml
services:
  vaadin:
    image: vaadin/my-app:${MY_APP_VERSION:-latest}
    build: ../../../my-app
    ports:
      - "8080:8080"
  web:
    depends_on:
      - vaadin
    image: "traefik:${TRAEFIK_VERSION:-v3.7}"
    volumes:
      - ../../traefik.yml:/etc/traefik/traefik.yml:ro
      - ./vaadin.yml:/etc/traefik/dynamic/vaadin.yml:ro
    ports:
      - "9090:80"
```

`vaadin.yml`:

```yaml
http:
  routers:
    vaadin:
      rule: PathPrefix(`/`)
      entryPoints: [web]
      middlewares: [compress]
      service: vaadin
  services:
    vaadin:
      loadBalancer:
        servers:
          - url: http://vaadin:8080
  middlewares:
    # Compression on, the way a real deployment has it — but never over a
    # streamed response. Unlike nginx's gzip_types allowlist this is a
    # denylist, so the streamed types have to be named: Traefik's compress
    # middleware does NOT skip text/event-stream on its own.
    compress:
      compress:
        excludedContentTypes:
          - text/event-stream
          - text/plain
```

Service names stay `vaadin` / `web` to match the other trees — and `vaadin` + a published
`8080` is what the CI readiness wait looks for.

### Prefix rules

Every context-prefix rule is `` PathPrefix(`/app/`) || Path(`/app`) ``, never bare
`` PathPrefix(`/app`) ``: Traefik's `PathPrefix` is a raw string prefix and also matches
`/application`.

- `custom-to-root-context` — `stripPrefix: { prefixes: ["/app"] }` and nothing else, so
  `/test-redirect` lands on `/hello-flow`, outside the prefix (measured). That is the
  documented breakage this scenario exists to show; `custom-to-root-context-forwarded-prefix`
  is the same config plus `FRAMEWORK`, and returns `/app/hello-flow`. Keeping both is what
  makes the comparison legible.
- `root-to-custom-context` — `addPrefix: { prefix: /app }`, plus the three-part fix
  probe 5 verified: `SERVER_SERVLET_SESSION_COOKIE_PATH: /`, a `headers` middleware
  sending `X-Forwarded-Prefix: /`, and `SERVER_FORWARD_HEADERS_STRATEGY=FRAMEWORK`.
  `addPrefix` does not emit `X-Forwarded-Prefix` itself, hence the middleware:

  ```yaml
      forwarded-root:
        headers:
          customRequestHeaders:
            X-Forwarded-Prefix: "/"   # "" would REMOVE the header
  ```

  The session cookie alone is not enough — Vaadin's `csrfToken` cookie is scoped to the
  backend's context path too, and without this every Hilla call is 401. Contrary to the
  issue, `/test-redirect` *is* fixable in this direction: with the filter-order fix it
  returns `/hello-flow`, the correct public URL.
- `custom-to-root-context-forwarded-prefix` — `stripPrefix` emits `X-Forwarded-Prefix`;
  backend runs `SERVER_FORWARD_HEADERS_STRATEGY=FRAMEWORK` so `ForwardedHeaderFilter`
  rebuilds the context path from it. Depends on the filter-order fix above.

### `servlet-mapping` — four routers, explicit priorities

Hilla lives at the context root regardless of `vaadin.url-mapping`, so `/ui/HILLA/*` and
`/ui/connect/*` must be stripped back and must out-prioritise the catch-all. Priorities
are written out rather than left to Traefik's rule-length default:

| router | rule | priority | middlewares |
|---|---|---|---|
| `hilla-push` | `` PathPrefix(`/ui/HILLA/`) `` | 100 | `strip-ui`, `compress` |
| `hilla-connect` | `` PathPrefix(`/ui/connect/`) `` | 100 | `strip-ui`, `compress` |
| `vaadin-ui` | `` PathPrefix(`/ui/`) || Path(`/ui`) `` | 50 | `compress` |
| `root` | `` PathPrefix(`/`) `` | 1 | `compress` |

Getting this subtly wrong yields a working app with a dead Hilla `Flux` — caught by
`HelloHillaIT`, but only when `it.websocket=true`, i.e. never in the `-sse` variants.

### `load-balancer`

```yaml
      loadBalancer:
        sticky:
          cookie:
            name: ROUTEID
            path: /
            httpOnly: true
        servers:
          - url: http://vaadin-1:8080
          - url: http://vaadin-2:8080
```

Traefik synthesizes the affinity cookie itself, so no `TOMCAT_JVMROUTE` — same as the
NGINX sibling. `APP_NAME` distinguishes the backends. Keep CI's "warm all backends" burst
in mind: the first cookie-less request round-robins.

### `*-sse` — refusing the upgrade on purpose

```yaml
    no-websocket:
      headers:
        customRequestHeaders:
          Upgrade: ""
          Connection: ""
```

An empty value **removes** the header. This is the mechanism behind decision 4: Traefik
has no "don't upgrade" switch, so the scenario has to strip the client's intent.

### `traefik/https/root-context`

Simpler than both siblings: Traefik sets `X-Forwarded-Proto` itself from the entryPoint's
TLS state, so there is no `RequestHeader set X-Forwarded-Proto`
(`apache-httpd/https/root-context/vaadin.conf:12-13`) and no
`proxy_set_header X-Forwarded-Proto $scheme`
(`nginx/https/root-context/default.conf.template:31`). Certificates go in the dynamic
file, keeping the shared static config scenario-agnostic:

```yaml
tls:
  certificates:
    - certFile: /etc/traefik/tls/localhost.crt
      keyFile: /etc/traefik/tls/localhost.key
```

Backend keeps `SERVER_FORWARD_HEADERS_STRATEGY: NATIVE`, as in the NGINX sibling.

## Repo wiring checklist

- `scenarios.tsv` — 25 rows (`key | description | paths | short`, `short` ≤ 60 chars).
- `scripts/gen-readmes.sh` — `proxy_label` arms for `traefik/http`, `traefik/https`,
  `traefik/labels`; a `traefik/*` generation arm inlining `vaadin.yml` in a ```yaml fence
  and linking the shared `traefik/traefik.yml`. `traefik/labels/root-context` has no
  config file, so it needs the fallback branch the NGINX arm already models
  (`_No … detected …_`), worded for labels.
- `run-scenario.sh:117-123` — add the three traefik proxy dirs to the picker.
- `.github/workflows/traefik.yml` — new caller; `.github/proxy-images.txt` — add
  `traefik:v3.7`, keeping it in step with the compose defaults for Dependabot.
- `README.md` — scenario tables + a Traefik notes section (no response rewriting;
  `TRAEFIK_VERSION`).
- `CLAUDE.md` — repository layout, the amended `*-sse` paragraph (decision 4), the
  `TRAEFIK_VERSION` knob next to `MY_APP_VERSION`, and Traefik caveats: `PathPrefix` is a
  raw prefix; `compress` does not skip `text/event-stream`; no response rewriting, so
  cookie paths and redirects are the app's problem; v3.3.5 is a hard floor.
- No change needed: `gen-matrix.sh`, `check-catalog.sh`, `run-all.sh`, `run-test.sh`,
  `integration-tests/`.

## Rollout

Each step ends green on `lint.yml` (catalogue consistency) and on its own scenarios.

### Step 0 — results (executed 2026-09-17 against traefik 3.7.13)

Harness: `trash_gitignore_/traefik-probe/` (untracked scratch) — `./run-probes.sh`,
six compose stacks. `traefik:v3.7` resolves to **3.7.13**, the build the issue was
verified against. Probes ran against `vaadin/my-app:latest` and, for the filter-order
question, a purpose-built `vaadin/my-app:probe-filterorder`.

| Risk | Question | Result |
|---|---|---|
| 0 | `X-Forwarded-Proto: wss` behind TLS | **Reproduces, and is harmless.** Traefik does send `wss` (and forwards `Connection`/`Upgrade`) on an HTTP/1.1 upgrade. The suite still passes **6/6**, Hilla `Flux` over WebSocket included. No `headers` middleware needed. |
| 1 | `root-to-custom-context` session | **Worse than described, then fully fixed.** See below. |
| 2 | Atmosphere behind `no-websocket` | Not probed yet — belongs to PR 2's `*-sse` work. |
| 3 | `/test-redirect` filter order | **The tie resolves the wrong way.** `Location` came back as `/hello-flow` instead of `/app/hello-flow`. With `HIGHEST_PRECEDENCE + 10` it is correct in *both* prefix directions. |
| 4 | `stripPrefix` on a bare prefix | **Closed.** The backend receives `GET / HTTP/1.1`, not an empty path, so `multiple-root-context` needs **no** `redirectRegex`. |
| 6 | The 60s severing | **Does not reproduce on 3.7.13.** 75s trickled body → HTTP 200 at 76s through both a default-timeout and a 300s proxy. Idle WebSocket → still open after 100s (defaults) and 80s (tuned). |

#### Risk 1 in detail: the cookie fix is half a fix

`SERVER_SERVLET_SESSION_COOKIE_PATH: /` does rescue the session — `JSESSIONID` moves from
`Path=/app` to `Path=/`, and the second request stops getting a fresh id. But Vaadin's
**`csrfToken` cookie stays `Path=/app`**, because that property only governs the servlet
container's session cookie. A browser at `/` therefore never sends it, Hilla's client
cannot read it, and every endpoint call is rejected: `HelloHillaIT` failed 2/2 while
`HelloFlowIT` passed. Isolated precisely — the same POST returns **200** with the token
supplied by hand and **401** with the cookies a browser actually holds.

The fix is to stop patching cookies one at a time and tell the backend the truth about its
public prefix: a `headers` middleware sending `X-Forwarded-Prefix: /` plus
`SERVER_FORWARD_HEADERS_STRATEGY=FRAMEWORK`. Spring trims the trailing slash, so
`getContextPath()` becomes `""`, and *every* derived value — both cookie paths, redirects,
asset URLs — is built for the public URL. Result: both cookies `Path=/`, `/test-redirect`
→ `/hello-flow`, and **6/6 tests pass**. `addPrefix` does not emit `X-Forwarded-Prefix`
itself (only `stripPrefix` does), which is why it has to be set by hand.

#### The `my-app` change is required, and does not regress the other trees

`Ordered.HIGHEST_PRECEDENCE + 10` on `Application.redirectTest` is applied on this branch.
Verified after rebuilding: `custom-to-root` gives `/app/hello-flow` and `root-to-custom`
gives `/hello-flow` — both correct. Re-ran `nginx/http/custom-to-root-context` with the
patched image: **6/6** and `/test-redirect` unchanged, as expected since
`ForwardedHeaderFilter` is only registered under `FRAMEWORK`.

#### Two harness lessons worth keeping

- **Traefik's TLS entryPoint negotiates HTTP/2 via ALPN.** HTTP/2 forbids
  `Connection`/`Upgrade`, so an h2 client cannot express an upgrade and the `wss`
  question silently answers itself *wrongly* — the first run showed
  `X-Forwarded-Proto: https` and no `Upgrade` header at all. `curl --http1.1` is
  mandatory here. This is also a real difference between the trees: the NGINX sibling's
  `listen 443 ssl` serves HTTP/1.1 only, so `traefik/https/*` is the first scenario in
  the repo to exercise h2 — worth a line in its README.
- Readiness must be waited on an **app** path. Waiting on the whoami route passed
  instantly and produced a spurious 502 from a still-booting backend.

#### Correction to a plan assumption

The `*-sse` family needs no special build: the pom pins `vaadin.version=25.3.0-rc1` and
`my-app/src/main/resources/vaadin-featureflags.properties` sets
`com.vaadin.experimental.ssePushTransport=true`, so the default image already carries the
SSE transport (the app logs it as an enabled feature preview). CLAUDE.md:132-142 says
25.3/25.4-SNAPSHOT is required; that line is now stale.

### PR 1 — the spine (~1 day)

`traefik/traefik.yml`, then `root-context`, `root-context-sse`, `custom-context`,
`custom-to-root-context`, `root-to-custom-context`, `https/root-context`,
`custom-to-root-context-forwarded-prefix` (it is the answer to probe 3, so it belongs
here), the `my-app` filter-order fix, catalogue rows, `gen-readmes.sh` arms,
`run-scenario.sh` picker, the CI caller and the pinned image.

All three schedule risks are deliberately inside PR 1.

### PR 2 — the rest (~1–1.5 days)

The remaining 14 `traefik/http` scenarios plus `https/root-context-sse`.
`servlet-mapping` and its two prefix variants are the real work: four routers of priority
ordering, with `/ui/HILLA/push` the path most likely to be wrong in a way that only shows
up as a dead Hilla `Flux`.

### PR 3 — docs and polish (~0.5 day)

`README.md` Traefik notes, `CLAUDE.md`, `traefik/labels/root-context`, and a one-off
manual check of risk 6 (see below).

Schedule risk: +0.5 day if the filter-order fix does not repair `/test-redirect`, +1 day
if `root-to-custom-context` cannot keep a session, +0.5 day if `wss` breaks PUSH behind
TLS.

## Open questions carried into implementation

1. **Atmosphere behind `no-websocket`** (risk 2). Still the one unmeasured risk. Verified
   only that the `Upgrade` header can be removed (`customRequestHeaders: {Upgrade: ""}`).
   The NGINX SSE template drops the same headers and produces 501, so this should hold; if
   Atmosphere answers 200 and the client hangs instead, nine scenario *descriptions*
   change — the configs do not. Belongs to PR 2.
2. **Router priority in the `servlet-mapping` families** (issue risk 5). Unprobed and the
   real work of PR 2: four routers, with `/ui/HILLA/push` the path most likely to be wrong
   in a way that only shows up as a dead Hilla `Flux` — caught by `HelloHillaIT`, but
   only when `it.websocket=true`.
3. **Whether `X-Forwarded-Prefix: /` has side effects** beyond cookies and redirects in
   `root-to-custom-context*`. The suite passes and both cookies are right, but the suite
   is a smoke test; `VAADIN_PUSH_URL` and the servlet-mapping variants of that family
   (PR 2) should be re-checked against it rather than assumed.
