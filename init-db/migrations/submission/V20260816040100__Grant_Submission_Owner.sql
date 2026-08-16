-- SPLIT-003: grant only Submission-owner DML to the locked shadow user.
-- Runtime credentials are provisioned and unlocked outside Flyway.
-- Mirrors migrations/notification/V20260815100100__Grant_Notification_Owner.sql.

CREATE USER IF NOT EXISTS 'submission_rw'@'%' ACCOUNT LOCK;
GRANT USAGE ON *.* TO 'submission_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `submission`.* TO 'submission_rw'@'%';

FLUSH PRIVILEGES;
