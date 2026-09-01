> ⚠️ **历史证据（保留原样）**：当前权威定档见 **[REVIEW_V3.md](../REVIEW_V3.md)**（审查实际代码）。本文档为 v1/v2 审查的原始 finding 清单，finding 在代码中的实际去向见 [V3 §7](../REVIEW_V3.md)。

# Contest 修复计划审查 — 原始 Finding 列表

**计划文件**：`docs/CONTEST_AND_VIRTUAL_CONTEST_FIX_PLAN.md`
**审查日期**：2026-06-17
**总 finding 数**：79
**验证状态**：confirmed=0/9(v1/v2) | partial=9 | refuted=0

## 验证状态图例

- **CONFIRMED** (✅)：两名审查者结论一致
- **PARTIALLY_CONFIRMED** (🟡)：一名确认一名反驳，需人工复核
- **REFUTED** (❌)：被审查者证据反驳
- **(v2)**：仅 v2 审查者确认（v1 部分支持/反驳）

## 严重度图例

- **CRITICAL** (🔴)：生产 100% 故障 / 数据丢失 / 安全漏洞
- **HIGH** (🟠)：线上事故 / 性能严重退化 / UX 灾难
- **MEDIUM** (🟡)：个别用户问题 / 技术债累积
- **LOW** (🟢)：代码质量 / 最佳实践
- **INFO** (⚪)：术语 / 分类 / 文档

## 完整 Finding 表格（按 ID 排序）

