# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

UltiCode is a full-stack competitive programming platform inspired by LeetCode. It consists of a Vue 3 frontend and a NestJS backend with MySQL database managed through Prisma ORM.

## Development Commands

### Root Level (Development)
```bash
npm run dev                    # Run both frontend and backend concurrently
npm run dev:frontend           # Run frontend only (Vite dev server)
npm run dev:backend            # Run backend only (NestJS with watch mode)
npm run lint                   # Lint both frontend and backend
npm run format                 # Format both frontend and backend
npm run type-check             # Type check both frontend and backend
```

### Frontend (from /frontend)
```bash
npm run dev                    # Lint, type-check, format, then start Vite dev server
npm run build                  # Build for production
npm run preview                # Preview production build
npm run type-check             # Run Vue type checking
npm run lint                   # Run ESLint with auto-fix
npm run format                 # Run Prettier
npm test                       # Run Vitest tests once
npm run test:watch             # Run Vitest in watch mode
npm run test:coverage          # Run tests with coverage
npm run validate:mocks         # Validate mock data structure
npm run validate:mocks:verbose # Validate mocks with verbose output
npm run validate:mocks:strict  # Validate mocks in strict mode
```

### Backend (from /backend)
```bash
npm run start:dev              # Lint, type-check, format, generate Prisma, reset DB, start with watch
npm run start:dev:reset        # Reset DB and start with watch (skip other checks)
npm run build                  # Build NestJS application
npm run start:prod             # Start production server
npm run lint                   # Run ESLint with auto-fix
npm run format                 # Run Prettier
npm run type-check             # Type check without emitting files
npm test                       # Run Jest tests
npm run test:watch             # Run Jest in watch mode
npm run test:cov               # Run tests with coverage
npm run test:e2e               # Run end-to-end tests
npm run prisma:generate        # Generate Prisma client
npm run prisma:migrate         # Create and apply migration
npm run db:reset               # Reset database and run seed
npm run db:seed                # Seed database with initial data
```

## Architecture

### Backend Architecture (NestJS)

The backend follows NestJS modular architecture with the following key modules:

**Module Structure:**
- `UserModule` - User management and profiles
- `ProblemModule` - Problem CRUD, details, examples, languages, tags
- `ProblemListModule` - Problem lists with user-specific categorization
- `SolutionModule` - User solutions with comments
- `ContestModule` - Contest management, rankings, participants
- `ForumModule` - Community posts and comments
- `SubmissionModule` - Code submission tracking with performance stats
- `VoteModule` - Vote tracking (upvote/downvote)
- `EdgeOperationsModule` - Generic interaction system (votes, favorites, charges, analyze)
- `ViewModule` - View count tracking for solutions and forum posts
- `AuthModule` - JWT-based authentication
- `ProblemNoteModule` - User-specific problem notes

**Database:**
- Uses MySQL with Prisma ORM
- TypeORM is configured but Prisma is primary ORM
- Database connection hardcoded in `app.module.ts` (host: localhost, user: root, password: 123456, db: ulticode)
- Prisma schema in `backend/prisma/schema.prisma` is the source of truth
- Seed data located in `backend/prisma/seed/data/`

**Edge Operations System:**
The `EdgeOperationsModule` implements a generic interaction system that handles multiple operation types (VOTE_UP, VOTE_DOWN, FAVORITE, CHARGE, ANALYZE) across different target types (SOLUTION, SOLUTION_COMMENT, FORUM_POST, FORUM_COMMENT, PROBLEM, PROBLEM_LIST). This unified approach replaces separate favorite/like systems with a single edge table.

**Important Backend Patterns:**
- BigInt serialization is globally patched in `main.ts` to convert BigInt to Number for JSON
- CORS is enabled for `localhost:5173` and `localhost:5174`
- All entities use TypeORM decorators even though Prisma is the primary ORM
- Database resets automatically on `npm run start:dev`

### Frontend Architecture (Vue 3)

