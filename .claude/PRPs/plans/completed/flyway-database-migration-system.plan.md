# Plan: Flyway Database Migration System

## Summary

将现有的 ulticode 数据库（包括 28 张表、45 条 INSERT 语句，共 2477 行）完整迁移到新的 `init-db/` 目录，使用 Flyway 进行完整的数据库版本管理，采用时间戳（`V{YYYYMMDDHHMMSS}`）作为版本号。

## User Story

As a DevOps engineer,
I want to manage all database changes through versioned migration scripts,
So that I can track, reproduce, and deploy database changes consistently across all environments.

## Problem → Solution

**当前状态**：
- 数据库通过 mysqldump 一次性导出（`/home/david/ulticode_db_backup_20260530_130501.sql`）
- 无版本化管理，无法追踪每次变更
- 无 Flyway 集成，无法与 Spring Boot 自动集成

**期望状态**：
- `init-db/` 目录包含完整的 Flyway 迁移脚本
- 所有历史表结构通过 `V20260530130501__Baseline.sql` 管理
- 新增表结构变更通过时间戳版本脚本管理（如 `V20260601120000__AddNewTable.sql`）
- 与 Spring Boot `application.yml` 中现有的 datasource 配置无缝集成

---

## Metadata

- **Complexity**: Medium
- **Source PRD**: N/A (standalone feature)
- **Estimated Files**: 6 files (migration baseline + config + docs)
- **Estimated Tables**: 28 tables from existing database

---

## Directory Structure

```
init-db/
├── README.md                          # 使用说明
├── pom.xml                            # Maven 配置（可选，用于独立 Flyway CLI）
├── flyway.conf                        # Flyway 配置
├── migrations/                        # Flyway 迁移脚本目录
│   └── V20260530130501__Baseline.sql  # 初始基线版本
└── sql/                               # 历史 SQL 备份（可选）
    └── 20260530_ulticode_dump.sql     # 原始 dump 备份
```

---

## SQL Dump 分析

| 项目 | 数值 |
|------|------|
| 总行数 | 2,477 |
| 表数量 | 28 |
| INSERT 语句 | 45 |
| 表名列表 | DailyRecommendation, achievements, appeals, audit_logs, bookmarks, ... |

---

## Patterns to Mirror

### MIGRATION_SCRIPT_FORMAT
来源：`backend-spring/src/main/resources/db/migration/V20260322__Create_Email_Tables.sql`

```sql
-- Email module tables
-- Creates email_templates and email_logs tables

CREATE TABLE IF NOT EXISTS `email_templates` (
    `id` VARCHAR(36) NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    ...
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**要点**：
- 顶部注释说明迁移目的
- 使用 `CREATE TABLE IF NOT EXISTS` 确保幂等性
- 表结构完整（含 ENGINE、CHARSET、COLLATE）
- 包含所有索引、外键、约束

---

## Files to Change / Create

| File | Action | Justification |
|------|--------|---------------|
| `init-db/README.md` | CREATE | 完整的项目说明文档 |
| `init-db/flyway.conf` | CREATE | Flyway 独立运行配置 |
| `init-db/pom.xml` | CREATE | Maven 项目配置（可选） |
| `init-db/migrations/V20260530130501__Baseline.sql` | CREATE | 核心基线迁移脚本 |
| `init-db/sql/20260530_ulticode_dump.sql` | CREATE | 原始 dump 备份 |
| `init-db/validate-migration.sh` | CREATE | Git Hook 验证脚本 |

---

## NOT Building

- **不包含数据迁移**：基线脚本仅包含 28 张表的 DDL 结构，不包含 45 条 INSERT 数据（数据迁移可选）
- **不修改 Spring Boot 配置**：Flyway 将通过 Spring Boot 原生集成运行，无需修改 `application.yml`
- **不创建 UNDO 脚本**：Undo 脚本为可选功能，当前计划不涉及

---

## Step-by-Step Tasks

### Task 1: 创建 init-db 目录结构

- **ACTION**: 创建目录结构
- **IMPLEMENT**:
  ```bash
  mkdir -p init-db/migrations init-db/sql
  ```
- **MIRROR**: 无（新建目录）
- **GOTCHA**: 确保父目录 `/home/david/project/UltiCode-Public-Next/` 可写
- **VALIDATE**: `ls -la init-db/` 显示正确结构

---

### Task 2: 创建基线迁移脚本 V20260530130501__Baseline.sql

- **ACTION**: 将 2477 行的 SQL dump 转换为 Flyway 基线脚本
- **IMPLEMENT**:

处理步骤：
1. 移除 MySQL 特有注释（`/*!40101 */`, `/*!50503 */` 等）
2. 移除 `LOCK TABLES` 语句
3. 保留 `SET FOREIGN_KEY_CHECKS=0`（用于批量导入）
4. 保留所有 `DROP TABLE IF EXISTS`
5. 保留所有 `CREATE TABLE` 语句
6. 移除数据 INSERT 语句（保留空表结构）
7. 添加 Flyway 头部注释

脚本头部：
```sql
-- V20260530130501__Baseline.sql
-- UltiCode Database Baseline Migration
-- Generated from: /home/david/ulticode_db_backup_20260530_130501.sql
-- Tables: 28 (structure only, no data)
-- Date: 2026-05-30 13:05:01
-- Description: Initial baseline capturing all existing table structures

