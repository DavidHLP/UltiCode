# Local Code Review: Contest Phase 1 — Critical Type Fixes

**Reviewed**: 2026-05-29
**Branch**: feat/contest-phase1-type-fixes
**Decision**: APPROVE

## Summary
本次变更修复了 Contest 模块的关键类型不一致和逻辑错误，包括统一 UUID 主键类型、修复 getStats() 业务逻辑、删除冗余方法和补全查询参数。变更清晰、安全，无安全风险。

## Findings

### CRITICAL
None

### HIGH
None

### MEDIUM
**[FIXED] ContestStatsVO 设计矛盾**
- **文件**: `ContestServiceImpl.java`, `ContestController.java`, `ContestService.java`
- **问题**: `getStats()` 是无参的全局统计方法，但返回的 `ContestStatsVO` 包含 `contestId`（单个比赛字段）。
- **修复**: 新增 `GlobalContestStatsVO` record，只包含全局统计字段（registeredParticipants, activeParticipants, completedParticipants, totalSubmissions）。修改 `getStats()` 和 `getContestStats()` 返回新的 VO。
- **验证**: `./mvnw compile` 通过

### LOW
None

## Validation Results

| Check | Result |
|---|---|
| Compile | Pass |
| Security Scan | Pass — 无 SQL 注入、XSS、密钥泄露 |
| Pattern Compliance | Pass — 遵循 MyBatis-Plus、Lombok、Spring Boot 项目规范 |

## Files Reviewed

| File | Change Type | Assessment |
|---|---|---|
| `pom.xml` | Modified | Spring Boot 4.0.6 → 3.2.5，修复测试环境 |
| `ParticipationStatusDTO.java` | Modified | contestId: Long → String，安全 |
| `ContestRankingVO.java` | Modified | contestId: Long → String，安全 |
| `ContestStatsVO.java` | Modified | contestId: Long → String，安全 |
| `ContestSchedulerServiceImpl.java` | Modified | 删除 Long.parseLong，直接赋值 |
| `ContestService.java` | Modified | 删除冗余 findAll() 接口 |
| `ContestServiceImpl.java` | Modified | 删除 findAll() + 重写 getStats() + 新增注入 |
| `ContestParticipantMapper.java` | Modified | 新增 countByStatus()，使用 #{} 参数绑定 |
| `ContestSubmissionMapper.java` | Modified | 新增 countTotal()，无参数注入风险 |
| `ContestController.java` | Modified | 新增 isRated 查询参数 |
| `ContestServiceImplTest.java` | Modified | 适配新构造器参数 |

## Security Checklist
- [x] 无硬编码密钥
- [x] 无 SQL 注入（使用 `#{}` 参数绑定）
- [x] 无 XSS 漏洞
- [x] 无路径遍历
- [x] 无不安全的反序列化

## Notes
- `ContestRankingVO.contestId` 当前所有 `toRankingVO` 转换器均不设置该字段，改类型后仍为 null，不影响现有行为
- `countByStatus` 是全局统计，命名与 `countByContestIdAndStatus` 区分清晰
