SET FOREIGN_KEY_CHECKS=0;
-- UltiCode Migration: V105__moderation_constraints
-- Adds unique constraint on reports and CHECK constraints on status columns

-- Prevent duplicate reports from same user for same entity
ALTER TABLE reports ADD CONSTRAINT uk_reports_reporter_entity
  UNIQUE (reporter_id, entity_type, entity_id);

-- Ensure moderation_queue status values are valid
ALTER TABLE moderation_queue ADD CONSTRAINT chk_queue_status
  CHECK (status IN ('PENDING', 'UNDER_REVIEW', 'RESOLVED', 'DISMISSED', 'APPEAL_PENDING'));

-- Ensure reports status values are valid
ALTER TABLE reports ADD CONSTRAINT chk_report_status
  CHECK (status IN ('PENDING', 'REVIEWED', 'RESOLVED', 'DISMISSED'));

-- Ensure appeals status values are valid
ALTER TABLE appeals ADD CONSTRAINT chk_appeal_status
  CHECK (status IN ('PENDING', 'UNDER_REVIEW', 'APPROVED', 'REJECTED'));

SET FOREIGN_KEY_CHECKS=1;
