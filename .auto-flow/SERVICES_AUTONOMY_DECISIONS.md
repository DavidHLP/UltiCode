# Services autonomy decisions

- 2026-08-19: Freeze `SubmissionFactsSnapshot` as the immutable Submission intake seam. Require `schemaVersion == CURRENT_SCHEMA_VERSION`, matching user/problem facts, and fail closed on missing or invalid facts.
- 2026-08-19: Prove local transaction/outbox/fence/reaper behavior before any production route change.
- 2026-08-19: Use disposable owner-grant rehearsal only; do not revoke production grants or perform production cutover without external authority.
- 2026-08-19: Retain legacy compatibility paths until quiesce, reconciliation, rollback artifact, observation window and external acceptance evidence exist.

## Resume

Ready local work is exhausted for this packet. The only next actions are external: obtain ARCH-002 target/account authority and ARCH-003 production stability/deployment/quiesce/observation/rollback/retirement evidence. Do not delete legacy paths or claim production acceptance from local rehearsal.
