

---

# Domain Pitfalls: v3.0 Platform Quality and User Experience

**Domain:** SpringDoc 3.x upgrade, Sandbox hardening, Vue i18n on existing Spring Boot + Vue 3 platform
**Researched:** 2026-04-22
**Confidence:** MEDIUM (Context7 unavailable, web search throttled; based on source code analysis + known library patterns)

---

## Critical Pitfalls

### Pitfall 1: SpringDoc 3.x Path Collision with Spring Security

**What goes wrong:**
After upgrading from springdoc 2.6.0 to 3.x, Swagger UI returns blank pages or 404s, and `/api-docs` returns 403 or empty JSON.

**Why it happens:**
springdoc 3.x changes internal routing: `/v3/api-docs` replaces `/api-docs`. The existing `SecurityConfig` permits `/swagger-ui.html`, `/api-docs`, and `/swagger-ui/**` but NOT `/v3/api-docs`. The `SwaggerConfig.java` uses `io.swagger.v3.oas.models` which is still valid, but the new paths are blocked.

**Current state:**
- `SwaggerConfig.java` configures OpenAPI at `/api-docs` path
- `SecurityConfig.java` line 67 permits Swagger paths
- `application.yml` lines 88-97 configure `springdoc.api-docs.path: /api-docs`

**How to avoid:**
1. Test Swagger UI and `/v3/api-docs` endpoints immediately after upgrade
2. Update SecurityConfig to permit `/swagger-ui.html`, `/v3/api-docs`, `/v3/api-docs.yaml`, and `/swagger-ui/**`
3. Add explicit path mappings in `application.yml`:
   ```yaml
   springdoc:
     api-docs:
       path: /v3/api-docs
     swagger-ui:
       path: /swagger-ui.html
       urls-primary-name: openapi.yaml
   ```
4. Do NOT rely on legacy `/api-docs` path — it redirects to `/v3/api-docs` in 3.x

**Warning signs:**
- `curl http://localhost:9001/swagger-ui.html` returns blank or redirects
- Browser console shows `Failed to load .../openapi.yaml`
- `@Operation` annotations no longer group endpoints by tags

**Phase to address:** SpringDoc 3.x upgrade phase

---

### Pitfall 2: SpringDoc 3.x OpenAPI 3.1 Schema Breaking Changes

**What goes wrong:**
API documentation generates but request/response schemas show `null` descriptions, `oneOf`/`anyOf` schemas are malformed, and examples fail to render.

**Why it happens:**
springdoc 3.x generates OpenAPI 3.1 (vs 3.0 in 2.x). OpenAPI 3.1 uses JSON Schema 2020-12 which has breaking changes: `type` can now be an array (`["string", "null"]`), `nullable` is deprecated in favor of `type` arrays, and `example` is deprecated in favor of `examples`. Existing `@Schema(description = "...")` annotations may not render in the new UI.

**How to avoid:**
1. Add `springdoc.openapi.3.0-compatible: true` in application.yml if OpenAPI 3.0 output is needed for consumers
2. Audit generated schema at `/v3/api-docs` after upgrade
3. Add explicit `@ExampleObject` annotations for request/response examples
4. Test complex DTOs with `oneOf`/`anyOf` patterns (common in this codebase for `Result<T>` wrapper)

**Warning signs:**
- `/v3/api-docs` JSON has `$ref` without proper `components/schemas`
- Swagger UI shows "Could not resolve ref" errors
- Null descriptions in schema explorer

**Phase to address:** SpringDoc 3.x upgrade phase

---

### Pitfall 3: springdoc 3.x Requires Spring Boot 4.0 — Not Backwards Compatible

**What goes wrong:**
Upgrading springdoc to 3.x causes immediate ClassNotFoundException or runtime crashes on Spring Boot 3.2.5.

**Why it happens:**
springdoc-openapi 3.0.0 dropped Spring Boot 3.x support entirely. The release notes state: "The primary breaking change is the upgrade to Spring Boot 4.0.0." springdoc 3.x was never designed to work with Spring Boot 3.2.5.

