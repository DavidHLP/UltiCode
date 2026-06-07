# Admin Analytics 接口可用性测试报告

> 测试日期：2026-06-07
> 测试环境：localhost:9001 (Spring Boot, PM2 管理)
> 认证方式：admin / admin123 登录，Cookie + CSRF Token
> 权限校验：所有接口通过 `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")`

---

## 接口测试结果总览

| # | 方法 | 路径 | 状态 | 结果摘要 | 问题 |
|---|------|------|------|----------|------|
| 1 | GET | `/admin/analytics` | ❌ **404** | `{"code":40400,"message":"Not found"}` | 控制器未定义该通用端点 |
| 2 | GET | `/admin/analytics/user-activity` | ✅ **可用** | code=0, 返回 `activeUsersWeekly`, `peakActiveHours`, `topActiveUsers`, `userRetention` | `activeUsersDaily` 返回空数组；`averageSessionDuration=300.0` 硬编码；`lastActive` 全为 null |
| 3 | GET | `/admin/analytics/problem-completion` | ✅ **可用** | code=0, 返回 `totalAttempts=49`, `successfulAttempts=39`, `overallCompletionRate≈79.6%`, `byTag` 有 10 条, `trendingProblems` 有数据 | `byDifficulty` 全为 0 — SQL 映射异常 |
| 4 | GET | `/admin/analytics/contest-participation` | ✅ **可用** | code=0, 返回 `totalContests=4`, `totalParticipants=6`, `byType`, `topContests`, `participationTrend` | N+1 查询风险；`completionRate` 硬编码 100.0；`virtualParticipation` 占位数据 |
| 5 | GET | `/admin/analytics/revenue` | ✅ **可用** | code=0, 返回 `mrr=0`, `arr=0`, `subscriberCount=0`, `byPlan=[]` | 几乎所有数据为占位/默认值；`churnRate` 和 `conversionRate` 硬编码 |
| 6 | GET | `/admin/analytics/performance` | ✅ **可用** | code=0, 返回 `systemUptime=9085`, `memory=2.4%` | 大部分指标为 -1（不可用）；`slowestEndpoints=[]`, `errorBreakdown=[]` |

---

## 关键问题汇总

### 🔴 高优先级

1. **`/admin/analytics` 通用端点不存在 (404)**

   控制器 `AdminAnalyticsController` 只有 5 个子路径端点，没有泛型的 `@GetMapping`。若需求表中的通用查询确实需要，需新增端点。

2. **`problem-completion` 的 `byDifficulty` 全为 0**

   `submissionMapper.countProblemCompletionByDifficulty()` 自定义 SQL 返回的 `difficulty` 字段可能与代码硬编码的 `"EASY"/"MEDIUM"/"HARD"` 不匹配，或 SQL 本身返回空结果。

### 🟡 中优先级

3. **`user-activity` 的 `activeUsersDaily` 为空数组**

   `AuditLogMapper.countDailyActiveUsers()` 返回空 — audit_log 表可能无每日登录记录，或 SQL 条件不匹配。

4. **`contest-participation` 存在 N+1 查询**

   对每个 contest 执行单独的 `selectCount` 和 `selectList`，contest 数量大时会导致严重性能问题。涉及代码：
   - `AdminAnalyticsServiceImpl.java:69-75` (参赛者查询循环)
   - `AdminAnalyticsServiceImpl.java:86-96` (类型统计循环)
   - `AdminAnalyticsServiceImpl.java:104-106` (Top 竞赛循环)
   - `AdminAnalyticsServiceImpl.java:136-148` (趋势数据循环)

5. **大量硬编码占位值**

   | 接口 | 字段 | 硬编码值 | 说明 |
   |------|------|----------|------|
   | user-activity | `averageSessionDuration` | 300.0 | 固定 5 分钟 |
   | contest-participation | `completionRate` (TopContest) | 100.0 | 所有竞赛固定 100% |
   | revenue | `churnRate` | 5.0 | 固定默认流失率 |
   | revenue | `conversionRate` | 2.5 | 固定默认转化率 |
   | revenue | `newSubscribers` / `churned` | 0 | 未实现真实统计 |
   | performance | `cpu`, `disk`, `averageResponseTime`, `errorRate`, `throughput`, `cacheHitRate` | -1 | 无法获取的真实指标 |
   | contest-participation | `virtualParticipation` | total=0, avgRate=0.0 | 占位数据 |

### 🟢 低优先级

6. **`revenue` 接口功能正常但数据为空**

   无活跃订阅导致所有收入数据为零 — 这是业务状态而非代码 bug，但 `newSubscribers` 和 `churned` 永远返回 0（未实现真实统计）。

7. **`topActiveUsers` 的 `lastActive` 为 null**

   `user.getLastLoginAt()` 可能字段名映射不匹配，或 seed 数据未设置该字段。

---

## 涉及的关键源文件

| 文件 | 作用 |
|------|------|
| `backend-spring/.../admin/controller/AdminAnalyticsController.java` | 控制器，定义 5 个子路径端点 |
| `backend-spring/.../admin/service/AdminAnalyticsService.java` | 服务接口 |
| `backend-spring/.../admin/service/impl/AdminAnalyticsServiceImpl.java` | 门面实现，委托子服务 + 保留 contest/revenue 逻辑 |
| `backend-spring/.../admin/service/impl/AdminUserAnalyticsServiceImpl.java` | 用户活跃度报告实现 |
| `backend-spring/.../admin/service/impl/AdminContentAnalyticsServiceImpl.java` | 题目完成率报告实现 |
| `backend-spring/.../admin/service/impl/AdminPerformanceReportServiceImpl.java` | 性能报告实现（JMX 采集） |
| `backend-spring/.../admin/dto/UserActivityReportVO.java` | 用户活跃度 VO |
| `backend-spring/.../admin/dto/ProblemCompletionReportVO.java` | 题目完成率 VO |
| `backend-spring/.../admin/dto/ContestParticipationReportVO.java` | 竞赛参与度 VO |
| `backend-spring/.../admin/dto/RevenueReportVO.java` | 收入报告 VO |
| `backend-spring/.../admin/dto/PerformanceReportVO.java` | 性能报告 VO |

---

## 修复建议优先级

1. **补充 `/admin/analytics` 通用端点** — 或确认需求表中该端点为误列并移除
2. **修复 `byDifficulty` 全零** — 排查 `countProblemCompletionByDifficulty()` SQL 的 difficulty 字段值映射
3. **修复 `activeUsersDaily` 空数组** — 排查 `AuditLogMapper.countDailyActiveUsers()` SQL 及 audit_log 数据
4. **优化 N+1 查询** — 将 contest-participation 的循环查询替换为批量聚合 SQL
5. **逐步替换硬编码占位值** — 按业务优先级实现真实统计逻辑
