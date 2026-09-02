# P4-LEGACY-011 Submission Schema Contraction

> status: PASS (repository/disposable rehearsal)
> owner: Submission / App migration maintainers
> evidence_level: disposable MySQL contract; no production application

P4-008 and P4-009 removed App Submission reads, persistence, mapper scanning,
and duplicate DTOs. P4-010 removed the App compile dependency on
`backend-judge-runtime`. The remaining physical contraction is therefore the
existing separately invoked `flyway-contraction.conf` history, not the normal
owner migration chain.

## Safety contract

The contraction runbook requires, in order:

1. Owner Submission and Notification parity by row count, column signature, and
   table checksum.
2. A verified backup reference and explicit writer-quiescence confirmation.
3. Zero exact App legacy-table DML grants and zero schema/global privileges.
4. Proof rows in `ulticode.owner_contraction_proof` for both owners.
5. The explicit contraction confirmation before invoking the separate Flyway
   location.
6. A forward recovery descriptor: restore the verified pre-window backup, restore
   the owner route/grants only under an approved rollback procedure, and never
   infer rollback from a missing legacy table.

The existing migration remains immutable. No applied migration was edited.
`consumer_inbox` and `app_command_receipt` remain outside this contraction.

## Disposable verification

```text
contraction confirmation gate: PASS
legacy App grant rejection: PASS
legacy grant remains until explicit contract: PASS
broader App grant fail-closed gate: PASS
forward contraction and owner preservation: PASS
owner-schema-contraction-contract: PASS
```

Executed through:

```bash
bash scripts/test/owner-schema-contraction-contract.sh
```

The contract starts a disposable MySQL container, creates upgrade-shaped source
and owner tables, rejects missing confirmation and active App grants, verifies
parity/checksums, and validates the forward contraction path. Temporary
containers and data are destroyed on exit.

This evidence is limited to repository and disposable resources. It does not
claim a production database change, production writer drain, production backup
authority, RPO/RTO, zero downtime, or a reversible in-place DROP.