-- Disable foreign key checks for baseline migration
SET FOREIGN_KEY_CHECKS=0;
```

- **MIRROR**: 参考 `V20260322__Create_Email_Tables.sql` 的格式
- **GOTCHA**:
  - 某些表可能没有 `AUTO_INCREMENT`，检查 `id` 字段类型
  - 确保所有 `ENGINE=InnoDB` 和 `CHARSET` 设置正确
  - 某些表可能有 `JSON` 类型字段，需保留
- **VALIDATE**: `grep "CREATE TABLE" V20260530130501__Baseline.sql | wc -l` 返回 28

---

### Task 3: 创建 Flyway 配置文件

- **ACTION**: 创建 `init-db/flyway.conf`
- **IMPLEMENT**:
```properties
# Flyway Configuration for UltiCode Database

flyway.url=jdbc:mysql://localhost:23306/ulticode
flyway.user=ulticode
flyway.password=CHANGE_ME_strong_password
flyway.locations=filesystem:migrations
flyway.baselineOnMigrate=true
flyway.baselineVersion=20260530130501
flyway.baselineDescription=Initial baseline from existing database
flyway.sqlMigrationPrefix=V
flyway.sqlMigrationSeparator=__
flyway.sqlMigrationSuffixes=.sql
flyway.encoding=UTF-8
flyway.outOfOrder=false
flyway.validateOnMigrate=true
flyway.cleanDisabled=true
```

- **MIRROR**: 无（新建配置）
- **GOTCHA**:
  - `flyway.outOfOrder=false`：防止意外执行乱序迁移
  - `flyway.cleanDisabled=true`：生产环境禁止 clean 命令
- **VALIDATE**: `grep -E "^flyway\." flyway.conf | wc -l` 返回 10+

---

### Task 4: 创建 pom.xml（Maven 支持）

- **ACTION**: 创建 Maven 项目配置（可选，用于独立 Flyway CLI）
- **IMPLEMENT**:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.ulticode</groupId>
    <artifactId>init-db</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <name>UltiCode Database Init</name>
    <description>Flyway database migration scripts for UltiCode</description>

    <properties>
        <flyway.version>10.10.0</flyway.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
            <version>${flyway.version}</version>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
            <version>${flyway.version}</version>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <version>8.3.0</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.flywaydb</groupId>
                <artifactId>flyway-maven-plugin</artifactId>
                <version>${flyway.version}</version>
                <configuration>
                    <url>jdbc:mysql://localhost:23306/ulticode</url>
                    <user>ulticode</user>
                    <password>CHANGE_ME_strong_password</password>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- **MIRROR**: 无
- **GOTCHA**: Flyway 版本需与 Spring Boot 3.x 兼容（Spring Boot 3.2.5 内置 Flyway 9.x）
- **VALIDATE**: `xmllint --noout pom.xml` 无错误

---

### Task 5: 创建 README.md

- **ACTION**: 创建完整的使用说明文档
- **IMPLEMENT**: 见下方文档内容
- **MIRROR**: 无
- **GOTCHA**: 包含完整的使用场景和命令
- **VALIDATE**: `cat README.md | head -50` 显示正确格式

---

### Task 6: 创建迁移验证脚本

- **ACTION**: 创建 Git Hook 验证脚本
- **IMPLEMENT**:
```bash
#!/bin/sh
# validate-migration.sh
# Git pre-commit hook for Flyway migration naming validation

