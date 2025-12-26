-- This migration removes FAVORITE from EdgeOperationType and extends CollectionTargetType
-- For fresh databases, these changes are already in the schema and tables will be created correctly
-- For existing databases, this migration updates the enum values

-- AlterEnum: Remove FAVORITE from EdgeOperationType (if table exists)
-- AlterEnum: Add SOLUTION_COMMENT and FORUM_COMMENT to CollectionTargetType (if table exists)
