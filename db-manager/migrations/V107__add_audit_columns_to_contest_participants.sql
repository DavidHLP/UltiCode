-- V107__add_audit_columns_to_contest_participants.sql
-- Add missing created_at and updated_at columns to contest_participants table
-- Issue: ContestParticipant entity has createdAt/updatedAt but table doesn't have these columns

SET FOREIGN_KEY_CHECKS=0;

ALTER TABLE `contest_participants`
    ADD COLUMN `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER `attempt_count`,
    ADD COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER `created_at`;

SET FOREIGN_KEY_CHECKS=1;