| ID | 严重度 | 状态 | 主题 | 位置 |
|----|--------|------|------|------|
| F-ARCH-01 | CRITICAL | 🟡 v2 强 v1 (升 CRIT-5) | `findByContestIdAndUserId` 改造范围漏 4 处裸调用点 | Phase 1 §1.4 / §5.1 / §9.1 |
| F-ARCH-02 | HIGH | v1 反驳 / v2 部分确认 | virtualSessionId HMAC 化方案忽略 appSecret 轮换/冷启动/admin 逆查 | Phase 1 §1.3 / §2.2 / §7.1 |
| F-ARCH-03 | HIGH | v1 部分确认 / v2 确认 | `ContestScoreCalculator` 输入输出契约未定义 | Phase 3 §3.2 vs Phase 4 §4.1 / §4.7 |
| F-ARCH-04 | HIGH | 两名分歧（v1 部分 / v2 确认） | ScoringService/RankingService/RatingService 职责重叠 | Phase 4 §4.6 / Phase 5 §5.4 |
| F-ARCH-05 | HIGH | 两名分歧 | WS 实时方案 fallback 含糊，与 cache 设计冲突 | Phase 4 §4.4 / Phase 6 §6.3-6.4 |
| F-ARCH-06 | HIGH | 两名分歧 | cache 拆分缺 submission-level/first-solve-level 失效 | Phase 5 §5.4 / Phase 10 第 5 周 |
| F-ARCH-07 | CRITICAL | 🟡 v1 反驳 / v2 部分确认 (升 CRIT-6) | Migration 顺序在并发 dev 用户下不安全，缺 dual-write | Phase 0 §0 / Phase 1 §1.1-1.2 / §9.2 |
| F-ARCH-08 | MEDIUM | 两名分歧 | 第 5/6 步中间态脏状态 | Phase 2 §2.4 / §6 第 5-6 步 |
| F-ARCH-09 | MEDIUM | 两名分歧 | 5 周排期低估 Phase 0/4/5 工作量，无 buffer | Phase 10 §10 排期表 |
| F-ARCH-10 | MEDIUM | 两名分歧 | X-Virtual-Session-Id 引入 CSRF 复杂度 | Phase 1 §1.3 vs Phase 2 §2.2 vs Phase 7 §7.1 |
| F-ARCH-11 | LOW | 两名分歧 | 多端/多租户边界未设计 | Phase 1 §1.3 |
| F-ARCH-12 | MEDIUM | 两名分歧 | Phase 8 封榜与 Phase 5 排行榜二次返工 | Phase 5 §5.1-5.3 vs Phase 8 §8.3 |
| F-ARCH-13 | INFO | 两名分歧 | §0"交易边界"用词不当 | §0 结论 |
| F-ARCH-14 | LOW | 两名分歧 | 重打语义未明（同一用户同一 FINISHED 比赛可重打？） | Phase 1 §1.2 / Phase 3 §3.3 / §7.4 |
| F-ARCH-15 | INFO | 两名分歧 | §11 切片与 §6 步骤表矛盾 | §11 vs §6 步骤表 |
| DB-1 | CRITICAL | 🟡 v2 强 v1 (升 CRIT-1) | `is_virtual IS NULL` 反向填充针对零行 | Phase 1.2: "前置要求" |
| DB-2 | CRITICAL | 🟡 v2 强 v1 (升 CRIT-2) | 现有 unique key 与新 unique key 语义冲突 | Phase 1.2: `UNIQUE KEY uk_contest_user_virtual` |
| DB-3 | CRITICAL | 🟡 v2 强 v1 (升 CRIT-3) | HMAC-SHA256 64 字符写进 `varchar(40)` 列 | Phase 1.3: `HMAC-SHA256` |
| DB-4 | CRITICAL | 🟡 v2 强 v1 (升 CRIT-4) | Slug dedupe 静默破坏线上公共 URL | Phase 1.1: `slug-duplicate-{id后缀}` |
| DB-5 | HIGH | v1 反驳 / v2 部分确认 | Migration 锁 `contest_participants` 整个 dedup 窗口 | Phase 1.1, 1.2, 5.4 |
| DB-6 | HIGH | 两名分歧 | `contest.duration_minutes` 可变破坏 in-flight 虚拟赛 | Phase 2.1, 2.5, 4.3 |
| DB-7 | HIGH | 两名分歧 | `penaltyPerWrong` 读 contests + cache 分区，admin 改值致非确定失序 | Phase 4.3 + Phase 5.4 |
| DB-8 | HIGH | 两名分歧 | Flyway transaction + dedup UPDATE 无 rollback 故事 | Phase 1.1, 1.2 combined |
| DB-9 | MEDIUM | 两名分歧 | 清重复虚拟行缺真实 dedup | Phase 1.2: `清理重复虚拟行` |
| DB-10 | MEDIUM | 两名分歧 | Production rollback 无 inverse mapping | Phase 1.1, 1.2, 1.3 |
| DB-11 | MEDIUM | 两名分歧 | 时区不一致 `datetime(3)` 容器 vs JVM | Phase 4.2 `服务端是时间权威` |
| DB-12 | MEDIUM | 两名分歧 | `first_solve_records` 无 `is_virtual` 列 | Phase 4.7: `不触发真实 first solve` |
| DB-13 | MEDIUM | 两名分歧 | `contest_rankings` 已有 unique 未交叉验证 | Phase 1.2 vs Phase 5.1 |
| DB-14 | LOW | 两名分歧 | 项目 logical-delete 约定 `is_deleted` 未用 | Phase 1.1 |
| RACE-01 | HIGH | 两名分歧 (v2 升 HIGH-2) | `startVirtualContest` HMAC 幂等化缺真正并发原语 | Phase 1.3 / 1.4 / 6.4 commit 4 |
| RACE-02 | HIGH | 两名分歧 (v2 升 HIGH-3) | scheduler/手动 finish/submit 链路三写者 lost-update | Phase 2.5 / 3.1 / 3.2 / 3.3 / 4.1 |
| RACE-03 | MEDIUM | 两名分歧 | `@TransactionalEventListener(AFTER_COMMIT)` 失败不可回滚 | Phase 1.3 / 1.5 / 4.1 / 4.7 |
| RACE-04 | MEDIUM | 两名分歧 (v2 升 HIGH-10) | cache stampede 未解决，1k AC/min 下 5k ops/min 退化 | Phase 4.1 / 4.4 / 5.4 / 6.2 / 6.4 |
| RACE-05 | HIGH | 两名分歧 (v2 升 HIGH-4) | rating 系统虚拟/真实隔离不完整 | Phase 1.2 / 3.1 / 4.1 / 4.6 / 4.7 / 9.4 |
| RACE-06 | MEDIUM | 两名分歧 | penalty 计算 AC/WA 倒置到达 + 重复到达 | Phase 4.1 / 4.2 / 4.3 / 4.7 |
| RACE-07 | MEDIUM | 两名分歧 | multi-backend scheduler + WS broadcast 缺互斥 | Phase 3.1 / 3.3 / 4.6 / 6.3 / 7.1 |
| RACE-08 | LOW | 两名分歧 | BroadcastChannel + sessionStorage 跨 tab BFCache 漏消息 | Phase 4.4 / 6.2 / 6.3 / 6.4 / 7.1 / 7.2 / 9.5 |
| FE-01 | HIGH | v1 反驳 / v2 确认 (升 HIGH-17) | sessionStorage 5MB 配额 + ITP 7 天清空 | Phase 7.1 / 7.2 / Section 3.3 |
| FE-02 | CRITICAL | 🟡 v2 强 v1 (升 CRIT-8) | BroadcastChannel('virtual-contest') 命名空间 + payload schema 缺陷 | Phase 7.2 lines 991-1017 |
| FE-03 | CRITICAL | 🟡 v2 强 v1 | 倒计时容错：时钟漂移/serverNow 缺失/iOS 后台冻结 | Phase 7.3 lines 1018-1050 |
| FE-04 | HIGH | 两名分歧 (v2 升 HIGH-18) | 亚秒级计时 + setInterval 1000ms，最后一秒提交竞态 | Phase 6.4 / 7.3 / 2.2 |
| FE-05 | HIGH | 两名分歧 (v2 升 HIGH-18-class) | 状态枚举大写统一未考虑渐进升级期 | Phase 7.4 lines 1052-1074 |
| FE-06 | MEDIUM | 两名分歧 | WS join 权限未设计 ban/cancel/双 tab | Phase 6.1 / 6.4 |
| FE-07 | MEDIUM | 两名分歧 | i18n + a11y 倒计时/状态色盲/键盘焦点/aria-live 全遗漏 | Plan 全文 / Phase 7 / Phase 11 |
| FE-08 | MEDIUM | 两名分歧 | 网络断/弱网/5xx/504 serverNow 拉不到 UX 空白 | Phase 3.3 / 4.2 / 7.3 |
| FE-09 | LOW | 两名分歧 | BroadcastChannel 浏览器兼容 (Safari<15.4, iOS 隐私) | Phase 7.2 / Phase 18 |
| FE-10 | LOW | 两名分歧 | serverNow 时区/序列化歧义 | Phase 7.3 props / Phase 4.2 |
| PM-01 | HIGH | 两名分歧 (v2 升 HIGH-19) | Editorial/Clarification/封榜 压到 Phase 8 违反用户预期 | Phase 8, 8.1-8.3 |
| PM-02 | HIGH | 两名分歧 (v2 升 HIGH-20) | 封榜只定义后端规则，无 UI 规范 | Phase 8.3 |
| PM-03 | HIGH | 两名分歧 (v2 升 HIGH-21) | 虚拟榜 endpoint 缺前端入口设计 | Phase 5.2, Tiny Commit #14 |
| PM-04 | HIGH | 两名分歧 (v2 升 HIGH-22) | 首次虚拟赛用户 onboarding 缺失 | Phase 7, 3.3 |
| PM-05 | MEDIUM | 两名分歧 | 虚拟赛"零成就"一刀切误伤学习激励 | 默认产品决策表 |
| PM-06 | MEDIUM | 两名分歧 | 虚拟赛发现路径完全缺失 | 3.3 流程 |
| PM-07 | HIGH | 两名分歧 | 虚拟赛取消/contest 下架流程未设计 | 默认决策表, 9.1 风险 |
| PM-08 | MEDIUM | 两名分歧 | 分享/挑战朋友 功能缺失 | 全计划, 8.x 之后 |
| PM-09 | MEDIUM | 两名分歧 | 通知路径模糊，WS 推送如何落地到通知中心 | Phase 6.3, 3.3 |
| PM-10 | HIGH | 两名分歧 (v2 升 HIGH-23) | 反作弊/开卷策略完全空白 | 3.3 流程, 8.x 后 |
| PM-11 | MEDIUM | 两名分歧 | 虚拟榜隐私范围未定义 | Phase 5.2 权限段落 |
| PM-12 | MEDIUM | 两名分歧 | 虚拟赛 session 数据生命周期/清理策略缺失 | 全计划, 9.x 风险 |
| PM-13 | MEDIUM | 两名分歧 | 移动端体验完全没考虑 | Phase 7, 全计划 |
| PM-14 | MEDIUM | 两名分歧 | 历史虚拟赛数据可发现性缺失 | 3.3 流程, 5.2 端点 |
| PM-15 | HIGH | 两名分歧 | 网络断 5 分钟失败模式无 UX 兜底 | Phase 7, 9.x 风险 |
| PM-16 | MEDIUM | 两名分歧 | 新用户教育"虚拟赛是什么"无 onboarding | Phase 7.1 |
| OPS-01 | CRITICAL | 🟡 v2 强 v1 (升 CRIT-7) | 零 feature flag 闸门，5 周全量铺开无独立回滚 | §10 排期 + 整体 |
| OPS-02 | HIGH | 两名分歧 (v2 升 HIGH-12) | 22 commit 中间态在生产会爆炸，缺 stacked-merge | §6 实施顺序 22 步 |
| OPS-03 | HIGH | 两名分歧 (v2 升 HIGH-13) | 改 virtualSessionId 生成方式致 in-flight 虚拟 session 失联 | §1.3 + 1.2 + 7.1 |
| OPS-04 | HIGH | 两名分歧 (v2 升 HIGH-14) | 全计划零可观测性 | 整体缺失 |
| OPS-05 | HIGH | 两名分歧 (v2 升 HIGH-15) | "1000 AC/min"性能目标无压测基线/延迟预算/容量上限 | 第 5 周 + Phase 5 + 6 + 8.1 |
| OPS-06 | HIGH | 两名分歧 (v2 升 HIGH-16) | ICPC/IOI 评分核心改动无回滚路径 | Phase 4.1-4.7 |
| OPS-07 | MEDIUM | 两名分歧 | 虚拟 participant 行无限累积 | §1.2, 2, 5.4 |
| OPS-08 | MEDIUM | 两名分歧 | MySQL 备份窗口与 in-flight 虚拟 session 状态恢复无保障 | Phase 1 + 3 + 整体 |
| OPS-09 | MEDIUM | 两名分歧 | 测试覆盖无硬指标 | §7 测试计划 |
| OPS-10 | MEDIUM | 两名分歧 | 无 changelog/客服 runbook | §1.1, 8, 整体 |
| OPS-11 | MEDIUM | 两名分歧 | 无 dev/staging/prod 分层 | 整体 |
| OPS-12 | MEDIUM | 两名分歧 | 无正式 runbook/onboarding/postmortem 模板 | 整体 + 11 节后 |
| OPS-13 | LOW | 两名分歧 | staging 数据来源与 dedup 一致性未规划 | §1.1 + 缺失 staging 策略 |
| OPS-14 | LOW | 两名分歧 | on-call 培训与告警含义同步缺失 | 整体 + 缺失 on-call |
| OPS-15 | LOW | 两名分歧 | Flyway/MySQL/Redis 版本与 lock 行为未做版本兼容性测试 | 整体 + Phase 1 + 3 |
| OPS-16 | LOW | 两名分歧 | 网络分区/PM2 单点/scheduler 切换无 HA 设计 | Phase 3 + 6 + 整体 |

