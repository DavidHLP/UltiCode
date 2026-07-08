# Shared Packages AGENTS.md

> **Part of**: UltiCode (see [root AGENTS.md](../AGENTS.md) for project context)
> **Last Updated**: 2026-07-06

14 pnpm workspace packages consumed by `console/` and `management/` via symlinks. Each owns a single seam (deep-module pattern, ADR-0004 / ADR-0005 / ADR-0011).

## STRUCTURE

```
shared/
├── auth-core/          # Cookie, CSRF, auth-state machine, refresh coordinator, permissions
├── auth-ui/            # LoginForm, RegisterForm, AuthLayout + visual primitives
├── badge-config/       # Color maps: SUBMISSION_STATUS_COLOR_MAP, DIFFICULTY_COLOR_MAP
├── datetime-utils/     # formatDate, formatDuration, relativeTime helpers
├── design-system/      # Design tokens (CSS-only package, no index.ts)
├── domain-types/       # PageResult<T>, Problem, Contest, Comment, ForumPost — cross-stack DTO contract
├── http-client/        # createHttpClient() — CSRF/refresh/dedup/retry/401 seam
├── i18n-storage/       # Persisted translation-key storage backend
├── locale-composable/  # useLocale composable (builds on i18n-storage)
├── markdown-utils/     # renderMarkdown() + sanitizeHtml() (MarkdownIt + KaTeX + hljs + DOMPurify)
├── sandbox-types/      # DFormVerdict, OJDataType, DFormEnvelope (cross-language with docker/sandbox/)
├── sidebar-menu/       # SidebarMenuItem, SidebarGroupCollapsible types
├── submission-status/  # VERDICT_TO_STATUS_KEY + VERDICT_COLOR_MAP
└── theme/              # ThemeMode, useTheme, typography density, FOUC bootstrap
```

## Package Reference

| Package | Exports | Consumed By |
|---------|---------|-------------|
| `auth-core` | Cookie parse, CSRF manager, auth-state machine, `setOnAuthFailure`, permission checker, `cn` util | Both apps (via `@/shared/auth-core/src`) |
| `auth-core` subpaths | `@ulticode/auth-core/src/csrf`, `…/axiosCsrfInterceptor`, `…/refreshCoordinator` | Sibling shared packages (subpath exports) |
| `auth-ui` | LoginForm, RegisterForm, AuthLayout, AuthButton/Input/Card | Both apps |
| `domain-types` | `PageResult<T>`, `Problem`, `Contest`, `ContestStatus`, `Comment`, `ForumPost`, `UserStats`, `ProblemList` | Both apps (canonical DTO contract — replaces 11× `PageResult` re-declarations) |
| `http-client` | `createHttpClient()` | Both apps (replaces duplicated `request.ts`) |
| `markdown-utils` | `renderMarkdown()`, `sanitizeHtml()` | Both apps (owns sanitization pipeline) |
| `sandbox-types` | `DFormVerdict`, `OJDataType`, `DFormEnvelope` | Both apps + `docker/sandbox/` contract |
| `badge-config` | `SUBMISSION_STATUS_COLOR_MAP`, `DIFFICULTY_COLOR_MAP`, `badge()` | Management only (console has 0 imports — asymmetry noted, contract to align) |
| `submission-status` | `VERDICT_TO_STATUS_KEY`, `VERDICT_COLOR_MAP`, `getVerdictColor()` | Both apps |
| `sidebar-menu` | `SidebarMenuItem`, `SidebarMenuSubItem`, `SidebarGroupCollapsible` | Both apps |
| `datetime-utils` | `formatDate`, `formatDuration`, `relativeTime` helpers | Both apps (thin — 1 consumer each, justifies seam for consistent date formatting) |
| `i18n-storage` | Persisted translation-key storage backend | Both apps (infrastructure singleton for locale persistence) |
| `locale-composable` | `useLocale` composable (builds on `i18n-storage`) | Both apps (thin — 1 consumer each, wraps i18n-storage) |
| `theme` | `ThemeMode`, `useTheme`, `useColorTheme`, `applyTypographyDensity`, tokens | Both apps + `public/theme-bootstrap.js` |
| `design-system` | CSS tokens via `shared/design-system/style.css` (no JS exports) | Both apps |

