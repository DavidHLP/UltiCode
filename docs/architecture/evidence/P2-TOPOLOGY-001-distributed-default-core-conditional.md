# P2-TOPOLOGY-001 Distributed is the Sole Default; Core is Explicitly Conditional

> status: AMENDED
> head: working tree 2026-09-05
> scope: runtime topology default policy
> evidence: Repository Implemented + source/config review
>
> **Amendment 2026-09-05:** The distributed profile remains the only default.
> Core uses explicit assembly, not class/resource isolation. Its registry keeps
> Auth and Admin enabled and keeps App, Submission, Notification, and Search
> registered but disabled. No Core business HTTP/WS journey is currently
> available; the parent exposes readiness only.

## 1. Distributed is the sole default

### Source evidence

**`docs/architecture/overview.md:42-64`** — states distributed topology
(5 Data Owners + 2 Workers as separate PM2 processes) is the default
deployment model.

**`ecosystem.config.cjs:106-125`** — PM2 process definitions for
`ulticode-auth`, `ulticode-admin`, `ulticode-app`, `ulticode-submission`,
`ulticode-notification`, `ulticode-judge`, `ulticode-judge-worker`,
`ulticode-search`. No Core process is in the default PM2 list.

**`scripts/dev/devstack-manifest.sh`** — defines the default DevStack with
7 Owner/Worker services. Core is not part of the default manifest.

**`services/docs/SERVICES_ISSUES.md`** — conclusion states distributed is
the sole default; Core is opt-in only.

### Core is opt-in

**`services/core/src/main/resources/application.yml`**:
```yaml
core:
  owner-contexts:
    enabled: ${CORE_OWNER_CONTEXTS_ENABLED:false}
  judge:
    required: ${CORE_JUDGE_REQUIRED:false}
```

The Core process is opt-in, and its generic properties default to disabled.
The named `core` DevStack scope explicitly enables Auth/Admin child contexts
and keeps Judge readiness optional. This does not make Core a default route.
Core exposes no business HTTP/WS routes — only
`/api/v1/core/health/ready` on port 9108.

### No dual defaults

**`scripts/dev/test.sh:43`**:
```
core         Core boot assembly and readiness smoke without Owner side effects.
```

The `core` test mode is an **explicit separate entrypoint**, not a default.
Static and unit modes do not touch Core. Full-local/full/integration run
the distributed topology.

## 2. Core profile is conditional

### Profile activation

Core is activated only via:
- `scripts/dev/test.sh core` — parent/config/readiness smoke with Owner
  contexts disabled
- the named `core` DevStack/PM2 scope, which explicitly sets
  `CORE_OWNER_CONTEXTS_ENABLED=true`
- the `core` Spring profile (not in default DevStack)

### Core exposes only readiness HTTP

`CoreSecurityConfiguration` permits only `/api/v1/core/health/ready` and
denies all other requests. No business endpoints, no WebSocket push, no
Dubbo service export on HTTP.

### Core does not publish infrastructure or backend ports

Per `AGENTS.md` security invariant: "Base and production Compose
configurations must not publish MySQL, Redis, Nacos, or backend ports."
Core is a single-process assembly; its only published port is
`server.port=${CORE_SERVER_PORT:9108}` for the readiness probe.

## 3. Core cannot become a default

### Constraints (from handoff)

- "distributed MUST remain the sole default topology"
- "Core may only end as PROMOTE_LATER, RETAIN_TEMPORARILY_WITH_EXPIRY, or
  REMOVE_CORE_EXPERIMENT"
- "Core CANNOT flip to default"

### Expiry

Core is a conditional, time-boxed experiment. Its current allowlist,
expiry, and rollback are recorded by SVC-025 and ADR-0012.

## 4. Topology decision matrix

| Dimension | Distributed (default) | Core (conditional) |
|---|---|---|
| Service count | 5 Data Owners + 2 Workers | 1 Core JVM + independent Judge |
| Owner contexts | Separate processes | Auth/Admin enabled; App/Submission/Notification/Search registered but disabled |
| DataSource | Per-owner process | Parent has per-owner factories; enabled child startup receives owner properties |
| Business HTTP routes | Per-owner port | None (only readiness on 9108) |
| Dubbo | Nacos registry | Child contexts disable Dubbo; local adapters are explicit |
| Profile | default (no `core`) | `core` profile |
| Test mode | static/unit/full-local/full/integration | `core` parent/config/readiness smoke |
| State | Default topology | Experimental, opt-in, expires 2026-10-06 |

## 5. Evidence Level

Repository Implemented + source/config review. The registry, explicit scans,
parent readiness and disabled-owner behavior are source/test concerns. The
bounded URL loader is lifecycle support only and is not evidence of sibling
class/resource invisibility. No production deployment evidence is claimed.

The enabled-owner wiring and representative business journey remain
unvalidated; absence of those checks blocks promotion and preserves the
distributed default.

## Verification

- `bash scripts/test/core-profile-contract.sh` — explicit assembly and bounded
  lifecycle contract
- `bash scripts/dev/test.sh --describe` — Core is a separate parent smoke mode
- `bash scripts/dev/test.sh static` — zero-infrastructure static gates
- enabled-owner wiring and distributed/Core business journey — not run without
  disposable infrastructure and a Core business HTTP/WS seam