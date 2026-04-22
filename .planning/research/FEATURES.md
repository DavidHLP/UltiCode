# Feature Research: v3.0 平台质量与用户体验

**Domain:** Spring Boot 3.2.5 backend + Vue 3 frontend internationalization, API documentation, and sandbox security
**Researched:** 2026-04-22
**Confidence:** HIGH (based on existing codebase analysis + confirmed ecosystem patterns)

---

## Executive Summary

v3.0 delivers three user-facing quality improvements: OpenAPI 3.1 documentation (springdoc), continued sandbox hardening for the code execution platform, and frontend internationalization (i18n) for both Console and Management frontends. The most significant finding is that springdoc 3.x requires Spring Boot 4.0+, making it a blocking dependency that must be addressed before upgrading springdoc. The sandbox has existing isolation primitives (seccomp, capability dropping, cgroup limits) that can be extended. Vue i18n is already in the dependency tree and needs only a translation file architecture and locale management wiring.

---

## 1. SpringDoc OpenAPI 3.x Upgrade

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| OpenAPI 3.0/3.1 spec generation | Standard API documentation for consumers and tooling | LOW | springdoc auto-generates from annotations; SwaggerConfig already exists |
| Swagger UI interactive docs | Developers expect `/swagger-ui.html` to work | LOW | Already served at `/swagger-ui.html`; upgrade improves UI |
| API docs JSON/YAML export | Third-party consumers build clients from OpenAPI spec | LOW | springdoc serves at `/api-docs`; existing SecurityConfig permits access |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| OpenAPI 3.1 spec with rich schema definitions | Better client code generation, API mocking, and contract testing | MEDIUM | 3.1 adds `prefixItems`, `jsonSchemaDialect`, and improved discriminator support |
| Schema documentation with examples | Reduces API consumer questions, enables better API onboarding | LOW | Already using `@Operation`, `@ApiResponse` annotations; needs enrichment |
| `document-title` browser tab title | Brand polish for developer-facing docs | LOW | New in springdoc 2.8+; `springdoc.swagger-ui.document-title` property |

### Critical Constraint: Spring Boot Version Dependency

```
springdoc 3.x  →  requires Spring Boot 4.0+  →  requires Spring Framework 7
springdoc 2.8.17  →  compatible with Spring Boot 3.5.x  →  works on Spring Boot 3.2.5
```

**Current state:**
- `pom.xml`: Spring Boot 3.2.5, springdoc 2.6.0
- `PROJECT.md`: Spring Boot 3.5 mentioned (informational, not reflected in pom.xml)
- `CLAUDE.md`: Spring Boot 3.5 mentioned (informational)

**pom.xml is the source of truth.** Spring Boot 3.2.5 is the effective version.

### Migration Paths

**Option A — Upgrade Spring Boot first, then springdoc 3.x (Recommended for v3.0 if capacity exists):**
1. Upgrade Spring Boot 3.2.5 → 4.x (significant migration, requires testing all dependencies)
2. Then upgrade springdoc 2.6.0 → 3.x
3. Spring Boot 4.x upgrade is a separate major effort with breaking changes across the stack

**Option B — Upgrade springdoc within 2.x line (Safe path for v3.0):**
1. Upgrade springdoc 2.6.0 → 2.8.17 (lowest-risk, all 2.x versions are Spring Boot 3.x compatible)
2. Deliver OpenAPI 3.0 (not 3.1) with improved Swagger UI
3. Defer springdoc 3.x to post-Spring Boot 4.x upgrade

**Option C — Attempt springdoc 3.x on Spring Boot 3.2 (BLOCKED — will fail):**
- Causes `ClassNotFoundException: org.springdoc.core.LiteWebJarsResourceResolver`
- This is not a bug, it is a fundamental API incompatibility between springdoc 3.x and Spring Framework 6
- Do not attempt

### Breaking Changes in springdoc 3.x (beyond Spring Boot 4 requirement)

Based on available release notes:
- Scalar UI support added (alternative to Swagger UI)
- MCP (Model Context Protocol) module support
- Spring Framework 7 API versioning initial support
- No documented OpenAPI specification format changes (3.0.3 uses swagger-core 2.2.47 same as 2.8.17)

### Feature Dependencies

```
Spring Boot 4.x upgrade
    └──enables──> springdoc 3.x upgrade
                       └──delivers──> OpenAPI 3.1 spec

springdoc 2.6.0 → 2.8.17
    └──delivers──> OpenAPI 3.0 spec, improved Swagger UI
```