## 按严重度 + 状态汇总

### CRITICAL (8) — v2 强 v1
- F-ARCH-01, F-ARCH-07, DB-1, DB-2, DB-3, DB-4, FE-02, FE-03, OPS-01

注：FE-03 在 v1 是 PARTIAL/部分确认，v2 升 CRITICAL — 见主报告 PARTIAL 表

### HIGH (23) — 全部 v1 反驳 / v2 升 HIGH
- F-ARCH-02, F-ARCH-03, F-ARCH-04, F-ARCH-05, F-ARCH-06
- DB-5, DB-6, DB-7, DB-8
- RACE-01, RACE-02, RACE-04, RACE-05
- FE-01, FE-04, FE-05
- PM-01, PM-02, PM-03, PM-04, PM-07, PM-10, PM-15
- OPS-02, OPS-03, OPS-04, OPS-05, OPS-06

### MEDIUM (~25)
- F-ARCH-08, F-ARCH-09, F-ARCH-10, F-ARCH-12
- DB-9, DB-10, DB-11, DB-12, DB-13
- RACE-03, RACE-06, RACE-07
- FE-06, FE-07, FE-08
- PM-05, PM-06, PM-08, PM-09, PM-11, PM-12, PM-13, PM-14, PM-16
- OPS-07, OPS-08, OPS-09, OPS-10, OPS-11, OPS-12

