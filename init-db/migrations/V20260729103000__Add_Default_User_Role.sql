-- P2-DISC-005: Add DEFAULT 'USER' to users.role column
--
-- The users.role column has ENUM('USER','MODERATOR','ADMIN','SUPER_ADMIN') NOT NULL DEFAULT 'USER'
-- in V20260602_120000__Create_All_Tables.sql, but existing MySQL/MariaDB schema instances or altered
-- environments may lack the DEFAULT 'USER' constraint.
--
-- This migration ensures the DEFAULT 'USER' is explicitly present on users.role so that
-- legacy UserManagementServiceImpl.createUser (and any future account creation seam) can safely
-- omit the role placeholder write and rely on the database column DEFAULT.

ALTER TABLE `users`
  MODIFY COLUMN `role` ENUM('USER','MODERATOR','ADMIN','SUPER_ADMIN') NOT NULL DEFAULT 'USER';
