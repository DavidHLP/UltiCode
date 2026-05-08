-- Add content column to problem_details for full markdown description
ALTER TABLE `problem_details`
  ADD COLUMN `content` text COLLATE utf8mb4_unicode_ci
  AFTER `summary`;
