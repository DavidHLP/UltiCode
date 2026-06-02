-- Migration:
--   V20260531001000__Insert_Test_Audit_Logs.sql
--
-- Purpose:
--   Insert test audit log data for PROBLEM entity to verify audit trail functionality.
--   Used for development and testing of audit log UI and API.
--
-- Risk:
--   Low. Test data only. Uses INSERT ON DUPLICATE KEY UPDATE for idempotency.
--
-- Compatibility:
--   Compatible. Test data does not affect production functionality.
--
-- Rollback:
--   DELETE FROM audit_logs WHERE id LIKE 'audit-log-%';
--
-- Verify:
--   SELECT COUNT(*) FROM audit_logs WHERE id LIKE 'audit-log-%';
--   Should return 8
--   SELECT id, action, entity_type, entity_id FROM audit_logs WHERE id LIKE 'audit-log-%';

-- Insert test audit logs for PROBLEM entity
-- Routes: /admin/audit/log?entityType=PROBLEM&entityId=1
-- Note: Assuming admin user exists with id 'admin-001' (from Insert_Admin_User migration)

-- Audit log 1: Problem creation
INSERT INTO `audit_logs` (`id`, `performer_id`, `user_id`, `action`, `entity_type`, `entity_id`, `old_values`, `new_values`, `ip_address`, `user_agent`, `created_at`) VALUES
('audit-log-001', 'admin-001', NULL, 'CREATE', 'PROBLEM', '1', NULL, '{"title":"两数之和","difficulty":"Easy","status":"draft","isPublished":false}', '192.168.1.100', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', '2026-05-25 10:00:00.000')
ON DUPLICATE KEY UPDATE `action` = VALUES(`action`);

-- Audit log 2: Problem published
INSERT INTO `audit_logs` (`id`, `performer_id`, `user_id`, `action`, `entity_type`, `entity_id`, `old_values`, `new_values`, `ip_address`, `user_agent`, `created_at`) VALUES
('audit-log-002', 'admin-001', NULL, 'UPDATE', 'PROBLEM', '1', '{"isPublished":false,"status":"draft"}', '{"isPublished":true,"status":"published"}', '192.168.1.100', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', '2026-05-25 10:05:00.000')
ON DUPLICATE KEY UPDATE `action` = VALUES(`action`);

-- Audit log 3: Problem details updated
INSERT INTO `audit_logs` (`id`, `performer_id`, `user_id`, `action`, `entity_type`, `entity_id`, `old_values`, `new_values`, `ip_address`, `user_agent`, `created_at`) VALUES
('audit-log-003', 'admin-001', NULL, 'UPDATE', 'PROBLEM', '1', '{"title":"两数之和"}', '{"title":"两数之和（已更新）"}', '192.168.1.100', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36', '2026-05-26 14:30:00.000')
ON DUPLICATE KEY UPDATE `action` = VALUES(`action`);

-- Audit log 4: Difficulty changed
INSERT INTO `audit_logs` (`id`, `performer_id`, `user_id`, `action`, `entity_type`, `entity_id`, `old_values`, `new_values`, `ip_address`, `user_agent`, `created_at`) VALUES
('audit-log-004', 'admin-001', NULL, 'UPDATE', 'PROBLEM', '1', '{"difficulty":"Easy"}', '{"difficulty":"Medium"}', '192.168.1.101', 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36', '2026-05-27 09:15:00.000')
ON DUPLICATE KEY UPDATE `action` = VALUES(`action`);

-- Audit log 5: Content moderated - approved
INSERT INTO `audit_logs` (`id`, `performer_id`, `user_id`, `action`, `entity_type`, `entity_id`, `old_values`, `new_values`, `ip_address`, `user_agent`, `created_at`) VALUES
('audit-log-005', 'admin-001', NULL, 'MODERATE_APPROVE', 'PROBLEM', '1', '{"moderationStatus":"pending","moderationMessage":null}', '{"moderationStatus":"approved","moderationMessage":"所有检查已通过"}', '192.168.1.100', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', '2026-05-28 11:00:00.000')
ON DUPLICATE KEY UPDATE `action` = VALUES(`action`);

-- Audit log 6: Tags updated
INSERT INTO `audit_logs` (`id`, `performer_id`, `user_id`, `action`, `entity_type`, `entity_id`, `old_values`, `new_values`, `ip_address`, `user_agent`, `created_at`) VALUES
('audit-log-006', 'admin-001', NULL, 'UPDATE', 'PROBLEM', '1', '{"tags":["array"],"hasSolution":false}', '{"tags":["array","hash-table"],"hasSolution":true}', '192.168.1.100', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36', '2026-05-28 15:45:00.000')
ON DUPLICATE KEY UPDATE `action` = VALUES(`action`);

-- Audit log 7: Constraints updated
INSERT INTO `audit_logs` (`id`, `performer_id`, `user_id`, `action`, `entity_type`, `entity_id`, `old_values`, `new_values`, `ip_address`, `user_agent`, `created_at`) VALUES
('audit-log-007', 'admin-001', NULL, 'UPDATE', 'PROBLEM', '1', '{"constraints":"2 <= nums.length <= 10^4","difficultyRating":1200}', '{"constraints":"2 <= nums.length <= 10^5","difficultyRating":1350}', '192.168.1.102', 'Mozilla/5.0 (iPad; CPU OS 14_0 like Mac OS X) AppleWebKit/605.1.15', '2026-05-29 08:30:00.000')
ON DUPLICATE KEY UPDATE `action` = VALUES(`action`);

-- Audit log 8: Status changed to solved
INSERT INTO `audit_logs` (`id`, `performer_id`, `user_id`, `action`, `entity_type`, `entity_id`, `old_values`, `new_values`, `ip_address`, `user_agent`, `created_at`) VALUES
('audit-log-008', 'admin-001', NULL, 'UPDATE', 'PROBLEM', '1', '{"status":"published","acceptanceRate":49.2}', '{"status":"solved","acceptanceRate":52.8}', '192.168.1.100', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36', '2026-05-30 10:00:00.000')
ON DUPLICATE KEY UPDATE `action` = VALUES(`action`);
