# Plan: Caddy reverse-proxy scenarios

Date: 2026-11-14
Branch: `chore/caddy`

## Goal

Add a `caddy/` proxy tree beside `apache-httpd/`, `nginx/`, `traefik/` and
`haproxy/`: **23 scenarios** (21 `caddy/http`, 2 `caddy/https`), configured as a
shared `caddy/Caddyfile` plus one site block per scenario, pinned to
`caddy:2.11.9` (the current release, 2026-11-05).

The tree's reason to exist: Caddy is the proxy a lot of smaller and self-hosted
Vaadin deployments reach for first — one binary, automatic HTTPS, a short config
— and its configuration model differs from the other four. A Caddyfile is
handlers chosen by matchers, and **Caddy decides the order the handlers run
in**, not the file. That shapes one scenario (`root-to-custom-context-servlet-mapping`)
and is the single most useful thing this tree documents.

## Layout

Caddy has an `import` directive, so the split Apache uses is native here and no
mount trick is needed (compare HAProxy's `conf.d` concatenation):

```
caddy/Caddyfile              -> /etc/caddy/Caddyfile         # the image's default config path
<scenario site block>        -> /etc/caddy/vaadin.caddyfile  # imported by the line above's last line
```

The shared file carries the global options (`servers { protocols h1 h2 }`) and a
`(common)` snippet — `log` plus the compression allowlist — that every site
block imports. The compose files therefore need no `command:`.

The family structure is HAProxy's, for the same reason: with no location-scoped
upgrade switch, five families collapse onto one file each.

| file | scenarios |
|---|---|
| `templates/passthrough.caddyfile` | `root-context`, `root-context-push-url`, `custom-context`, `custom-context-push-url` |
| `templates/passthrough-sse.caddyfile` | `root-context-sse`, `root-context-push-url-sse`, `custom-context-sse` |
| `templates/strip-prefix.caddyfile` | `custom-to-root-context`, `custom-to-root-context-push-url` |
| `templates/add-prefix.caddyfile` | `root-to-custom-context`, `root-to-custom-context-push-url` |
| `templates/servlet-mapping.caddyfile` | `servlet-mapping`, `servlet-mapping-push-url` |
| scenario-local `vaadin.caddyfile` ×10 | the three remaining `*-sse`, the two `*-servlet-mapping` context translations, `load-balancer`, `load-balancer-sse`, `multiple-root-context`, the two `https/*` |

No Caddy-only scenario. 18 catalogue rows are N/A: the 17 AJP rows (Caddy has
no AJP transport, only HTTP and FastCGI) and `root-context-legacy` (one upgrade
idiom only).

Tiers: 8 `smoke` — the same mechanisms HAProxy marks smoke (`root-context`,
`root-context-sse`, `custom-context`, `custom-to-root-context`,
`servlet-mapping`, `load-balancer`, `multiple-root-context`,
`https/root-context`), minus its HAProxy-only cookie-prefix row. The catalogue
grows from 35/112 to 43/135 smoke rows.

## Step 0 — measured, not assumed (executed 2026-11-14)

**No Docker daemon was available on the machine this was written on**, so the
tree was not run through `docker compose`. Instead every scenario was run with
the official `caddy_2.11.9_linux_amd64` release binary (sha512 checked against
the release's checksum file) and the production `my-app` jar built from this
branch with `java -jar`, driven by the repo's own tools: `./run-test.sh` with
the flags `ci-run-chunk.sh` passes (transports, `it.websocket`, and a real
`it.proxy.restart.cmd` that restarts Caddy) and `scripts/check-compression.sh`.

The harness copied each scenario's two config files and changed only what a
container provides: service hostnames to `127.0.0.1:<published port>`, `:80` /
`:443` to `:9090` / `:9443`, and `/etc/caddy/...` paths to local ones. Backends
got the environment from the scenario's compose file. The proxy logic under
test was byte-for-byte the committed files. **The first CI run of `caddy.yml`
is still the first time these compose files themselves are brought up** —
mount paths, the image's default command, and `ci-run-chunk.sh`'s
`docker compose restart` of the `web` service are exercised there and nowhere
before. One part of that was checked from the registry instead: the
`caddy:2.11.9` linux/amd64 image config has `Cmd` =
`caddy run --config /etc/caddy/Caddyfile --adapter caddyfile`, no `Entrypoint`
and no `User`, so mounting the shared file over `/etc/caddy/Caddyfile` is all
the compose files need, and the root process can bind `:80`/`:443` and read the
mounted key.

All 16 config files also pass `caddy fmt --diff` (Caddy warns at startup about
an unformatted Caddyfile) and `caddy adapt --validate` as base + site pairs.

### Findings

| # | Question | Result |
|---|---|---|
| 1 | Does `encode gzip` leave streams alone by default? | **No.** With no `match`, `/sse-probe` came back `Content-Encoding: gzip` (it still flushed per event). The allowlist is mandatory, as in Traefik. With it: HTML gzipped, `/sse-probe` plain, ticks ~500 ms apart. |
| 2 | Does `reverse_proxy` need a `proxy_buffering off` equivalent? | **No.** Ticks trickle through with no `flush_interval`; Caddy flushes immediately for `text/event-stream` and responses of unknown length. |
| 3 | `header_down` over a multi-valued `Set-Cookie` | **Applies to every value.** One rule rescoped both `JSESSIONID` and `csrfToken` (`Path=/` → `Path=/app`). `Location: …/hello-flow` → `…/app/hello-flow`. |
| 4 | A space in a `header_down` regex | **Split by the tokenizer**, as in HAProxy, but loudly: `wrong argument count or unexpected line ending`. `\s` is used. |
| 5 | Unmatched request | **`200`, empty body.** The prefix-stripping scenarios end with `handle { respond 404 }`; `/hello-flow` outside `/app/` is then 404 as in NGINX. |
| 6 | `*-sse` refusal | `@upgrade header Upgrade *` + `respond @upgrade 501`: the upgrade gets **501 from Caddy**, a plain GET 200. |
| 7 | `lb_policy cookie ROUTEID` | Writes `ROUTEID=<hmac of upstream>; Path=/; HttpOnly; SameSite=Lax`. A backend still booting gives one `502` (`connection refused`) and is **not** blacklisted afterwards. |
| 8 | TLS from the existing fixture | `:443 { tls <crt> <key> }` serves the repo's cert over **HTTP/2**, requests nothing from ACME and starts no `:80` redirect listener. HTTP/3 is on by default for TLS sites and is turned off in the base (`Alt-Svc` would point at an unpublished UDP port). |
| 9 | Directive order in `root-to-custom-context-servlet-mapping` | **The trap is real.** Written flat (`uri @hilla strip_prefix /ui` then `rewrite * /app{uri}`), Caddy runs `rewrite` first, `@hilla` never matches and Hilla calls go to `/app/ui/connect/…`. Same backend, same suite: **16 run, 2 failures**, both `HelloHillaIT.buttonsTriggerNotifications`; every Flow test green. In a `route` block: 16/16. |

### Measured scenario results

All 23 green, run sequentially in one sweep (~75 s each). "16/16" is the full
suite including `PushReconnectIT` (Caddy restarted mid-test), `RuntimeScriptsIT`
and `DownloadRangesIT`; the `*-sse` runs are 13 with 1 skipped (Hilla push,
`it.websocket=false`), the same shape as the HAProxy SSE siblings.
`check-compression.sh` passed for every one: HTML gzipped, `/sse-probe` not,
ticks ~500 ms apart.

| scenario | tier | ITs | compression |
|---|---|---|---|
| `http/root-context` | smoke | 16/16 | ok |
| `http/root-context-sse` | smoke | 13, 1 skipped | ok |
| `http/custom-context` | smoke | 16/16 | ok |
| `http/custom-to-root-context` | smoke | 16/16 | ok |
| `http/servlet-mapping` | smoke | 16/16 | ok |
| `http/load-balancer` | smoke | 16/16 | ok |
| `http/multiple-root-context` | smoke | 16/16 on `/ui1/`, 16/16 on `/ui2/` | ok |
| `https/root-context` | smoke | 16/16 | ok |
| `http/root-to-custom-context` | full | 16/16 | ok |
| `http/root-context-push-url` | full | 16/16 | ok |
| `http/root-context-push-url-sse` | full | 13, 1 skipped | ok |
| `http/custom-context-sse` | full | 13, 1 skipped | ok |
| `http/custom-context-push-url` | full | 16/16 | ok |
| `http/custom-to-root-context-push-url` | full | 16/16 | ok |
| `http/custom-to-root-context-sse` | full | 13, 1 skipped | ok |
| `http/root-to-custom-context-push-url` | full | 16/16 | ok |
| `http/root-to-custom-context-sse` | full | 13, 1 skipped | ok |
| `http/servlet-mapping-push-url` | full | 16/16 | ok |
| `http/servlet-mapping-sse` | full | 13, 1 skipped | ok |
| `http/custom-to-root-context-servlet-mapping` | full | 16/16 | ok |
| `http/root-to-custom-context-servlet-mapping` | full | 16/16 (flat variant: 2 failures, finding 9) | ok |
| `http/load-balancer-sse` | full | 13, 1 skipped | ok |
| `https/root-context-sse` | full | 13, 1 skipped | ok |

## Repo wiring

- `scenarios.tsv` — 23 rows; `scripts/gen-readmes.sh` — `proxy_label` arms and
  a `caddy/*` arm resolving whatever is mounted at `/etc/caddy/vaadin.caddyfile`
  (the analogue of `haproxy_config_for`).
- `run-scenario.sh` picker; `scripts/check-catalog.sh` and `lint.yml`'s
  pinned-image check name the `caddy` tree.
- `.github/workflows/caddy.yml` — a copy of `haproxy.yml` with `proxy: caddy`
  and `caddy/**` path filters. `_scenarios.yml` unchanged: it reads the image
  from `.github/proxy-images.txt` (`caddy caddy:2.11.9`), keys SSE off the
  `-sse` suffix and finds backends by image.
- `.github/dependabot.yml` — `/caddy/**` in the `docker-compose` ecosystem and
  `caddy` in the `proxies` group.
- `README.md` — badge, proxy list, tree, CI table, `## Caddy Notes`.
- `CLAUDE.md` — layout, the `*-push-url` / `*-sse` / `multiple-root-context`
  conventions, `CADDY_IMAGE`, and five caveats.
- No change: `_scenarios.yml`, `gen-matrix.sh`, `ci-run-chunk.sh`,
  `check-compression.sh`, `run-test.sh`, `integration-tests/`.

## Open questions

1. **A Caddy-only scenario.** The obvious candidate is automatic HTTPS itself
   (`tls internal`, Caddy's own CA): it is what most Caddy deployments actually
   run, and the tests already ignore certificate errors. Left out because it
   would test Caddy's PKI rather than anything Vaadin does through a proxy.
2. **`flush_interval -1`.** Not needed today (finding 2), but it is the setting
   people copy from forum answers for "SSE behind Caddy". Worth a sentence if
   anyone asks; not worth a scenario.
3. **Should `root-to-custom-context-servlet-mapping` be `smoke`?** It is the only
   row that catches finding 9, and it is `full`, as in every other tree. By the
   catalogue's own rule ("smoke if it is the only row in its tree testing that
   mechanism") it arguably qualifies; it was left `full` to keep the smoke tier
   the same shape across trees.
