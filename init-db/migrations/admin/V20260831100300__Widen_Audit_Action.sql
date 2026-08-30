-- P1-AUDIT-001: align the pre-existing Admin audit tables with the owner
-- outbox action width before Admin consumes owner-local AuditRecorded events.
ALTER TABLE `audit_logs`
  MODIFY COLUMN `action` VARCHAR(64) NOT NULL;

ALTER TABLE `audit_outbox`
  MODIFY COLUMN `action` VARCHAR(64) NOT NULL;
