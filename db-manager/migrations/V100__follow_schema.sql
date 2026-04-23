SET FOREIGN_KEY_CHECKS = 0;

-- Follow relationships between users
CREATE TABLE user_follows (
    follower_id VARCHAR(50) NOT NULL COMMENT 'User who follows',
    following_id VARCHAR(50) NOT NULL COMMENT 'User being followed',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (follower_id, following_id),
    INDEX idx_user_follows_follower (follower_id),
    INDEX idx_user_follows_following (following_id),
    INDEX idx_user_follows_created (created_at),
    CONSTRAINT fk_user_follows_follower FOREIGN KEY (follower_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_follows_following FOREIGN KEY (following_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
