SET FOREIGN_KEY_CHECKS=0;

-- UltiCode Migration: V103__add_problem_version_table
-- Create problem_versions table for tracking problem change history

CREATE TABLE `problem_versions` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `problem_id` BIGINT NOT NULL,
  `version_number` INT NOT NULL,
  `snapshot_json` JSON NOT NULL COMMENT '完整题目快照',
  `change_type` VARCHAR(20) NOT NULL COMMENT 'CREATE | UPDATE | ROLLBACK',
  `change_summary` VARCHAR(255) COMMENT '变更摘要',
  `created_by` VARCHAR(40) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_problem_version` (`problem_id`, `version_number`),
  KEY `idx_problem_id` (`problem_id`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `fk_problem_versions_problem` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS=1;
