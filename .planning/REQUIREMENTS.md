# Requirements: UltiCode v1.9

**Defined:** 2026-04-22
**Core Value:** 平台安全性、功能完整性和交付自动化

## v1 Requirements

性能优化和质量强制。Each maps to roadmap phases.

### Performance

- [ ] **PERF-01**: Achievement N+1 查询优化（AchievementServiceImpl.getUserPoints() 使用 selectBatchIds() 批量查询替代循环 selectById）
- [x] **PERF-02**: Follow System 索引优化（user_follows 表添加 (following_id, created_at) 和 (follower_id, created_at) 复合索引；修复 toUserSummary() 的 N+1 问题）

### Quality

- [x] **MISS-01
**: 测试覆盖率强制执行（JaCoCo pom.xml 配置 jacoco:check 绑定到 verify phase）

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Dependencies

- **DEPS-03**: springdoc 升级到 3.x 长期支持版本（当 available）

### Missing Features

- **MISS-02**: Rate Limiting 端到端测试

## Out of Scope

| Feature | Reason |
|---------|--------|
| springdoc 3.x 升级 | 等待官方 Spring Boot 3.2.x 兼容版本 |
| 完整的 Rate Limiting 测试 | 需要更多基础设施 |

## Traceability

Which phases cover which requirements.

| Requirement | Phase | Status |
|-------------|-------|--------|
| PERF-01 | Phase 38 | Pending |
| PERF-02 | Phase 39 | Complete |
| MISS-01 | Phase 40 | Pending |

**Coverage:**
- v1 requirements: 3 total
- Mapped to phases: 3/3
- Unmapped: 0

---
*Requirements defined: 2026-04-22*
