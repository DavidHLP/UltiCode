# P1-CORE-003: Core Enabled-Owner Disposable Journey

## Current status

`P1-CORE-003` is **NOT_RUN / UNAVAILABLE** for the selected Core design.
Core child contexts are non-Web and the parent security chain exposes only
`/api/v1/core/health/ready`; there is no Core business HTTP/WS route on which
the four-step journey can execute.

## Selected scope and source evidence

The registry allowlist enables `auth` and `admin` only. The Admin child receives
`CoreLocalIdentityQueryAdapter` and `CoreLocalAuthorizationMutationAdapter`
through explicit startup registration. `CoreLocalAdapterWiringTest` proves an
actual Admin `AccountReadAdapter` resolves identity through the local contract;
it does not prove database, Redis, or full Auth/Admin startup.

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
variant is deferred until a real business HTTP/WS seam exists; do not report
the parent readiness smoke as a journey result.

## Cost and expiry

Record cold start, memory, required variables, changed files, and validation
steps only when both topologies run under identical disposable inputs. Do not
infer production SLO, HA, or feature equivalence. The expiry checkpoint is
`2026-10-06`; no automatic renewal is allowed.

## Validation result

- `CoreLocalAdapterWiringTest`: repository test, not disposable infrastructure.
- `scripts/dev/test.sh core`: parent/config/readiness smoke only.
- Enabled-owner Core wiring: not run.
- Distributed/Core business journey: Core unavailable; distributed run deferred
  until disposable credentials and seeded data are available.
