# Repository Guidelines

## Project Structure

- `backend/` NestJS API server. Code in `backend/src` (modules, controllers, services, DTOs, entities); database schema and migrations in `backend/prisma`; utility scripts in `backend/scripts`.
- `frontend/` public Vue 3 app. Pages in `frontend/src/views`, reusable UI in `frontend/src/components`, Pinia stores in `frontend/src/stores`, API clients in `frontend/src/api`, assets in `frontend/public` and `frontend/src/assets`.
- `admin-frontend/` Vue 3 admin app with a similar `src` layout and its own Vite config.
- Root `package.json` orchestrates monorepo commands.

## Build, Test, and Development Commands

- `npm install` at repo root; Node 20+ recommended.
- `npm run dev` runs frontend + backend via `concurrently`. Note: backend `start:dev` runs Prisma generate, lint/type-check/format, and `prisma migrate reset --force`.
- `npm run dev:frontend` / `npm run dev:backend` for single services.
- `npm run lint`, `npm run format`, `npm run type-check` run across both apps.
- Backend: `npm run build --prefix backend`, `npm run test --prefix backend`, `npm run test:cov --prefix backend`.
- Frontend: `npm run dev --prefix frontend`, `npm run build --prefix frontend`, `npm run test --prefix frontend`.
- Admin: `npm run dev --prefix admin-frontend`, `npm run build --prefix admin-frontend`.

## Coding Style & Naming Conventions

- ESLint + Prettier are the source of truth. Backend Prettier uses single quotes and trailing commas; admin frontend uses no semicolons, single quotes, and 100 char lines.
- Vue components are PascalCase with `<script setup lang="ts">` and live under `src/components` or `src/views`.
- NestJS files follow `*.module.ts`, `*.controller.ts`, `*.service.ts`, `*.dto.ts`, `*.entity.ts`.
- Frontend imports can use `@/` (maps to `frontend/src`).

## Testing Guidelines

- Backend uses Jest; tests live alongside code as `backend/src/**/*.spec.ts`.
- Frontend uses Vitest (`npm run test --prefix frontend`); follow Vitest defaults (`*.test.ts`/`*.spec.ts`) when adding tests.
- No explicit coverage thresholds are configured; run coverage on significant logic changes.

## Commit & Pull Request Guidelines

- Commit messages follow Conventional Commits (e.g., `feat(scope): ...`, `docs: ...`).
- PRs should include a concise summary, testing notes, and screenshots for UI changes.
- **Workflow Requirement**: After completing a task, you **MUST** run `npm run type-check`, `npm run lint`, and `npm run format`. You may only commit or consider the task complete if there are no errors.
- **Commit Scope**: You are only allowed to commit files that you have modified yourself.
- Call out Prisma schema/migration changes and include generated migration files.

## Configuration & Database

- Backend reads `DATABASE_URL` from `backend/.env`.
- Use Prisma for schema changes: `npm run prisma:migrate --prefix backend`, then `npm run db:seed --prefix backend` for local data.

## Admin Frontend Design Guide

Reference templates in `admin-frontend/src/template/` for consistent dashboard UI:

### Layout Structure

- `SidebarProvider` wraps layout with CSS variables (`--sidebar-width`, `--header-height`)
- `AppSidebar` (collapsible offcanvas) contains header, content sections, footer
- `SidebarInset` holds `SiteHeader` + main content

### Core Components

| Component              | Location  | Purpose                                   |
| ---------------------- | --------- | ----------------------------------------- |
| `AppSidebar`           | template/ | Collapsible sidebar with nav sections     |
| `SiteHeader`           | template/ | Page header with title + actions          |
| `SectionCards`         | template/ | Metric cards grid (4 columns responsive)  |
| `ChartAreaInteractive` | template/ | Area chart with time range filter         |
| `DataTable`            | template/ | TanStack table with drag-drop, pagination |
| `NavMain`              | template/ | Primary navigation menu                   |
| `NavDocuments`         | template/ | Document links with action menus          |
| `NavSecondary`         | template/ | Settings/Help links (bottom)              |
| `NavUser`              | template/ | User avatar + dropdown menu               |

### Libraries Used

- **UI**: `shadcn-vue` components (Card, Button, Badge, Table, Tabs, Select, Avatar, DropdownMenu)
- **Icons**: `@tabler/icons-vue`
- **Tables**: `@tanstack/vue-table`
- **Charts**: `@unovis/vue`
- **Drag & Drop**: `dnd-kit-vue`
- **Validation**: `zod`

### Design Patterns

- Container queries for responsive grids: `@container/main`, `@container/card`
- Gradient card backgrounds: `from-primary/5 to-card`
- Data attributes for state: `data-[state=open]:bg-sidebar-accent`
- Tabular numbers for metrics: `tabular-nums`
- Status badges with trend icons: `<Badge variant="outline"><IconTrendingUp />+12.5%</Badge>`

### Data Table Features

- Drag-and-drop rows (`DraggableRow`, `DragHandle`)
- Column visibility toggle
- Row selection with checkboxes
- Pagination with configurable page size
- Status indicators (done/in-progress icons)
- Row action dropdown menus