## WHERE TO LOOK

| Task | Location |
|------|----------|
| Add new shared package | Create `shared/<name>/` with `package.json` + `src/index.ts` — `pnpm-workspace.yaml` glob auto-discovers |
| Add CSRF/auth logic | `auth-core/src/` — expose via index.ts or subpath export |
| Change axios behavior | `http-client/src/createHttpClient.ts` |
| Change Markdown rendering | `markdown-utils/src/` — **always** keep DOMPurify in the pipeline |
| Change theme tokens | `theme/src/` — update `applyThemeToDOM.ts` + `public/theme-bootstrap.js` in both apps |
| Add badge color mapping | `badge-config/src/` or `submission-status/src/` (verdict→color link) |
| Wire package into an app | Add to app's `tsconfig.app.json` `include` array for type-checking |

## CONVENTIONS

### Deep-Module Pattern (ADR-0004 / ADR-0005 / ADR-0011)
Each package owns **one seam**. The interface is small; the implementation is deep.
When logic is duplicated between `console/` and `management/`, extract a shared package
and leave each app as a thin re-export seam.

### Consumption Pattern
```
console/src/shared/  →  symlink to ../../shared/
management/src/shared/  →  symlink to ../../shared/
```
Import path: `@/shared/<package>/src` (configured in each app's `tsconfig.app.json`).

### Subpath Exports (auth-core only)
`auth-core` exposes internal seams without re-exporting the full index:
```json
"exports": {
  ".": "./src/index.ts",
  "./src/csrf": "./src/csrf/index.ts",
  "./src/axiosCsrfInterceptor": "./src/axiosCsrfInterceptor.ts",
  "./src/refreshCoordinator": "./src/refreshCoordinator.ts"
}
```

### Theme Bootstrap Singleton
`console/public/theme-bootstrap.js` and `management/public/theme-bootstrap.js` are external
scripts matching `shared/theme/src/applyThemeToDOM.ts`. **Never** duplicate theme init logic
in `main.ts`, `onMounted`, or component code — causes hydration mismatch.

## ANTI-PATTERNS

- **Never call `markdown-it` directly** — use `renderMarkdown()` from `markdown-utils`
- **Never bypass DOMPurify** — sanitization is baked into the `renderMarkdown()` pipeline
- **Never set `data-theme` attribute from app code** — only the theme system writes it
- **Never duplicate logic between console and management** — extract a shared package instead
- **Never import shared package internals directly** — use the index.ts or declared subpath exports
- **`useThemeForceUpdate`** is test-only — never import in production code

## COMMANDS

```bash
# Per-package (run in each shared/<name>/)
pnpm test           # vitest --run
pnpm type-check     # tsc --noEmit

# auth-core is the most tested (5 spec files); others have 0-1 tests
cd shared/auth-core && pnpm test && pnpm type-check
cd shared/markdown-utils && pnpm test && pnpm type-check
cd shared/http-client && pnpm test && pnpm type-check
```

## NOTES

- **Auto-discovery**: `pnpm-workspace.yaml` glob `shared/*` picks up new packages automatically.
- **Test coverage is thin**: `markdown-utils` and `http-client` have 1 test each; `auth-core` has 5.
- **`design-system` has no `index.ts`** — it's a CSS-only package imported via `style.css`.
- **Console excludes** `**/auth-core/**` and `**/shared/theme/**` from its vitest run.
- **Management excludes** `**/shared/theme/**` only.
- **New packages**: must be added to the consuming app's `tsconfig.app.json` `include` array.
