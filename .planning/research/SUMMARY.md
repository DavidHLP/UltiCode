# Project Research Summary

**Project:** UltiCode v3.0 Milestone — 平台质量与用户体验
**Domain:** Platform quality: API documentation (springdoc), code execution sandbox hardening, frontend internationalization
**Researched:** 2026-04-22
**Confidence:** MEDIUM-HIGH (STACK/FEATURES HIGH; ARCHITECTURE/PITFALLS MEDIUM due to limited web research)

---

## Executive Summary

UltiCode v3.0 delivers three user-facing quality improvements: OpenAPI 3.0 documentation via springdoc, continued sandbox hardening for the code execution platform, and frontend internationalization (i18n) for both Console and Management frontends. The most significant finding is that springdoc 3.x requires Spring Boot 4.0+, making it a hard blocker that cannot be addressed in v3.0 — the safe path is springdoc 2.6.0 to 2.8.17, which works with the current SB 3.2.5. The sandbox has a critical flag-ordering bug (`--read-only` placed before `--tmpfs`) that silently breaks `/tmp` writes in production, plus a missing volume mount for the seccomp profile. Vue i18n has a version mismatch (Console 11.3.2, Management 10.0.8) that must be resolved before i18n work proceeds.

---

## Key Findings

### Recommended Stack

**springdoc OpenAPI:** Upgrade 2.6.0 to 2.8.17 — latest v2.x, compatible with Spring Boot 3.2.5, delivers OpenAPI 3.0 with improved Swagger UI (5.32.2). springdoc 3.x requires SB 4.0+ and Java 21 — not viable for v3.0.

**Vue i18n:** Align Management to Console by upgrading from 10.0.8 to 11.3.2. Console is already on the latest. Both frontends should use vue-i18n 11.x with Composition API mode (`legacy: false`).

**Sandbox hardening:** Add bubblewrap as a system dependency for additional namespace isolation (mount, network, PID). Current cgroup v2 + seccomp stack is solid foundation; bubblewrap adds unprivileged sandboxing without changing the container runtime.

**Core technologies:**
- `springdoc-openapi-starter-webmvc-ui` 2.8.17 — OpenAPI 3.0 docs, Swagger UI
- `vue-i18n` 11.3.2 — frontend i18n (both frontends aligned)
- `bubblewrap` v0.11.1 — unprivileged namespace sandbox for code execution
- Spring Boot 3.2.5 (no change, defer 4.x to post-v3.0)

### Expected Features

**Must have (table stakes):**
- SpringDoc 2.6.0 to 2.8.17 upgrade with enriched endpoint documentation
- Language switcher in Console UI with persistent localStorage preference
- Lazy-loaded translation files (avoid loading all locales at startup)
- Per-language resource limits (timeout/memory configurable per language)
- Seccomp profile path properly volume-mounted into sandbox containers

**Should have (competitive):**
- `/tmp` as dedicated tmpfs with size enforcement (currently has flag-ordering bug)
- Backend I18nController enrichment for user-facing translation content
- Seccomp syscall audit to verify coverage for all 5 supported languages
- Locale composable (`useLocale.ts`) with localStorage persistence and backend sync

**Defer (v2+):**
- springdoc 3.x upgrade (requires SB 4.0 + Java 21 migration)
- Japanese translations
- User namespace remapping (high complexity, rootless Docker changes)
- FUSE-based filesystem restrictions

### Architecture Approach

The v3.0 architecture follows a three-track parallel structure. The SpringDoc track is a straightforward version bump with annotation enrichment. The Sandbox track fixes two critical bugs (flag ordering, seccomp path mounting) and adds per-language limits plus tmpfs enforcement. The i18n track aligns vue-i18n versions, then builds the locale composable and lazy translation file architecture. All three tracks are independent and can be executed in parallel by different agents.

**Major components:**
1. `CodeExecutionService` — Docker-based code execution sandbox; needs flag reordering and seccomp volume mount fix
2. `I18nController` — existing backend locale preference storage; needs enrichment with user-facing strings
3. Console + Management frontends — vue-i18n setup; needs version alignment and lazy loading architecture

### Critical Pitfalls

1. **Sandbox `--read-only` before `--tmpfs` bug** — `buildDockerCommand()` in `SandboxServiceImpl` places `--read-only` before `--tmpfs /tmp:rw,exec,size=64m`. Docker processes flags left-to-right, so the root filesystem is made read-only before tmpfs is evaluated. Fix: reorder so `--tmpfs` appears before `--read-only`.

2. **Seccomp profile path not volume-mounted** — `DockerSandboxConfig.seccompProfilePath()` returns `/docker/sandbox/seccomp-profile.json` but this host path is never volume-mounted into the container. Fix: add `-v $(pwd)/docker/sandbox:/docker/sandbox:ro` to docker run command.

3. **Vue i18n missing keys silently suppressed** — Both frontends use `silentTranslationWarn: true` / `missingWarn: false`, hiding missing translation keys in production. Users see raw keys like `submission.status.passed`. Fix: enable `missingWarn` in development, add `check.ts` to CI gate.

4. **Vue i18n eager locale loading** — Both frontends load all locales at startup via `messages: { "zh-CN": zhCN, "en-US": enUS }`. For large translation files this adds 500KB+ to bundle. Fix: use lazy dynamic import for non-active locales.

