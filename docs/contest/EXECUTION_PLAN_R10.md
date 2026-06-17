# Contest 模块 R10 多轮执行计划 — 残留 deferred 收口 + 运维安全补全

> **作用**：收口 R7–R9 累计 deferred + SECURITY_REVIEW 残留 + F-01 复核销项。**完成模块 v4.3 收口**。
> **裁决依据**：[REVIEW_V3 §12](./REVIEW_V3.md) R10 deferred 5 项 + F-01 复核 2 项 + F-SEC-10/13 残留 2 项 = 9 项。
> **创建**：2026-06-17
> **预计 R10 工期**：3–4 人日
> **R10 完成条件**：9 项全绿 + F-01 audit doc §3.1/§6.4 标 ✅ + REVIEW_V3 §12 状态更新

---

## 0. 范围盘点

### 来自 REVIEW_V3 §12 R10 deferred 5 项

| ID | 项 | 类别 |
|---|---|---|
| R10.1 | per-contest evict 真实现（改 `@Cacheable` key 模板去 `'getGlobalRanking:' + #limit` 兼容尾巴）| 性能 |
| R10.2 | i18n view 模板接线（`ContestBrowseView` / `ContestRankingsView` / `MyContests` / WS banner）| i18n |
| R10.3 | i18n key 同步审计 | i18n |
| R10.4 | 旧 `getGlobalRankingsPaginated(page, limit)` 签名删除（保留 1 个版本后清理）| 清理 |
| R10.5 | M1 `contestMapper.selectById` 多一次查询优化（跨 submission 模块，**独立 PR**）| 性能 |

> 注：R10.5 与 R10.1 同属性能类但跨模块，独立 PR 处理；本计划只跟踪，独立部署。

### 来自 F-01-STATE_MACHINE_AUDIT 待复核销项 2 项（代码侧已通过验证）

| ID | 项 | 类别 |
|---|---|---|
| R10.6 | F-01 §3.1 `#5 finishVirtualContest` 复核销项（代码已走 `bulkFinishByIds`，仅文档标记）| 销项 |
| R10.7 | F-01 §6.4 F-06 `timeFromStart` 复核销项（代码已按虚拟/真实分支，注释已写 R6.2 锚定）| 销项 |

> R10.6/R10.7 经代码侧复核**无 violation**：
> - `ContestSchedulerServiceImpl.finishVirtualContest:251-255` 已 `participantMapper.bulkFinishByIds(List.of(participant.getId()), ...)`
> - `SubmissionServiceImpl.recordContestSubmissionIfNeeded:1387-1395` 已 `Boolean.TRUE.equals(p.getIsVirtual()) ? virtualClock : contestClock`

### 来自 SECURITY_REVIEW 残留 2 项（运维安全，非代码 bug）

| ID | 项 | 严重度 | 类别 |
|---|---|---|---|
| R10.8 | F-SEC-10 Flyway 迁移期间 admin/用户操作无锁 → `init-db/README.md` 加 "Migration Operational Checklist" 节 | MEDIUM | 运维 |
| R10.9 | F-SEC-13 虚拟赛结束无审计 / log retention 不清 → 新建 `docs/PRIVACY.md` 单节记录 log retention 策略 | LOW | 治理 |

---

## 1. 设计原则

1. **每项独立部署 / 独立回滚** —— 9 项可任意组合，独立 commit，独立验证
2. **R10.6 / R10.7 纯文档销项**，单 commit 即可；不混合代码改动
3. **R10.1 / R10.2 是性能/i18n 体验类**，无 schema 改动；可直接合 main
4. **R10.5 独立 PR** —— 跨 submission 模块改动需独立 review + 独立部署窗口
5. **R10.8 / R10.9 是文档/治理** —— 不需要后端测试，文档走 review 即可
6. **R10 完成 = 9 项全绿 + REVIEW_V3 §12 状态从 "deferred" → "closed"**

---

## 2. Round 10.1 — per-contest evict 真实现（F-21 完结）

### ⚠️ 2026-06-18 评估发现：1.1 已完成，1.2 接受为低优先（plan 误判）

**R10.1 原计划假设**：
- 1.1 `@Cacheable` key 模板需扩展含 contestId
- 1.2 `evictRankingCacheForContest(contestId)` 是占位 API 需真实接入

**代码侧实际**（`ContestServiceImpl.java:398`）：
```java
@Cacheable(value = "contestRanking", key = "'getRanking:' + #contestId + ':' + #limit + ':' + (#cursor ?: '0')")
public List<ContestRankingVO> getContestRanking(String contestId, Integer limit, String cursor) {
```

