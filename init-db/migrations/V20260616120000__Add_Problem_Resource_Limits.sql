-- ADR-002 §8 (P2-1): per-problem resource limits.
--
-- time_limit (seconds) and memory_limit (MiB). Both NULLABLE: NULL means
-- "use the global sandbox default", so existing problems keep their current
-- behaviour without a backfill. CodeExecutionService.resolveTimeoutSeconds /
-- resolveMemoryMb fall back to the global sandbox default when either is NULL.
--
-- Reviewed for backwards compatibility: additive ALTER only, no column
-- renamed/dropped, no NOT NULL constraint. Safe to apply on a live table.

ALTER TABLE problems
    ADD COLUMN time_limit INT NULL COMMENT 'per-problem time limit in seconds; NULL = global sandbox default',
    ADD COLUMN memory_limit INT NULL COMMENT 'per-problem memory limit in MiB; NULL = global sandbox default';
