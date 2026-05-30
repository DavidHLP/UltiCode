# init-db - UltiCode Flyway Database Migration

Database version management using Flyway with timestamp-based versioning.

## Directory Structure

```
init-db/
├── README.md                          # This file
├── pom.xml                             # Maven configuration
├── flyway.conf                         # Flyway standalone configuration
├── migrations/                         # Flyway migration scripts
│   └── V20260530130501__Baseline.sql  # Initial baseline (67 tables)
└── sql/                               # Historical backups
```

## Quick Start

### 1. 查看迁移状态
```bash
cd init-db && mvn flyway:info
```

### 2. 执行迁移
```bash
cd init-db && mvn flyway:migrate
```

### 3. 创建新迁移脚本
```bash
# Format: V{YYYYMMDDHHMMSS}__{Description}.sql
touch migrations/V20260601120000__AddNewFeature.sql
```

## Migration Naming Convention

| 格式 | 示例 |
|------|------|
| 时间戳版本 | `V20260530130501__Baseline.sql` |
| 新功能 | `V20260601120000__AddUserIndex.sql` |
| Bug 修复 | `V20260603090000__FixContestTable.sql` |

命名规则：
- **前缀**: `V` (Versioned)
- **时间戳**: `YYYYMMDDHHMMSS` (14位数字)
- **分隔符**: 双下划线 `__`
- **描述**: 英文描述，下划线分隔

## 数据库现有表结构（基线版本）

包含 67 张表的完整 DDL 结构，涵盖：
- 用户认证 (users, roles, permissions)
- 竞赛系统 (contests, contest_participants, contest_problems)
- 题目管理 (problems, problem_translations, test_cases)
- 论坛 (forum_posts, forum_replies, votes)
- 提交评测 (submissions, submission_results)
- 成就系统 (achievements, user_achievements)
- 通知系统 (notifications)
- 审计日志 (audit_logs)
- 更多...

## 对现有数据库执行 Baseline

如果数据库已存在且无 Flyway 历史记录：

```bash
# 使用 Flyway CLI
flyway baseline \
    -url=jdbc:mysql://localhost:23306/ulticode \
    -user=ulticode \
    -password=CHANGE_ME_strong_password \
    -baselineVersion=20260530130501 \
    -baselineDescription="Initial baseline from existing database"

# 或使用 Maven
cd init-db && mvn flyway:baseline \
    -Dflyway.baselineVersion=20260530130501 \
    -Dflyway.baselineDescription="Initial baseline"
```

## Spring Boot 集成

Spring Boot 已内置 Flyway 支持。在 `application.yml` 中无需额外配置（使用默认路径 `classpath:db/migration/`）。

如需自定义，在 `application.yml` 中添加：
```yaml
spring:
  flyway:
    enabled: true
    baselineOnMigrate: true
    baselineVersion: 20260530130501
    locations: classpath:db/migration
```

## Flyway 命令参考

| 命令 | 说明 |
|------|------|
| `flyway:info` | 查看当前迁移状态 |
| `flyway:migrate` | 执行所有待处理迁移 |
| `flyway:validate` | 验证当前状态 |
| `flyway:baseline` | 创建基线标记 |
| `flyway:repair` | 修复损坏的 schema history |
| `flyway:clean` | 清理所有迁移（**生产环境禁用**）|

## 添加新迁移

1. 创建迁移脚本：
```bash
touch migrations/V20260601120000__AddNewTable.sql
```

2. 编辑脚本内容：
```sql
-- V20260601120000__AddNewTable.sql
-- Description: Add new table for feature X

CREATE TABLE IF NOT EXISTS `new_table` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

3. 执行迁移：
```bash
cd init-db && mvn flyway:migrate
```

## Git Hook（可选）

安装迁移验证钩子：
```bash
cp validate-migration.sh ../.git/hooks/pre-commit
chmod +x ../.git/hooks/pre-commit
```

这会在提交前验证迁移文件命名是否符合规范。

## 备份原始 SQL Dump

原始数据库导出文件已保存至：
```
sql/20260530_ulticode_dump.sql
```

如需重新生成基线迁移脚本：
```bash
docker exec ulticode-mysql mysqldump -uulticode -p'CHANGE_ME_strong_password' \
    --single-transaction ulticode > sql/20260530_ulticode_dump.sql
```

## 故障排除

### Q: 迁移失败，提示表已存在
A: 数据库已有表结构，需要先执行 baseline：
```bash
mvn flyway:baseline
```

### Q: Flyway 报告 checksum 不匹配
A: 迁移文件被修改过，使用 repair 修复：
```bash
mvn flyway:repair
```

### Q: 无法连接到数据库
A: 检查 `flyway.conf` 中的 URL、用户名、密码是否正确。

## 注意事项

1. **不要修改已应用的迁移脚本** - 这会导致 checksum 不匹配
2. **生产环境禁用 clean 命令** - `flyway.cleanDisabled=true`
3. **使用 outOfOrder=false** - 防止意外执行乱序迁移
4. **始终先备份数据库** - 再执行任何迁移操作