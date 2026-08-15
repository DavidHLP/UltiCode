-- NOTIFY-006: establish the physical Notification owner schema.
--
-- The account is created locked and without a password. Deployment provisions
-- the runtime credential out-of-band and explicitly unlocks the account after
-- the cutover preflight succeeds; this migration never creates usable secrets.

CREATE SCHEMA IF NOT EXISTS `notification`;

CREATE USER IF NOT EXISTS 'notification_rw'@'%' ACCOUNT LOCK;
GRANT USAGE ON *.* TO 'notification_rw'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `notification`.* TO 'notification_rw'@'%';

FLUSH PRIVILEGES;
