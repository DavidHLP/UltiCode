-- V110: Add announcement_id column for notification deduplication and cascade
-- This replaces fragile title+type+createdAt matching logic
-- Rollback: ALTER TABLE notifications DROP COLUMN announcement_id; DROP INDEX idx_notifications_announcement_id;

-- Safety: drop temp table if it exists from a previous failed run
DROP TEMPORARY TABLE IF EXISTS tmp_announcement_groups;

ALTER TABLE notifications ADD COLUMN announcement_id VARCHAR(64) DEFAULT NULL AFTER metadata;

-- Index for grouping/cascade lookups
CREATE INDEX idx_notifications_announcement_id ON notifications (announcement_id);

-- Backfill: assign same announcement_id to all SYSTEM notifications sharing the same title+type+createdAt
-- Step 1: Create a temp table mapping each dedup group to one UUID
CREATE TEMPORARY TABLE tmp_announcement_groups (
    dedup_key VARCHAR(512) NOT NULL,
    group_announcement_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (dedup_key)
);

INSERT INTO tmp_announcement_groups (dedup_key, group_announcement_id)
SELECT CONCAT(title, '|', type, '|', DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s')), UUID()
FROM notifications
WHERE category = 'SYSTEM'
GROUP BY CONCAT(title, '|', type, '|', DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s'));

-- Step 2: Apply the grouped announcement_id back to notifications
UPDATE notifications n
INNER JOIN tmp_announcement_groups g
    ON CONCAT(n.title, '|', n.type, '|', DATE_FORMAT(n.created_at, '%Y-%m-%d %H:%i:%s')) = g.dedup_key
SET n.announcement_id = g.group_announcement_id;

DROP TEMPORARY TABLE IF EXISTS tmp_announcement_groups;
