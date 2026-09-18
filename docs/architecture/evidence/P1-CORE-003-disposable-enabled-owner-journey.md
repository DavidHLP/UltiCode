# P1-CORE-003: Core Enabled-Owner Disposable Journey

## Current status

`P1-CORE-003` is **PARTIAL / BOUNDED WIRING PROVEN** for the selected Core
design. The opt-in disposable gate has locally validated real Auth/Admin child
boot, readiness, identity read, legal permission grant, missing-signer
fail-closed behavior, Redis access, and cleanup. Core child contexts remain
non-Web and the parent security chain exposes only `/api/v1/core/health/ready`;
there is no Core business HTTP/WS route on which the four-step journey can
execute.

## Selected scope and source evidence

The registry allowlist enables `auth` and `admin` only. The Admin child receives
the four exact local singletons through `CoreLocalContractAssembly`: the owner
context manager plus `CoreLocalIdentityQueryAdapter`,
`CoreLocalAuthorizationMutationAdapter`, and `CoreLocalAccountQueryAdapter`.
`CoreLocalAdapterWiringTest` proves the in-process contract paths, while the
disposable gate additionally proves real database/Redis-backed Auth/Admin child
startup and cleanup.

App, Submission, Notification, and Search remain registered but disabled.
Their consumers still require their distributed Dubbo seams or additional
local adapters; they are not hidden prerequisites for this task.

## First journey (distributed reference)

The bounded journey remains:

1. `POST /auth/login`
2. `GET /problems/{id}`
3. `POST /bookmarks/quick`
4. ordinary-user `POST /problems` → expected 403/typed denial

The distributed `app-journey` scope is the executable reference. The Core
business variant remains deferred because Core has no business HTTP/WS seam;
do not report the parent readiness smoke or the bounded wiring proof as a
business journey result.

## Cost and expiry

Record cold start, memory, required variables, changed files, and validation
steps only when both topologies run under identical disposable inputs. Do not
infer production SLO, HA, or feature equivalence. The expiry checkpoint is
`2026-10-06`; no automatic renewal is allowed.

## Validation result

- `CoreLocalAdapterWiringTest`: repository-level local contract test.
- `scripts/test/core-enabled-owner-journey.sh`: disposable Auth/Admin bounded
  wiring proof; locally passed with real Testcontainers infrastructure.
- `scripts/dev/test.sh core`: parent/config/readiness smoke only.
- Distributed/Core business journey: Core has no business route, so no parity
  result is claimed.
