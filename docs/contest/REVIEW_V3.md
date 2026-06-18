# Contest 模块最终定档 Code Review (v3)

**版本**: v3 (最终定档)
**日期**: 2026-06-17
**审查对象**: contest 模块**实际代码** —— `backend-spring/.../modules/contest/` + `console/src/views/contest/` + 相关测试与 migration
**审查方式**: 4 维度并行取证(① 数据/安全 ② 架构/并发/评分管线 ③ 前端/测试 ④ 运维/migration)+ 争议项人工复核
**v3 初始裁决（2026-06-17）**: 不建议合入 —— 需补齐核心评分功能后重新定档
**v3.1 状态（R1–R5 落地，2026-06-17）**: P0-1 ~ P0-5 全部已实施落地，详见 [EXECUTION_PLAN.md §"实施记录"](./EXECUTION_PLAN.md#实施记录)。本地 code review: [`.claude/reviews/contest-r1-r5-local-review.md`](../../.claude/reviews/contest-r1-r5-local-review.md) → **APPROVE**
**v3.2 状态（R6–R9 closure，2026-06-17）**: **模块 v4.2 完结**。49 PRD finding + 12 LOW + 6 F-SEC HIGH/CRITICAL + R8 review fixups 全部关闭或显式 deferred to R10。详见 §10–§12。

---

## 0. 本报告定位

> v1 / v2 审查的是 `PLAN.md` **修复计划文档**(裁决 REJECT-WITH-MAJOR-REVISIONS)。
> **v3 审查的是 recent commits 已落地后的实际代码**,是当前权威决策依据。
> 历史报告(REVIEW.md / REVIEW_V2.md / SECURITY_REVIEW.md / FINDINGS_RAW.md)作为证据链保留,其 finding 在代码中的实际去向见 §7 对照表。

---

## 1. 定档结论

> **本节保留 v3 初始裁决与判断依据,作为决策历史。** 当前最新状态见 §12 **v4.2 完结** 收口记录。

**v3 初始裁决（2026-06-17）: 不建议合入 —— 需补齐核心评分功能后重新定档。**

判断依据是 **PRD 自身定义的验收基线**(`docs/contest/PRD.md` §1.3):产品方把 contest 缺陷明列为 **6 CRITICAL(F-01~F-06)+ 12 HIGH**。核实当时代码,6 个 CRITICAL 中 **3 个明确未交付、1 个未真正生效**。一个竞赛平台若评分模式 / 罚时 / 是否计分这些核心配置全部失效,属于**主功能未完成**,而非边缘缺陷。

> 基础设施层(安全 / migration / 鉴权 / 性能 / 前端鉴权链)已达可合入水准;问题集中在**业务功能完整度**(评分规则生效、auto-finish 兜底、真榜隔离)。
>
> **v3.2 收口（2026-06-17, R6–R9 落地后）: 模块 v4.2 完结**。上述 5 项 P0 全部由 R1–R4 收口,基础设施层与业务功能层均达可合入水准。详见 §10–§12。

---

## 2. PRD 6 CRITICAL 当前状态(逐条核实)

| PRD CRITICAL | 当前状态 | 证据(代码现状) |
|---|---|---|
| **F-02 评分模式死代码** ⭐⭐⭐⭐ | ❌ 未修 | `ContestScoringServiceImpl.java:176` `int penalty = 20;` 硬编码;`scoringMode` / `penaltyPerWrong`(`Contest.java:51`,默认 null)**零消费**;评分代码无 ICPC/IOI 分支 |
| **F-04 WS 死代码** | ❌ 未接入 UI | `useContestSocket.ts` 的 `joinContest` 在 `console/src/views` **零调用**,实时推送链路未接通 |
| **F-05 autoFinish 无调度** | ⚠️ 部分 | `autoFinishVirtualParticipants()`(`ScoringServiceImpl:218`)**无 caller**;`ContestScheduler` `@Scheduled(fixedRate=10_000)` 只扫比赛本身 UPCOMING/RUNNING→FINISHED;但**在线用户**靠前端 `VirtualContestTimer.vue:79`→`/virtual/finish` 能结束 |
| **F-03 isRated 无视** | ⚠️ 未真正生效 | `isRated` 仅用于查询过滤 / VO 传参(`RankingServiceImpl:184`、`ContestServiceImpl:511/543/576`),**未在 `RatingCalculationService` 做 gate** |
| F-01 状态机互斥 | ✅ 已审计 | [F-01-STATE_MACHINE_AUDIT.md](./F-01-STATE_MACHINE_AUDIT.md) R6.2 落地；不变量 B.1 由 R6.5 generated column 收口；#5 `finishVirtualContest` + F-06 `timeFromStart` 复核动作见 F-01 §3.1 / §6.4 |
| F-06 提交不读虚拟时间 | ✅ 已修 | R6.2（`timeFromStart` 改用 `participant.startedAt` 虚拟赛 / `actualStartTime` 真实赛）；F-01 §6 同步审计 |

---

## 3. 必须修复(合入前,P0)

| # | 问题 | 影响 | 级别 | 修复方向 |
|---|---|---|---|---|
| **P0-1** | 评分配置完全不生效(F-02) | admin 配的 ICPC/IOI/penaltyPerWrong/isRated/tieBreaker 全部形同虚设,所有比赛按硬编码罚时跑 | 🔴 阻断 | `applyJudgeResult` 接入 `scoringMode` 分支(ICPC 罚时 / IOI 部分分);`penalty` 读 `getPenaltyPerWrong()`;评分前 gate `isRated`;排名按 `tieBreaker` 排序 |
| **P0-2** | auto-finish 兜底未接线 + 真实 participant 不转 FINISHED(F-05 派生) | 离线用户虚拟赛永不结束;真实赛结束后 participant 卡 STARTED,而 rating 靠 `findByContestIdAndStatus(...,"STARTED")` 取参赛者——**rating 依赖此 bug 才 work**,任何后续改动引爆 | 🔴 阻断 | `autoFinishVirtualParticipants` 加 `@Scheduled`;真实赛 `transitionToFinished` 批量 `STARTED→FINISHED` participant;rating 查询改为按 `contest.status=FINISHED` 而非 participant.status |
| **P0-3** | 真榜混入虚拟(F-12) | `selectParticipantsWithUserByContestId`(Mapper:270)/ `...Paginated`(305)`WHERE contest_id=?` **无 is_virtual 过滤**,虚拟赛选手出现在真实榜 | 🟠 必修 | 两 SQL 加 `AND cp.is_virtual = 0` |
| **P0-4** | startVirtual 并发原语缺失(RACE-01)+ 身份混淆(CRIT-5) | 连点"开虚拟赛"产生多条 STARTED 行(UUID 使唯一键失效);`findByContestIdAndUserId LIMIT 1`(Mapper:42)无 ORDER BY,真实+虚拟并存时随机返回 → `submitContestProblem` 身份混淆 | 🟠 必修 | 加 `(contest_id,user_id,is_virtual)` 唯一约束 + catch `DuplicateKeyException`;9 处旧查询改用已存在但零调用的 `findByContestIdAndUserIdAndVirtualSessionId`(Mapper:128)或加 `ORDER BY is_virtual` |
| **P0-5** | slug 无 UNIQUE + 改 title 丢 slug(F-09) | `contests.slug` 是 KEY 非 UNIQUE(`Create_All_Tables:310`),`generateSlug` 不查冲突(`ContestServiceImpl:701`),`updateContest` 改 title 覆盖原 slug,**无 redirect/history**,线上 URL 串号失效 | 🟠 必修 | 加 `uk_contests_slug`(先 dedup 历史)+ service 层冲突校验 |

**预估工期**:P0-1~P0-5 合计 **1.5~2 周**。

---

## 4. 可接受为技术债(合入后限期,P1)

- **CRIT-7 feature flag 缺失**:`FeatureFlagsProperties` 定义 `useNewContestSystem` 等旗杆但 contest 模块**零消费**,无 kill switch → 补 flag 接线,成本低。
- **可观测性**:日志覆盖良好(关键路径全 info/warn,带 contestId/userId),但**零 Micrometer 指标** → 补评分延迟 / DuplicateKey 次数 / auto-finish 触发数。
- **F-04 WS 接入 / IOI 完整实现**:若产品分阶段(先 ICPC + 非实时),可后置。
- **测试覆盖**:`VirtualContestTimer`(竞态回归)、`rankingStore`、`contest.schema` 有效;但 `ContestDetailView` / `Registration` / `ProblemList` / `ContestTimer` **零测试**,`useContestSocket` 13 用例多为存在性空壳 → 补详情页 mount 流。
- **virtual_session_id varchar(40)**(v2 CRIT-3 残留):当前 UUID(36 字符)兼容,未来若改方案需扩到 ≥64。

---

## 5. 已修复确认(基础设施已达可合入水准)

- **IDOR / 横向越权(v2 CRIT-9/10)彻底消除**:`userId` 强制来自 `SecurityContextHolder`(`SecurityUtil:24`),sessionId 用 `UUID.randomUUID()`(`SchedulerServiceImpl:181`)且**不作为身份令牌** —— 比原 review 建议的 HMAC+appSecret 方案更简洁、无密钥管理面。
- **migration 安全**:`V20260617120000__Contest_Scoring_Hardening.sql` 纯 additive(3 条 CREATE INDEX),无破坏性 DDL,无 checksum 风险。
- **Admin 鉴权 100%**:`AdminContestController` 10 端点全 `@PreAuthorize` + `@RateLimit`,service 层双重校验(`ContestServiceImpl:118`)。
- **性能**:`rating O(n²)→O(n)`(HashMap 预加载 `RatingCalculationServiceImpl:65-70`)、排行榜 N+1 消除(单 JOIN)、首杀原子(UK+DuplicateKey)、重复 AC 加分防护(UK)。
- **前端鉴权链干净**:纯 httpOnly cookie + CSRF(`useContestSocket.ts:197-203`);i18n 双语 190 key 100% 对齐;management 前端 API/视图/路由/测试全对齐。

---

## 6. 对 v2 关键分歧的裁决(虚拟赛结束链路)

并行取证阶段一度判"虚拟赛永不结束 = CRITICAL"。人工复核后**纠正为 HIGH/部分修复**:

- 虚拟赛结束**主路径是前端 timer 驱动**:`VirtualContestTimer.vue:79` → `stores/contest.ts:261` → `ContestController:596`(`/virtual/finish`)→ `SchedulerServiceImpl:217/238 setStatus(FINISHED)`。**在线用户能正常结束**。
- 真正缺失的是 `autoFinishVirtualParticipants` 的**离线兜底**(无 `@Scheduled` caller),以及真实赛结束后 participant 不批量 FINISHED 的派生隐患(P0-2)。
- 因此阻断合入的真正理由不是"死代码",而是 **F-02 评分核心功能未交付** + F-05 派生的 rating 脆弱性。

---

## 7. v2 → 代码现状 finding 对照(确保文档不偏差)

| v2 CRITICAL | 代码现状 | 说明 |
|---|---|---|
| CRIT-1 (DB-1 is_virtual NULL UPDATE) | ✅ 不适用 | 迁移从未写 `is_virtual=NULL` |
| CRIT-2 (DB-2 unique key 冲突) | 🟡 部分 | 应用层 catch `DuplicateKeyException`,但 NULL 多行无强约束 |
| CRIT-3 (DB-3 varchar(40)) | 🟡 未修(兼容) | UUID 36 字符暂兼容,定时炸弹 |
| CRIT-4 (DB-4 slug dedupe) | ❌ 未修 → P0-5 | slug 无 UNIQUE / redirect |
| CRIT-5 (F-ARCH-01 调用点) | 🟡 部分 → P0-4 | 9 处仍走旧方法,新方法零调用 |
| CRIT-6 (F-ARCH-07 migration) | ✅ 不适用 (ADR-011) | hardening migration 解耦 | 隐式灰度由 ADR-006 §2.4 覆盖；R8.5 决策不引独立 flag |
| CRIT-7 (OPS-01 feature flag) | ❌ 未修 → P1 | 旗杆零消费 |
| CRIT-8 (FE-02 BroadcastChannel) | ✅ 不适用 | 根本未用 BroadcastChannel,走 STOMP topic 天然隔离 |
| CRIT-9 (F-SEC-01 IDOR) | ✅ 已修 | UUID + 认证态 userId |
| CRIT-10 (F-SEC-02 appSecret) | ✅ 已修 | 实际用 UUID,无 HMAC/appSecret |

---

## 8. 文档历史

| 版本 | 日期 | 审查对象 / 阶段 | 裁决 |
|---|---|---|---|
| v1 | 2026-06-17 | PLAN.md(6 视角) | REJECT |
| v2 | 2026-06-17 | PLAN.md(7 视角含 Security) | REJECT-WITH-MAJOR-REVISIONS |
| v3 | 2026-06-17 | 实际代码(4 维度 + 复核) | 不建议合入 —— 补齐 P0 后重新定档 |
| v3.1 | 2026-06-17 | R1–R5 落地后复审 | P0-1~P0-5 全部已实施；本地 review APPROVE |
| v3.2 | 2026-06-17 | R6–R9 收口 | **模块 v4.2 完结**（详见 §10–§12）|

---

## 9. 重新定档 checklist

- [x] P0-1 评分配置生效(scoringMode / penaltyPerWrong / isRated / tieBreaker) — **R4** (ADR-006 Accepted)
- [x] P0-2 auto-finish 兜底接线 + 真实 participant 批量 FINISHED + rating 查询改 contest.status — **R3** (ADR-007 Accepted)
- [x] P0-3 真榜 SQL 加 is_virtual=0 — **R2**
- [x] P0-4 startVirtual 并发原语 + 9 处查询统一 — **R3**
- [x] P0-5 slug UNIQUE + 冲突校验 — **R1**
- [x] F-01 状态机互斥 — **R6.2** 审计 doc (`F-01-STATE_MACHINE_AUDIT.md`) + finishVirtualContest 改走 bulkFinishByIds；剩余不变量 B.1 由 R6.5 generated column 收口
- [x] F-03 isRated 守卫 — **R6.1** + ADR-009
- [x] F-04 WS 接入 — **R6.4**（ContestRankingsView 接入 useContestSocket）+ ADR-008
- [x] F-06 提交读虚拟时间 — **R6.2**（`timeFromStart` 改用 `participant.startedAt` 虚拟赛 / `actualStartTime` 真实赛）
- [x] F-07 服务端时间窗 — **R6.2**（虚拟赛 `started_at + duration_minutes` 硬截止，409 CONTEST_ENDED）
- [x] F-08 成就污染 — **R6.3**（`findIsVirtualBySubmissionId` gate）
- [x] F-10 finishVirtual 不重算 — **R6.1** + ADR-007 §7 / ADR-009
- [x] F-13 visibilitychange — **R6.4**（VirtualContestTimer）
- [x] F-15 TS enum 错配 — **R6.5**（文档化 + 注释兜底；完整 enum 化留 S5）
- [x] F-17 WS join 鉴权 — **R6.4**（ContestSubscribeAuthInterceptor）+ ADR-008
- [x] F-18 WS 卸载清理 — **R6.4**（onUnmounted cleanup）+ ADR-008
- [x] 重新跑后端 `./mvnw test` + 前端 `pnpm type-check` — 本轮 5/5 RatingCalculationServiceImplTest + 12/12 ContestScoringServiceImplTest + 编译通过

**R1-R6 全部完成。Sprint S1-S4 全部签收。**

---

## 10. v3.3 / R7 收口（2026-06-17）

R7 多轮执行（详见 [_archive/EXECUTION_PLAN_R7](./_archive/EXECUTION_PLAN_R7_2026-06-17.md)）落地 7 项：

- **R7.2** F-22 audit doc 明确"无 violation"+ F-31 启动时 sweep
- **R7.3** F-29 指数退避 + F-43 rejected 事件通道
- **R7.4** F-15 跨端枚举对齐（前端 `isInVirtualContest` 改用 `STARTED` 兜底）
- **R7.5** ADR-010 决策记录（F-35/F-38/F-50-52）
- **R7.6** ADR-007 §8 评估记录（F-24 keyset / F-27 限流 / CRIT-6 shadow 模式 → 留 R8）
- **R7.1** `evictRankingCacheForContest(contestId)` 占位 API（per-contest eviction 留 R8；NFR-P1 在 < 10k 行分页区间不触发）

**R7 收口后**：49 finding 全部关闭或显式 deferred。PRD Sprint S1-S8 全部签收。

模块状态：**v4.0 "完结"**。后续 R8 候选列表（按优先级）：
1. R7.6 延期的 F-24 keyset 分页（仅 10k+ 行分页场景触发）
2. R7.6 延期的 F-27 限流 key 加 contestId 维度（需 RateLimitAspect SpEL 支持）
3. R7.1 延期的 per-contest 排行榜 evict
4. R7.4 延期的 F-32 ContestTimer 组件统一 + F-28/30/39/40/41/46 UX 一致性（Sprint S8 集群）
5. R7.6 延期的 CRIT-6 shadow 模式（runtime + ops 配合）
6. R5/R6/R7 累计 12 项 LOW 收口

---

## 11. v3.3 / R8 收口（2026-06-17）

R8 落地（详见 [_archive/EXECUTION_PLAN_R8](./_archive/EXECUTION_PLAN_R8_2026-06-17.md)）：

- **R8.1–R8.4** 评审发现 HIGH-1/HIGH-2/HIGH-3 + MED-1/2 修复（selectParticipantsKeyset NULL bug、ContestRankingsView WS 接入、ContestScoringServiceImpl 真实 participant 边界、autofinish 不重算虚拟）
- **R8.5** ADR-011 决策：CRIT-6 灰度由 ADR-006 §2.4 覆盖，**不引独立 flag**（隐式灰度，避免运维摩擦）
- **R8.6** MyContests 虚拟赛 tab + 12 LOW 收口（`LOW_REMAINING.md` F-35~F-47 全 ✅）

**R8 收口后**：HIGH 全部 FIXED；MED 中 M1 跨模块 deferred（独立 PR）；LOW L3 设计选择 deferred。Sprint S1-S8 全部签收。

---

## 12. v3.4 / R9 收口 — 模块 v4.2 完结（2026-06-17）

R9 落地（详见 [_archive/EXECUTION_PLAN_R9](./_archive/EXECUTION_PLAN_R9_2026-06-17.md)）：

- **R9.1** F-24 keyset 分页重设计（`getContestRanking(contestId, limit, cursor)`，cursor 格式 `rank:userId`）
- **R9.2** per-contest evict 占位 API 落地（`evictRankingCacheForContest(contestId)`；**真 per-contest eviction** → **R10**）
- **R9.3** i18n 接入 —— R9_PLACEHOLDER.ts 删除，en-US/zh-CN `contest.ts` 写回 keys；**view 模板接线 + i18n key 同步审计** → **R10**
- **R9.4** F-46 multi-tab 检测（localStorage 跨标签广播 + 30s stale 释放；后端 R3.3 FOR UPDATE 是最后一道防线）

**R9 收口后**：

| 维度 | 状态 |
|---|---|
| 49 PRD finding（F-01~F-49）| 全部关闭或显式 deferred |
| 12 LOW（F-35~F-47）| 全部关闭 |
| 6 F-SEC HIGH/CRITICAL（F-SEC-01~06）| 全部关闭 |
| R7/R8 review fixups | 全部关闭或显式 deferred |
| **模块裁决** | **v4.2 完结** |

### R10 实际收口（2026-06-18 验证后）

完整计划：[_archive/EXECUTION_PLAN_R10](./_archive/EXECUTION_PLAN_R10_2026-06-18.md)

> **R10 计划 9 项中 4 项 plan 误判**（基于 R9 文档推断而未做代码侧验证）。R10 实际完成度：5/9 ✅ + 4/9 plan 误判 + **0 行代码改动**。模块 v4.3 收口**不成立**；v4.2 保持为权威裁决。

| ID | 2026-06-18 状态 | 备注 |
|---|---|---|
| **R10.1.1** | ✅ R9.1 已落地 | `ContestServiceImpl.java:398` `@Cacheable` key 已含 `#contestId` |
| **R10.1.2** | ⚠️ DEFERRED | per-contest evict 接受为低优先（`< 10k` 行可接受）|
| **R10.2** | ⚠️ plan 误判 | R9 阶段已用业务命名空间完成 i18n；9 个 key 是死键 |
| **R10.3** | ✅ 0 漂移 | [I18N_AUDIT_R10.md](./I18N_AUDIT_R10.md) |
| **R10.4** | ⚠️ ABORTED | `getGlobalRankingsPaginated` 与 `getContestRanking` 是两个独立功能（全局 vs 单场），非同 API 旧/新版本 |
| **R10.5** | ⚠️ plan 误判 / DEFERRED | denormalize 引入 cascade 写放大 + R6.2/F-06 双轨时钟对账风险；推荐改用单 SQL JOIN 方案（独立 PR） |
| **R10.6/10.7** | ✅ F-01 销项 | doc-only 落地，详见下方 |
| **R10.8/10.9** | ✅ F-SEC-10/13 收口 | doc-only 落地（`init-db/README.md` / `docs/PRIVACY.md`） |
| **MED-3** | ⏸ 留 R10.x 续轮 | `WebSocketAuthenticationException` 顶层化，cosmetic 重构 |

### F-01 状态机（已 R10 销项）

- ✅ **R10.6** `finishVirtualContest` 复核：`ContestSchedulerServiceImpl.finishVirtualContest:251-255` 走 `bulkFinishByIds`，无 violation
- ✅ **R10.7** F-06 `timeFromStart` 复核：`SubmissionServiceImpl.recordContestSubmissionIfNeeded:1387-1395` 三元分支正确（虚拟用 `p.getStartedAt()`，真实用 `contest.getActualStartTime()`），无 violation
- 详见 [F-01-STATE_MACHINE_AUDIT §3.1/§6.4](./F-01-STATE_MACHINE_AUDIT.md) R10 复核结果 + [_archive/EXECUTION_PLAN_R10](./_archive/EXECUTION_PLAN_R10_2026-06-18.md)

### SECURITY_REVIEW 残留

- ✅ **F-SEC-10** MEDIUM Flyway 迁移期间 admin/用户操作无锁 —— **R10.8 已关单**（[init-db/README.md Migration Operational Checklist](../../init-db/README.md#migration-operational-checklist)）
- 🟡 **F-SEC-12** LOW 反作弊钩子（开卷/刷题/账号多开）未设计 —— **业务决策项**，等产品定义反作弊需求后单独立项
- ✅ **F-SEC-13** LOW 虚拟赛结束无审计 / log retention 不清 —— **R10.9 已关单**（[docs/PRIVACY.md Log Retention 表](../PRIVACY.md#log-retention)）
- 🟡 **F-SEC-14** INFO 计划本身偏功能性，安全章节 0 字节 —— **文档结构问题**，建议 EXECUTION_PLAN.md 后续版本加 §"Security Requirements" 章节（OWASP API Top 10 逐条对应），不在 R10 范围

> **R10 收口后 SECURITY 残留 4 项中 2 项已关单**（F-SEC-10/13），2 项保留为业务/文档决策（F-SEC-12/14）。F-SEC-14 留作后续 PLAN 改版时一并处理。

### F-22 业务决策项

- "全局单活跃"约束（同一用户同一时刻只能开一个虚拟赛）跨 contest 唯一索引 —— 当前不实施，等产品/业务方共同决定（详见 [F-22-VIRTUAL-SESSION-CROSS-CONTEST-AUDIT.md §5](./F-22-VIRTUAL-SESSION-CROSS-CONTEST-AUDIT.md)）
