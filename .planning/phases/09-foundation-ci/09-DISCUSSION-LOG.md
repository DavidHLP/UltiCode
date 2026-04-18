# Phase 9: Foundation + CI - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-18
**Phase:** 09-foundation-ci
**Areas discussed:** CI workflow structure, Dockerfile fixes, Nginx CSP, Secrets mapping, Build caching
**Mode:** Auto (non-interactive)

---

## CI Workflow Structure

| Option | Description | Selected |
|--------|-------------|----------|
| Consolidate into single ci.yml with dorny/paths-filter | Requirements specify single ci.yml; dorny/paths-filter enables fine-grained conditional jobs within one workflow | ✓ |
| Keep existing separate CI files | ci-backend.yml and ci-frontend.yml already work with native path triggers | |
| Create unified ci.yml that calls existing workflows | Reusable workflow pattern — ci.yml orchestrates, existing files implement | |

**Auto-selected:** Consolidate into single ci.yml with dorny/paths-filter (matches REQUIREMENTS CI-01)

---

## JAR Naming Fix (FOUND-01)

| Option | Description | Selected |
|--------|-------------|----------|
| Use Maven finalName in pom.xml | Set `<finalName>app</finalName>` for predictable output — survives version bumps | ✓ |
| Hardcode correct version in Dockerfile | Change `0.0.1-SNAPSHOT` to `1.0.0` in Dockerfile COPY | |
| Use wildcard in Dockerfile COPY | `COPY --from=builder /app/target/*.jar ./app.jar` | |

**Auto-selected:** Maven finalName (most robust — eliminates version matching permanently)

---

## pnpm-lock.yaml Copy (FOUND-02)

| Option | Description | Selected |
|--------|-------------|----------|
| Add explicit COPY for pnpm-lock.yaml before install | Standard Docker layer caching pattern — lockfile change invalidates only install layer | ✓ |
| Restructure Dockerfile to copy entire directory first | Simpler but invalidates cache on any source change | |

**Auto-selected:** Explicit COPY for pnpm-lock.yaml (best practice for layer caching)

---

## Nginx CSP (FOUND-03)

| Option | Description | Selected |
|--------|-------------|----------|
| No changes needed — CSP already correct | `'self' ${API_ORIGIN:-}` covers same-origin proxy, proxy_pass handles Docker networking | ✓ |
| Add backend:9001 to connect-src explicitly | Redundant — browser connects to nginx origin, not backend directly | |

**Auto-selected:** No changes (verified correct in code review)

---

## Secrets Mapping Document

| Option | Description | Selected |
|--------|-------------|----------|
| Markdown table in docs/secrets-mapping.md | Human-readable, easy to search, matches project documentation style | ✓ |
| YAML structured file | Machine-parseable but harder to read as documentation | |
| Inline in CLAUDE.md | Already documented partially, but would bloat the file | |

**Auto-selected:** Markdown table in docs/secrets-mapping.md (consistent with project docs)

---

## Docker Build Verification

| Option | Description | Selected |
|--------|-------------|----------|
| Path-filtered — only when Docker files change | Saves ~10 min CI time per PR; Dockerfile breakage is caught by path triggers | ✓ |
| Run on every PR | Comprehensive but wasteful — most PRs don't touch Dockerfiles | |

**Auto-selected:** Path-filtered (balanced approach — verify only when relevant)

---

## Claude's Discretion

- Exact job dependency graph in ci.yml
- Error handling patterns in CI
- Test artifact configuration details
- Concurrency group naming

## Deferred Ideas

None — all decisions stayed within phase scope.
