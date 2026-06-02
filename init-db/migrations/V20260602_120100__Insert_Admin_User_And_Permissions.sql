-- ============================================================
-- V2: Insert Admin User and Grant Permissions
-- Creates the super admin account and assigns all permissions
-- ============================================================

-- Insert super admin user (password: admin123, BCrypt encoded)
INSERT INTO `users` (
    `id`, `username`, `name`, `email`, `password`,
    `role`, `is_active`, `is_banned`, `is_deleted`, `joined_at`
) VALUES (
    UUID(), 'admin', 'SuperAdmin', 'admin@ulticode.com',
    '$2a$10$TdoAPJ1mnUV/Osnws1nVbecA1VOuTLMyZ/Yo18Rkgh4yHjXGj3lyq',
    'ADMIN', 1, 0, 0, NOW(3)
);

-- Grant all permissions to ADMIN role
-- role_permissions uses (role, action, resource) enum tuples
INSERT INTO `role_permissions` (`id`, `role`, `action`, `resource`)
SELECT UUID(), 'ADMIN', a.action, r.resource
FROM (
    SELECT 'CREATE' AS action UNION ALL
    SELECT 'READ' UNION ALL
    SELECT 'UPDATE' UNION ALL
    SELECT 'DELETE' UNION ALL
    SELECT 'MODERATE' UNION ALL
    SELECT 'PUBLISH' UNION ALL
    SELECT 'MANAGE_USERS' UNION ALL
    SELECT 'MANAGE_PERMISSIONS'
) a
CROSS JOIN (
    SELECT 'USER' AS resource UNION ALL
    SELECT 'PROBLEM' UNION ALL
    SELECT 'SUBMISSION' UNION ALL
    SELECT 'CONTEST' UNION ALL
    SELECT 'FORUM_POST' UNION ALL
    SELECT 'FORUM_COMMENT' UNION ALL
    SELECT 'SOLUTION' UNION ALL
    SELECT 'SOLUTION_COMMENT' UNION ALL
    SELECT 'PROBLEM_LIST' UNION ALL
    SELECT 'ROLE' UNION ALL
    SELECT 'PERMISSION' UNION ALL
    SELECT 'NOTIFICATION' UNION ALL
    SELECT 'ACHIEVEMENT' UNION ALL
    SELECT 'BILLING' UNION ALL
    SELECT 'SYSTEM' UNION ALL
    SELECT 'DASHBOARD' UNION ALL
    SELECT 'MODERATION' UNION ALL
    SELECT 'BACKUP' UNION ALL
    SELECT 'AUDIT_LOG' UNION ALL
    SELECT 'REPORT' UNION ALL
    SELECT 'SEARCH' UNION ALL
    SELECT 'TAG' UNION ALL
    SELECT 'BOOKMARK' UNION ALL
    SELECT 'FOLLOW' UNION ALL
    SELECT 'VOTE' UNION ALL
    SELECT 'EMAIL' UNION ALL
    SELECT 'QUEUE' UNION ALL
    SELECT 'RECOMMENDATION'
) r
WHERE NOT EXISTS (
    SELECT 1 FROM `role_permissions` rp
    WHERE rp.role = 'ADMIN' AND rp.action = a.action AND rp.resource = r.resource
);