---

## 2. Sandbox Isolation Hardening

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Network isolation (`--network none`) | Prevent code from reaching external services | LOW | Already implemented in `CodeExecutionService` |
| Capability dropping (`--cap-drop ALL`) | Remove all Linux capabilities from container | LOW | Already implemented |
| Read-only filesystem (`--read-only`) | Prevent writes to container filesystem | LOW | Already implemented |
| PID limit (`--pids-limit 128`) | Prevent fork bombs and process spawning | LOW | Already implemented |
| Memory limit (`--memory 256m`) | Prevent OOM exhaustion of host | LOW | Already implemented |
| Seccomp profile (`--seccomp` profile) | Block dangerous syscalls | LOW | Already implemented with `seccomp-profile.json` |
| Timeout enforcement | Prevent infinite loops from consuming resources | LOW | Already implemented via `sandboxConfig.timeout()` |

### Differentiators (Hardening Opportunities)

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| User namespace isolation | Map container root to non-root host user (rootless containers) | HIGH | Requires Docker userns-remap or rootless Docker configuration; changes container user from root to UID 1000 |
| AppArmor/SELinux confinement | Mandatory access control beyond seccomp | HIGH | Requires host-level AppArmor profile; complex to maintain |
| Filesystem overlay with `/tmp` on tmpfs | Enforce memory-backed temp storage, prevent disk I/O attacks | MEDIUM | Mount `/tmp` as tmpfs with size limit; existing sandbox already mounts `/tmp` but as regular overlay |
| Sysbox runtime | Lightweight embedded hypervisor for stronger isolation | HIGH | Changes container runtime; significant operational complexity |
| FUSE-based filesystem restrictions | Prevent filesystem-based DoS via disk I/O | MEDIUM | Requires `fusermount` in sandbox image; can limit disk bandwidth and inode consumption |
| Additional syscall blocking | Block `perf_event_open`, `ptrace` (already blocked in seccomp), `keyctl` (already blocked) | LOW | Already has comprehensive seccomp profile blocking clone, mount, keyctl, unshare, setns, ptrace |
| Per-language resource limits | Different timeout/memory limits per language (e.g., C++ compile is slower) | LOW | Can be implemented as config per language in `CodeExecutionService` |

### Existing Sandbox Implementation (Confirmed from Codebase)

**File:** `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java`

Existing Docker flags (confirmed):
```
--rm
--network none
--cap-drop ALL
--read-only
--pids-limit 128
--memory 256m
--cpus 0.5
--user 1000
--group-add $(getent group docker | cut -d: -f3)
-v {codeDir}:/code:ro
-v {scriptPath}:/execute.sh:ro
--tmpfs /tmp:rw,noexec,nosuid,size=64m
```

Existing seccomp profile blocks:
- `ptrace` (EPERM)
- `mount` (EPERM)
- `keyctl` (EPERM)
- `unshare`, `setns` (via CLONE_NEWUSER|CLONE_NEWNS|CLONE_NEWPID|CLONE_NEWNET|CLONE_NEWIPC|CLONE_NEWUTS masked)
- clone (via masked flags)

### Recommended Hardening Priorities for v3.0

| Priority | Feature | Rationale | Risk |
|----------|---------|-----------|------|
| P1 | Syscall audit — verify seccomp coverage | Confirm existing profile is complete for all supported languages | LOW |
| P1 | Per-language resource config | Python/JS runs faster than C++; different timeouts per language | LOW |
| P2 | `/tmp` as dedicated tmpfs with size enforcement | Prevent disk-based DoS, enforce memory-backed temp | MEDIUM |
| P2 | Network allowlist (if needed for CDN resources) | Currently `--network none` blocks everything; if problems need external data, need allowlist | MEDIUM |
| P3 | User namespace remapping | Map container UID 1000 to host UID 100000+ (rootless pattern) | HIGH |
| P3 | FUSE restrictions | Prevent filesystem-based DoS via `/dev/fuse` | MEDIUM |

### Anti-Features

| Feature | Why Avoid | Alternative |
|---------|-----------|-------------|
| Full rootless container with Podman | Changes container runtime entirely, complex CI/CD changes | Keep Docker, add user namespace remapping |
| AppArmor mandatory policy | Host-level MAC policy requires OS-level config; breaks container portability | Keep seccomp ( DAC ), add syscall deny-list for remaining risks |
| `docker run --privileged` | Completely disables security primitives | Never — even for "quick testing" |

