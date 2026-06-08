-- Fix audit_logs.performer_id to reference real admin user
-- ------------------------------------------------------------
-- 背景: V20260603_120100__Seed_Audit_Logs_Test_Data.sql 写入的
--       performer_id='admin-001' 在 users 表中不存在, 导致
--       /admin/audit/logs 与 /admin/audit/stats 的 performer /
--       topPerformers 关联字段全 null.
-- 修复: 把 audit-log-001..008 的 performer_id 改为真实 admin UUID
--       (通过子查询 SELECT admin 账号的 id, 不硬编码 UUID)
-- 路由:  GET /admin/audit/logs
--        GET /admin/audit/stats
-- 风险:  低, 仅影响 seed 数据; 真实生产数据 performer_id 都来自
--        AuditAspect, 不存在此问题
-- 回滚:  UPDATE audit_logs SET performer_id = 'admin-001'
--        WHERE id LIKE 'audit-log-%';
-- ------------------------------------------------------------

UPDATE `audit_logs`
SET `performer_id` = (SELECT `id` FROM `users` WHERE `username` = 'admin' AND `role` = 'ADMIN' LIMIT 1)
WHERE `id` LIKE 'audit-log-%' AND `performer_id` = 'admin-001';
