-- Insert default admin user with known password "admin123"
-- Password hash: BCrypt encode of "admin123"
-- The password meets the requirements: at least 8 chars, 1 uppercase, 1 lowercase, 1 digit

INSERT INTO `users` (
    `id`, `username`, `name`, `email`, `avatar`, `password`,
    `bio`, `company`, `github`, `joined_at`, `location`, `twitter`, `website`,
    `preferred_language`, `role`, `is_active`, `is_banned`, `banned_until`,
    `banned_reason`, `last_login_at`, `created_by`, `updated_by`, `is_deleted`,
    `deleted_at`, `deleted_by`, `password_reset_token_hash`, `password_reset_expires_at`
) VALUES (
    'u-admin-001', 'admin', _utf8mb4'系统管理员', 'admin@ulticode.com',
    'https://api.dicebear.com/7.x/shapes/svg?seed=admin',
    '$2a$10$Klat0ofVcHijwMMFq.XjlekSzJFW6XzN3haePpxTHUM7CKGuVK64e',
    NULL, NULL, NULL, NOW(), NULL, NULL, NULL,
    'en-US', 'SUPER_ADMIN', 1, 0, NULL,
    NULL, NULL, NULL, NULL, 0,
    NULL, NULL, NULL, NULL
) ON DUPLICATE KEY UPDATE
    `password` = VALUES(`password`),
    `role` = VALUES(`role`),
    `is_active` = VALUES(`is_active`);
