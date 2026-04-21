# Phase 34: Swagger UI 修复 - Context

**Gathered:** 2026-04-21
**Status:** Ready for planning

<domain>
## Phase Boundary

修复 Swagger UI，使其可通过 `/swagger-ui.html` 正常访问，所有 REST API endpoints 正确显示，Try-out 功能正常工作。

</domain>

<decisions>
## Implementation Decisions

### Root Cause
- SwaggerConfig.java 被完全注释掉（注释说明 "springdoc 2.x incompatible with Spring Boot 3.2.5"）
- springdoc.version 实际已是 2.6.0（降级后的正确版本）
- application.yml 中 `springdoc.swagger-ui.enabled: false` 禁用了 Swagger

### Fix Approach
- **D-01:** 取消 SwaggerConfig.java 的注释，启用 `@Configuration` 和 `OpenAPI` bean
- **D-02:** 修改 application.yml 中 `springdoc.swagger-ui.enabled: true`（或设置环境变量 `SPRINGDOC_ENABLED=true`）
- **D-03:** 验证 SecurityConfig 中 PUBLIC_ENDPOINTS 已包含 `/swagger-ui/**`, `/swagger-ui.html`, `/api-docs/**`, `/v3/api-docs/**`

### Verification Criteria
- Swagger UI 页面加载返回 HTTP 200
- API 文档显示所有 REST endpoints
- Try-out 功能可正常发送请求

### Claude's Discretion
- 具体配置细节（是否需要额外的 OpenAPI 自定义 bean）由 planner 决定

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Context
- `.planning/REQUIREMENTS.md` — DEPS-01 requirement definition
- `.planning/ROADMAP.md` — Phase 34 goal and success criteria
- `backend-spring/pom.xml` — springdoc dependency (version 2.6.0 confirmed)
- `backend-spring/src/main/resources/application.yml` — springdoc config (currently disabled)
- `backend-spring/src/main/java/com/ulticode/common/config/SwaggerConfig.java` — commented-out config
- `backend-spring/src/main/java/com/ulticode/common/config/SecurityConfig.java` — PUBLIC_ENDPOINTS already includes Swagger paths

### Springdoc Documentation
- springdoc-openapi-starter-webmvc-ui 2.6.0 — compatible with Spring Boot 3.2.x

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- SecurityConfig.java already has Swagger paths in PUBLIC_ENDPOINTS — no security changes needed
- springdoc 2.6.0 dependency already in pom.xml — no dependency changes needed

### Established Patterns
- Spring Boot 3.x + springdoc 2.6.0 compatibility (documented in CLAUDE.md backend startup issues)
- Standard springdoc auto-configuration approach

### Integration Points
- Swagger UI at `/swagger-ui.html`
- OpenAPI JSON at `/v3/api-docs`
- Public access via SecurityConfig PUBLIC_ENDPOINTS

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard springdoc auto-configuration approach.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>
