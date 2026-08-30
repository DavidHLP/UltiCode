# Resume

- Objective: implement the user-authorized microservice architecture remediation across P0-P3, repository delivery controls, verification, two reviews, local commits, and truthful external blockers.
- Active task: P0-SEC-001 shared secure-cookie policy; a focused HTTP-header regression is RED.
- Branch: fix/architecture-remediation from main@8b4012b3d13678eaec38a82980c8e3558123b5a8; origin/main is one commit behind the baseline.
- Scope: the 42 tasks under architecture_remediation_20260830 in .auto-flow/TASKS.yaml.
- Invariants: retain five Data Owners, two Workers, Submission Owner, Streams adapters, Inbox, Worker SLO, AdminUserEnricher, BackupProcessPort, contract gate, owner migration manifest, idempotency, and user work.
- Explicit exclusions: Kubernetes, Service Mesh, Kafka, Seata, further App service split, and five independent database clusters.
- Authority: repository code/config/migration/scripts/tests/docs/local commits are authorized; push, production data, production deploy, production credential rotation, sudo/group mutation, and production account changes are not.
- Baseline static gates: architecture/docs/migration-preflight/Compose dev/Compose prod all exit 0; evidence is under .auto-flow/evidence/architecture-remediation-20260830/.
- Docker blocker: current user lacks group docker; docker version/info exit 1 on /var/run/docker.sock root:docker 0660. Do not rerun Docker-dependent gates until re-login/newgrp or another non-mutating compatible context exists.
- Maven baseline: clean compile/test/verify all exit 0; 809 Surefire reports, 2739 tests, 0 failures, 0 errors, 20 skipped. Coverage summary is persisted in maven-summary.json.
- Degraded tooling: Java LSP references were unavailable, so exported-symbol impact discovery uses Codebase Memory plus direct source.
- Next: commit the baseline/traceability checkpoint and the P0-SEC-001 RED checkpoint, then implement the minimum shared cookie policy.
- Delivery: local Conventional Commits only; no push.
