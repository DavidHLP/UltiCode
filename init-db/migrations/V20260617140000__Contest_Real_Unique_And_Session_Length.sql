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

SET NAMES utf8mb4;

-- CRIT-2: partial-unique emulation
ALTER TABLE contest_participants
    ADD COLUMN is_real_active TINYINT GENERATED ALWAYS AS (
        CASE WHEN is_virtual = 0 AND status = 'STARTED' THEN 1 ELSE NULL END
    ) VIRTUAL;
ALTER TABLE contest_participants
    ADD UNIQUE KEY uk_real_active (contest_id, user_id, is_real_active);

-- CRIT-3: widen virtual_session_id. NOT NULL is unchanged.
ALTER TABLE contest_participants
    MODIFY virtual_session_id VARCHAR(64) DEFAULT NULL;
