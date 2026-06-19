---
title: 审核模块运维深读 (状态机 / 种子数据 / 统计口径)
tags: [ops, security, reference]
status: living
updated: 2026-06-19
owner: backend
---

# 审核模块运维深读 (状态机 / 种子数据 / 统计口径)

> 本文档基于 2026-06-19 真实改动(Spring Boot 3.2.5 / MyBatis-Plus / MySQL 9.1)沉淀,目的是让任何接手审核(moderation)模块的人在**改 status / 造种子 / 排统计卡片**时不再踩状态机与 SQL 口径的坑。

---

## TL;DR — 30 秒上手

```bash
# 1. 种子重跑(dev 调试 / migrate.sh repair 后)
./scripts/dev/migrate.sh migrate   # V20260619120000__Seed_Moderation_Data.sql 幂等

# 2. 验证审核统计卡片(前端 /moderation 四张卡)
set -a; source .env; set +a
docker exec -e MYSQL_PWD="$DB_PASSWORD" ulticode-mysql \
  mysql --default-character-set=utf8mb4 -u "$DB_USER" "$DB_NAME" \
  -e "SELECT status, COUNT(*) FROM moderation_queue GROUP BY status; \
      SELECT IFNULL(resolution,'') AS r, COUNT(*) FROM moderation_queue \
      WHERE status='RESOLVED' GROUP BY r;"
```

