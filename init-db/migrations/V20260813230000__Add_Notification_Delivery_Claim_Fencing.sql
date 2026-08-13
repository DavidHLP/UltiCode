-- NOTIFY-003: fence notification delivery leases across dispatcher instances.
ALTER TABLE `notification_delivery_ledger`
 ADD COLUMN `claimed_at` datetime(3) DEFAULT NULL
 COMMENT 'Current delivery lease timestamp'
 AFTER `delivered_at`,
 ADD COLUMN `claim_owner` varchar(80) DEFAULT NULL
 COMMENT 'Dispatcher lease owner'
 AFTER `claimed_at`;

-- Preserve the lease age of rows created before claim fencing was deployed.
UPDATE `notification_delivery_ledger`
SET `claimed_at` = `delivered_at`
WHERE `delivery_state` = 'CLAIMED'
  AND `claimed_at` IS NULL;

CREATE INDEX `idx_notification_delivery_ledger_claim`
 ON `notification_delivery_ledger` (`delivery_state`, `claimed_at`, `claim_owner`);
