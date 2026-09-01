# ADR-0001: Defer platform expansion beyond the current microservice stack

- Status: Accepted
- Date: 2026-08-30
- Deciders: Repository architecture remediation authority granted by the user; implemented by the current architecture task
- Review trigger: see “Re-evaluation conditions”

## Context

UltiCode already has five Data Owners, two Workers, provider-owned Contract Modules, Dubbo Triple, Nacos, Redis Streams, Outbox/Inbox, MySQL owner schema/account isolation, MeiliSearch, OpenTelemetry, Prometheus instrumentation, Compose and the current deployment scripts.

The remediation objective is to make those mechanisms secure, deep, testable, reversible and operable. Introducing another orchestration, messaging or transaction platform before the existing owner cutovers and control planes are complete would create new seams without removing the current risks.

## Decision

The current remediation does **not** introduce:

1. Kubernetes.
2. A Service Mesh.
3. Kafka or another replacement message broker.
4. Seata or another distributed-transaction coordinator.
5. Further decomposition of the App business process into more microservices.
6. Five independent database clusters, one per Data Owner.

The remediation may implement:

- multi-instance services under Compose/current deployment tooling;
- a shared highly available MySQL topology while retaining Owner schema/account isolation;
- Redis Sentinel/Cluster or another Redis-compatible HA mode;
- a multi-node Nacos production profile;
- Dubbo TLS/mTLS and workload identity;
- a remote isolated Judge runtime;
- focused Contract Module ownership changes that do not add business processes.

## Why the current mechanisms are sufficient now

### Service orchestration

The immediate gaps are fixed container identity, singleton jobs, graceful shutdown, health/readiness, rollout and artifact verification. These can be proved with the current Compose and host-deploy surfaces before adopting another scheduler.

### Messaging

Redis Streams already provides consumer groups, PEL, claim/reclaim, ACK, retry, DLQ, idempotency and replay. The required work is command-level ACL, crash/replay verification, backpressure and observability—not a broker replacement.

### Transactions

Owner-local transactions plus Outbox/Inbox avoid cross-service atomic commits. The remaining cross-schema audit write must be removed, not coordinated by a distributed transaction manager.

### App service size

`app-api` and Dubbo seams need ownership cleanup, but there is no demonstrated independent team, release cadence, capacity curve or SLO that justifies another business process. Contract cleanup precedes any future deployment split.

### Databases

Five Owner schemas/accounts already establish logical ownership. The present production-readiness gap is shared-instance HA, backup/restore and least privilege. Five independent clusters would multiply operational cost before a workload or compliance trigger exists.

## Alternatives considered

### Kubernetes now

Rejected for this remediation. It would add cluster lifecycle, ingress, secret, policy, workload and deployment complexity while scheduler safety, workload identity and stateful HA semantics still require application changes.

### Service Mesh now

Rejected. Mesh mTLS would not replace provider authorization, end-user delegation, Cookie/CSRF security, Redis ACL or contract ownership. Dubbo mTLS and explicit caller policy are narrower.

### Kafka now

Rejected. Current reliability requirements fit the existing Redis Streams Module. Migration would require dual publication, offset transfer, schema governance, replay tooling and operations without a measured throughput or retention need.

### Seata now

Rejected. It would couple Owner availability and reintroduce distributed transaction coordination where Outbox/Inbox is already the chosen consistency model.

### More App microservices now

Rejected. The immediate problem is contract and ownership sprawl, not proven process-level scaling or team autonomy.

### Five independent database clusters now

Rejected. A shared HA MySQL topology with strict Owner accounts has lower operational cost and meets the present ownership model.

## Consequences

### Positive

- Remediation stays within the existing operational skill set.
- Existing deep Modules remain authoritative.
- Rollback remains repository- and artifact-based.
- Security and data ownership fixes are not hidden behind infrastructure expansion.
- Capacity decisions can be based on measured SLOs.

### Negative

- Compose/current host deployment remains the orchestration limit until triggers are met.
- MySQL, Redis and Nacos require explicit HA reference profiles and drills.
- Redis Streams remains a shared infrastructure failure domain.
- App remains one business process even after Contract Module cleanup.

## Risks

- Deferral can become permanent if triggers are not reviewed.
- Current tooling may eventually limit rollout speed or scheduling policy.
- Shared stateful infrastructure may become a capacity or compliance constraint.

These risks are mitigated by the explicit re-evaluation conditions below and by metrics added under `P2-OBS-001`.

## Re-evaluation conditions

Re-open this ADR when one or more of the following is demonstrated with evidence:

### Kubernetes or another orchestrator

- more than one production host requires automatic placement/failover;
- rolling deployment and service recovery cannot meet the agreed availability SLO with current tooling;
- environment count or replica count makes host-level reconciliation operationally unsafe;
- compliance requires policy-as-code controls unavailable in the current deployment surface.

### Service Mesh

- service count and certificate rotation make direct Dubbo mTLS policy unmaintainable;
- measured cross-service telemetry gaps remain after OTel/Dubbo instrumentation;
- uniform traffic policy is required across protocols and cannot be enforced in application/shared libraries.

### Kafka or another broker

- measured sustained throughput, retention, replay horizon or consumer fan-out exceeds the verified Redis Streams envelope;
- Redis memory economics or recovery time violates the queue SLO;
- event retention/audit requirements require immutable long-term log storage.

### Distributed transaction coordinator

- a mandatory business invariant cannot be represented by owner-local transaction plus idempotent Outbox/Inbox;
- compensation is proven insufficient and the availability/coupling cost is explicitly accepted.

### Further App decomposition

- a bounded domain has an independent owning team and release cadence;
- it needs materially different scaling or availability;
- App incidents repeatedly cross-impact unrelated domains;
- a stable provider-owned contract and data ownership transition are already proven.

### Independent Owner database clusters

- compliance requires physical data isolation;
- noisy-neighbor or maintenance windows violate SLOs;
- one Owner requires materially different engine/version/capacity;
- shared HA MySQL cannot meet measured recovery or scaling targets.

## Future migration costs

Any future adoption must budget for:

- mixed-version compatibility and rollback;
- data/event backfill and reconciliation;
- dual-run observation;
- secret/certificate migration;
- CI/CD and runbook replacement;
- operator training and on-call ownership;
- decommissioning of the old control plane only after proof.

## Next review

Review at the first of:

- an approved production SLO change;
- a second production host;
- a measured Redis/MySQL/Nacos capacity or recovery violation;
- a new compliance requirement;
- six months after first production traffic.
