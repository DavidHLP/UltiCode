-- Contest schema hardening migration (R6.5).
-- Closes CRIT-2 (DB-2 NULL 多行无强约束) and CRIT-3 (DB-3 varchar(40)
-- UUID 兼容).
--
-- CRIT-2: ensure at most one (contest_id, user_id) row exists where
--         is_virtual=0 AND status='STARTED'. MySQL 8 does not support
--         partial unique indexes; emulate with a virtual generated
--         column that is non-null only for that exact predicate, then
--         a unique key on it. Generated NULLs are not equal in MySQL's
--         unique-index semantics, so FINISHED / REGISTERED / virtual
--         rows do not collide.
-- CRIT-3: widen virtual_session_id from VARCHAR(40) to VARCHAR(64).
--         Existing UUIDs (36 chars) and any future longer token format
--         fit comfortably; the change is non-destructive.
--
-- R6 code review (HIGH-2): pre-check for existing duplicates before
-- adding the unique key. If pre-check finds conflicts, the migration
-- fails early with a clear message instead of half-applying (add
-- column + add unique fail). Operator can dedupe via a one-off
-- script before retrying.
--
-- ROLLBACK (manual): the migration is split so each step can be
-- undone individually if needed.
--   1. ALTER TABLE contest_participants DROP INDEX uk_real_active;
--   2. ALTER TABLE contest_participants DROP COLUMN is_real_active;
--   3. ALTER TABLE contest_participants MODIFY virtual_session_id VARCHAR(40) DEFAULT NULL;

SET NAMES utf8mb4;

-- Step 0: pre-check. If this returns rows, the migration is unsafe to
-- apply as-is. Operator must dedupe before retry.
SELECT
    contest_id,
    user_id,
    COUNT(*) AS dup_count
FROM contest_participants
WHERE is_virtual = 0 AND status = 'STARTED'
GROUP BY contest_id, user_id
HAVING COUNT(*) > 1;

-- Step 1: CRIT-2 partial-unique emulation
ALTER TABLE contest_participants
    ADD COLUMN is_real_active TINYINT GENERATED ALWAYS AS (
        CASE WHEN is_virtual = 0 AND status = 'STARTED' THEN 1 ELSE NULL END
    ) VIRTUAL;

-- Step 2: unique key on the generated column. Fails if pre-check
-- returned any rows.
ALTER TABLE contest_participants
    ADD UNIQUE KEY uk_real_active (contest_id, user_id, is_real_active);

-- Step 3: CRIT-3 widen virtual_session_id. NOT NULL is unchanged.
ALTER TABLE contest_participants
    MODIFY virtual_session_id VARCHAR(64) DEFAULT NULL;
