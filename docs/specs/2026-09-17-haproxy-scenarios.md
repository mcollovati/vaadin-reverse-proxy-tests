# Plan: HAProxy reverse-proxy scenarios

Date: 2026-09-17
Issue: [#16](https://github.com/mcollovati/vaadin-reverse-proxy-tests/issues/16)
Branch: `chore/haproxy`

## Goal

Add a `haproxy/` proxy tree beside `apache-httpd/`, `nginx/` and `traefik/`:
**24 scenarios** (22 `haproxy/http`, 2 `haproxy/https`), configured as a shared
`haproxy/haproxy-base.cfg` plus one scenario config, both mounted into
`/usr/local/etc/haproxy/conf.d` and loaded with `haproxy -f <dir>`.

The tree's reason to exist: HAProxy is the engine under OpenShift's router and
`haproxy-ingress`, so it is where a lot of enterprise Vaadin deployments
actually land. It is also the only proxy here with **no `<Location>` / `location`
construct at all** — a frontend carries every path and a backend carries every
request — and the only one with a separate post-upgrade lifetime
(`timeout tunnel`). Both shape the whole tree: the `*-push-url` family needs no
configuration of its own, and half the scenarios collapse onto shared files.

## Repo state: what the issue assumed vs. what is true on `main`

The issue predates 8794a1e (the CI rework) and 516bcc3 (the Traefik tree). Its
wiring notes need three corrections, all in our favour:

| Issue says | Actual state on `main` |
|---|---|
| "the backend-wait step at `integration-tests.yml:170` selects services with `startswith("vaadin")` … will silently wait for nothing" | **Retired.** `_scenarios.yml:213-221` selects backends by image `vaadin/my-app`. Two constraints remain: every haproxy compose must tag the backend `vaadin/my-app:${MY_APP_VERSION:-latest}` *and* publish a host port, or the step fails with `No published backend ports found`. |
| "This tree roughly doubles the CI matrix. The workflow improvements should land first — see the companion CI issue." | **Done** (#18). `_scenarios.yml` holds the logic and thin per-proxy callers decide when to run; a new tree needs a new caller, not a change to `_scenarios.yml`. `traefik.yml` is the template to copy. |
| "Only two places hardcode proxy names: `run-scenario.sh:118-126`, `scripts/gen-readmes.sh`" | Still true, plus: `scripts/check-catalog.sh:22` and `lint.yml`'s pinned-image check **already list `haproxy`** in their tree arrays. So a scenario directory without a catalogue row fails `lint.yml` immediately — directories and rows must land in the same commit. |
| "No change needed to `gen-matrix.sh`, `run-all.sh`, the CI matrix, `check-compression.sh`" | True, and more so: `gen-matrix.sh` derives scheme/port from the `*/https` suffix, and `_scenarios.yml:148,152` keys the push transport and `it.websocket` off `endsWith(scenario, '-sse')`, so the SSE family inherits `SERVER_SENT_EVENTS` + `-Dit.websocket=false` for free. |

New work CI does need: `.github/workflows/haproxy.yml` (a copy of `traefik.yml`
with `proxy: haproxy` and haproxy path filters), `haproxy:3.2.23` in
`.github/proxy-images.txt`, and a Dependabot `docker-compose` entry — which
today covers only `/apache-httpd/**` and `/nginx/**`, so `traefik/` is already
unwatched and `haproxy/` would join it. Add both directories and the
`haproxy` pattern to the `proxies` group in the same commit.

## Step 0 — measured, not assumed (executed 2026-09-17)

The issue's first riskiest unknown was "**nothing has been parsed by HAProxy**".
Everything below was run for real against **HAProxy 3.2.23** (`haproxy:3.2`,
the current LTS) and `vaadin/my-app:latest` built from this branch, in
`trash_gitignore_/haproxy-probe/` (untracked scratch, the same harness shape the
Traefik plan used). Ten draft configs were parse-checked with `haproxy -c`, and
ten scenarios were brought up and driven with the repo's own tools —
`./run-test.sh` (the Playwright suite) and `scripts/check-compression.sh`.
Everything the tree does that is not a copy of something already verified is
covered: the baseline, both prefix directions, the servlet mapping and its
harder context-translating variant, the SSE refusal, both load-balancing
idioms, two apps behind one proxy, and TLS.

| # | Question | Result |
|---|---|---|
| 1 | Does the proposed shared-base layout parse? | **No — and the fix is structural.** See below. |
| 2 | Is the baseline sound? | `root-context`: **6/6 tests**, HTML gzipped, `/sse-probe` uncompressed and trickling (~500 ms apart). |
| 3 | `Set-Cookie` / `Location` rewriting by raw regex | **Works, and the obvious regex is wrong.** See below. |
| 4 | Does HAProxy coalesce SSE ticks? | **No.** Six ticks over 2.5 s through the proxy, no `proxy_buffering off` equivalent needed, and `compression type` being an allowlist keeps `text/event-stream` out of the compressor by construction. |
| 5 | `*-sse` refusal | **Confirmed.** `http-request deny deny_status 501 if { req.hdr(Upgrade) -m found }` answers `501 Not Implemented` from the proxy; the SSE suite runs 4 tests, 1 skipped, green — the same shape as the Apache and NGINX `*-sse` siblings. |
| 6 | `servlet-mapping` | **6/6**, with two ACLs and one `replace-path` — a third of the NGINX sibling. |
| 7 | TLS without a new fixture | **Confirmed.** `bind ssl crt` finds `<crt>.key` beside the certificate, so the existing `tls/localhost.key` only has to be mounted as `localhost.crt.key`. `https/root-context` is **6/6**, `Location` comes back with the `https` scheme, and the bind serves **HTTP/2** by default (`curl --http1.1` to reproduce an upgrade by hand, as in `traefik/https`). |
| 8 | `cookie JSESSIONID prefix` round trip | **Works.** `JSESSIONID=v1~AE9EEC7F…` goes out, comes back stripped, and the suite is 6/6. |
| 9 | Routing two apps by prefix | **Two ordering traps, both fatal and both silent.** See below. |

### The layout does not parse as the issue proposes it

```
[ALERT] config : parsing [10-vaadin.cfg:5]: frontends and backends cannot
        inherit from the same defaults section if it defines TCP/HTTP rules
```

A named `defaults` section carrying `http-request` rules can be inherited by
frontends **or** by backends, not by both. The base therefore needs two
sections, one chained off the other:

```
defaults vaadin                 # mode, timeouts, compression, logging
defaults vaadin-front from vaadin   # + the http-request rules
```

and scenarios write `frontend vaadin-in from vaadin-front` /
`backend vaadin-app from vaadin`. Everything else about decision 1 survives:
the base and the scenario file still only parse as a pair, and
`haproxy -f <dir>` concatenates them in lexical order (`00-base.cfg`,
`10-vaadin.cfg`).

### The cookie regex that looks right is silently wrong

Two traps, one after the other:

1. **The config tokenizer splits on whitespace before the regex engine sees
   it.** `(.*;[ ]*[Pp]ath=)` fails to parse with
   `missing terminating ] for character class` — the exact quoting collision
   the issue predicted. `\s` works; inside a character class a literal space
   does not.
2. **Tomcat writes `Path=/app`, with no trailing slash.** A regex anchored on
   `/app/` matches nothing, `replace-header` silently does nothing, and the
   browser at `/` never sends the session cookie back. Measured on
   `root-to-custom-context`: `6 tests, 1 failure, 4 errors`, every view timing
   out. With the anchor moved to the end of the value —
   `(.*;\s*[Pp]ath=)/app(;.*)?$` → `\1/\2` — the same scenario is **6/6**.

This is worth a comment in every config that carries one of these rules: there
is no `proxy_cookie_path` here, and a rewriting rule that matches nothing looks
exactly like a rewriting rule that is not needed.

A third, unrelated tokenizer surprise, found while formatting the base: a
backslash at the end of a line does **not** continue a directive.
`compression type … \` followed by an indented second line is rejected with
`unknown keyword 'application/xml' in 'defaults' section`. Long lists stay on
one line.

### Rule order decides the backend, and `multiple-root-context` is where it bites

HAProxy evaluates the whole `http-request` ruleset *before* `use_backend`. Two
consequences, met in that order while getting `multiple-root-context` up:

1. A `replace-path` in the frontend rewrites the path before `use_backend`
   evaluates its ACL, so the prefix that was supposed to choose the backend is
   already gone: every request answered `503`, logged as
   `vaadin-in vaadin-in/<NOSRV>`. The fix is to strip the prefix **in the
   backend**, which is allowed to carry `http-request` rules of its own.
2. An unconditional `http-request deny` meant as "nothing outside the two
   prefixes exists" then denies everything, because it too runs before
   `use_backend`. It has to carry the condition: `unless app1 or app2`.

Neither shows up in `haproxy -c`; both look like a routing typo. With the rules
in the right places the scenario works the way the NGINX sibling does, including
the part Traefik cannot do — each app's `csrfToken` is rescoped to its own
prefix (`Path=/ui1`, `Path=/ui2`) by a response rewrite, so the two apps do not
clobber each other and neither backend needs `FRAMEWORK`.

### Measured scenario results

| probe | ITs | compression | notes |
|---|---|---|---|
| `root-context` | 6/6 | ok | Hilla `Flux` over WebSocket included |
| `root-context-sse` (deny) | 4 run, 1 skipped | ok | 501 comes from HAProxy, not Atmosphere |
| `custom-to-root-context` | 6/6 | ok | `Location` and `Set-Cookie` both rewritten; `/app/test-redirect` → `/app/hello-flow` |
| `root-to-custom-context` | 6/6 | ok | after the cookie-anchor fix |
| `servlet-mapping` | 6/6 | ok | |
| `custom-to-root-context-servlet-mapping` | 6/6 | ok | the hardest one: prefix strip and Hilla rewrite in the same frontend, most specific rule first |
| `load-balancer` | 6/6 | ok | `ROUTEID=v1; path=/; HttpOnly` inserted by `cookie ROUTEID insert indirect nocache httponly` |
| `load-balancer-cookie-prefix` | 6/6 | ok | the new scenario; `JSESSIONID=v1~AE9EEC7F…` round-trips |
| `multiple-root-context` | 6/6 on `/ui1/`, 6/6 on `/ui2/` | ok | after the two rule-order fixes; both apps' cookies rescoped per prefix |
| `https/root-context` | 6/6 | ok | `bind :443 ssl crt` + sibling key; served over **HTTP/2** by default, `Location` comes back `https://` |

## Decisions

The issue puts four items up for sign-off. Recommendations, with what the
measurements changed:

| # | Decision | Recommendation |
|---|---|---|
| 1 | Shared base + literal per-scenario cfg, not NGINX-style `${VAR}` templates | **Agreed, with the two-section correction above.** The tokenizer/regex collision is real and was hit on the first rewriting rule, so templating the configs with environment variables would fight the parser on exactly the scenarios that need it most. |
| 2 | `*-sse` refuses the upgrade explicitly | **Agreed, and it is the second tree to do so.** Traefik already had to refuse rather than omit (`headers` middleware deleting `Upgrade`/`Connection`); HAProxy denies with 501. CLAUDE.md's `*-sse` paragraph, amended once for Traefik, needs a second sentence: two of four trees now refuse, and the mechanism differs in each. |
| 3 | TLS needs no new fixture | **Agreed — verified.** Mount `tls/localhost.key` as `localhost.crt.key` beside `localhost.crt`; do not add a combined PEM to `generate-cert.sh`. |
| 4 | `load-balancer-cookie-prefix` as the new scenario, `load-balancer-jvmroute` as an optional stretch | **Agreed.** Nothing in the repo currently exercises a proxy that *modifies* the session cookie in flight — Apache and NGINX both synthesize a separate `ROUTEID` to avoid it, and so does Traefik. Note that `TOMCAT_JVMROUTE` is today consumed by **no** scenario at all (CLAUDE.md's "sticky sessions via `jvmRoute` (Apache)" is inaccurate — the Apache configs synthesize `ROUTEID` too), so `load-balancer-jvmroute` would be its first real user. Keep it out of PR 1–3 and decide once the rest is green. |

## Scenario inventory (24)

23 port straight across from NGINX; 1 is HAProxy-only. 18 catalogue rows are
N/A (17 AJP + `root-context-legacy`), for the reasons the issue gives: HAProxy
speaks HTTP/1.x, HTTP/2, FastCGI and raw TCP and has never had an AJP mux, and
it has only ever had one WebSocket idiom, so there is no legacy style to
contrast.

**`haproxy/http` (22)** — the 21 `nginx/http` scenarios plus one:

`root-context`, `root-context-sse`, `root-context-push-url`,
`root-context-push-url-sse`, `custom-context`, `custom-context-sse`,
`custom-context-push-url`, `root-to-custom-context`,
`root-to-custom-context-sse`, `root-to-custom-context-push-url`,
`custom-to-root-context`, `custom-to-root-context-sse`,
`custom-to-root-context-push-url`, `custom-to-root-context-servlet-mapping`,
`root-to-custom-context-servlet-mapping`, `servlet-mapping`,
`servlet-mapping-sse`, `servlet-mapping-push-url`, `load-balancer`,
`load-balancer-sse`, `multiple-root-context`,
**`load-balancer-cookie-prefix`** (HAProxy-only).

**`haproxy/https` (2)** — `root-context`, `root-context-sse`.

### 24 scenarios, 16 config files

There is no `location` construct, so a backend carries every path a frontend
accepted. That collapses five families onto one file each:

| file | scenarios |
|---|---|
| `templates/passthrough.cfg` | `root-context`, `root-context-push-url`, `custom-context`, `custom-context-push-url` |
| `templates/passthrough-sse.cfg` | `root-context-sse`, `root-context-push-url-sse`, `custom-context-sse` |
| `templates/strip-prefix.cfg` | `custom-to-root-context`, `custom-to-root-context-push-url` |
| `templates/add-prefix.cfg` | `root-to-custom-context`, `root-to-custom-context-push-url` |
| `templates/servlet-mapping.cfg` | `servlet-mapping`, `servlet-mapping-push-url` |
| scenario-local `vaadin.cfg` ×11 | the three remaining `*-sse` variants, the two `*-servlet-mapping` context translations, the three `load-balancer*`, `multiple-root-context`, and the two `https/*` |

The `*-push-url` scenarios keep their directories: they exercise
`VAADIN_PUSH_URL` end to end, and "the proxy config is byte-identical to its
sibling — it is the *same file*" is the finding, the way
`apache-httpd/http/root-context-push-url-sse` already says so in its
description. The `*-sse` variants keep the deny rule written out literally in
each file rather than hidden in a third shared `defaults` section: every
scenario README inlines its config, and a refusal that is not visible there is
a refusal nobody reads.

## Config skeletons

### `haproxy/haproxy-base.cfg` (shared, mounted read-only as `conf.d/00-base.cfg`)

```
global
    log stdout format raw local0 info
    maxconn 4096

# Two sections, not one: a named defaults that carries http-request rules can be
# inherited by frontends or by backends, never by both — HAProxy refuses with
# "frontends and backends cannot inherit from the same defaults section if it
# defines TCP/HTTP rules". So the rule-free half is shared and the rules hang
# off a second section that only frontends use.
defaults vaadin
    mode http
    log global
    option httplog
    option forwardfor
    timeout connect 5s
    timeout client  300s
    timeout server  300s
    # No other tree has this knob: after an upgrade the connection stops being a
    # request and `timeout tunnel` governs it instead of client/server. Vaadin's
    # PUSH heartbeat is every 60s, so neither value is ever reached — which is
    # the point of writing them down rather than leaving HAProxy's "no timeout"
    # default in place.
    timeout tunnel  1h
    # Compression, the way a real deployment has it — but never over a streamed
    # response. Like nginx's gzip_types this is an allowlist, so text/event-stream
    # (SSE push) and text/plain (streaming and long polling) stay out by simply
    # not being named. Compare Traefik, where the same list is a denylist.
    compression algo gzip
    # One line: a backslash continuation is NOT joined here — the next line is
    # parsed as a new keyword and the config is rejected.
    compression type text/html text/css application/javascript application/json application/xml image/svg+xml

defaults vaadin-front from vaadin
    # HAProxy sends no X-Forwarded-Proto of its own. Deriving it from the
    # bind's TLS state means the https scenarios need no rule of their own —
    # compare `RequestHeader set X-Forwarded-Proto` (Apache) and
    # `proxy_set_header X-Forwarded-Proto $scheme` (NGINX).
    http-request set-header X-Forwarded-Proto %[ssl_fc,iif(https,http)]
```

### `haproxy/http/root-context/docker-compose.yml` — the shape every scenario follows

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
    image: "${HAPROXY_IMAGE:-haproxy:3.2.23}"
    # The image's entrypoint prepends `haproxy -W -db` to any argument list
    # starting with a dash. Pointing -f at a directory is what makes the shared
    # base + scenario split work: HAProxy concatenates the files in lexical order.
    command: ["-f", "/usr/local/etc/haproxy/conf.d"]
    volumes:
      - ../../haproxy-base.cfg:/usr/local/etc/haproxy/conf.d/00-base.cfg:ro
      - ../../templates/passthrough.cfg:/usr/local/etc/haproxy/conf.d/10-vaadin.cfg:ro
    ports:
      - "9090:80"
```

### `templates/passthrough.cfg`

```
frontend vaadin-in from vaadin-front
    bind :80
    default_backend vaadin-app

backend vaadin-app from vaadin
    server vaadin vaadin:8080
```

Nothing configures WebSocket: HAProxy relays an `Upgrade` transparently on
whatever path it arrives on, which is why this same file serves `custom-context`
(the backend's context path is simply passed through) and both `*-push-url`
scenarios.

### `templates/passthrough-sse.cfg`

```
frontend vaadin-in from vaadin-front
    bind :80
    # Apache and NGINX refuse an upgrade by omission: Upgrade and Connection are
    # hop-by-hop headers neither forwards unless a directive puts them back, and
    # the *-sse configs simply do not. HAProxy relays them and has no switch to
    # stop it, so the refusal is written out — and the 501 then comes from the
    # proxy rather than from Atmosphere.
    http-request deny deny_status 501 if { req.hdr(Upgrade) -m found }
    default_backend vaadin-app

backend vaadin-app from vaadin
    server vaadin vaadin:8080
```

### `templates/strip-prefix.cfg` — proxy at `/app/`, app at `/`

```
frontend vaadin-in from vaadin-front
    bind :80
    acl is_app path /app
    acl is_app path_beg /app/
    # There is no <Location> here: a frontend sees every path. Without this deny
    # the backend would answer at / too, where the NGINX sibling has nothing.
    http-request deny deny_status 404 unless is_app
    http-request replace-path ^/app/?(.*) /\1
    default_backend vaadin-app

backend vaadin-app from vaadin
    # The only two things the backend says about its own location, and the only
    # two HAProxy can repair — there is no ProxyPassReverse and no
    # proxy_cookie_path, just raw regexes over the response headers.
    http-response replace-header Location ^(https?://[^/]+)?/(.*) \1/app/\2
    # Anchored on the end of the value rather than on what follows the path:
    # Tomcat writes `Path=/; HttpOnly`, so a regex that expects another path
    # segment after the slash matches nothing at all — which from the outside
    # looks exactly like a rule that was not needed.
    http-response replace-header Set-Cookie (.*;\s*[Pp]ath=)/(;.*)?$ \1/app\2
    server vaadin vaadin:8080
```

### `templates/add-prefix.cfg` — proxy at `/`, app on `/app`

```
frontend vaadin-in from vaadin-front
    bind :80
    http-request set-path /app%[path]
    default_backend vaadin-app

backend vaadin-app from vaadin
    http-response replace-header Location ^(https?://[^/]+)?/app(/.*) \1\2
    http-response replace-header Set-Cookie (.*;\s*[Pp]ath=)/app(;.*)?$ \1/\2
    server vaadin vaadin:8080
```

Worth contrasting with `traefik/http/root-to-custom-context`, which cannot
rewrite a response and has to arrange all of this on the way in
(`X-Forwarded-Prefix` + `FRAMEWORK` + `SERVER_SERVLET_SESSION_COOKIE_PATH`).
Here the backend needs no environment variable at all beyond its context path —
the same split the Apache and NGINX siblings have.

### `templates/servlet-mapping.cfg`

```
frontend vaadin-in from vaadin-front
    bind :80
    # Hilla does not follow vaadin.url-mapping: /HILLA/* (push) and /connect/*
    # (browser-callables) stay at the context root whatever the Vaadin servlet
    # is mapped to, while the browser asks for them under /ui. See vaadin/hilla#289.
    acl hilla path_beg /ui/HILLA/
    acl hilla path_beg /ui/connect/
    http-request replace-path ^/ui(/.*) \1 if hilla
    default_backend vaadin-app

backend vaadin-app from vaadin
    server vaadin vaadin:8080
```

The two context-translating variants stack the rules, most specific first —
`replace-path` rules are evaluated in order and the later one no longer matches
a path the earlier one already rewrote:

```
    # custom-to-root-context-servlet-mapping: /app/ui/HILLA/... -> /HILLA/...
    http-request replace-path ^/app/ui(/(HILLA|connect)/.*) \1
    http-request replace-path ^/app/?(.*) /\1
```

### `load-balancer` and `load-balancer-cookie-prefix`

```
backend vaadin-app from vaadin
    balance roundrobin
    # Affinity cookie synthesized by the proxy, as in every other tree.
    cookie ROUTEID insert indirect nocache httponly
    server vaadin-1 vaadin-1:8080 cookie v1
    server vaadin-2 vaadin-2:8080 cookie v2
```

`load-balancer-cookie-prefix` differs in one line — `cookie JSESSIONID prefix
nocache` — and that line is the whole scenario: HAProxy prefixes its own server
id onto Tomcat's session id on the way out (`JSESSIONID=v1~ABC123`) and strips
it again on the way in. No other scenario in the repo puts a proxy in the middle
of the session id. Note that `prefix` does **not** consume `TOMCAT_JVMROUTE`:
the two are independent mechanisms that happen to compose
(`JSESSIONID=v1~ABC123.vaadin-1`).

No `check` on the servers, deliberately: an unchecked HAProxy backend retries a
refused connection (`retries` defaults to 3) instead of blacklisting the peer
the way NGINX's `fail_timeout` and Apache's `retry` do, so the startup race
documented in CLAUDE.md does not arm here. CI's "wait for every published
backend port" step still applies and still costs nothing.

### `multiple-root-context`

```
frontend vaadin-in from vaadin-front
    bind :80
    acl app1 path /ui1
    acl app1 path_beg /ui1/
    acl app2 path /ui2
    acl app2 path_beg /ui2/
    use_backend vaadin-app-1 if app1
    use_backend vaadin-app-2 if app2
    # Conditional, because http-request rules run before use_backend: an
    # unconditional deny here rejects the two prefixes as well.
    http-request deny deny_status 404 unless app1 or app2

backend vaadin-app-1 from vaadin
    # And for the same reason the prefix strip lives here rather than in the
    # frontend, where it would erase the prefix the ACL above matches on.
    http-request replace-path ^/ui1/?(.*) /\1
    http-response replace-header Location ^(https?://[^/]+)?/(.*) \1/ui1/\2
    http-response replace-header Set-Cookie (.*;\s*[Pp]ath=)/(;.*)?$ \1/ui1\2
    server vaadin1 vaadin1:8080
```

(and the same again for `/ui2`). The backends keep the distinct session cookie
*names* the NGINX sibling gives them; the `csrfToken` cookie, which both apps
issue under the same name, is separated by the `Set-Cookie` rewrite rather than
by telling each app its prefix the way `traefik/http/multiple-root-context` has
to.

### `haproxy/https/root-context`

```
frontend vaadin-in from vaadin-front
    bind :443 ssl crt /usr/local/etc/haproxy/tls/localhost.crt
    default_backend vaadin-app
```

with

```yaml
      - ../../../tls/localhost.crt:/usr/local/etc/haproxy/tls/localhost.crt:ro
      - ../../../tls/localhost.key:/usr/local/etc/haproxy/tls/localhost.crt.key:ro
```

`crt` takes a PEM that may carry the key; when it does not, HAProxy loads
`<crt>.key` from beside it, which is why the existing fixture works unchanged
under a second name. The backend keeps `SERVER_FORWARD_HEADERS_STRATEGY=NATIVE`
as in both siblings; `X-Forwarded-Proto` comes from the shared base.

## Repo wiring checklist

- `scenarios.tsv` — 24 rows (`key | description | paths | short`, `short` ≤ 60
  chars). Say in the `*-push-url` descriptions that the config is the shared
  file, not a copy of it.
- `scripts/gen-readmes.sh` — `proxy_label` arms for `haproxy/http` and
  `haproxy/https`; a `haproxy/*` generation arm that resolves whatever the
  compose file mounts at `conf.d/10-vaadin.cfg` (the direct analogue of
  `nginx_template_for`), inlines it in a fenced block and links the shared
  `haproxy/haproxy-base.cfg`.
- `run-scenario.sh` — add `haproxy/http` and `haproxy/https` to the picker.
- `.github/workflows/haproxy.yml` — new caller, copied from `traefik.yml`;
  `.github/proxy-images.txt` — add `haproxy:3.2.23`, keeping it in step with the
  compose defaults for Dependabot.
- `.github/dependabot.yml` — add `/haproxy/**` (and, while there, the missing
  `/traefik/**`) to the `docker-compose` ecosystem and `haproxy` to the
  `proxies` group.
- `README.md` — badge, the proxy list, the directory tree, and an
  `## HAProxy Notes` section.
- `CLAUDE.md` — repository layout, the amended `*-sse` paragraph, the
  `load-balancer-cookie-prefix` naming convention, the `HAPROXY_IMAGE` knob, and
  the caveats: no `location` construct so a frontend sees every path; the
  two-`defaults` inheritance rule; the config tokenizer versus regexes with
  spaces; `Path=/app` has no trailing slash; `compression type` is an allowlist.
- No change needed: `scripts/gen-matrix.sh`, `scripts/check-catalog.sh`,
  `scripts/check-compression.sh`, `run-all.sh`, `run-test.sh`,
  `integration-tests/`, `lint.yml`.

## Rollout

Each step ends green on `lint.yml` (catalogue consistency, generated READMEs)
and on its own scenarios. Before any of them, re-run the parse loop from the
issue over every `.cfg` in the tree — it is two seconds and it is the check that
would have caught both regex traps above:

```bash
for s in haproxy/*/*/[a-z]*.cfg haproxy/templates/*.cfg; do
  docker run --rm -v "$PWD/haproxy:/probe:ro" haproxy:3.2.23 haproxy -c -dr \
    -f /probe/haproxy-base.cfg -f "/probe/${s#haproxy/}" >/dev/null \
    || echo "FAILED: $s"
done
```

### PR 1 — the spine (~1 day)

`haproxy-base.cfg`, `templates/passthrough.cfg`, `templates/passthrough-sse.cfg`,
`templates/strip-prefix.cfg`, `templates/add-prefix.cfg`,
`templates/servlet-mapping.cfg` and the scenarios that use them —
`root-context`, `root-context-sse`, `custom-context`, `custom-to-root-context`,
`root-to-custom-context`, `servlet-mapping` — plus the catalogue rows,
the `gen-readmes.sh` arms, the `run-scenario.sh` picker, the CI caller and the
pinned image. This is the shape-defining PR; everything after it is repetition.
All six scenarios in it are already verified end to end (Step 0), so the review
is about wording and wiring rather than about whether it works.

### PR 2 — the rest of `haproxy/http` minus load balancing (~0.5 day)

The remaining `*-push-url` and `*-sse` directories (compose-file-only, since
they reuse the templates) plus the two `*-servlet-mapping` context translations,
which are the only real work: a prefix rewrite and the Hilla rewrite in the same
frontend, where the ordering decides whether Hilla's `Flux` is alive — and that
is only caught with `it.websocket=true`, i.e. never in an `-sse` variant.
`custom-to-root-context-servlet-mapping` is already verified 6/6 (Step 0) and
its rules are in this plan; `root-to-custom-context-servlet-mapping` is the
mirror image and is the one place left where a rule could be subtly wrong.

### PR 3 — load balancing, multi-app, TLS (~1 day)

`load-balancer`, `load-balancer-sse`, `load-balancer-cookie-prefix`,
`multiple-root-context`, `https/root-context`, `https/root-context-sse`, the
README and CLAUDE.md updates. This is where the issue expected the risk to be;
after Step 0 all five distinct configs in it are measured green, and what is
left is the writing — the notes section, the caveats, and the scenario
descriptions that have to say *why* `load-balancer-cookie-prefix` exists.

## Open questions carried into implementation

1. **`load-balancer-jvmroute`** (the issue's optional stretch). Unprobed. It
   would be the first scenario in the repo to consume `TOMCAT_JVMROUTE` at all,
   which is an argument for it; the `stick match` / `stick store-response`
   converter chain is the least-travelled syntax in the plan, which is an
   argument for deciding after PR 3 rather than before.
2. **Whether `timeout tunnel` deserves a scenario of its own.** It is the one
   knob no other tree has an equivalent for, and the issue names it as a reason
   to add HAProxy at all — but nothing in the current 24 exercises it, because
   Vaadin's 60s heartbeat keeps a push connection from ever being idle long
   enough. A `root-context-tunnel-timeout` with a deliberately short value
   would document what a too-small tunnel timeout does to PUSH; it is also the
   only scenario here that would need a test the smoke suite does not have.
3. **Whether `deny 404 unless is_app` is the right shape** for the prefix
   scenarios, or whether letting the backend answer everywhere is closer to what
   an OpenShift route actually does. The former matches the Apache and NGINX
   siblings; the latter is arguably the more honest HAProxy idiom.
