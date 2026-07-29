-- V20260729181000__Add_Reclaim_Attempts_To_Ledger.sql
-- P6-INBOX-001: Add reclaim_attempts column to notification_delivery_ledger
-- to bound FAILED → CLAIMED reclaims and prevent infinite retry loops

ALTER TABLE `notification_delivery_ledger`
  ADD COLUMN `reclaim_attempts` int NOT NULL DEFAULT 0
  AFTER `failure_reason`;
