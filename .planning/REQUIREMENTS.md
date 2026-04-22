# Requirements: UltiCode v2.0

**Defined:** 2026-04-22
**Milestone:** v2.0 Dependencies & Quality
**Status:** Active

## v1 Requirements

### Dependencies

- [ ] **DEPS-01**: springdoc 2.6.0 → 2.8.17 升级（兼容 Spring Boot 3.2.5）
  - 验证 swagger-ui 在 /swagger-ui.html 正常显示
  - 验证 /api-docs 返回有效 OpenAPI JSON
  - 无 breaking changes

- [ ] **DEPS-02**: 添加 Testcontainers Redis 依赖到 pom.xml
  - testcontainers-bom 已存在，添加 redis testcontainer 依赖
  - 用于 Rate Limiting E2E 测试

### Quality

- [ ] **TEST-01**: Rate Limiting E2E 测试
  - @SpringBootTest + @AutoConfigureMockMvc + Testcontainers Redis
  - 测试 rate-limited 端点超过限制后返回 429
  - 每个测试前 flush Redis keys 避免 false 429
  - 测试认证端点的 rate limit tier（auth/register=5/min）

- [ ] **JAC-01**: JaCoCo thresholds 提高
  - LINE: 3% → 5%
  - BRANCH: 1% → 3%
  - 在 TEST-01 测试添加后执行

## v2 Requirements (Deferred)

- [ ] **DEPS-03**: springdoc 3.x 升级
  - Deferred to: v3.0 (需要 Spring Boot 4.x 升级)
  - springdoc 3.x requires Spring Boot 4.0.5+

## Out of Scope

| Feature | Reason |
|---------|--------|
| springdoc 3.x 升级 | 需要 Spring Boot 4.x，当前项目使用 3.2.5 |
| JaCoCo thresholds 20%/10% | 过于激进，需要更多测试覆盖才能提高 |
| Spring Boot 4.x 升级 | 重大迁移，包含 breaking changes |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| DEPS-01 | Phase 41 | Active |
| DEPS-02 | Phase 41 | Active |
| TEST-01 | Phase 42 | Active |
| JAC-01 | Phase 43 | Active |
| DEPS-03 | - | Deferred to v3.0 |

**Coverage:**
- v1 requirements: 4 total
- Mapped to phases: 4/4
- Complete: 0/4
- Unmapped: 0

---

*Last updated: 2026-04-22*