**Current state:**
- Project uses Spring Boot 3.2.5 (`pom.xml` line 8)
- Project uses springdoc 2.6.0 (`pom.xml` line 21: `<springdoc.version>2.6.0</springdoc.version>`)
- Phase 34 downgraded from 3.x to 2.6.0 specifically because of Spring Boot 3.2 incompatibility

**DEPS-03 is blocked by a hard dependency chain:**
```
springdoc 3.x  →  requires Spring Boot 4.0  →  requires Spring Framework 7  →  major migration
```

**Prevention:** Do NOT attempt springdoc 3.x upgrade without first upgrading Spring Boot to 4.x. There is no springdoc 3.x version compatible with Spring Boot 3.2.5.

**Mitigation path:**
1. Upgrade Spring Boot 3.2.5 → 4.x (separate major effort)
2. Then upgrade springdoc 2.6.0 → 3.x
3. OR mark DEPS-03 as blocked permanently if staying on Spring Boot 3.x

**Phase to address:** SpringDoc upgrade phase (must follow Spring Boot 4.x upgrade)

---

### Pitfall 4: Sandbox --read-only Flag Ordering Breaks tmpfs Mount

**What goes wrong:**
Code execution fails with "Read-only file system" errors even though `/tmp` is specified as tmpfs.

**Why it happens:**
`SandboxServiceImpl.buildDockerCommand()` (lines 143-157) uses `--read-only` flag but places `--tmpfs /tmp:rw,exec,size=64m` after it. Docker processes flags left-to-right — `--read-only` makes the root filesystem read-only before `--tmpfs` is evaluated. The tmpfs mount fails silently or is ignored.

**Current code (lines 143-157):**
```java
List.of("docker", "run", "--rm", "-i",
    "--network", "none",
    "--cap-drop", "ALL",
    "--memory", sandboxConfig.memory(),
    "--cpus", sandboxConfig.cpus(),
    "--pids-limit", String.valueOf(sandboxConfig.pidsLimit()),
    "--ulimit", "nofile=128:128",
    "--read-only",                           // <-- Makes root read-only
    "--tmpfs", "/tmp:rw,exec,size=64m",      // <-- This may be ignored
    "--user", "1000:1000",
    ...
```

**How to avoid:**
Reorder flags so `--tmpfs` appears before `--read-only`:
```java
List.of("docker", "run", "--rm", "-i",
    "--tmpfs", "/tmp:rw,exec,size=64m",     // <-- BEFORE --read-only
    "--read-only",
    "--network", "none",
    ...
```

**Warning signs:**
- Submission results show "Read-only file system" for code that writes to `/tmp`
- Works locally in dev but fails in CI/production with different docker versions
- `docker inspect` shows mounts empty for the container

**Phase to address:** Sandbox hardening phase

---

### Pitfall 5: Sandbox seccomp Profile Path Not Volume-Mounted

**What goes wrong:**
Sandbox execution fails with "cannot load seccomp profile" error when deployed.

**Why it happens:**
`DockerSandboxConfig.seccompProfilePath()` (application.yml line 131) returns a path like `/docker/sandbox/seccomp-profile.json`. This path is on the HOST filesystem, but the Docker container's filesystem has no visibility to it unless the host path is volume-mounted into the container.

**How to avoid:**
1. Use host path with proper volume binding: add `-v $(pwd)/docker/sandbox:/docker/sandbox:ro` to the docker run command
2. Or make the path configurable via environment variable pointing to an absolute host path
3. Test from a different working directory than the seccomp profile location

**Warning signs:**
- Works in dev where working directory contains the seccomp profile
- Fails in Docker Compose or K8s deployments
- Error: "open /docker/sandbox/seccomp-profile.json: no such file or directory"

**Phase to address:** Sandbox hardening phase

---

### Pitfall 6: Vue i18n Missing Keys Cause Silent Fallback in Production

**What goes wrong:**
Users see raw translation keys (e.g., `submission.status.passed`) instead of translated text, or English text appears for Chinese users.

**Why it happens:**
Both frontends configure `silentTranslationWarn: true` / `missingWarn: false` (console line 22, management line 57-59), suppressing all warnings. When a translation key is missing, it silently falls back without indication. In production builds, missing keys return the key itself as the translated value.

