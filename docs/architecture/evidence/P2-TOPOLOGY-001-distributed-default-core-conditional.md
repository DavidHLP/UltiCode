# P2-TOPOLOGY-001 Distributed is the Sole Default; Core is Explicitly Conditional

> status: FROZEN
> head: c344f6268084a893f0bde871da21e5130a331207
> scope: runtime topology default policy
> evidence: Repository Implemented

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
    enabled: ${CORE_OWNER_CONTEXTS_ENABLED:true}
```

The flag defaults to `true` but Core only activates when the `core` profile
is used. The default DevStack (`devstack-manifest.sh`) does not use the
`core` profile. Core does **not** expose any HTTP business routes — only
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
- `scripts/dev/test.sh core` — runs
  `mvn -pl core -am install -DskipTests` then `mvn -Punit -pl core test`
- `CORE_OWNER_CONTEXTS_ENABLED=true` environment in the Core PM2 config
- The `core` Spring profile (not in default DevStack)

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

Core is marked as a **conditional, time-boxed experiment**. Its status is
tracked by SVC-025 in `services/docs/SERVICES_ISSUES.md` and by ADR-0011.

## 4. Topology decision matrix

| Dimension | Distributed (default) | Core (conditional) |
|---|---|---|
| Service count | 7 PM2 processes | 1 JVM |
| Owner contexts | Separate processes | In-JVM child contexts |
| DataSource | Per-owner process | Per-child DataSource props |
| Business HTTP routes | Per-owner port | None (only readiness on 9108) |
| Dubbo | Nacos registry | `dubbo.enabled=false`, check=false |
| Profile | default (no `core`) | `core` profile |
| Test mode | static/unit/full-local/full/integration | `core` only |
| State | Production topology | Experimental, opt-in |

## 5. Evidence Level

Repository Implemented. Source-anchored to `overview.md`,
`ecosystem.config.cjs`, `devstack-manifest.sh`, `application.yml`,
`test.sh`, `CoreSecurityConfiguration`, and `AGENTS.md`.

No production deployment evidence claimed — this is the repository's
documented default policy.

## Verification

- `grep -n "core" ecosystem.config.cjs` — Core not in default PM2 list
- `grep -n "CORE_OWNER_CONTEXTS_ENABLED" services/core/src/main/resources/application.yml` — opt-in flag
- `bash scripts/dev/test.sh --describe` — `core` mode listed as explicit
- `bash scripts/dev/test.sh static` — does not reference Core owner contexts