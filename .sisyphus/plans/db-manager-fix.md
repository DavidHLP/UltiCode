# db-manager 修复与稳定化计划

## TL;DR

> **核心目标**：修复 db-manager 所有已知 bug，应用 16 个待处理迁移，建立测试基础设施，编写工作流文档。
>
> **交付物**：
> - 修复后的 Python CLI 源码（无 bug、无重复、安全）
> - 完整应用的 41 个迁移，数据库状态健康
> - pytest 测试套件（≥80% 覆盖率）
> - CLI 使用手册 + 数据库管理工作流文档
>
> **预估工作量**：大（Large）
> **并行执行**：是，分 4 个阶段波浪
> **关键路径**：阶段 0 备份 → 阶段 1 修复迁移状态 → 阶段 2 代码修复 → 最终验证

---

## Context

### 原始需求
用户要求：分批执行数据库管理工具，分步处理 bug，让 db-manager 正常稳定工作，设计工作流文档。

### 面试总结
**关键讨论**：
- 立即修复并应用迁移（确认）
- 分阶段修复代码 bug（A: 多阶段，确认）
- 编写 CLI 使用手册（执行工作量文档，确认）
- 优先级由 Prometheus 决定（确认）

### 研究发现
- 41 个迁移文件，25 个已应用，16 个待处理
- V104 在 flyway_schema_history 中出现 3 次（DELETE + SQL + SQL），状态损坏
- V26.1 使用小数点版本号，Flyway 11+ 支持但尚未应用
- info.py 解析逻辑脆弱，无边界检查
- config.py 默认端口 3306 而非项目 23306
- flyway_adapter.py CLI 模式下密码通过 -password=xxx 暴露
- 6 个操作文件重复相同代码
- 无测试目录，无 .venv，无代码检查工具

### Metis 审查结果
- **V104 损坏需优先处理**：手动清理重复记录后再应用迁移
- **实际待处理 16 个，不是 12 个**
- **CLI 模式密码暴露更严重**（-password=xxx 参数）
- **阶段 0（验证+保护）必须先执行**：备份 + 确认损坏详情

---

## Work Objectives

### Core Objective
修复 db-manager 所有功能性和安全性 bug，将 16 个待处理迁移应用到数据库，建立自动化测试和质量检查流程，产出可操作的运维文档。

### Concrete Deliverables
- `src/db_manager/` 目录下所有修复后的 Python 文件
- `tests/` 目录下完整的 pytest 测试套件
- `docs/` 或 `README.md` 更新后的使用手册
- `.sisyphus/evidence/` 下的验证截图/日志

### Definition of Done
- [ ] `db-manager info` 显示所有 41 个迁移状态正常（无失败、无重复）
- [ ] `db-manager validate` 返回验证通过
- [ ] `pytest tests/` 全部通过，覆盖率 ≥80%
- [ ] `ruff check src/` 无错误
- [ ] `ps aux | grep flyway` 不显示明文密码

### Must Have
- 迁移状态完全修复（V104 去重 + 16 个待处理迁移应用）
- 核心 bug 修复（端口、解析、密码、重复代码）
- 测试基础设施可运行（.venv + pytest）
- CLI 使用手册覆盖所有命令

### Must NOT Have (Guardrails)
- 不得修改 migrations/ 目录下 SQL 文件的内容（只改文件名或顺序）
- 不得删除已应用迁移的历史记录（V1-V22, V103-V105）
- 不得引入新的 Python 依赖（pytest/ruff/black 除外）
- 不得修改数据库 schema 结构（只修复状态和应用已有迁移）
- 避免过度抽象和模板化代码

---

## Verification Strategy

> **ZERO HUMAN INTERVENTION** - 所有验证由代理执行。

### Test Decision
- **Infrastructure exists**: NO（需从零建立）
- **Automated tests**: YES (Tests-after) - 先修复代码，再写测试
- **Framework**: pytest + pytest-cov
- **Coverage target**: ≥80% line coverage

### QA Policy
每个任务必须包含代理可执行的 QA 场景。证据保存到 `.sisyphus/evidence/`。

---

## Execution Strategy

### Parallel Execution Waves

