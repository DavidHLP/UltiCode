SET FOREIGN_KEY_CHECKS = 0;

-- Achievement definition table
CREATE TABLE achievements (
    id VARCHAR(36) PRIMARY KEY,
    `key` VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    icon VARCHAR(500),
    category VARCHAR(50) NOT NULL COMMENT 'problem_solving, consistency, contest, social, special',
    tier INT NOT NULL DEFAULT 1 COMMENT '1=bronze, 2=silver, 3=gold, 4=platinum',
    criteria JSON COMMENT 'Achievement criteria in JSON format: {type, target}',
    points INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_achievements_category (category),
    INDEX idx_achievements_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- User achievement earned records
CREATE TABLE user_achievements (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    achievement_id VARCHAR(36) NOT NULL,
    earned_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_user_achievement (user_id, achievement_id),
    INDEX idx_user_achievements_user_id (user_id),
    INDEX idx_user_achievements_achievement_id (achievement_id),
    CONSTRAINT fk_user_achievements_achievement FOREIGN KEY (achievement_id) REFERENCES achievements(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