### LOW (~10)
- F-ARCH-11, F-ARCH-14
- DB-14
- RACE-08
- FE-09, FE-10
- OPS-13, OPS-14, OPS-15, OPS-16

### INFO (3)
- F-ARCH-13, F-ARCH-15

## 关键交叉引用

### v1 vs v2 立场差异最大的 finding
- **OPS-01 (CRITICAL)**：v1 REFUTED（22 commit 已隔离）→ v2 CONFIRMED（无 flag）→ **采纳 v2**
- **FE-02 (CRITICAL)**：v1 REFUTED（命名冲突是臆想）→ v2 CONFIRMED → **采纳 v2**
- **F-ARCH-07 (CRITICAL)**：v1 REFUTED → v2 PARTIAL → **采纳 v2 部分**

### DB-* finding 高置信度
- DB-1, DB-2, DB-3, DB-4 在 v2 全部强 v1 升 CRITICAL
- 主要证据基于 init-db/migrations/V20260602_120000__Create_All_Tables.sql 实际 schema

### 完整 finding 标题列表（用于全文搜索）

#### CRITICAL
- F-ARCH-01: findByContestIdAndUserId 替换审计未覆盖 ContestServiceImpl 三处调用点
- F-ARCH-07: Migration 顺序"schema → app code → feature enable"在并发 dev 用户下不安全
- DB-1: Plan fabricates a NULL history that does not exist
- DB-2: Plan collides with an existing UNIQUE on (contest_id, user_id, virtual_session_id)
- DB-3: HMAC-SHA256 hex (64 chars) silently truncates into `virtual_session_id varchar(40)`
- DB-4: Slug dedup strategy is undefined for live contests with bookmarked URLs
- FE-02: BroadcastChannel 通道名"virtual-contest"是全局命名空间
- FE-03: 倒计时组件统一只画"每次回前台都重新计算"和 visibilitychange/focus/reconnect 三事件
- OPS-01: 无 feature flag 闸门，5 周全量铺开无法独立回滚