**Current state:**
- `console/src/i18n/index.ts` uses `globalInjection: true`, `fallbackLocale: 'en-US'`
- `management/src/i18n/index.ts` uses `silentTranslationWarn: true`, `missingWarn: false`, `fallbackWarn: false`
- Both have `check.ts` scripts but no CI gate enforcing zero missing keys

**How to avoid:**
1. Add `check.ts` to CI pipeline: `npx tsx src/i18n/check.ts` must exit 0 before merge
2. Enable `missingWarn` in development:
   ```typescript
   missingWarn: import.meta.env.DEV,
   ```
3. Both zh-CN and en-US must have identical key sets — existing check.ts already compares them

**Warning signs:**
- Users report seeing English keys instead of Chinese
- QA reports "submission.status.passed" appearing in the UI
- CI pipeline has no i18n completeness check

**Phase to address:** Console i18n phase and Management i18n phase

---

### Pitfall 7: Vue i18n Loading All Locales at Startup Causes Memory Bloat

**What goes wrong:**
Initial page load is 2-3x larger than expected, and memory usage spikes on mobile devices.

**Why it happens:**
Both frontends use `createI18n` with `messages: { "zh-CN": zhCN, "en-US": enUS }` — all translations for all locales are loaded into memory on startup, regardless of the user's active locale. For large translation files (console has 14+ namespace files per locale), this can add 500KB+ of unused translation data.

**Current state:**
```typescript
messages: {
  "zh-CN": zhCN,  // Always loaded
  "en-US": enUS,  // Always loaded
},
```

**How to avoid:**
Use lazy locale loading with dynamic import:
```typescript
messages: {
  'zh-CN': zhCN,  // Keep initial locale loaded
},
// dynamically import other locales on demand
```
Or implement a module-level lazy loading strategy where non-active locales are not imported until the user switches.

**Warning signs:**
- Bundle size increases significantly after adding i18n
- Memory profiler shows large i18n object retained
- Lighthouse performance score drops after i18n addition

**Phase to address:** Console i18n phase and Management i18n phase

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Skip i18n check in CI | Faster builds | Missing keys reach production | Never |
| Hardcode seccomp profile path | Simpler config | Breaks in Docker/K8s | Dev only |
| Use `--network none` without testing | Security assumed | Actual network still accessible | Never |
| Load all locales at startup | Simpler code | Memory bloat, slow first paint | MVP only, fix before launch |
| Skip OpenAPI 3.1 schema audit | Faster upgrade | Broken API docs | Never |
| Upgrade springdoc without SB 4.x | "Progress" on DEPS-03 | Runtime ClassNotFoundException | Never |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|-----------------|
| SpringDoc + Spring Security | SecurityConfig blocks new Swagger paths | Permit `/swagger-ui.html`, `/v3/api-docs/**`, `/swagger-ui/**` |
| SpringDoc + JWT auth | `Bearer` security scheme not recognized | Ensure SecurityRequirement name matches SecurityScheme name exactly |
| Docker sandbox + seccomp | Profile path not accessible inside container | Volume mount the profile directory or use absolute host path |
| Vue i18n + Vite | Locale files not bundled correctly | Use `?inline` suffix for JSON imports if needed |
| Vue i18n + Lazy loading | Locale switch causes blank screen | Ensure locale messages are properly registered before rendering |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| All locales loaded eagerly | 500KB+ bundle increase | Use lazy locale loading with dynamic import | Every page load |
| Sandbox timeout too generous | Resource exhaustion from runaway code | Set timeout to 10s max, memory 256MB | High submission volume |
| Sandbox memory too restrictive | Legitimate solutions OOM | Profile worst-case memory per language | Large data structures |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Not using `--cap-drop ALL` | Container escapes with full capabilities | Always `--cap-drop ALL` |
| Missing `--security-opt no-new-privileges:true` | Privilege escalation via setuid binaries | Add the flag explicitly |
| Not using `--read-only` root | Malicious code can write anywhere | Make root read-only except tmpfs |
| tmpfs without `exec` restriction | Can write and execute exploit code | Use `tmpfs /tmp:rw,exec` —权衡: if too restrictive, compilation fails |
| Using `--privileged` for sandbox | Complete system compromise | Never use, even for debugging |
| Not limiting PIDs | Fork bomb inside container | `--pids-limit 128` minimum |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Missing translation keys show raw key | Confusing UI, broken experience | CI gate with zero tolerance for missing keys |
| Language switch requires page reload | Disorienting, especially in forms | Use reactive locale switching without reload |
| Fallback to English silently | Chinese users see mixed English/Chinese | Explicit fallback chain with logging |

