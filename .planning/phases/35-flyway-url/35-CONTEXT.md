# Phase 35: Flyway URL 修复 - Context

**Gathered:** 2026-04-22
**Status:** Ready for planning

<domain>
## Phase Boundary

修复 CI workflow 中的 Flyway 下载 URL，使其不再返回 404。迁移任务需要在 CI 环境中正常执行数据库迁移。

</domain>

<decisions>
## Implementation Decisions

### Flyway Version
- **D-01:** 使用 Flyway 10.17.0（10.x 最新稳定版）
- 10.x 长期支持版本，企业级稳定
- Maven Central URL 已验证返回 HTTP 200

### Download URL
- **D-02:** Maven Central URL: `https://repo1.maven.org/maven2/org/flywaydb/flyway-commandline/10.17.0/flyway-commandline-10.17.0-linux-x64.tar.gz`
- 官方 Redgate URL 返回 404，不可用
- Maven Central 为官方事实来源

### CI Workflow Changes
- **D-03:** 更新 `.github/workflows/ci.yml` 中的 `migrate-validate` job
- 替换 `11.3.4` → `10.17.0`
- 保持现有的 wrapper script 方式（JRE 检测问题已处理）

### Verification
- **D-04:** 验证 `flyway --version` 成功输出
- 验证 `python -m db_manager.cli migrate` 执行成功
- 验证 `python -m db_manager.cli validate` 通过

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Context
- `.planning/REQUIREMENTS.md` — DEPS-02 requirement definition
- `.planning/ROADMAP.md` — Phase 35 goal and success criteria
- `.github/workflows/ci.yml` — 当前 CI workflow（需修改的行：204）

### Technical Reference
- Maven Central Flyway: `https://repo1.maven.org/maven2/org/flywaydb/flyway-commandline/10.17.0/flyway-commandline-10.17.0-linux-x64.tar.gz` (HTTP 200 verified 2026-04-22)

</canonical_refs>

<codebase_context>
## Existing Code Insights

### CI Workflow Pattern
- `migrate-validate` job 已有完整结构
- 使用 Python db-manager 调用 Flyway
- Flyway CLI 通过 curl 下载 + wrapper script 解压

### Reusable Assets
- 现有的 MySQL health check 等待逻辑
- 现有的 db-manager CLI 调用模式

### Integration Points
- 修改 `.github/workflows/ci.yml` 第 204 行
- 版本号变更需同步更新 wrapper script 中的路径

</codebase_context>

<specifics>
## Specific Ideas

- 保持现有的 Alpine JRE workaround（wrapper script 方式）
- 不改变 db-manager 的调用方式
- 最小化变更，只改 URL 和版本号

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 35-flyway-url*
*Context gathered: 2026-04-22*
