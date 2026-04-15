SET FOREIGN_KEY_CHECKS = 0;

-- Password reset columns (D-12: store BCrypt hash + expiry on users table)
ALTER TABLE users
    ADD COLUMN password_reset_token_hash VARCHAR(255) DEFAULT NULL,
    ADD COLUMN password_reset_expires_at DATETIME(3) DEFAULT NULL;

CREATE INDEX idx_users_password_reset_token ON users(password_reset_token_hash);

SET FOREIGN_KEY_CHECKS = 1;
