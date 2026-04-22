# Technology Stack — v3.0 平台质量与用户体验

**Project:** UltiCode v3.0 Milestone
**Researched:** 2026-04-22
**Confidence:** HIGH (verified via Context7, npm, official docs, Maven Central)

---

## 1. SpringDoc OpenAPI 3.x Upgrade

### Current State
- Using `springdoc-openapi-starter-webmvc-ui` **2.6.0** (downgraded from 2.8.x in v2.0 due to Spring Boot 3.2.5 incompatibility)
- Spring Boot **3.2.5** with Java 17

### Finding: springdoc 3.x requires Spring Boot 4.x

**Source:** [springdoc-openapi releases](https://github.com/springdoc/springdoc-openapi/releases)

| Library Version | Spring Boot Required | Status |
|-----------------|---------------------|--------|
| springdoc 2.8.17 | Spring Boot 3.5.13 | Latest v2.x, requires SB 3.x |
| **springdoc 3.0.3** | **Spring Boot 4.0.5** | **Latest v3.x, requires SB 4.x** |

### Verdict: NOT YET VIABLE for v3.0

**springdoc 3.x requires Spring Boot 4.x**, but:
- Project uses Spring Boot **3.2.5** (Java 17)
- Spring Boot 4.x requires **Java 21+**
- Upgrading to springdoc 3.x means upgrading Spring Boot AND Java version

### Recommendation

| Option | Action | Risk |
|---------|--------|------|
| **Stay on v2.x** | springdoc 2.6.0 -> 2.8.17 for bug fixes | OpenAPI 3.1 blocked until SB upgrade |
| **Spring Boot upgrade first** | SB 3.2.5 -> 3.5.x, then 4.x (Java 17->21) | Large migration, not in v3.0 scope |

### Safe Upgrade Path for v3.0

Even without Spring Boot 4, **springdoc 2.6.0 -> 2.8.17** is safe:

```xml
<springdoc.version>2.8.17</springdoc.version>
```

Benefits:
- Spring Boot 3.5.13 compatibility ceiling (still works on 3.2.5)
- Latest swagger-ui (5.32.2) and swagger-core (2.2.47)
- `@Range` constraint validation annotation support
- Swagger UI browser tab title customization
- Bug fixes and schema resolution improvements

### Future (post-v3.0): Full springdoc 3.x Migration

When upgrading to Spring Boot 4.x:
1. Java 17 -> Java 21
2. Spring Boot 3.2.5 -> 3.5.x (verify 2.8.x compatibility)
3. Spring Boot 3.5.x -> 4.0.x
4. springdoc 2.8.x -> 3.0.x

---

## 2. Vue I18n (Frontend Multi-language)

### Current State
- **Console:** `vue-i18n` **11.3.2** (latest)
- **Management:** `vue-i18n` **10.0.8** (older)
- Both on Vue 3.5.x

### vue-i18n Ecosystem

| Package | Purpose | Best For |
|---------|---------|----------|
| `vue-i18n` | Full i18n solution | Console, Management (full-featured) |
| `petite-vue-i18n` | Lightweight subset | Smaller bundles, simple i18n |
| `@intlify/vue-i18n-bundle` | Tree-shaking helper | Bundle size optimization |

### Finding: vue-i18n 11.x is current standard for Vue 3

**Source:** [npm vue-i18n](https://www.npmjs.com/package/vue-i18n), [intlify/vue-i18n releases](https://github.com/intlify/vue-i18n/releases)

- **Console** already on 11.3.2 (latest) — no change needed
- **Management** is on 10.0.8 — should upgrade to 11.x for consistency

### vue-i18n Version Comparison

| Version | Vue 3 Required | Key Features |
|---------|---------------|--------------|
| vue-i18n 11.x | ^3.0.0 | Composition API, TypeScript, v-t directive, linked messages, 856 code snippets in Context7 |
| vue-i18n 10.x | ^3.0.0 | Legacy composition API |

### Recommendation

| Frontend | Current | Recommended | Action |
|----------|---------|-------------|--------|
| Console | 11.3.2 | 11.3.2 | No change (current) |
| Management | 10.0.8 | 11.3.2 | Upgrade for consistency |

**Why not vue-i18next?** `vue-i18next` is a wrapper around i18next. `vue-i18n` is the official Vue ecosystem solution with better Vue 3 integration, Composition API support, and single dependency instead of i18next + wrapper.

---

## 3. Sandbox Isolation Hardening

### Current State
- cgroup v2 + seccomp already in place
- Looking for additional isolation layers

### Technologies for Container Sandbox Hardening

| Technology | Type | Layer | Status |
|------------|------|-------|--------|
| cgroup v2 | Resource limits | kernel | In use |
| seccomp | Syscall filtering | kernel | In use |
| **bubblewrap** | Unprivileged namespace sandbox | userspace | **Not in use** |
| **landlock** | Filesystem access control | kernel (5.13+) | **Not in use** |
| gVisor | User-space kernel | container runtime | Overkill for this use case |
| sysbox | Secure container runtime | container runtime | Complex setup |

### bubblewrap (Recommended Addition)

**Source:** [bubblewrap releases](https://github.com/containers/bubblewrap) (v0.11.1, March 2026)

**What it adds:**
- Unprivileged sandboxing (does not require root in container)
- Mount namespaces with tmpfs root
- User, IPC, PID, network, UTS namespaces
- PR_SET_NO_NEW_PRIVS to prevent privilege escalation
- bind-mount control with readonly/nodev options
- seccomp filter integration

**Why bubblewrap for UltiCode:**
- Code execution sandbox needs filesystem isolation (tmpfs root)
- Network namespace isolation prevents exfiltration
- PID namespace hides host processes
- Works with existing cgroup v2 + seccomp stack
- Lower overhead than gVisor

### landlock (Future Consideration)

- Linux kernel 5.13+ (most systems now support)
- Filesystem access control without root
- Complementary to bubblewrap, not a replacement
- Not yet widely adopted in container runtimes

### Recommendation

| Addition | Priority | Justification |
|----------|----------|---------------|
| **bubblewrap** | HIGH | Adds mount/network/PID namespace isolation for code execution |
| **landlock** | LOW | Future enhancement, kernel 5.13+ required |

**Integration approach:**
- Add bubblewrap as a system dependency in Docker/PM2 setup
- Wrap code execution in bubblewrap invocation
- Existing cgroup v2 + seccomp remain as first layer
- bubblewrap adds filesystem and namespace isolation

---

## 4. Summary: Stack Changes for v3.0

| Area | Current | Recommended | Change Type |
|------|---------|-------------|-------------|
| SpringDoc | 2.6.0 | 2.8.17 (safe upgrade) | Version bump (defer 3.x until SB 4) |
| Spring Boot | 3.2.5 | 3.2.5 (defer 4.x) | No change |
| Java | 17 | 17 (defer 21) | No change |
| Console vue-i18n | 11.3.2 | 11.3.2 | No change |
| Management vue-i18n | 10.0.8 | 11.3.2 | Upgrade |
| Sandbox | cgroup v2 + seccomp | + bubblewrap | New dependency |

---

## Sources

- [springdoc-openapi releases](https://github.com/springdoc/springdoc-openapi/releases) — HIGH confidence (official)
- [vue-i18n npm package](https://www.npmjs.com/package/vue-i18n) — HIGH confidence (npm registry)
- [intlify/vue-i18n releases](https://github.com/intlify/vue-i18n/releases) — HIGH confidence (official)
- [bubblewrap GitHub](https://github.com/containers/bubblewrap) — HIGH confidence (official)
- Context7 `/springdoc/springdoc-openapi` — HIGH confidence (library docs)
- Context7 `/intlify/vue-i18n` — HIGH confidence (library docs)
