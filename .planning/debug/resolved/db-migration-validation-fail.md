---
status: resolved
trigger: "检测数据库迁移部分内容的设计以及问题 ， 确保使用正常的命令可以正常的去进行数据库迁移"
created: 2026-04-19
updated: 2026-04-21

## Symptoms

1. **Expected behavior**: `db-manager/.venv/bin/python -m db_manager.cli migrate` 应该正常执行所有pending迁移(V23/V24/V25)
2. **Actual behavior**: Flyway报错 "Validate failed: Migrations have failed validation - Detected resolved migration not applied to database: 10.1, 21"
3. **Error messages**: ERROR: Validate failed...outOfOrder=true needed
4. **Timeline**: 今天尝试执行时出现，可以稳定复现
5. **Reproduction**: 直接运行 `cd db-manager && .venv/bin/python -m db_manager.cli migrate` 就能复现
6. **History**: 之前可以用，现在失败了

## Root Cause

V26__follow_schema.sql 的版本号（26）低于已安装的 V99，导致 Flyway 拒绝执行。同时 user_follows 表已存在于数据库中，说明 V26 之前被手动执行过但 Flyway 没有记录。

## Fix

1. 将 V26 重命名为 V100（follow 功能逻辑上在 edge schema 之后）
2. 手动删除 Flyway 历史中失败的 rank 27 记录
3. 插入正确的 V100 记录标记为已应用
4. 运行 repair 同步 checksum

## Verification

✅ `db-manager/.venv/bin/python -m db_manager.cli migrate` 现在正常执行

