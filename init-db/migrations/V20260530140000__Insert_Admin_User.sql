-- Insert admin user with super admin role
-- Password is the bcrypt hash of 'admin123'

INSERT INTO `users` (`id`, `username`, `email`, `password`, `name`, `avatar`, `bio`, `role`, `is_active`, `joined_at`)
VALUES (
    'admin-001',
    'admin',
    'admin@ulticode.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    '超级管理员',
    NULL,
    'UltiCode 平台超级管理员',
    'SUPER_ADMIN',
    1,
    NOW(3)
) ON DUPLICATE KEY UPDATE `username` = `username`;

-- Note: roles and user_roles tables do not exist in baseline schema.
-- The users table uses an enum 'role' field (USER/MODERATOR/ADMIN/SUPER_ADMIN) instead.