- 1.1 ✅ **已在 R9.1 落地**（R9 commit message 也确认：`The @Cacheable key template in ContestServiceImpl was NOT changed...` 这句话描述的是 `getGlobalRanking` 的 key 保留向后兼容，但 R9 同时**新建**了 `getContestRanking(contestId, limit, cursor)` 并已含 contestId 维度）
- 1.2 ⏸ 仍为 `evictRankingCache()` 全局 `clear()`，但 `ContestScoringServiceImpl.java:305-310` 注释明确：
  > *"Current global clear() is acceptable at the < 10k-row pagination range; NFR-P1 is not triggered."*

**R10.1 状态**：
- **1.1 已完成**（R9.1 落地时已含 contestId）
- **1.2 DEFERRED**（现有代码注释已声明当前规模可接受，且 Redis SCAN/KEYS 引入复杂度不划算；如未来 > 10k 行触发 NFR-P1，应作为 R11 独立产品决策 + 独立 PR）

**R10.1 plan 误判来源**：plan 基于 R9 计划文档而未对代码做实际验证，R9.1 实际已部分实现 R10.1.1。

---

## 2b. Round 10.1 — per-contest evict 真实现（原计划，作废）

---

## 3. Round 10.2 — i18n view 模板接线（F-40/F-41/R9.3 完结）

### 改动

#### 3.1 引用 en-US/zh-CN `contest.ts` 中已写回但未引用的 keys

- `empty.contests` → `console/src/views/contest/ContestBrowseView.vue`
- `empty.rankings` → `console/src/views/contest/ContestRankingsView.vue`
- `empty.virtualHistory` → `console/src/views/contest/MyContests.vue` "我的虚拟赛" tab
- `loading.rankings` / `loading.history` → 对应列表骨架屏
- `error.rankingsLoadFailed` → ranking 错误 toast
- `error.alreadyInVirtualContestOtherTab` → R9.4 multi-tab 检测 toast
- `connection.reconnecting` / `connection.reconnectFailed` → WS banner
- `connection.rejected` → R7.3 rejected 事件 toast
- `replay.*` → 虚拟赛历史 tab

#### 3.2 硬编码字符串替换审计

`grep -rn '暂无\|加载中\|失败\|重连' console/src/views/contest/` 验证无遗漏

### 验收

- `pnpm validate:i18n-keys` 全绿（管理端现有规则）
- 9 个 view 模板全部无硬编码中文字符（grep 验证）
- 切换 en-US / zh-CN 浏览器语言，UI 文案 100% 一致

### 回滚

- view 改回 `R9_PLACEHOLDER.ts` 形式（注意此文件已删除；回退需 git revert）
- 推荐：用 `git revert <commit>` 整批回退

### 关联 ADR

无新 ADR

---

## 4. Round 10.3 — i18n key 同步审计

### 改动

#### 4.1 跨端 i18n 完整一致性审计

- console / management / shared 三个 i18n 源
- en-US / zh-CN 全部模块文件
- 工具：项目 `pnpm validate:i18n-keys` + 自写脚本 `scripts/i18n-audit.sh` 跑 key 集合 diff

#### 4.2 漂移 key 报告（不进 plan，纯审计产物）

输出 `docs/contest/I18N_AUDIT_R10.md`：
- 缺失（en 有 zh 无 / 反之）
- 未引用（写在 locale 但无 view 引用）
- 硬编码（view 有但 locale 无）

### 验收

- 漂移 key 数为 0（或显式接受为产品决策）
- `I18N_AUDIT_R10.md` 生成

### 回滚

- 审计产物是 doc，删即可；不改代码

### 关联 ADR

无

---

## 5. Round 10.4 — 旧 `getGlobalRankingsPaginated` 签名删除

### ⚠️ 2026-06-17 评估发现：ABORTED（plan 误判）

**R10.4 原计划假设**：`getGlobalRankingsPaginated(page, limit)` 是被新 `getContestRanking(contestId, limit, cursor)` 取代的旧版本，R9 保留 1 个版本后清理。

**代码侧实际**：
- `getGlobalRankingsPaginated` 用 `globalRankingMapper`（L18 import / L25 / L65 field）→ **全局跨场次榜单**（无 contestId）
- `getContestRanking(contestId, ...)` 用不同 mapper（`selectParticipantsKeyset`）→ **单场榜单**
- 两者是**两个独立功能**，不是同 API 旧/新版本
- `console/src/api/contest.ts:166-176` `fetchGlobalRankings` 仍调 `/contest/rankings/global`
- `console/src/api/contest.schema.ts:111-129` 有独立 `GlobalRankingEntry` schema
- `GlobalRanking` entity 独立存在

**结论**：**不删除**。删除会破坏 console 全局榜单功能。

**R10.4 状态**：ABORTED（plan 误判）。原"清理"动机应由 R10.1 性能改动覆盖（cache key 模板扩展、evict 精确化），与 API 签名删除无关。

