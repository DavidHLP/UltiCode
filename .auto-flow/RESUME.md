# Resume

ARCHFIX-003 complete in actual workspace after freshness and duplicate account contract rework.
Backfill uses `UserDirectoryRow.freshAt()` directly; findById handles null profile projection; findByIds filters requested IDs and deduplicates by accountId.
Evidence: app-web focused suite 24 tests, 0 failures, 0 errors, 0 skipped; source scan and git diff --check PASS.
