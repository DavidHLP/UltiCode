-- NOTIFY-006: grant only Notification-owner DML to the locked shadow user.
-- Runtime credentials are provisioned and unlocked outside Flyway.

CREATE USER IF NOT EXISTS 'notification_rw'@'%' ACCOUNT LOCK;
GRANT USAGE ON *.* TO 'notification_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `notification`.* TO 'notification_rw'@'%';

FLUSH PRIVILEGES;