5. **springdoc 3.x blocked by Spring Boot 4.0** — springdoc 3.0.3 requires SB 4.0.5, which requires Java 21. Attempting to upgrade springdoc to 3.x on SB 3.2.5 causes `ClassNotFoundException`. Do not attempt; document as blocked.

---

## Implications for Roadmap

Based on research, suggested phase structure:

### Phase 1: SpringDoc Upgrade (2.6.0 to 2.8.17)
**Rationale:** Lowest risk change in the milestone — pure version bump with no architectural impact.
**Delivers:** OpenAPI 3.0 spec at `/api-docs`, improved Swagger UI, `@Range` constraint annotation support.
**Uses:** springdoc 2.8.17 (from STACK.md).
**Implements:** Annotation enrichment on key endpoints (`@Operation`, `@ApiResponse`).
**Avoids:** springdoc 3.x path collision and schema breaking changes (documented in PITFALLS.md as post-SB4 concerns).

### Phase 2: Sandbox Hardening
**Rationale:** Two critical bugs must be fixed before any new sandbox features. Per-language limits and tmpfs enforcement build on the fixed foundation.
**Delivers:** Working `/tmp` writes, loadable seccomp profile, per-language timeouts/memory, bubblewrap integration.
**Implements:** `CodeExecutionService.buildDockerCommand()` flag reordering, seccomp volume mount, per-language `SandboxConfig`.
**Avoids:** Flag-ordering bug (PITFALLS #4), seccomp path resolution bug (PITFALLS #5).

### Phase 3: Vue i18n Alignment and Architecture
**Rationale:** Version mismatch between frontends must be resolved before any i18n work. The locale composable and lazy loading are prerequisites for the UI language switcher.
**Delivers:** Aligned vue-i18n 11.3.2 on both frontends, `useLocale` composable, lazy-loaded translation files, language switcher in Console header.
**Implements:** `useLocale.ts` composable, `i18n/index.ts` lazy loading setup, `check.ts` CI gate.
**Avoids:** Silent missing key issue (PITFALLS #6), eager locale loading (PITFALLS #7).

### Phase Ordering Rationale

- **Independence:** All three tracks are independent — no cross-dependencies between SpringDoc, Sandbox, and i18n changes.
- **Bug priority:** Sandbox bugs (flag ordering, seccomp path) should be fixed early since they affect production correctness.
- **Prerequisite ordering:** i18n version alignment (Management 10.0.8 → 11.3.2) must happen before any i18n architecture work.
- **SpringDoc safe path:** The 2.6.0 → 2.8.17 upgrade is safe to do anytime; no blockers.

### Research Flags

**Needs research during planning:**
- **Phase 2 (Sandbox):** bubblewrap integration testing — verify it works with existing Docker setup and does not conflict with seccomp profiles. No prior phase research available.
- **Phase 3 (i18n):** `check.ts` CI gate implementation — existing script exists but needs integration into the pipeline.

**Standard patterns (skip research-phase):**
- **Phase 1 (SpringDoc):** Version bump pattern is well-documented; annotation enrichment follows existing `@Operation`/`@ApiResponse` patterns already in codebase.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | springdoc version compatibility verified via official releases; vue-i18n versions verified via npm registry; bubblewrap verified via GitHub releases |
| Features | HIGH | Based on existing codebase analysis plus confirmed ecosystem patterns; feature priorities align with v2.0 milestone conventions |
| Architecture | MEDIUM | ARCHITECTURE.md focused on v1.9 (Achievement N+1, Follow indexes, JaCoCo) — sandbox/i18n architectural patterns inferred, not deeply verified |
| Pitfalls | MEDIUM | Critical bugs (flag ordering, seccomp path) confirmed from codebase inspection; vue-i18n config issues confirmed; but Context7 was unavailable for some verifications |

**Overall confidence:** MEDIUM-HIGH

**Gaps to Address:**
- **Sandbox bubblewrap integration:** No prior research on how bubblewrap integrates with the existing Docker-based sandbox. Needs integration testing plan during Phase 2 planning.
- **i18n `check.ts` CI integration:** Existing script needs to be wired into the pipeline. Specific CI platform (GitHub Actions, Jenkins) not specified — needs validation.
- **Per-language resource profiling:** Recommended per-language limits need actual profiling data. Default limits in `sandboxConfig` are assumed; should be validated with real submissions during Phase 2.

---

## Sources

### Primary (HIGH confidence)
- springdoc-openapi releases (github.com/springdoc/springdoc-openapi) — version compatibility table, SB 4.0 requirement
- vue-i18n npm package (npmjs.com/package/vue-i18n) — version 11.3.2 confirmed as latest
- intlify/vue-i18n releases (github.com/intlify/vue-i18n) — ecosystem version map
- bubblewrap GitHub (github.com/containers/bubblewrap) — v0.11.1 feature set
- Existing codebase: `CodeExecutionService.java`, `DockerSandboxConfig.java`, `console/src/i18n/index.ts`, `management/src/i18n/index.ts`

### Secondary (MEDIUM confidence)
- PITFALLS.md — critical bugs confirmed from code inspection but some mitigations (e.g., bubblewrap integration) are inferential
- FEATURES.md — feature priorities based on v2.0 milestone patterns; actual user value may vary

### Tertiary (LOW confidence)
- ARCHITECTURE.md — sandbox/i18n architectural patterns are inferred; no phase-specific research exists for v3.0 sandbox or i18n architecture

---
*Research completed: 2026-04-22*
*Ready for roadmap: yes*
