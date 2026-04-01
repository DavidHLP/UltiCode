-- Migration: Add problem_list_bookmarks and problem_list_categories tables
-- These tables are required by the Problem List feature for saving and categorizing problem lists
-- Created: 2026-04-01

-- ============================================
-- Problem List Bookmarks
-- Stores user bookmarks/saves of problem lists
-- ============================================
CREATE TABLE `problem_list_bookmarks` (
    `id` VARCHAR(36) PRIMARY KEY,
    `user_id` VARCHAR(36) NOT NULL,
    `list_id` VARCHAR(36) NOT NULL,
    `category_id` VARCHAR(36),
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_list_id` (`list_id`),
    INDEX `idx_category_id` (`category_id`),
    UNIQUE KEY `uk_user_list` (`user_id`, `list_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Problem List Categories
-- User-defined categories for organizing saved problem lists
-- ============================================
CREATE TABLE `problem_list_categories` (
    `id` VARCHAR(36) PRIMARY KEY,
    `user_id` VARCHAR(36) NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `description` TEXT,
    `icon` VARCHAR(50),
    `color` VARCHAR(20),
    `sort_order` INT DEFAULT 0,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
