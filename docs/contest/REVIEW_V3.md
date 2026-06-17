# Contest 模块最终定档 Code Review (v3)

**版本**: v3 (最终定档)
**日期**: 2026-06-17
**审查对象**: contest 模块**实际代码** —— `backend-spring/.../modules/contest/` + `console/src/views/contest/` + 相关测试与 migration
**审查方式**: 4 维度并行取证(① 数据/安全 ② 架构/并发/评分管线 ③ 前端/测试 ④ 运维/migration)+ 争议项人工复核
**最终裁决**: **不建议合入 —— 需补齐核心评分功能后重新定档**

---

## 0. 本报告定位

> v1 / v2 审查的是 `PLAN.md` **修复计划文档**(裁决 REJECT-WITH-MAJOR-REVISIONS)。
> **v3 审查的是 recent commits 已落地后的实际代码**,是当前权威决策依据。
> 历史报告(REVIEW.md / REVIEW_V2.md / SECURITY_REVIEW.md / FINDINGS_RAW.md)作为证据链保留,其 finding 在代码中的实际去向见 §7 对照表。

---

## 1. 定档结论

**不建议合入 —— 需补齐核心评分功能后重新定档。**

判断依据是 **PRD 自身定义的验收基线**(`docs/contest/PRD.md` §1.3):产品方把 contest 缺陷明列为 **6 CRITICAL(F-01~F-06)+ 12 HIGH**。核实当前代码,6 个 CRITICAL 中 **3 个明确未交付、1 个未真正生效**。一个竞赛平台若评分模式 / 罚时 / 是否计分这些核心配置全部失效,属于**主功能未完成**,而非边缘缺陷。

> 基础设施层(安全 / migration / 鉴权 / 性能 / 前端鉴权链)已达可合入水准;问题集中在**业务功能完整度**(评分规则生效、auto-finish 兜底、真榜隔离)。

---

## 2. PRD 6 CRITICAL 当前状态(逐条核实)

| PRD CRITICAL | 当前状态 | 证据(代码现状) |
|---|---|---|
| **F-02 评分模式死代码** ⭐⭐⭐⭐ | ❌ 未修 | `ContestScoringServiceImpl.java:176` `int penalty = 20;` 硬编码;`scoringMode` / `penaltyPerWrong`(`Contest.java:51`,默认 null)**零消费**;评分代码无 ICPC/IOI 分支 |
| **F-04 WS 死代码** | ❌ 未接入 UI | `useContestSocket.ts` 的 `joinContest` 在 `console/src/views` **零调用**,实时推送链路未接通 |
| **F-05 autoFinish 无调度** | ⚠️ 部分 | `autoFinishVirtualParticipants()`(`ScoringServiceImpl:218`)**无 caller**;`ContestScheduler` `@Scheduled(fixedRate=10_000)` 只扫比赛本身 UPCOMING/RUNNING→FINISHED;但**在线用户**靠前端 `VirtualContestTimer.vue:79`→`/virtual/finish` 能结束 |
| **F-03 isRated 无视** | ⚠️ 未真正生效 | `isRated` 仅用于查询过滤 / VO 传参(`RankingServiceImpl:184`、`ContestServiceImpl:511/543/576`),**未在 `RatingCalculationService` 做 gate** |
| F-01 状态机互斥 | 待复核 | 本轮未深入,建议合入前补查 |
| F-06 提交不读虚拟时间 | 待复核 | 本轮未深入,建议合入前补查 |

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
| CRIT-6 (F-ARCH-07 migration) | 🟡 部分 | hardening migration 解耦,应用层无 shadow |
| CRIT-7 (OPS-01 feature flag) | ❌ 未修 → P1 | 旗杆零消费 |
| CRIT-8 (FE-02 BroadcastChannel) | ✅ 不适用 | 根本未用 BroadcastChannel,走 STOMP topic 天然隔离 |
| CRIT-9 (F-SEC-01 IDOR) | ✅ 已修 | UUID + 认证态 userId |
| CRIT-10 (F-SEC-02 appSecret) | ✅ 已修 | 实际用 UUID,无 HMAC/appSecret |

---

## 8. 文档历史

| 版本 | 日期 | 审查对象 | 裁决 |
|---|---|---|---|
| v1 | 2026-06-17 | PLAN.md(6 视角) | REJECT |
| v2 | 2026-06-17 | PLAN.md(7 视角含 Security) | REJECT-WITH-MAJOR-REVISIONS |
| **v3** | **2026-06-17** | **实际代码(4 维度 + 复核)** | **不建议合入 —— 补齐 P0 后重新定档** |

---

## 9. 重新定档 checklist

- [ ] P0-1 评分配置生效(scoringMode / penaltyPerWrong / isRated / tieBreaker)
- [ ] P0-2 auto-finish 兜底接线 + 真实 participant 批量 FINISHED + rating 查询改 contest.status
- [ ] P0-3 真榜 SQL 加 is_virtual=0
- [ ] P0-4 startVirtual 并发原语 + 9 处查询统一
- [ ] P0-5 slug UNIQUE + 冲突校验
- [ ] F-01 / F-06 人工复核结论
- [ ] 重新跑后端 `./mvnw test` + `./mvnw -Dtest='*IT' test`
