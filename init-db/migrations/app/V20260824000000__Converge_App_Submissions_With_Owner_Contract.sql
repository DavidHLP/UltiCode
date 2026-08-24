-- Converge the App-local `submissions` read/write seam with the canonical
-- Submission owner contract (init-db/migrations/submission/
-- V20260816040000__Create_Submission_Owner_Tables.sql).
--
-- The bootstrap shape created by V20260729140300 (verdict / execution_time_ms /
-- memory_used_kb) predates the submission-owner split and does not match the
-- runtime `Submission` entity used by SubmissionMapper (MyBatis-Plus CRUD over
-- canonical columns). With APP_SUBMISSION_ROUTING_MODE=local this drift makes
-- local reads/writes silently mis-map, which is the reported data anomaly.
--
-- Idempotent: every clause is guarded by INFORMATION_SCHEMA probes, so the
-- migration is a no-op once converged and safe on empty tables. No data is
-- modified; only column shape is aligned.

-- Each guarded clause carries a leading comma; the first applied clause is
-- stripped below so exactly one valid ALTER statement is prepared.
SET @ddl := CONCAT(
    IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'verdict') > 0,
       ', DROP COLUMN `verdict`', ''),
    IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'execution_time_ms') > 0,
       ', DROP COLUMN `execution_time_ms`', ''),
    IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'memory_used_kb') > 0,
       ', DROP COLUMN `memory_used_kb`', ''),
    IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'runtime') = 0,
       ', ADD COLUMN `runtime` INT NOT NULL DEFAULT 0', ''),
    IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'memory') = 0,
       ', ADD COLUMN `memory` DOUBLE DEFAULT NULL', ''),
    IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'notes') = 0,
       ', ADD COLUMN `notes` TEXT DEFAULT NULL', ''),
    IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'runtime_percentile') = 0,
       ', ADD COLUMN `runtime_percentile` DOUBLE DEFAULT NULL', ''),
    IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'memory_percentile') = 0,
       ', ADD COLUMN `memory_percentile` DOUBLE DEFAULT NULL', ''),
    IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'test_details') = 0,
       ', ADD COLUMN `test_details` JSON DEFAULT NULL', ''),
    IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'memoryDistBinsMb') = 0,
       ', ADD COLUMN `memoryDistBinsMb` JSON DEFAULT NULL', ''),
    IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'runtimeDistBinsMs') = 0,
       ', ADD COLUMN `runtimeDistBinsMs` JSON DEFAULT NULL', ''),
    IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'submissions' AND COLUMN_NAME = 'retry_count') = 0,
       ', ADD COLUMN `retry_count` INT NOT NULL DEFAULT 0', '')
);
SET @ddl := IF(@ddl = '', 'SELECT 1', CONCAT('ALTER TABLE `submissions`', SUBSTRING(@ddl, 2)));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