MIGRATION_REGEX="^V[0-9]{14}__[a-zA-Z_]+\\.sql$"

echo "Validating Flyway migration naming convention..."

for FILE in $(git diff --cached --name-only --diff-filter=A | grep 'migrations/'); do
    BASENAME=$(basename "$FILE")
    if ! echo "$BASENAME" | grep -Eq "$MIGRATION_REGEX"; then
        echo "ERROR: Migration file '$FILE' does not follow naming convention"
        echo "Expected format: V{YYYYMMDDHHMMSS}__{Description}.sql"
        echo "Example: V20260601120000__AddNewFeature.sql"
        exit 1
    fi
    echo "  ✓ $BASENAME"
done

echo "All migrations validated successfully."
exit 0
```

- **MIRROR**: 无
- **GOTCHA**: 脚本需要执行权限 `chmod +x validate-migration.sh`
- **VALIDATE**: `./validate-migration.sh` 执行成功

---

## Testing Strategy

### 静态验证

| 验证项 | 命令 | 预期结果 |
|--------|------|----------|
| SQL 语法检查 | `mysql -u ulticode -p -e "SOURCE init-db/migrations/V20260530130501__Baseline.sql"` | 无错误 |
| 迁移文件数量 | `ls init-db/migrations/*.sql | wc -l` | 1 |
| 配置文件格式 | `grep -E "^flyway\." init-db/flyway.conf | wc -l` | 10+ |
| 表结构完整性 | `grep "CREATE TABLE" init-db/migrations/V20260530130501__Baseline.sql | wc -l` | 28 |

### 集成测试（可选）

| 测试 | 命令 | 预期结果 |
|------|------|----------|
| Flyway info | `cd init-db && mvn flyway:info` | 显示基线版本 20260530130501 |
| Flyway migrate dry-run | `cd init-db && mvn flyway:migrate -Dflyway.target=20260530130501` | 跳过所有迁移 |
| Spring Boot 集成 | 启动后端，检查 `flyway_schema_history` 表 | 基线记录存在 |

---

## Migration Script Naming Convention

### 格式
```
V{YYYYMMDDHHMMSS}__{Description}.sql
```

### 示例

| 文件名 | 说明 |
|--------|------|
| `V20260530130501__Baseline.sql` | 初始基线（现有数据库快照） |
| `V20260601120000__AddNewFeature.sql` | 新增功能表 |
| `V20260602140000__AddUserIndex.sql` | 新增索引 |
| `V20260603090000__FixBugInTable.sql` | 修复表结构 |

### 命名规则

- **时间戳**：使用创建迁移时的精确时间（秒级）
- **描述**：使用下划线分隔的英文描述（如 `AddUserIndex`）
- **分隔符**：双下划线 `__` 分隔版本号和描述
- **前缀**：必须以 `V` 开头

---

## Spring Boot 集成

Spring Boot 已经包含 Flyway 依赖（通过 `spring-boot-starter-jdbc`），无需额外配置。

当前 `application.yml` 中的 Flyway 相关配置：
```yaml
# 无需修改，Flyway 默认读取 classpath:db/migration/
# 如需自定义，可添加：
# spring.flyway.enabled=true
# spring.flyway.locations=classpath:db/migration
# spring.flyway.baselineOnMigrate=true
# spring.flyway.baselineVersion=20260530130501
```

---

## Existing Database Baseline Process

### 步骤 1：备份现有数据库
```bash
docker exec ulticode-mysql mysqldump -uulticode -p'CHANGE_ME_strong_password' \
    --single-transaction ulticode > init-db/sql/20260530_ulticode_dump.sql
```

### 步骤 2：对现有数据库执行 baseline
```bash
# 使用 Flyway CLI
flyway baseline \
    -url=jdbc:mysql://localhost:23306/ulticode \
    -user=ulticode \
    -password=CHANGE_ME_strong_password \
    -baselineVersion=20260530130501 \
    -baselineDescription="Initial baseline from existing database"
```

### 步骤 3：验证
```bash
flyway info -url=jdbc:mysql://localhost:23306/ulticode \
    -user=ulticode -password=CHANGE_ME_strong_password
```

预期输出：
```
+-----------+----------------+----------------+------+
| Version   | Description    | Installed on   | State|
+-----------+----------------+----------------+------+
| 20260530130501 | Initial baseline | 2026-05-30 ... | Baselines |
+-----------+----------------+----------------+------+
```

---

## Acceptance Criteria

- [ ] `init-db/` 目录结构完整（包含 README.md, flyway.conf, pom.xml）
- [ ] `V20260530130501__Baseline.sql` 包含所有 28 张表的 CREATE 语句
- [ ] `flyway.conf` 配置正确，可独立运行
- [ ] `pom.xml` 格式正确，可通过 Maven 运行 Flyway
- [ ] README.md 包含完整的使用说明
- [ ] Git Hook 验证脚本可正确执行
- [ ] 对现有数据库执行 baseline 后，`flyway_schema_history` 表包含基线记录

---

## Validation Commands

### 本地验证
```bash
# 1. 检查目录结构
ls -la init-db/

# 2. 检查迁移脚本数量
ls init-db/migrations/*.sql

# 3. 检查表数量
grep "CREATE TABLE" init-db/migrations/V20260530130501__Baseline.sql | wc -l

# 4. 检查 Flyway 配置
grep -E "^flyway\." init-db/flyway.conf

# 5. Maven 编译
cd init-db && mvn compile -q

# 6. 验证 pom.xml 格式
xmllint --noout init-db/pom.xml && echo "pom.xml is valid"
```

### Git Hook 安装（可选）
```bash
# 在项目根目录
cp init-db/validate-migration.sh .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| SQL dump 格式不兼容 | Low | High | 手动检查并清理 MySQL 特有语法 |
| 现有数据库已有 Flyway 表 | Medium | Medium | 执行 `flyway baseline` 前检查 `flyway_schema_history` 是否存在 |
| 时间戳冲突 | Very Low | Low | 使用秒级时间戳，几乎不可能冲突 |

---

## Notes

1. **数据迁移**：当前计划仅包含表结构迁移（DDL），不包含数据迁移（45 条 INSERT）。如需数据迁移，可在 `V20260530130501__Baseline.sql` 中添加 INSERT 语句，或创建单独的 `V20260530130501__Baseline-data.sql` 文件。

2. **UNDO 脚本**：Undo 脚本为可选功能，当前计划不涉及。如需创建，可在 `undo/` 子目录中管理。

3. **多环境管理**：当前配置为单数据库管理。如需管理多环境（dev/staging/prod），可在 `flyway.conf` 中配置多个 environment 或使用 Spring Profile。

4. **迁移脚本位置**：当前使用 `filesystem:migrations`（独立运行），Spring Boot 集成时使用 `classpath:db/migration`。

5. **基线版本号**：使用导出时间戳 `20260530130501` 作为基线版本，确保唯一性。