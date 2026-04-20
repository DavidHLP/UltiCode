# Phase 24: PM2 / Build Infrastructure - Context

**Gathered:** 2026-04-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Build system uses standard tooling for environment management.

**INFRA-01:** PM2 ecosystem.config.cjs replaces custom .env parser with dotenv npm package
**INFRA-02:** Maven build order documented (recommend-api must mvn install before backend-spring)

</domain>

<decisions>
## Implementation Decisions

### dotenv Integration (INFRA-01)

- **D-01:** Replace custom .env fs/readFileSync parser with `dotenv` npm package
- **D-02:** `dotenv` config should be called at top of ecosystem.config.cjs before module exports
- **D-03:** All existing env var fallbacks (`process.env.X || 'default'`) remain unchanged — dotenv only replaces the .env file loading mechanism
- **D-04:** No runtime env var validation or schema enforcement needed — INFRA-01 is purely a parser swap

### Maven Build Documentation (INFRA-02)

- **D-05:** Build order documented in CLAUDE.md (under Backend Startup Issues section already exists)
- **D-06:** Maven build order: `recommend-api` module must be installed before `backend-spring` due to `recommend-api` dependency in backend pom.xml

### Claude's Discretion

- npm package installation approach (dotenv/config vs dotenv/config with path)
- Exact location in CLAUDE.md to insert build order note

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Infrastructure
- `ecosystem.config.cjs` — Current PM2 config with custom .env parser (lines 1-14) — MUST replace
- `backend-spring/start.cjs` — Spring Boot launcher script
- `backend-spring/pom.xml` — Maven config showing `recommend-api` dependency (line 215)
- `.planning/REQUIREMENTS.md` — INFRA-01, INFRA-02 requirements
- `.planning/ROADMAP.md` — Phase 24 success criteria

### No external specs

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ecosystem.config.cjs` — Already has custom .env parser (lines 1-14) — needs replacement
- `backend-spring/start.cjs` — Maven wrapper launcher for backend

### Established Patterns
- PM2 ecosystem uses `dotenv` npm package (standard approach)
- Node.js `require('dotenv').config()` pattern widely documented

### Integration Points
- ecosystem.config.cjs is entry point for all 5 PM2 services (9001-9005)
- Maven build order affects CI/CD pipeline in `backend-spring/`

</code_context>

<specifics>
## Specific Ideas

No specific references from discussion — standard tooling swap with documented approach.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 24-pm2-build-infrastructure*
*Context gathered: 2026-04-20*
