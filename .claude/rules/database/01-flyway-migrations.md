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

## 迁移内容约束

在已有"禁止修改历史迁移"基础上追加：

- **所有 SQL 必须向后兼容** —— 禁止 DROP 仍在代码中引用的列或表
- **新增列必须给出 DEFAULT 值** —— 避免在已有数据上失败（NOT NULL 列尤其重要）
- **种子数据脚本使用 `Seed_` 前缀的描述**（如 `V20260602030000__Seed_Forum_Test_Data.sql`）
