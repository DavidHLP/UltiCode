-- Add audit columns to contest_problems table
-- The ContestProblem entity has createdAt/updatedAt with MyBatis-Plus auto-fill
-- but the database table was missing these columns, causing SQL errors

SET FOREIGN_KEY_CHECKS = 0;

ALTER TABLE `contest_problems`
    ADD COLUMN `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ADD COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

SET FOREIGN_KEY_CHECKS = 1;