**Tech Stack:**
- Vue 3 with Composition API
- Vue Router for routing
- Pinia for state management
- TailwindCSS v4 with custom UI components
- TypeScript for type safety
- Vite as build tool
- Axios for HTTP requests

**Directory Structure:**
- `/src/components/` - Reusable UI components (heavily uses reka-ui based shadcn-vue components)
- `/src/views/` - Page-level components organized by feature (problems, forum, contest, personal, auth)
- `/src/api/` - API service layer (wraps axios with type-safe helpers)
- `/src/stores/` - Pinia stores
- `/src/types/` - TypeScript type definitions
- `/src/utils/` - Utility functions (markdown, date, auth, vote)
- `/src/router/` - Vue Router configuration
- `/src/features/` - Feature-specific components (sider layout)
- `/src/hooks/` - Composable functions

**API Layer:**
- Base URL configured via `VITE_API_BASE_URL` env variable (defaults to `http://localhost:3000`)
- All requests go through axios instance in `utils/request.ts`
- JWT token automatically added to requests via interceptor
- 401/403 responses automatically clear auth token
- Type-safe API helpers: `apiGet`, `apiPost`, `apiPatch`, `apiDelete`

**Routing:**
- Route groups for forum, contest, problemset, personal pages
- Problem detail uses slug-based routing: `/problems/:slug/:tab?`
- Nested routes use `AppLayout.vue` wrapper for consistent sidebar
- Default redirect to forum home

**Important Frontend Patterns:**
- Monaco Editor integration for code editing
- Markdown rendering with KaTeX support for math formulas
- Custom markdown-it plugins for footnotes, task lists, link attributes
- ECharts integration for data visualization
- Form validation using vee-validate with Zod schemas

### Problem List System

The problem list system supports both public/featured lists and user-specific organization:

1. **ProblemList** - The list itself with author, visibility settings
2. **ProblemListProblemRelation** - Links problems to lists with sort order
3. **UserProblemListCategory** - User-defined categories for organizing saved lists
4. **UserProblemListCategoryItem** - Tracks which lists users save and in which categories

Users can save any public list and organize them into custom categories.

### Authentication Flow

- JWT-based authentication
- Token stored in localStorage via `utils/auth.ts`
- Token included in all API requests via axios interceptor
- Login/register returns `access_token` and user info
- Unauthorized responses clear token and could redirect to login

## Key Technical Decisions

1. **Database IDs:** Problem IDs use BigInt, most other IDs use String (VARCHAR(40))
2. **Timestamps:** Uses `DateTime` types, most have `@default(now())` or `@updatedAt`
3. **Cascading Deletes:** Most relations use `onDelete: Cascade` for referential integrity
4. **Enum Types:** Difficulty, ProblemStatus, ContestType, ContestStatus, FlairType, VoteState, EdgeOperationType, etc.
5. **Node Version:** Requires Node.js ^20.19.0 || >=22.12.0 (specified in frontend package.json)

## Common Workflows

### Adding a New Feature Module

Backend:
1. Generate module: `nest generate module feature-name`
2. Create entity with TypeORM decorators (even if using Prisma)
3. Update Prisma schema in `backend/prisma/schema.prisma`
4. Run `npm run prisma:migrate` to create migration
5. Create service, controller, DTOs
6. Register module in `app.module.ts`
7. Add seed data in `backend/prisma/seed/data/`

Frontend:
1. Create API service in `src/api/feature-name.ts`
2. Define types in `src/types/feature-name.ts`
3. Create views in `src/views/feature-name/`
4. Add routes in `src/router/index.ts`
5. Create store if needed in `src/stores/`

### Database Changes

1. Modify `backend/prisma/schema.prisma`
2. Run `npm run prisma:migrate` to generate migration
3. Migration automatically applied on next `npm run start:dev` (due to db:reset)
4. Update corresponding TypeORM entities if they exist
5. Update seed data if schema changes affect it

### Running Tests

Frontend tests use Vitest with jsdom. Backend tests use Jest. Mock data validation available via `validate:mocks` scripts.