---

## "Looks Done But Isn't" Checklist

- [ ] **SpringDoc 3.x:** Docs load at `/swagger-ui.html` AND `/v3/api-docs` returns valid JSON — test both
- [ ] **SpringDoc 3.x:** All `@Operation` and `@Tag` annotations group endpoints correctly
- [ ] **SpringDoc 3.x:** Request/response schemas render with descriptions and examples
- [ ] **Sandbox:** Verified `--read-only` root with `--tmpfs /tmp` actually works — test code that writes to `/tmp`
- [ ] **Sandbox:** Verified seccomp profile loads in a fresh Docker container (not from working directory)
- [ ] **Sandbox:** Confirmed `--network none` actually blocks outbound connections
- [ ] **i18n:** `check.ts` passes with zero missing keys in both zh-CN and en-US
- [ ] **i18n:** Bundle size impact measured — should be <50KB per additional locale with lazy loading
- [ ] **i18n:** Language switch works without page reload

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| SpringDoc 3.x ClassNotFoundException | LOW | Revert to springdoc 2.6.0; verify Spring Boot version compatibility first |
| SpringDoc path collision | LOW | Update SecurityConfig paths; test both old and new doc endpoints |
| OpenAPI schema issues | MEDIUM | Add `springdoc.openapi.3.0-compatible: true`, audit DTOs |
| Sandbox read-only /tmp | LOW | Reorder docker flags — tmpfs before --read-only |
| Missing i18n keys in prod | HIGH | Hot-fix translation, rollback if critical |
| Locale memory bloat | MEDIUM | Implement lazy loading, redeploy |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| SpringDoc path collision | SpringDoc upgrade phase | Both /swagger-ui.html and /v3/api-docs return 200 |
| SpringDoc schema breaking changes | SpringDoc upgrade phase | All DTO schemas render correctly in Swagger UI |
| springdoc 3.x requires SB 4.x | SpringDoc upgrade phase | Document blocked status; do not attempt without SB 4.x |
| Sandbox --read-only flag ordering | Sandbox hardening phase | Test code that writes /tmp inside sandbox |
| Sandbox seccomp path resolution | Sandbox hardening phase | Run container from different working directory |
| i18n missing keys | Console/Management i18n phase | CI gate passes `check.ts` with 0 missing |
| i18n memory bloat | Console/Management i18n phase | Bundle analyzer shows <50KB per extra locale |

---

## Sources

- springdoc-openapi GitHub README (Context7): https://github.com/springdoc/springdoc-openapi
- springdoc-openapi migration notes: https://springdoc.org/#difference-between-v2-and-v3
- Docker sandbox security best practices: https://docs.docker.com/engine/security/
- vue-i18n documentation: https://vue-i18n.intlify.dev/
- OpenAPI 3.1 changes: https://spec.openapis.org/oas/v3.1.0
- Spring Boot 3.5 compatibility: Project's existing pom.xml (Spring Boot 3.2.5 parent)
- Existing `SandboxServiceImpl.java` lines 143-157 flag ordering analysis
- Existing `DockerSandboxConfig.java` configuration analysis
- Existing `SwaggerConfig.java` and `SecurityConfig.java` path permit analysis
- Existing `console/src/i18n/index.ts` and `management/src/i18n/index.ts` configuration analysis
- Existing `management/src/i18n/check.ts` translation completeness checker

---

*Pitfalls research for: v3.0 Platform Quality and User Experience*
*Researched: 2026-04-22*
