# Archive — 89 历史迁移归档候选（Candidate 4 延期说明）

> 报告 `/tmp/architecture-review-20260822-170000.html#candidate-4` 期望 `89 → 1 + archive/`：将 72 shared + 5×2-5 owner 共 89 个已应用迁移压缩为单一 `baseline.sql` 并移入 `archive/`，使 AI/工具扫描从 89 降至 ~10。

**当前状态：延期（deferred），未执行物理归档**

- **约束**：`AGENTS.md §Database changes` — 已应用迁移不可改写/移动/删除。89 文件当前全部保留在 `migrations/*.sql` 与 `migrations/{auth,admin,app,notification,submission}/`，`flyway_schema_history` 已在各部署环境记录这些版本。
- **已交付的替代**：`init-db/baseline/baseline.sql`（3511行，6 schema，130表）+ `validate-baseline.sh` + `baseline-adopt.sh`（`130 vs 130 PASS`，`ADOPT_EXIT:0`）已提供“单一真实来源”供 AI 导航（扫描 1 文件即可得最终形态），但 Flyway 增量仍扫描 89 文件。

**为何当前不能物理归档（需 ADR）：**

- 移动已应用的 89 文件会使已部署环境的 `flyway_schema_history` 中记录的版本在文件系统缺失，`validate` 将失败；`ignoreMissingMigrations=true` 会绕过该校验，掩盖历史不一致，直接违反 `AGENTS.md` 不可变约束
- 示例中的 `mv init-db/migrations/*/*.sql init-db/migrations/archive/*/` 目标展开不可靠（多源对单目标），且未区分 5 个 Owner 的层级与版本映射，执行即破坏

**安全替代（非破坏性 manifest）：**

- 本目录仅保留本 README 作为归档候选的 ADR 占位，不存放任何迁移副本，不参与 `flyway*.conf` 扫描
- 候选清单（仅清单，不移动）可通过以下只读命令生成，供 ADR 评审时作为归档范围依据：
  ```bash
  ls init-db/migrations/V*.sql | wc -l          # 72 shared
  ls init-db/migrations/*/*.sql | wc -l          # 17 owners (5+2+4+3+3)
  ./init-db/scripts/validate-baseline.sh          # 130 vs 130
  ./init-db/scripts/baseline-adopt.sh            # 130 vs 130
  ```

**在未获 ADR 批准前，本目录仅作为占位，不参与任何 `flyway*.conf` 的 `locations`，`git diff --check` 保持干净。**
