# Update AGENTS.md for UltiCode Repository

## Objective
Update the existing `AGENTS.md` file at `/home/davidhlp/project/UltiCode-Public-Next/AGENTS.md` with recent learnings and corrections from the CSRF token rotation fix implementation.

## Current State
- AGENTS.md exists and is comprehensive (314 lines, last updated 2026-05-04)
- Content is mostly accurate but missing recent CSRF implementation details
- Some minor updates needed based on codebase inspection

## Required Changes

### 1. Update Header
- Change "Last Updated" from `2026-05-04` to `2026-05-05`
- Keep context note about init-deep analysis

### 2. Add New Section: Shared Package Gotchas
Insert after "Testing Quirks" section:

```markdown
### Shared Package Gotchas

- `shared/auth-core/package.json` uses TypeScript 5.9.3 (not 6.x like frontends) — has its own `type-check` script
- `shared/auth-core` tests run with `cd shared/auth-core && pnpm test` (uses vitest)
- Console imports shared code directly via path aliases; Management uses symlink
- Adding new exports to `shared/auth-core/src/index.ts` requires both frontends to be checked for import path issues
```

### 3. Add New Section: CSRF Token Rotation
Insert after "Shared Package Gotchas" section:

```markdown
### CSRF Token Rotation

- Backend implements strict token rotation: validates token, generates new one, returns via `X-New-CSRF-Token` header
- Old token has 5-minute grace period (set via Redis TTL) for concurrent requests
- Both frontends use shared `createCsrfAxiosInterceptor()` from `@/shared/auth-core/src` for:
  - Request: attaches `X-CSRF-Token` for non-GET/HEAD/OPTIONS
  - Response: captures `X-New-CSRF-Token` from 2xx responses
  - Error: 403 CSRF errors trigger one retry with fresh token
```

### 4. Content to Preserve (Verified Accurate)
- All Quick Start commands
- Architecture overview
- Backend Response Format
- Frontend API Client Pattern
- Authentication Flow
- curl examples
- Frontend Design System
- Shared Code section
- Database Migrations
- All Critical Pitfalls
- Port Reference
- Debugging commands
- Environment Variables
- CI Pipeline description
- Additional Documentation links
- Behavioral Guidelines

### 5. No Changes Needed
- Build Order (still accurate)
- Frontend dev script trap (still accurate)
- ESLint Version Split (still accurate)
- No Git Hooks (still accurate)
- PM2 Environment Variables (still accurate)
- Backend Startup Issues (still accurate)
- Docker Services (still accurate)
- Testing Quirks (still accurate)

## Verification Steps

1. Read the updated AGENTS.md file
2. Verify all new sections are present
3. Verify existing content is preserved
4. Check that no stale or incorrect information remains

## Success Criteria
- AGENTS.md updated with new CSRF and shared package information
- All existing accurate content preserved
- File remains concise and high-signal
