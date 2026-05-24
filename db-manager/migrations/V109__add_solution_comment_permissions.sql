-- Add SOLUTION_COMMENT resource permissions mirroring FORUM_COMMENT
-- This aligns the permission system with the comments management page
-- which distinguishes between forum and solution comments.

-- Step 1: Add SOLUTION_COMMENT to the resource ENUM (if not already added)
ALTER TABLE role_permissions MODIFY COLUMN resource ENUM(
  'USER', 'PROBLEM', 'SUBMISSION', 'CONTEST', 'FORUM_POST',
  'FORUM_COMMENT', 'SOLUTION', 'SOLUTION_COMMENT',
  'PROBLEM_LIST', 'ROLE', 'PERMISSION', 'NOTIFICATION',
  'ACHIEVEMENT', 'BILLING', 'SYSTEM', 'DASHBOARD', 'MODERATION',
  'BACKUP', 'AUDIT_LOG', 'REPORT', 'SEARCH', 'TAG',
  'BOOKMARK', 'FOLLOW', 'VOTE', 'EMAIL', 'QUEUE', 'RECOMMENDATION'
) NOT NULL;

-- Step 2: Add SOLUTION_COMMENT permissions mirroring FORUM_COMMENT (with explicit IDs)
INSERT INTO role_permissions (id, role, action, resource) VALUES
  ('rp_sol_sc_sa_c', 'SUPER_ADMIN', 'CREATE', 'SOLUTION_COMMENT'),
  ('rp_sol_sc_sa_r', 'SUPER_ADMIN', 'READ', 'SOLUTION_COMMENT'),
  ('rp_sol_sc_sa_u', 'SUPER_ADMIN', 'UPDATE', 'SOLUTION_COMMENT'),
  ('rp_sol_sc_sa_d', 'SUPER_ADMIN', 'DELETE', 'SOLUTION_COMMENT'),
  ('rp_sol_sc_sa_m', 'SUPER_ADMIN', 'MODERATE', 'SOLUTION_COMMENT'),
  ('rp_sol_sc_sa_p', 'SUPER_ADMIN', 'PUBLISH', 'SOLUTION_COMMENT'),
  ('rp_sol_sc_sa_mu', 'SUPER_ADMIN', 'MANAGE_USERS', 'SOLUTION_COMMENT'),
  ('rp_sol_sc_sa_mp', 'SUPER_ADMIN', 'MANAGE_PERMISSIONS', 'SOLUTION_COMMENT'),
  ('rp_sol_sc_ad_c', 'ADMIN', 'CREATE', 'SOLUTION_COMMENT'),
  ('rp_sol_sc_ad_r', 'ADMIN', 'READ', 'SOLUTION_COMMENT'),
  ('rp_sol_sc_ad_u', 'ADMIN', 'UPDATE', 'SOLUTION_COMMENT'),
  ('rp_sol_sc_ad_d', 'ADMIN', 'DELETE', 'SOLUTION_COMMENT'),
  ('rp_sol_sc_ad_m', 'ADMIN', 'MODERATE', 'SOLUTION_COMMENT'),
  ('rp_sol_sc_ad_p', 'ADMIN', 'PUBLISH', 'SOLUTION_COMMENT'),
  ('rp_sol_sc_ad_mu', 'ADMIN', 'MANAGE_USERS', 'SOLUTION_COMMENT'),
  ('rp_sol_sc_mo_r', 'MODERATOR', 'READ', 'SOLUTION_COMMENT'),
  ('rp_sol_sc_mo_u', 'MODERATOR', 'UPDATE', 'SOLUTION_COMMENT'),
  ('rp_sol_sc_mo_d', 'MODERATOR', 'DELETE', 'SOLUTION_COMMENT'),
  ('rp_sol_sc_mo_m', 'MODERATOR', 'MODERATE', 'SOLUTION_COMMENT');