# init-db - UltiCode Database Migration

数据库版本管理工具，独立于 Spring Boot 运行。

## 目录结构

```
init-db/
├── README.md              # 本文件
├── pom.xml               # Maven 配置 (Flyway + MySQL)
├── flyway.conf           # Flyway CLI 独立运行配置
├── validate-migration.sh # 迁移脚本验证钩子
├── migrations/            # Flyway SQL 迁移脚本
│   ├── V20260322__Create_Email_Tables.sql
│   ├── V20260530130501__Baseline.sql
│   └── V20260530140000__Insert_Admin_User.sql
└── sql/                  # 历史 SQL 备份
    └── 20260530_ulticode_dump.sql
```

## 快速开始

### 方式一：Maven (推荐)

```bash
cd init-db

# 查看迁移状态
./mvnw flyway:info

# 执行迁移
./mvnw flyway:migrate

# 验证状态
./mvnw flyway:validate

# 修复元数据
./mvnw flyway:repair

# 创建基线（数据库已有表时）
./mvnw flyway:baseline -Dflyway.baselineVersion=20260530130501 -Dflyway.baselineDescription="Initial baseline"
```

### 方式二：Flyway CLI

```bash
# 确保已安装 Flyway CLI 或使用 Docker
docker run --rm -v $(pwd):/workspace -w /workspace flyway/flyway:10.10.0 \
  -url=jdbc:mysql://localhost:23306/ulticode \
  -user=ulticode \
  -password=CHANGE_ME_strong_password \
  migrate
```

### 方式三：直接执行 SQL

```bash
mysql -u ulticode -p'CHANGE_ME_strong_password' ulticode < migrations/V20260530130501__Baseline.sql
```

## 创建新迁移

```bash
# 格式: V{YYYYMMDDHHMMSS}__{Description}.sql
touch migrations/V20260601120000__AddNewFeature.sql
```

编辑迁移文件内容：
```sql
-- V20260601120000__AddNewFeature.sql
CREATE TABLE IF NOT EXISTS `new_table` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## 环境配置

数据库连接信息从环境变量或 `.env` 文件读取：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DB_HOST` | localhost | 数据库主机 |
| `DB_PORT` | 23306 | 数据库端口 |
| `DB_USER` | ulticode | 数据库用户 |
| `DB_PASSWORD` | CHANGE_ME_strong_password | 数据库密码 |
| `DB_NAME` | ulticode | 数据库名称 |

## 迁移历史

| 版本 | 描述 | 状态 |
|------|------|------|
| V20260322 | 创建邮件模块表 | 已应用 |
| V20260530130501 | 基线迁移 (67 张表) | 已应用 |
| V20260530140000 | 插入管理员用户 | 已应用 |

## Spring Boot 集成

Spring Boot 启动时自动从 `init-db/migrations/` 读取并执行迁移：

```yaml
# backend-spring/src/main/resources/application.yml
spring:
  flyway:
    enabled: true
    locations: filesystem:../init-db/migrations
    baseline-on-migrate: true
    baseline-version: 20260322
```