```
阶段 0（验证与保护 - 4 个任务并行）：
├── T0.1: 创建数据库备份快照
├── T0.2: 验证待处理迁移清单（16 个）
├── T0.3: 分析 V104 损坏详情
└── T0.4: 检查 .env 中 DB_PORT 设置

阶段 1（迁移修复 - 必须串行）：
├── T1.1: 手动清理 V104 重复记录
├── T1.2: 应用 V23-V31 迁移批次
├── T1.3: 应用 V99-V101 迁移批次
├── T1.4: 应用 V106-V108 迁移批次
└── T1.5: 验证所有 41 个迁移状态正常

阶段 2（代码修复 - 5 个任务并行）：
├── T2.1: 修复 config.py 默认端口
├── T2.2: 修复 info.py 脆弱解析
├── T2.3: 修复密码暴露
├── T2.4: 提取公共代码消除重复
└── T2.5: 修复 cli.py 裸异常捕获

阶段 3（测试基础设施 - 6 个任务）：
├── T3.1: 创建 .venv 并安装依赖
├── T3.2: 配置 ruff + black 代码检查
├── T3.3: 编写 config.py 单元测试
├── T3.4: 编写 flyway_adapter.py 单元测试
├── T3.5: 编写 operations/ 单元测试
└── T3.6: 验证覆盖率 ≥80%

阶段 4（文档 - 3 个任务并行）：
├── T4.1: 编写 CLI 使用手册
├── T4.2: 编写迁移工作流文档
└── T4.3: 编写数据库管理最佳实践指南

Wave FINAL（验证 - 4 个并行审查）：
├── F1: 计划合规审计（oracle）
├── F2: 代码质量审查（unspecified-high）
├── F3: 真实手动 QA（unspecified-high）
└── F4: 范围保真度检查（deep）
```

### Dependency Matrix

| Task | Blocked By | Blocks |
|------|------------|--------|
| T0.1-T0.4 | None | T1.1 |
| T1.1 | T0.1, T0.3 | T1.2 |
| T1.2 | T1.1 | T1.3 |
| T1.3 | T1.2 | T1.4 |
| T1.4 | T1.3 | T1.5 |
| T1.5 | T1.4 | F1-F4 |
| T2.1-T2.5 | T0.4 | T3.x |
| T3.1-T3.6 | T2.x | F1-F4 |
| T4.1-T4.3 | T1.5, T3.6 | F1-F4 |
| F1-F4 | All above | User OK |

---

## TODOs

### 阶段 0：验证与保护

- [x] T0.1. 创建数据库备份快照
  - **What**: mysqldump 完整备份到 /tmp/ulticode-backup-{timestamp}.sql
  - **QA**: 备份文件存在且大小 > 1KB
  - **Commit**: NO

- [x] T0.2. 验证待处理迁移清单
  - **What**: 列出文件 vs 已应用，确认差集 = 16 个
  - **QA**: `comm -23` 输出行数 = 16
  - **Commit**: NO

- [x] T0.3. 分析 V104 损坏详情
  - **What**: 查询 flyway_schema_history 中 V104 的 3 条记录，分析类型
  - **QA**: 确认 3 条记录，其中一条 type='DELETE'
  - **Commit**: NO

- [x] T0.4. 检查 .env 中 DB_PORT 设置
  - **What**: 读取 .env 确认 DB_PORT=23306
  - **QA**: grep 输出包含 23306
  - **Commit**: NO

### 阶段 1：迁移修复

- [x] T1.1. 手动清理 V104 重复记录
  - **What**: DELETE flyway_schema_history 中 type='DELETE' 的 V104 记录
  - **QA**: V104 只剩 2 条记录 (installed_rank 26+28)，DELETE 记录已删除
  - **Commit**: NO

- [ ] T1.2. 应用 V23-V31 迁移批次
  - **What**: db-manager migrate，关注编码修复（V26, V26.1, V29）
  - **QA**: V23-V31 全部 success=1，中文正常显示
  - **Commit**: NO

- [ ] T1.3. 应用 V99-V101 迁移批次
  - **What**: db-manager migrate 应用 edge/follow schema
  - **QA**: V99-V101 success=1
  - **Commit**: NO

- [ ] T1.4. 应用 V106-V108 迁移批次
  - **What**: db-manager migrate 应用最终修复
  - **QA**: V106-V108 success=1
  - **Commit**: NO

- [ ] T1.5. 验证所有 41 个迁移状态正常
  - **What**: info + validate + migrate 确认无待处理
  - **QA**: validate 通过，migrate 报告 "up to date"
  - **Commit**: NO

### 阶段 2：代码修复

- [ ] T2.1. 修复 config.py 默认端口（3306→23306）
  - **What**: 修改 config.py:44 默认值
  - **QA**: 不设置 DB_PORT 时端口仍为 23306
  - **Commit**: YES - `fix(db-manager): correct default MySQL port`

- [ ] T2.2. 修复 info.py 脆弱解析
  - **What**: 添加边界检查，删除冗余赋值
  - **QA**: info 命令正常输出
  - **Commit**: YES - `fix(db-manager): add bounds checking to info parser`

- [ ] T2.3. 修复密码暴露
  - **What**: CLI 模式改用 FLYWAY_PASSWORD 环境变量
  - **QA**: ps aux 不显示密码
  - **Commit**: YES - `security(db-manager): pass password via env var`

- [ ] T2.4. 提取公共代码消除重复
  - **What**: 创建 _common.py，提取 check_flyway_installed()
  - **QA**: 6 个操作文件导入公共函数，命令正常
  - **Commit**: YES - `refactor(db-manager): extract shared code to _common.py`

