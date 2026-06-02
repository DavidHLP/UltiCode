-- Insert admin user with super admin role
-- Password is the bcrypt hash of 'admin123'

INSERT INTO `users` (`id`, `username`, `email`, `password`, `nickname`, `avatar_url`, `bio`, `role`, `is_active`, `created_at`, `updated_at`)
VALUES (
    'admin-001',
    'admin',
    'admin@ulticode.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    '超级管理员',
    NULL,
    'UltiCode 平台超级管理员',
    'ADMIN',
    1,
    NOW(3),
    NOW(3)
) ON DUPLICATE KEY UPDATE `username` = `username`;

-- Insert super_admin role
INSERT INTO `roles` (`id`, `name`, `description`, `is_system`)
VALUES ('super_admin', '超级管理员', '拥有系统所有权限的超级管理员', 1)
ON DUPLICATE KEY UPDATE `name` = `name`;

-- Assign super_admin role to admin user
INSERT INTO `user_roles` (`user_id`, `role_id`, `created_at`)
VALUES ('admin-001', 'super_admin', NOW(3))
ON DUPLICATE KEY UPDATE `user_id` = `user_id`;
