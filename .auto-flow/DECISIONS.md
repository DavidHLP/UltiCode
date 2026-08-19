
## ARCHFIX-DEC-005: UserDirectoryQueryPort is the bounded ARCHFIX-003 seam

- **Context**: `AdminAnalyticsPort` and `SubmissionAdminReadPort` already provide independent coarse owner seams. `OwnerUserSearchReadAdapter` still composes Auth account, Auth identity and App profile reads for Search, so merging all three domains would create a new god contract.
- **Decision**: Introduce only an App-owned `UserDirectoryQueryPort` for Search user-directory reads. Its contract is versioned, bounded and batch-oriented: text search, stable id-ascending enumeration, and batch lookup by account ids. The returned row contains safe display fields plus source freshness/version metadata. Auth owns account/identity data; App owns profile projection; the adapter hides composition behind this single seam.
- **Alternatives**: Merge Admin analytics and Submission admin reads into one universal Query interface (rejected: unrelated lifecycle and ownership); keep per-user synchronous enrichment (rejected: leaks timeout/retry/freshness composition); immediately build an event projection (deferred: larger migration than this atomic slice).
- **Consequences**: Search callers stop knowing the Auth/App fan-out. Existing Admin and Submission contracts remain unchanged. Old adapter remains rollback-only until ARCHFIX-005.
- **Affected Tasks**: ARCHFIX-003, ARCHFIX-004, ARCHFIX-005
