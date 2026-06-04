---
paths:
  - "init-db/migrations/**/*.sql"
  - "init-db/flyway.conf"
description: Flyway 数据库迁移规范
---

# Flyway 迁移规范

- 文件命名：`V<yyyyMMdd>_<HHmmss>__<description>.sql`（双下划线分隔版本与描述）
- 禁止修改已应用的历史迁移脚本；修复通过新增迁移实现
- 大批量 seed 数据拆分为独立迁移，避免单个文件超过 1000 行
- DDL 与 DML 分文件：建表放一个迁移，初始数据放另一个迁移
- 字符集：库与表统一 `utf8mb4`，排序规则 `utf8mb4_unicode_ci`
- 完整规范待后续补充