---

## 3. Vue i18n Frontend Internationalization

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Language switcher in UI | Users expect to select their preferred language | LOW | Needs integration with locale management |
| Persistent locale preference | Users expect language choice to survive page reload | LOW | localStorage + backend user preference |
| Lazy-loaded translation files | Avoid loading all languages upfront, keep bundle small | MEDIUM | vue-i18n supports dynamic import |
| RTL-aware layout | Not required initially (no Arabic/Hebrew languages planned) | N/A | Design for RTL but defer implementation |
| Pluralization support | "1 problem" vs "5 problems" | LOW | vue-i18n built-in `$t` with plural forms |
| Date/number formatting per locale | 2026-04-22 vs 22/04/2026 vs 2026年4月22日 | MEDIUM | `@internationalized/date` already in package.json |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Automatic locale detection | Default to browser language without manual selection | LOW | `navigator.language` check on mount |
| Locale-aware date formatting | Consistent formatting across browser and server | MEDIUM | Use `@internationalized/date` (already installed in console) |
| API-driven translations | Translations served from backend i18n module | MEDIUM | `I18nController` already exists; needs enrichment with user-facing content |
| Lazy-loaded language bundles | Keep initial bundle small, load translations on demand | MEDIUM | vite dynamic import with `import()` |

### Current State

**Console (`console/package.json`):**
- `vue-i18n`: ^11.3.2 (latest 11.x)
- `@internationalized/date`: ^3.10.0 (already installed)

**Management (`management/package.json`):**
- `vue-i18n`: ^10.0.8 (older 10.x)
- `@internationalized/date`: ^3.7.0 (older)

**Backend `I18nController`:** Already exists at `backend-spring/src/main/java/com/ulticode/modules/i18n/controller/I18nController.java`

**Issue:** Major version mismatch between console (11.x) and management (10.x). Should align on one version before i18n work.

### Recommended i18n Architecture

```
frontend/src/
├── locales/
│   ├── en.json           # English (default)
│   ├── zh-CN.json        # Simplified Chinese
│   └── ja.json           # Japanese (if needed)
├── i18n/
│   ├── index.ts          # vue-i18n setup with lazy loading
│   └── composables/
│       ├── useLocale.ts  # locale management composable
│       └── useI18n.ts    # typed translation helper
└── views/                # components import from locales
```

### Locale Management Composable (`useLocale.ts`)

```typescript
// Locale sources in priority order:
// 1. User preference (localStorage + backend API)
// 2. Navigator language
// 3. Fallback: 'en'

export const useLocale = () => {
  const locale = useStorage<'en' | 'zh-CN' | 'ja'>('ulticode-locale', 'en')

  const setLocale = async (newLocale: string) => {
    locale.value = newLocale
    // Persist to backend if user is authenticated
    await userApi.updateLocale(newLocale)
  }

  const isRTL = computed(() => ['ar', 'he', 'fa'].includes(locale.value))

  return { locale, setLocale, isRTL }
}
```

### vue-i18n Setup with Lazy Loading

```typescript
// i18n/index.ts
import { createI18n } from 'vue-i18n'
import type { Locale } from 'vue-i18n'

const messages = {
  en: () => import('@/locales/en.json'),
  'zh-CN': () => import('@/locales/zh-CN.json'),
}

export const i18n = createI18n({
  legacy: false,  // Use Composition API mode
  locale: localStorage.getItem('ulticode-locale') ?? 'en',
  fallbackLocale: 'en',
  lazy: true,
  messages,
})
```

### Feature Dependencies

```
Align vue-i18n versions (console 11.x ↔ management 10.x)
    └──required before──> i18n architecture implementation

Backend I18nController enrichment
    └──provides──> Translation content for UI strings

Frontend locale management composable
    └──provides──> Persistent language preference

Lazy-loaded translation files
    └──requires──> Build system configuration for dynamic import
```

### Anti-Features

| Feature | Why Avoid | Alternative |
|---------|-----------|-------------|
| All translations in a single JSON file | Large bundle size, forces loading all languages | Use lazy loading per locale |
| Hardcoded translation keys in code | `this.$t('nav.settings')` — string typos, no autocomplete | Typed translation keys via generated types |
| Server-side rendering of translations at build time | Locks in locale at build, cannot switch at runtime | Keep translations as runtime JSON, not build-time |
| Automatic browser-only locale detection without user override | Good first guess but users should control | Detection as default, user can override |

