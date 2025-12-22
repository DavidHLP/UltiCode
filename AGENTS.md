# Repository Guidelines

## Project Structure & Module Organization
- `frontend/`: Vue 3 + Vite app. Source in `frontend/src`, static assets in `frontend/public`, build output in `frontend/dist`.
- `backend/`: NestJS API. Source in `backend/src`, Prisma schema/seed in `backend/prisma`, build output in `backend/dist`.
- Shared root scripts live in `package.json`. Each app has its own `package.json` and tooling config.

## Build, Test, and Development Commands
Root helpers (run from repo root):
- `npm run dev`: run frontend + backend in parallel.
- `npm run dev:frontend`: run Vite dev server with lint/type-check/format.
- `npm run dev:backend`: run Nest dev server (includes lint/type-check/format + db reset).
- `npm run lint` / `npm run format` / `npm run type-check`: run both apps.

Frontend:
- `npm run build --prefix frontend`: build Vite app.
- `npm run test --prefix frontend`: run Vitest once.

Backend:
- `npm run build --prefix backend`: compile Nest app.
- `npm run test --prefix backend`: run Jest (specs in `backend/src/**/*.spec.ts`).
- `npm run db:reset --prefix backend`: reset DB and run migrations (destructive).

## Coding Style & Naming Conventions
- TypeScript across frontend and backend; follow ESLint + Prettier (`npm run lint`, `npm run format`).
- Vue components use PascalCase filenames (e.g., `ProblemDetailView.vue`).
- Composables/hooks use `useX` naming (e.g., `useProblemDetail`).
- Use existing API mappers for snake_case ↔ camelCase conversions.

## Testing Guidelines
- Backend uses Jest; name tests `*.spec.ts` and keep them near the source.
- Frontend uses Vitest; add tests alongside components or under `frontend/src`.
- Run relevant tests before PRs; add coverage only when requested.

## Commit & Pull Request Guidelines
- Commit messages follow Conventional Commits (e.g., `feat: ...`, `fix: ...`).
- PRs should include: summary of changes, testing notes (commands run), and screenshots for UI changes.

## Configuration Tips
- Frontend API base URL via `VITE_API_BASE_URL`.
- Backend DB setup is managed by Prisma in `backend/prisma`.
