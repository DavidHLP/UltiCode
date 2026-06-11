-- V20260611130000__Add_Notifications_Is_Deleted.sql
-- Add logical-delete column to notifications table (Q12 fix).
-- Existing rows default to is_deleted=0 (not deleted). Destructive operations
-- (clearAll, deleteNotification) become UPDATE is_deleted=1 once the
-- @TableLogic annotation is added to Notification.deleted.

ALTER TABLE notifications
    ADD COLUMN is_deleted TINYINT UNSIGNED NOT NULL DEFAULT 0;

ALTER TABLE notifications
    ADD INDEX idx_notifications_user_deleted (user_id, is_deleted, created_at);