---

## Feature Dependencies

```
springdoc 3.x upgrade
    └──blocks──> Spring Boot 4.x upgrade must happen first
              OR
    └──use instead──> springdoc 2.8.17 upgrade (no blocker)

Vue i18n alignment
    └──requires──> vue-i18n version alignment (console 11.x ↔ management 10.x)
              └──requires──> Backend I18nController for user-facing strings

Sandbox hardening
    └──independent──> No dependencies on other v3.0 features
              └──can ship in any order within v3.0
```

---

## MVP Recommendation

### Launch With (v3.0)

**springdoc:**
- [ ] springdoc 2.6.0 → 2.8.17 upgrade (safe path, no Spring Boot 4 dependency)
- [ ] Document that springdoc 3.x requires Spring Boot 4.x upgrade as separate tracking item
- [ ] Enrich `@Operation` and `@ApiResponse` annotations with descriptions across key endpoints

**Sandbox hardening:**
- [ ] Audit seccomp profile completeness for all 5 supported languages (JS, Python, Java, C, C++)
- [ ] Implement per-language resource limits (timeout/memory configurable per language)
- [ ] Add `/tmp` tmpfs enforcement with size limit

**Vue i18n:**
- [ ] Align vue-i18n versions (upgrade management from 10.x to 11.x to match console)
- [ ] Create locale management composable with localStorage persistence
- [ ] Set up lazy-loaded translation file architecture
- [ ] Add English and Simplified Chinese translation files
- [ ] Wire language switcher in Console header component
- [ ] Backend I18nController provides user locale preference storage

### Add After Validation (v3.x)

- [ ] Japanese translation file
- [ ] Locale-aware date/number formatting via `@internationalized/date`
- [ ] Backend API for fetching translations (dynamic, not build-time)
- [ ] springdoc 3.x upgrade (after Spring Boot 4.x migration)

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| springdoc 2.6.0 → 2.8.17 | MEDIUM | LOW (version bump) | P1 |
| vue-i18n version alignment | HIGH (prerequisite for i18n) | LOW (package bump) | P1 |
| Locale management composable | HIGH (foundation for i18n) | LOW | P1 |
| Lazy-loaded translation files | HIGH (performance) | MEDIUM | P1 |
| Language switcher in UI | HIGH (user-facing) | LOW | P1 |
| Backend I18nController enrichment | MEDIUM (translation source) | MEDIUM | P2 |
| Per-language sandbox limits | MEDIUM (security) | LOW | P2 |
| Sandbox tmpfs enforcement | MEDIUM (security) | MEDIUM | P2 |
| Seccomp profile audit | MEDIUM (security verification) | LOW | P2 |
| Spring Boot 4.x upgrade | LOW (enables springdoc 3.x) | VERY HIGH | P3 |
| springdoc 3.x upgrade | LOW (nice-to-have) | HIGH (after SB4) | P3 |
| Japanese translations | LOW | MEDIUM | P3 |
| User namespace remapping | LOW (advanced hardening) | HIGH | P3 |

---

## Competitor Feature Analysis

| Feature | LeetCode | HackerRank | Our Approach |
|---------|----------|------------|--------------|
| API documentation | Swagger UI (internal) | OpenAPI 3.0 | springdoc 2.x → 3.x when SB4 available |
| Language support | 13+ languages | 55+ languages | 5 languages (JS, Python, Java, C, C++) |
| Code sandbox | Proprietary secure runner | Proprietary secure runner | Docker with seccomp + capability dropping |
| i18n | English + Chinese | English only | vue-i18n with lazy loading |
| Locale persistence | Cookie-based | N/A | localStorage + backend preference |

---

## Sources

- **springdoc:** Existing `STACK.md` research (confirmed springdoc 3.x requires SB4), `pom.xml`, springdoc GitHub releases
- **Sandbox:** Existing phase 12 research (`12-RESEARCH.md`), `CodeExecutionService.java`, `seccomp-profile.json`, `SECURITY.md`
- **Vue i18n:** `console/package.json` (vue-i18n 11.3.2), `management/package.json` (vue-i18n 10.0.8), vue-i18n official documentation
- **Codebase:** `backend-spring/src/main/java/com/ulticode/modules/i18n/controller/I18nController.java`, `SwaggergConfig.java`

---

*Feature research for: v3.0 Platform Quality & User Experience*
*Researched: 2026-04-22*
