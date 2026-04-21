# Requirements: UltiCode v1.8

**Defined:** 2026-04-21
**Core Value:** 平台安全性、功能完整性和交付自动化

## v1 Requirements

技术债修复需求。Each maps to roadmap phases.

### Dependencies

- [ ] **DEPS-01**: Swagger UI 可访问（springdoc 降级到 2.6.0 或升级到兼容版本）
- [ ] **DEPS-02**: CI workflow 中 Flyway 下载 URL 正确（使用 Redgate 官方 URL）

### Performance/Pitfalls

- [ ] **PITFALL-01**: Achievement 成就检查改为异步（@Async + @EventListener，AFTER_COMMIT 阶段）
- [ ] **PITFALL-02**: Admin Forum Stats 返回真实数据（查询 forum_comments 和 forum_votes 表）

### Bug Fixes

- [ ] **BUG-01**: Admin Forum Stats 不再返回硬编码零值（DEPS-02 的另一面）

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Dependencies

- **DEPS-03**: springdoc 升级到 3.x 长期支持版本（当 available）

### Performance

- **PERF-01**: Achievement N+1 查询优化（JOIN FETCH）
- **PERF-02**: Follow System 索引优化（composite index on user_follows）

### Missing Features

- **MISS-01**: 测试覆盖率强制执行（JaCoCo thresholds 已配置但需验证）
- **MISS-02**: Rate Limiting 端到端测试

## Out of Scope

| Feature | Reason |
|---------|--------|
| springdoc 3.x 升级 | 等待官方 Spring Boot 3.2.x 兼容版本 |
| 完整的 Rate Limiting 测试 | 需要更多基础设施 |
| Achievement N+1 优化 | 可在 PITFALL-01 之后单独处理 |

## Traceability

Which phases cover which requirements.

| Requirement | Phase | Status |
|-------------|-------|--------|
| DEPS-01 | Phase 34 | Pending |
| DEPS-02 | Phase 35 | Pending |
| PITFALL-01 | Phase 36 | Pending |
| BUG-01 / PITFALL-02 | Phase 37 | Pending |

**Coverage:**
- v1 requirements: 4 total
- Mapped to phases: 4/4
- Unmapped: 0

---
*Requirements defined: 2026-04-21*
*Last updated: 2026-04-21 after v1.8 roadmap created*