**核心要点**:
- **`queue.status` 只取 4 态** —— `PENDING / UNDER_REVIEW / RESOLVED / APPEAL_PENDING`。枚举里的 `DISMISSED` **从不写入 status**,只落地到 `resolution` 列(误报 = `status=RESOLVED + resolution=DISMISSED`)。
- **统计卡片 resolved/dismissed 用 `resolution` 维度互斥分区** —— 不是 status 维度(分不开)。`countResolved = status=RESOLVED AND IFNULL(resolution,'')<>'DISMISSED'`;`countDismissed = status=RESOLVED AND resolution='DISMISSED'`。
- **种子时间锚点用 `CONCAT(CURDATE(), ' HH:MM:SS')`** —— 固定字面量隔天跑会让 `resolvedToday` 恒 0。
- **改 `.env` 后用 `pm2 delete + start`,不要 `restart --update-env`** —— 见 [[RUNBOOK#21 Pm2 Env Cache Trap|RUNBOOK §2.1]]。

---

## 审核状态机(ground truth)

`ModerationStatus` 枚举声明 5 个值,但代码路径**只写 4 个**。这是模块最关键的不变量。

| 起点 | 触发 | 终点 | 写入位置 | `resolution` 落地 |
|------|------|------|----------|------------------|
| (无) | `createReport()` 新建 queue | `PENDING` | `ModerationServiceImpl.createReport` | NULL |
| `PENDING` | `claimItem()`/`assignItem()` | `UNDER_REVIEW` | `ModerationQueueMapper.assignToModerator` | 不变 |
| `UNDER_REVIEW` | `unassign()` | `PENDING` | `ModerationQueueMapper.unassign` | 不变 |
| `PENDING/UNDER_REVIEW` | `performAction(DELETED/HIDDEN)` → `DeleteHideHandler` | `RESOLVED` | `DeleteHideHandler` | `DELETED` / `HIDDEN` |
| `PENDING/UNDER_REVIEW` | `performAction(WARNED)` → `WarnHandler` | `RESOLVED` | `WarnHandler` | `WARNED` |
| `PENDING/UNDER_REVIEW` | `performAction(TEMP_BANNED/PERM_BANNED)` → `BanHandler` | `RESOLVED` | `BanHandler` | `TEMP_BANNED` / `PERM_BANNED` |
| `PENDING/UNDER_REVIEW` | `performAction(RESTORED/DISMISSED/RESOLVED)` → `RestoreDismissHandler` | `RESOLVED` | `RestoreDismissHandler` | `RESTORED` / **`DISMISSED`** / `RESOLVED` |
| `PENDING/UNDER_REVIEW` | `performAction(APPEAL_PENDING)` → `AppealHandler(APPEAL_PENDING)` | `APPEAL_PENDING` | `AppealHandler` | `APPEAL_PENDING`(不设 resolvedAt)|
| `RESOLVED` | `createAppeal()`(仅作者)| `APPEAL_PENDING` | `ModerationServiceImpl.createAppeal` | `APPEAL_PENDING` |
| `APPEAL_PENDING` | `reviewAppeal(APPROVED/REJECTED)` | `RESOLVED` | `ModerationServiceImpl.reviewAppeal`(不走 Handler)| `APPEAL_APPROVED` / `APPEAL_REJECTED` |

**为什么 DISMISSED 落 resolution 而非 status**:`RestoreDismissHandler` 对 `RESTORED/DISMISSED/RESOLVED` 三种 action **统一** `setStatus(RESOLVED)`。DISMISSED 的业务语义是「该 queue 已结案,结案结论是无效举报」。若提升为独立 status,`countResolvedToday`(`status=RESOLVED AND DATE(resolved_at)=CURDATE()`)就会漏掉当天 dismissed 的项,统计口径破裂。

**其他表枚举速查**:
- `report.status`(`ReportStatus`,4 值):`PENDING/REVIEWED/RESOLVED/DISMISSED`。performAction 时 action=DISMISSED → 报告批量置 `DISMISSED`,否则置 `RESOLVED`。
- `report.category`(`ReportCategory`,9 值):`SPAM/HARASSMENT/HATE_SPEECH/VIOLENCE/SEXUAL_CONTENT/MISINFORMATION/WRONG_ANSWER/COPYRIGHT/OTHER`。`createReport` 会 `.toUpperCase()`。
- `appeal.status`(`AppealStatus`,4 值):`PENDING/UNDER_REVIEW/APPROVED/REJECTED`(**避开 `ESCALATED`**,枚举无此值)。
- `moderation_actions.action`(列名是 `action`,**不是** `action_type`)= `ModerationActionType`(11 值,同 resolution 枚举)。

> 5 个 Handler(`DeleteHideHandler / RestoreDismissHandler / WarnHandler / BanHandler / AppealHandler`)是 sealed interface `ModerationActionHandler` 的 `permits` 列表,`ModerationActionHandler.from()` 把 11 个 ActionType 映射进去。

---

## 统计口径修复(resolvedCount / dismissedCount 恒 0)

### 1. Bug 现象

前端审核中心(`/moderation`)四张统计卡片中,`resolvedCount`(实质性处理数)和 `dismissedCount`(误报数)**恒为 0**,即便 DB 里有 RESOLVED 的 queue。

### 2. 根因

`ModerationStatsVO.resolvedCount/dismissedCount` 此前**从未被 `getStats()` 填充**;`ModerationQueueMapper` 也缺对应 count 方法。

### 3. 修复:resolution 维度互斥分区

**为什么不能用 status 维度**:DISMISSED 落为 `status=RESOLVED`,status 维度分不开 RESOLVED 和 DISMISSED —— 用 `count(status=RESOLVED)` 当 resolvedCount 会把误报算进去;没有 `status=DISMISSED` 的行可作 dismissedCount。

**改用 resolution 维度互斥分区**(`ModerationQueueMapper`):

```java
// resolvedCount:实质性处理(删除/隐藏/警告/封禁/恢复/申诉结论),排除误报
@Select("SELECT COUNT(*) FROM moderation_queue "
    + "WHERE status = 'RESOLVED' AND IFNULL(resolution, '') <> 'DISMISSED'")
long countResolved();

// dismissedCount:误报(status=RESOLVED, resolution=DISMISSED)
@Select("SELECT COUNT(*) FROM moderation_queue "
    + "WHERE status = 'RESOLVED' AND resolution = 'DISMISSED'")
long countDismissed();
```

两个口径都以 `status='RESOLVED'` 为基底,按 `resolution` 互斥分区;两者之和 = 所有 `status=RESOLVED` 的 queue(无重叠、无遗漏)。

### 4. `IFNULL` 防 NULL

`countResolved` 用 `IFNULL(resolution, '') <> 'DISMISSED'` 而非 `resolution <> 'DISMISSED'`。SQL 三值逻辑下 `NULL <> 'DISMISSED'` 返回 NULL(非 true),会被 WHERE 过滤掉。若历史数据有 `status=RESOLVED` 但 `resolution=NULL` 的脏行,`IFNULL` 把 NULL 规范化为 `''`,使该行被计入 resolvedCount 而非丢失。`countDismissed` 不需 IFNULL(`NULL = 'DISMISSED'` 为 NULL,自然被过滤,符合预期)。

### 5. Service 层接线

`getStats()` 补两行:`stats.setResolvedCount(queueMapper.countResolved())` + `stats.setDismissedCount(queueMapper.countDismissed())`。

> **这个互斥口径决策不显然,但不横切多端 / 可逆 / 无供应商锁定**,因此不写 ADR,沉淀在本节 + mapper Javadoc。详见 [[adr/README#何时该写一篇 Adr|adr/README §何时该写一篇 ADR]] 的判断标准。

---

## 种子数据集如何造 / 为何这样造

迁移:`init-db/migrations/V20260619120000__Seed_Moderation_Data.sql`(6 表 37 行)。

| 表 | 行数 | 覆盖维度 |
|----|------|----------|
| `moderation_queue` | 8 | status: PENDING 2 / UNDER_REVIEW 2 / RESOLVED 3 / APPEAL_PENDING 1 |
| `reports` | 12 | status: PENDING 3 / REVIEWED 4 / RESOLVED 4 / DISMISSED 1 |
| `moderation_actions` | 7 | action: HIDDEN/DELETED/PERM_BANNED/WARNED/TEMP_BANNED/DISMISSED/APPEAL_PENDING(7 种) |
| `user_bans` | 3 | 临时已解封 / 永久生效 / 历史已解封 |
| `user_warnings` | 3 | 未确认 / 已确认 / 已过期 |
| `appeals` | 4 | PENDING / UNDER_REVIEW / APPROVED / REJECTED |

### 1. 状态自洽(对照代码 ground truth)

- **queue.status 只取 4 态**:表达「误报」用 `status=RESOLVED + resolution=DISMISSED`(**不**用 `status=DISMISSED`)。
- **appeal ↔ queue 关联**:`PENDING/UNDER_REVIEW` 的 appeal 关联 `APPEAL_PENDING` 的 queue;`APPROVED` 的 appeal 关联 `RESOLVED + resolution=APPEAL_APPROVED` 的 queue 且闭环 ban 已解封;`REJECTED` 关联 `RESOLVED + resolution=DELETED` 的 queue 且维持永封。
- **时间线有序**:`appeal.created_at > queue 首次 resolved`(符合「先结案才能申诉」,`createAppeal` 校验 `status=RESOLVED`)。
- **action 链闭环**:辱骂评论 queue 操作链 `HIDDEN → DELETED → PERM_BANNED` 对应永封;广告帖 queue 操作链 `WARNED → TEMP_BANNED` 对应 7 天封(后申诉解封)。

### 2. time anchors 用 `CONCAT(CURDATE())` 对齐 resolvedToday

`countResolvedToday` 口径(Mapper):`WHERE status='RESOLVED' AND DATE(resolved_at)=CURDATE()`。

种子用 `CONCAT(CURDATE(), ' HH:MM:SS')` 构造当天时间锚点(`@t_resolve=09:30` 等),**任意日期执行迁移都能命中**。若用固定字面量 `2026-06-19 09:30:00`,隔天跑就 resolvedToday=0,两张卡片恒空。命中项 = 3(queue-005/006/007)。历史时间用固定字面量(`2026-04` 等),仅作叙事背景。

### 3. 幂等(DELETE + INSERT)

每张表先 `DELETE FROM ... WHERE id IN (...)` 显式列举所有种子 id,再 `INSERT`。重复执行迁移(dev 多次 apply、`migrate.sh repair` 后重跑、手工 `docker exec mysql` 调试)不撞唯一约束:
- `moderation_queue` 唯一约束 `(entity_type, entity_id)`
- `reports` 唯一约束 `uk_reports_reporter_entity(reporter_id, entity_type, entity_id)`
- 主键 id(`mod-queue-*` / `report-*` / 等)

> Flyway 正常不重复 apply(checksum 锁),DELETE+INSERT 是为 dev 调试路径兜底。

### 4. `@admin_id` 动态子查询(去掉 `is_active` 过滤的教训)

```sql
SET @admin_id = (SELECT id FROM users WHERE username='admin' AND role='ADMIN' LIMIT 1);
```

**关键陷阱:不加 `AND is_active=1` 过滤**。`V20260606130000__Secure_Refresh_Tokens_And_Lock_Seed_Accounts.sql`(安全修复迁移)把 admin 锁定为 `is_active=0`。若带 `is_active=1` 过滤会返回 NULL,导致所有 `reviewed_by_id / banned_by_id / performed_by_id`(NOT NULL 列)插入失败。admin 行**仍在 users 表**,id 可取 —— 只是不允许登录。所有 reviewer/performed_by_id/banned_by_id 统一指向 `@admin_id`。

### 5. reporter_id 必须真实用户(uk 约束)

`reports.reporter_id` 受 `uk_reports_reporter_entity(reporter_id, entity_type, entity_id)` 约束,(reporter, entity) 不重复。种子全部用 demo 用户池,且 **reporter ≠ 被举报内容 author**(避免自举报)。同一 queue 的多条 report 用不同 reporter 触发不同 category。

### 6. 其他 DDL 风险

1. `moderation_queue/reports/appeals/user_bans` 的 `updated_at` NOT NULL **无默认值** → 必须显式赋值
2. `moderation_actions/user_warnings` **无 `updated_at` 列** → 不能写
3. `entity_type` 必须 snake_case 小写:`forum_post / forum_comment / solution / solution_comment`(与 `resolveAuthorId` switch 一致)
4. `moderation_actions` 列名是 `action`(**不是** `action_type`)
5. `user_bans` 无 status 列,状态由 `is_permanent / ends_at / unbanned_at` 表达
6. `user_warnings` 状态由 `acknowledged_at / expires_at` 表达,`category` NOT NULL

---

## 排障(审核统计卡片异常)

### 现象:resolvedCount/dismissedCount 恒 0

| 检查项 | 命令 / 验证 |
|--------|------------|
| DB 是否有 RESOLVED queue | `SELECT status, COUNT(*) FROM moderation_queue GROUP BY status` |
| resolution 分布 | `SELECT IFNULL(resolution,'') r, COUNT(*) FROM moderation_queue WHERE status='RESOLVED' GROUP BY r` |
| Service 是否调到 mapper | `pm2 logs ulticode-9001 --nostream --lines 200` 看 `/moderation/stats` 请求轨迹 |
| Mapper 方法是否存在 | 反编译确认:`jad com.ulticode.modules.moderation.mapper.ModerationQueueMapper`(见 [[arthas-mcp-usage]])|
| 前端是否取到字段 | 浏览器 DevTools Network → `/moderation/stats` 响应 `data.resolvedCount` / `data.dismissedCount` |

### 现象:resolvedToday 恒 0(即便有当天 RESOLVED)

- 检查种子/真实数据的 `resolved_at` 是否当天 —— `DATE(resolved_at)=CURDATE()` 依赖时区。容器与宿主时区不一致会导致 CURDATE 错位。
- `docker exec ulticode-mysql mysql -e "SELECT NOW(), CURDATE()"` 确认 MySQL 时区。

### 现象:重跑迁移后审核中心数据错乱

- 确认走的是 `./scripts/dev/migrate.sh migrate`(项目级 wrapper),不是裸 `flyway`(不在 PATH)。checksum 不匹配先 `./scripts/dev/migrate.sh repair`(见 [[RUNBOOK#46 Flyway Checksum Mismatch|RUNBOOK §4.6]])。

---

## PM2 env 缓存陷阱(改 `.env` 后 9001 崩溃)

修复统计口径后,`./mvnw package` + `pm2 restart ulticode-9001 --update-env` 验证时,9001 反复崩溃,日志报 `RedisWrongPasswordException`(或 DB 认证失败),重启计数 ↺ 飙升。

### 根因

`pm2 restart --update-env` **只刷新运行时环境变量覆盖**,**不重读 `ecosystem.config.cjs` 的 `envFromFile`**(PM2 daemon 缓存了首次 start 时的内容)。`.env` 改动后 `--update-env` 不重新加载文件,9001 实际 env 里的 `REDIS_PASSWORD/DB_PASSWORD` 是 stale 值。`pm2 env <id>` 显示的也是 stale,**不可信**。

### 修复

```bash
pm2 delete ulticode-9001 && pm2 start ecosystem.config.cjs --only ulticode-9001
```

验证进程真实 env(读 `/proc/<pid>/environ`,不经 PM2 缓存层):

```bash
tr '\0' '\n' < /proc/$(pm2 pid ulticode-9001)/environ | grep -E 'REDIS_PASSWORD|DB_PASSWORD'
```

### 教训

改 `.env` 后**永远用 `delete + start`**,不要 `restart --update-env`。完整权威说明见 [[RUNBOOK#21 Pm2 Env Cache Trap|RUNBOOK §2.1]]。

---

## 可用工作流(已成)

```
┌─────────────────────────────────────────────────────┐
│  改统计口径 / 加种子 / 改 Handler                       │
└─────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────┐
│  cd backend-spring && ./mvnw compile -B               │
│  ./mvnw package -DskipTests                           │
│  cp target/app.jar(若用 worktree,见下方 PM2 cwd 陷阱)│
└─────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────┐
│  改了 .env? → pm2 delete + start                       │
│  没改 .env? → pm2 restart ulticode-9001 --update-env  │
└─────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────┐
│  curl / 验证 /proc/<pid>/environ + 前端审核中心卡片       │
└─────────────────────────────────────────────────────┘
```

**黄金法则**:
1. 改 `resolution` 口径前,先核对状态机本节 —— `DISMISSED` 落 resolution 不落 status。
2. 种子时间锚点一律 `CONCAT(CURDATE(), ' HH:MM:SS')`,不要写死日期。
3. `@admin_id` 子查询**不**加 `is_active=1`(admin 被安全迁移锁为 inactive)。
4. 改 `.env` → `delete + start`,不 `restart --update-env`。
5. PM2 cwd 锁在 main worktree(跑 `target/app.jar`)—— 在 worktree 里改源码必须 cp 回 main,否则 `pm2 restart` 跑的是 stale 代码。

---

## 测试验证(本会话已通过)

- ✅ `V20260619120000__Seed_Moderation_Data.sql` 6 表 37 行迁移成功(2026-06-19)
- ✅ `countResolved`/`countDismissed` 互斥口径 SQL 验证:两 count 之和 = `count(status=RESOLVED)`(无重叠)
- ✅ `resolvedToday` 命中 3 项(`CONCAT(CURDATE())` 锚点任意日期生效)
- ✅ `ModerationServiceImpl.getStats()` 接线后前端审核中心 resolvedCount/dismissedCount 卡片不再恒 0
- ✅ `pm2 delete + start` 后 `/proc/<pid>/environ` 与 `.env` 一致,无 stale env

所有验证**没有**复现卡片恒 0 / 9001 认证失败。

---

## See also

- [[README|工程文档首页]] — 仓库总入口
- [[backend]] — 后端架构总览(moderation 模块在 §模块清单)
- [[data]] — 数据模型总览(moderation 6 表在 §Moderation & Audit)
- [[RUNBOOK#21 Pm2 Env Cache Trap|../RUNBOOK.md §2.1]] — PM2 env 缓存陷阱权威说明
- [[RUNBOOK#46 Flyway Checksum Mismatch|../RUNBOOK.md §4.6]] — Flyway checksum 不匹配修复
- [[arthas-mcp-usage]] — Arthas MCP 反编译 mapper / watch getStats 实战
- [`../../CLAUDE.md` §Database Rules (Flyway)](../../CLAUDE.md) — Flyway 迁移安全修复迁移必须保留(仓库根)
- [`../../init-db/migrations/V20260619120000__Seed_Moderation_Data.sql`](../../init-db/migrations/V20260619120000__Seed_Moderation_Data.sql) — 种子数据迁移源
- 同目录其他 ops 文档:见 [[ops/README|./README.md]]