### 若未来真要重构全局榜单

- 应作为 R10.x 或 R11 独立产品决策（"全局榜单是否要 keyset 分页 + cursor"）
- 需要 console / management 前端 + 后端 mapper + 缓存策略完整方案
- 不能作为 R10 "清理旧代码" 一并处理

---

## 5b. Round 10.4 — 旧 `getGlobalRankingsPaginated` 签名删除（原计划，作废）

### 关联 ADR

无

---

## 6. Round 10.5 — M1 `contestMapper.selectById` 优化（**独立 PR**）

### 改动

> 跨 submission 模块，需独立 PR + 独立 review + 独立部署窗口

`backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/SubmissionServiceImpl.java`：
- 当前 `recordContestSubmissionIfNeeded` 调 `contestMapper.selectById(cp.getContestId())` 多一次查询
- 改为：通过 `contestProblem` 携带 `contestStartTime` / `contestActualStartTime` 字段避免额外 select
- 涉及 `ContestProblem` entity 加字段 + mapper + service 调用方

### 验收

- IT 测试：`recordContestSubmissionIfNeeded` 内 SQL 查询次数减少 1
- 性能：单提交耗时减少 < 5ms（实际只省一次主键查询）
- 回归：所有现有 SubmissionServiceImplTest + IT 通过

### 回滚

- 独立 commit，可单独 revert

### 关联 ADR

无（建议后续 R11 评估是否升级 ADR-006）

---

## 7. Round 10.6 + 10.7 — F-01 复核销项（文档）

### 改动

#### 7.1 F-01-STATE_MACHINE_AUDIT.md 更新

- §3.1：将 "🟡 **需复核**" → "✅ **已复核（R10 2026-06-17）**"
- §5 结论表：`#5 finishVirtualContest` 行状态 `🟡 需复核` → `✅ 已通过`
- §6.4：将 "**需复核**" → "✅ **已复核（R10 2026-06-17）**"
- §6 标题后加 R10 复核证据块（指向代码行号）

#### 7.2 REVIEW_V3 §12 更新

- "F-01 状态机待复核" 节 → "F-01 状态机已销项（R10）"
- 加一行：`✅ R10 验证: finishVirtualContest + timeFromStart 均无 violation`

### 验收

- F-01 audit doc 中无 "需复核" 残留
- REVIEW_V3 §12 "F-01 状态机待复核" 节取消 / 改为 ✅

### 回滚

- 文档 revert

### 关联 ADR

无

---

## 8. Round 10.8 — F-SEC-10 迁移期 checklist

### 改动

`init-db/README.md` 新增 "## Migration Operational Checklist" 节：

```markdown
## Migration Operational Checklist

### Before Running

1. **Schedule maintenance window** for any DDL on `contest_submissions` /
   `contest_participants` tables (largest tables, longest lock time)
2. **Verify MySQL version ≥ 8.0** to use `ALGORITHM=INPLACE, LOCK=NONE`
3. **Backup database** (full + binlog) before any unique index / generated column

### MySQL Session Settings (per migration run)

```sql
SET SESSION innodb_lock_wait_timeout = 10;   -- default 50s
SET SESSION lock_wait_timeout = 10;
```

### DDL Hints (MySQL 8.0+)

Prefer online DDL to avoid blocking writes:

```sql
ALTER TABLE contest_participants
  ADD UNIQUE KEY uk_active_global (user_id, is_active_global),
  ALGORITHM=INPLACE, LOCK=NONE;
```

### Seed vs Migration Ordering

- Schema migrations: `V{timestamp}__*.sql` (versioned, runs once)
- Repeatable seed/data: `R__*.sql` (Flyway repeatable, runs on checksum change)
- Never mix schema changes into `R__` files (will re-run, break)

### Rollback

Flyway does not auto-rollback. For each migration, prepare a reverse SQL in
`init-db/rollback/V{timestamp}__*.rollback.sql` (manual execution only).
```

### 验收

- `init-db/README.md` 含 "Migration Operational Checklist" 节
- 内容覆盖：pre-check / session settings / DDL hints / seed ordering / rollback

### 回滚

- 文档 revert

### 关联 ADR

建议 ADR-012（运维安全决策，**R10.8 同步起草**）

---

## 9. Round 10.9 — F-SEC-13 log retention 策略

### 改动

新建 `docs/PRIVACY.md`：

