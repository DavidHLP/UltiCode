-- Add missing timestamp and version columns to problems table
ALTER TABLE `problems`
  ADD COLUMN `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  ADD COLUMN `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  ADD COLUMN `version` INT NOT NULL DEFAULT 1;

-- Add index for created_at
CREATE INDEX `problems_created_at_idx` ON `problems`(`created_at`);

-- Add index for version
CREATE INDEX `problems_version_idx` ON `problems`(`version`);
