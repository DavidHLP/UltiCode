---
name: ulticode-db-migration
description: UltiCode Flyway database migration operations reference. Covers migration file naming, running migrations, database connection, and MySQL conventions. Trigger when creating or modifying database schemas.
---

# UltiCode 数据库迁移 (Flyway)

## 迁移文件位置

```
init-db/
├── migrations/           # Flyway SQL 迁移脚本 (V*.sql)
├── sql/                  # 原始 SQL dump 文件
└── flyway.conf           # Flyway CLI 配置
```

## 迁移文件命名

格式：`V{版本号}__{描述}.sql`

示例：
- `V20260322__Create_Email_Tables.sql`
- `V20260530130501__Baseline.sql`
- `V20260530140000__Insert_Admin_User.sql`
- `V20260602030000__Seed_Forum_Test_Data.sql`

版本号格式：`V{YYYYMMDD}{HHMMSS}` — 时间戳递增确保顺序。

## 运行迁移

```bash
# 独立运行（不依赖 backend-spring）
cd init-db && mvn flyway:migrate

# 通过 backend-spring（开发时自动运行）
cd backend-spring && ./mvnw spring-boot:run
```

## 配置来源

数据库连接配置从 `.env` 读取：

```
DB_HOST=localhost
DB_PORT=23306
DB_USER=ulticode
DB_PASSWORD=ulticode
DB_NAME=ulticode
```

## 直接操作数据库

```bash
# Docker 容器内操作（注意：必须加 --default-character-set=utf8mb4 避免中文乱码）
docker exec ulticode-mysql mysql --default-character-set=utf8mb4 -u ulticode -p'CHANGE_ME_strong_password' ulticode

# 客户端直连
mysql -h 127.0.0.1 -P 23306 -u ulticode -pulticode ulticode
```

## 迁移规则

1. 迁移脚本一旦执行，**不可修改**（Flyway 校验 checksum）
2. 修改已有表结构：新建迁移脚本（如 `V20260602040000__Alter_User_Add_Column.sql`）
3. 种子数据脚本：使用 `Seed_` 描述前缀
4. 所有 SQL 必须向后兼容（不能 DROP 已使用的列）
5. 新增列给默认值，避免破坏现有数据

## MySQL 建表约定

- 表名、字段名全小写 + 下划线
- 主键 `bigint unsigned` 自增
- 必备字段 `id`, `create_time`, `update_time`
- 布尔字段 `is_xxx` (unsigned tinyint)
- 小数用 `decimal`，禁止 `float/double`
- varchar 长度不超过 5000