```markdown
# UltiCode Privacy & Log Retention

## Scope

This document covers user-generated data retention, especially sensitive
contest-related data (virtual session records, AC/failure history, admin
audit trails).

## Log Retention

| Data Type | Retention | Storage | Deletion Trigger |
|-----------|-----------|---------|------------------|
| Application logs (INFO/WARN) | 90 days | `logs/ulticode-9001-*.log` (rotated) | Auto via logback config |
| Virtual session records | 180 days | MySQL `contest_participants` (is_virtual=1) | Manual `DELETE` after retention |
| Submission code & test details | 180 days | MySQL `submissions` / `contest_submissions` | Manual `DELETE` after retention |
| Admin audit log (scoring config changes, ban actions) | 365 days | MySQL `audit_log` (if exists) / application log | Manual |
| Authentication events (login/logout/failures) | 365 days | MySQL `auth_events` (if exists) / application log | Manual |

## PII / Sensitive Fields

- Email / phone: stored as-is, not displayed in plaintext (mask `1****@foo.com`)
- User IP: stored in audit log, not exposed in any user-facing API
- Code submission: stored 180 days, then purged

## GDPR / Data Subject Access

For data export / deletion requests, use `scripts/dev/data-subject.sh <userId>`
(planned; not implemented — see R10 follow-ups).

## R10 Note

Currently `finishVirtualContest` and `autoFinishVirtualParticipants` log via
`log.info` only. No dedicated `audit_log` table for contest actions exists.
This is **accepted as low risk** until product defines anti-cheat requirements.
```

### 验收

- `docs/PRIVACY.md` 存在且包含 log retention 表
- CLAUDE.md 引用 `docs/PRIVACY.md`（如需要）

### 回滚

- 文档 revert / 删除

### 关联 ADR

无

---

## 10. 部署顺序

由于 9 项**完全独立**且无 schema 改动，可**任意顺序**部署：

```
推荐顺序（按风险/价值）：
1. R10.6 / R10.7 （doc-only，零风险，先销 F-01 隐患）
2. R10.8 / R10.9 （doc-only，零风险，先闭环 SECURITY 残留）
3. R10.4 （旧签名清理，编译验证简单）
4. R10.1 / R10.2 / R10.3 （代码改动，需测试）
5. R10.5 （独立 PR，跨模块，最后处理）
```

每个 R 独立 commit + 独立 PM2 重启 + 独立验证。

---

## 11. 验收总表

| Round | Finding | 验收命令/方法 | 估时 |
|-------|---------|--------------|------|
| R10.1 | F-21 | 单 contest evict 隔离 IT + 现有 NFR-P1 不退化 | 0.5 |
| R10.2 | LOW i18n | grep 硬编码 = 0 + pnpm validate:i18n-keys 全绿 | 1 |
| R10.3 | LOW i18n | `I18N_AUDIT_R10.md` 漂移 key = 0 | 0.5 |
| R10.4 | LOW cleanup | 编译 + 单测 + IT 全绿 | 0.25 |
| R10.5 | M1 | 独立 PR + SubmissionServiceImplIT 通过 | 0.5–1 |
| R10.6 | F-01 §3.1 | grep `updateById.*FINISHED` 在 finishVirtualContest 路径 = 0 | 0.1 |
| R10.7 | F-01 §6.4 | grep `timeFromStart` 写入路径仅 1 处且分支正确 | 0.1 |
| R10.8 | F-SEC-10 | `init-db/README.md` 含 Migration Operational Checklist 节 | 0.25 |
| R10.9 | F-SEC-13 | `docs/PRIVACY.md` 存在 + 含 log retention 表 | 0.25 |
| **合计** | **9 项** | | **3.5–4 人日** |

**R10.1–R10.9 全绿 → 模块 v4.3 完结。** 所有 R7–R9 deferred + F-01 复核 + F-SEC-10/13 残留全部关闭。

---

## 12. 与历史报告的关系

| 文档 | 关系 |
|---|---|
| [REVIEW_V3 §12](./REVIEW_V3.md) | 列出 5 项 R10 deferred 来源；R10 完成后更新状态 |
| [F-01-STATE_MACHINE_AUDIT.md](./F-01-STATE_MACHINE_AUDIT.md) §3.1/§6.4 | R10.6/R10.7 复核销项落地 |
| [SECURITY_REVIEW.md](./SECURITY_REVIEW.md) F-SEC-10/13 | R10.8/R10.9 落地；建议同步起草 ADR-012（运维安全）|
| [LOW_REMAINING.md](./completed/LOW_REMAINING.md) | R7 收口产物；R10 与之正交（LOW 已全 ✅）|

---

## 13. 后续 R11 候选（非本计划范围）

- **F-22 业务决策** — "全局单活跃"约束跨 contest 唯一索引，等产品/业务方决定
- **CRIT-6 shadow 模式** — runtime + ops 配合，R7.6 延期
- **F-15 TS enum 完整化** — 跨端 enum 统一（CLAUDE.md 已记"前端 TS enum vs 后端 String 错配"）
- **F-32 ContestTimer 组件统一** — R7.4 延期