#### HIGH（关键部分）
- F-ARCH-02: virtualSessionId HMAC 化方案忽略了 appSecret 轮换、冷启动一致性和 admin 视角查询
- F-ARCH-03: Phase 4.1 ContestScoreCalculator 抽取的输入输出契约未定义
- F-ARCH-04: ScoringService / RankingService / RatingService 三者职责重叠
- F-ARCH-05: WS 实时方案对 WS 断连场景的 fallback 描述含糊
- F-ARCH-06: cache key 拆分方案缺失 submission-level / first-solve-level 失效
- DB-5: Migration 锁 contest_participants 整个 dedup 窗口
- DB-6: contest.duration_minutes is mutable while virtual contests are active
- DB-7: penaltyPerWrong reads from contests per submission event, but Phase 5.4 partitions cache
- DB-8: Flyway transaction model + dedup UPDATE: if dedup fails partway
- RACE-01: startVirtualContest HMAC 幂等化方案未指定真正的并发原语
- RACE-02: auto-finish scheduler 与用户手动 finishVirtualContest 在同一 participant 行上存在 lost-update
- RACE-05: rating 系统对虚拟 AC 的隔离不完整
- FE-01: sessionStorage 容量边界 + 隐私模式降级未设计
- FE-04: 亚秒级计时精度 + setInterval 1000ms
- FE-05: 状态枚举统一阶段"API 层大写,前端类型也统一大写"未考虑渐进升级期
- PM-01: Editorial 与 Clarification 被压到 Phase 8
- PM-02: 封榜(freeze)只定义了后端规则,完全没有 UI 规范
- PM-03: 虚拟榜 endpoint 缺失前端入口设计
- PM-04: 首次虚拟赛用户 onboarding 缺失
- PM-07: 虚拟赛取消/比赛下架流程未设计
- PM-10: 反作弊/开卷策略完全空白
- PM-15: 网络断 5 分钟的失败模式没有 UX 兜底
- OPS-02: 22 个 commit 顺序的中间态在生产会爆炸
- OPS-03: Phase 1 改 virtualSessionId 生成方式会致 in-flight 虚拟 session 失联
- OPS-04: 全计划零可观测性设计
- OPS-05: "1000 AC/min"性能目标无压测基线
- OPS-06: ICPC/IOI 评分核心逻辑改动无回滚路径

---

## 数据来源

- 计划文件：`docs/CONTEST_AND_VIRTUAL_CONTEST_FIX_PLAN.md` (1601 行)
- 数据库迁移：`init-db/migrations/V20260602_120000__Create_All_Tables.sql` (line 151, 157, 164, 223, 261, 279, 281, 303, 339)
- 后端代码：`backend-spring/src/main/java/com/ulticode/modules/contest/`
  - `ContestServiceImpl.java` (line 252, 359, 438, 478)
  - `ContestSchedulerServiceImpl.java` (line 82, 111, 168, 190, 218, 260, 267)
  - `ContestScoringServiceImpl.java` (line 63-202, 261, 267)
  - `SubmissionServiceImpl.java` (line 1360)
- 前端代码：`console/src/views/contest/` + `management/src/views/contest/`
- Feature Flag：`backend-spring/.../common/config/FeatureFlagsProperties.java`
- 项目文档：`docs/SECURITY_REVIEW_2026-06-06.md` + `docs/SECURITY_REMEDIATION_RUNBOOK_2026-06-06.md`
