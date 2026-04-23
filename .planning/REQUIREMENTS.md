# Requirements: UltiCode v3.0

**Defined:** 2026-04-22
**Core Value:** 平台安全性、功能完整性和交付自动化

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### API Documentation

- [ ] **API-01**: SpringDoc 升级到 2.8.17（OpenAPI 3.0，兼容 Spring Boot 3.2.5）
- [ ] **API-02**: 关键 endpoints 添加 @Operation/@ApiResponse 注解丰富
- [ ] **API-03**: Swagger UI 在 /swagger-ui.html 正常访问

### Sandbox Hardening

- [x] **SAND-01**: 修复 --read-only 在 --tmpfs 之前的 flag ordering bug
- [x] **SAND-02**: 修复 seccomp profile path 未 volume-mounted 问题
- [x] **SAND-03**: 实现 per-language resource limits（timeout/memory per language）
- [x] **SAND-04**: /tmp tmpfs size enforcement（size=64m）
- [x] **SAND-05**: bubblewrap 集成测试验证 namespace isolation

### Frontend i18n

- [x] **I18N-01
**: Management vue-i18n 10.0.8 → 11.3.2 与 Console 对齐
- [x] **I18N-02
**: 构建 useLocale composable（localStorage 持久化 + 后端同步）
- [x] **I18N-03
**: 翻译文件 lazy loading（非 active locale 动态 import）
- [x] **I18N-04**: Console header 添加 language switcher（zh-CN / en-US）
- [x] **I18N-05
**: 启用 missingWarn 使 missing translation keys 在开发时可见

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| springdoc 3.x upgrade | Requires Spring Boot 4.0 + Java 21 — separate major effort post-v3.0 |
| Japanese translations | Not required for v3.0 scope |
| User namespace remapping | High complexity, rootless Docker changes |
| FUSE-based filesystem restrictions | Defer until bubblewrap integration validated |
| Backend content i18n | Database i18n out of scope for v3.0 |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| API-01 | Phase 45 | Pending |
| API-02 | Phase 45 | Pending |
| API-03 | Phase 45 | Pending |
| SAND-01 | Phase 46 | Complete |
| SAND-02 | Phase 46 | Complete |
| SAND-03 | Phase 46 | Complete |
| SAND-04 | Phase 46 | Complete |
| SAND-05 | Phase 46 | Complete |
| I18N-01 | Phase 47 | Complete |
| I18N-02 | Phase 47 | Complete |
| I18N-03 | Phase 47 | Complete |
| I18N-04 | Phase 47 | Complete |
| I18N-05 | Phase 47 | Complete |

**Coverage:**
- v1 requirements: 13 total
- Mapped to phases: 13
- Unmapped: 0 ✓

---
*Requirements defined: 2026-04-22*
*Last updated: 2026-04-22 after initial definition*
