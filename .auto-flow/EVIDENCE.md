# Auto-pilot Evidence

Objective: reviewer CR remediation for the architecture review report.

## Final evidence

- Auth contract shape: 16 tests, 0 failures, 0 errors, 0 skipped.
- Auth mapper role-count integration: 2 tests, 0 failures, 0 errors, 0 skipped, real MySQL 8.0.
- App HiddenCaseLeakIT plus DefaultSubmissionProjectionTest: 14 tests, 0 failures, 0 errors, 0 skipped.
- Submission SubmissionUserQueryProviderIT: 8 tests, 0 failures, 0 errors, 0 skipped, real MySQL 8.0.
- Search worker unit: 11 tests, 0 failures, 0 errors, 0 skipped.
- Search worker E2E: 4 tests, 0 failures, 0 errors, 0 skipped, real Redis 7 plus HTTP Meili stub.
- Affected owner reactor test: BUILD SUCCESS.
- Full services ./mvnw verify -B: BUILD SUCCESS.
- Fresh direct integration selector: 68 reports, 225 tests, 0 failures, 0 errors, 17 skips.
- First wrapper run exposed 2 strict-stubbing errors in HiddenCaseLeakIT; single-read semantics were restored and the regression passed on rerun.
- Host RPC timed out while waiting for long integration Maven commands; fresh Surefire XML was independently aggregated with exit 0.

## Authority

Development/TEST-TARGET only. No commit, push, publish, deploy, production action, or production acceptance claim.
