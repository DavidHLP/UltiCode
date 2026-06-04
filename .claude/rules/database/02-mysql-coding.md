---
paths:
  - "init-db/migrations/**/*.sql"
  - "**/mapper/**/*.xml"
  - "**/*Mapper.java"
description: MySQL DDL/SQL 项目特定规范
---

# MySQL 项目规范

- 字符集统一 `utf8mb4` + 排序规则 `utf8mb4_unicode_ci`
- 表必备字段：`id`（`bigint unsigned` 自增）、`create_time`、`update_time`、`is_deleted`（逻辑删除）
- 字段名小写蛇形，禁用保留字（desc、range、match 等）
- 索引命名：`pk_<col>` / `uk_<col>` / `idx_<col>` / `idx_<a>_<b>`
- 查询禁止 `SELECT *`，必须显式列出字段
- 详细规范见 `backend/05-mysql-database.md`