- [ ] T2.5. 修复 cli.py 裸异常捕获
  - **What**: 改为捕获具体异常，保留 SystemExit/KeyboardInterrupt
  - **QA**: 无 `except Exception`
  - **Commit**: YES - `fix(db-manager): avoid bare except clause`

### 阶段 3：测试基础设施

- [ ] T3.1. 创建 .venv 并安装依赖
  - **What**: python3 -m venv .venv && pip install -e ".[dev]"
  - **QA**: .venv 存在，pytest 可执行
  - **Commit**: NO

- [ ] T3.2. 配置 ruff + black 代码检查
  - **What**: 添加 pyproject.toml 配置，运行 ruff check
  - **QA**: ruff check src/ 无错误
  - **Commit**: YES - `chore(db-manager): add ruff and black config`

- [ ] T3.3. 编写 config.py 单元测试
  - **What**: tests/test_config.py 覆盖 get_db_config(), get_jdbc_url()
  - **QA**: pytest tests/test_config.py 通过
  - **Commit**: YES - `test(db-manager): add config tests`

- [ ] T3.4. 编写 flyway_adapter.py 单元测试
  - **What**: tests/test_flyway_adapter.py 覆盖检测和命令构建
  - **QA**: pytest tests/test_flyway_adapter.py 通过
  - **Commit**: YES - `test(db-manager): add flyway_adapter tests`

- [ ] T3.5. 编写 operations/ 单元测试
  - **What**: tests/test_operations.py 覆盖所有 6 个操作
  - **QA**: pytest tests/test_operations.py 通过
  - **Commit**: YES - `test(db-manager): add operations tests`

- [ ] T3.6. 验证覆盖率 ≥80%
  - **What**: pytest --cov=src --cov-report=term
  - **QA**: 覆盖率报告显示 ≥80%
  - **Commit**: NO

### 阶段 4：文档

- [ ] T4.1. 编写 CLI 使用手册
  - **What**: 更新 README.md，覆盖所有命令用法
  - **QA**: 每个命令有示例和说明
  - **Commit**: YES - `docs(db-manager): update CLI usage guide`

- [ ] T4.2. 编写迁移工作流文档
  - **What**: 创建 docs/migration-workflow.md
  - **QA**: 包含常见问题和解决方案
  - **Commit**: YES - `docs(db-manager): add migration workflow guide`

- [ ] T4.3. 编写数据库管理最佳实践指南
  - **What**: 创建 docs/best-practices.md
  - **QA**: 包含备份、恢复、编码修复流程
  - **Commit**: YES - `docs(db-manager): add database management best practices`

---

## Final Verification Wave

- [ ] F1. **计划合规审计** — `oracle`
  验证所有 Must Have 存在，所有 Must NOT Have 不存在。

- [ ] F2. **代码质量审查** — `unspecified-high`
  运行 tsc/ruff/pytest，检查 AI slop 模式。

- [ ] F3. **真实手动 QA** — `unspecified-high`
  执行所有 QA 场景，保存证据到 .sisyphus/evidence/。

- [ ] F4. **范围保真度检查** — `deep`
  验证每个任务只做了计划内的事，无范围蔓延。

---

## Commit Strategy

| Batch | Tasks | Message |
|-------|-------|---------|
| 1 | T2.1 | `fix(db-manager): correct default MySQL port to 23306` |
| 2 | T2.2 | `fix(db-manager): add bounds checking to info table parser` |
| 3 | T2.3 | `security(db-manager): pass password via env var instead of CLI arg` |
| 4 | T2.4 | `refactor(db-manager): extract shared flyway check to _common.py` |
| 5 | T2.5 | `fix(db-manager): avoid bare except clause in CLI` |
| 6 | T3.2 | `chore(db-manager): add ruff and black config` |
| 7 | T3.3-T3.5 | `test(db-manager): add unit tests for config, adapter, operations` |
| 8 | T4.1-T4.3 | `docs(db-manager): update documentation` |

---

## Success Criteria

### Verification Commands
```bash
# 迁移状态
cd db-manager && .venv/bin/python -m db_manager.cli info
# Expected: 所有 41 个迁移显示 Success

# 验证
cd db-manager && .venv/bin/python -m db_manager.cli validate
# Expected: 退出码 0

# 测试
cd db-manager && .venv/bin/pytest tests/ -v --cov=src
# Expected: 全部通过，覆盖率 ≥80%

# 代码质量
cd db-manager && .venv/bin/ruff check src/
# Expected: 无错误

# 密码安全
ps aux | grep flyway
# Expected: 不显示明文密码
```

### Final Checklist
- [ ] 所有 41 个迁移 success=1
- [ ] 无 flyway_schema_history 损坏记录
- [ ] pytest 全部通过
- [ ] 覆盖率 ≥80%
- [ ] ruff 无错误
- [ ] 密码不暴露
- [ ] CLI 使用手册完整
- [ ] 工作流文档完整
