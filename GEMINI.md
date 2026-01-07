# Gemini.md

This file provides guidance to Gemini Code (Gemini.ai/code) when working with code in this repository.

# Using Gemini CLI for Large Codebase Analysis

When analyzing large codebases or multiple files that might exceed context limits, use the Gemini CLI with its massive
context window. Use `gemini -p` to leverage Google Gemini's large context capacity.

## File and Directory Inclusion Syntax

Use the `@` syntax to include files and directories in your Gemini prompts. The paths should be relative to WHERE you run the
gemini command:

### Examples:

**Single file analysis:**
  ```bash
  gemini -p "@src/main.py Explain this file's purpose and structure"

  Multiple files:
  gemini -p "@package.json @src/index.js Analyze the dependencies used in the code"

  Entire directory:
  gemini -p "@src/ Summarize the architecture of this codebase"

  Multiple directories:
  gemini -p "@src/ @tests/ Analyze test coverage for the source code"

  Current directory and subdirectories:
  gemini -p "@./ Give me an overview of this entire project"
  
#
 Or use --all_files flag:
  gemini --all_files -p "Analyze the project structure and dependencies"

  Implementation Verification Examples

  Check if a feature is implemented:
  gemini -p "@src/ @lib/ Has dark mode been implemented in this codebase? Show me the relevant files and functions"

  Verify authentication implementation:
  gemini -p "@src/ @middleware/ Is JWT authentication implemented? List all auth-related endpoints and middleware"

  Check for specific patterns:
  gemini -p "@src/ Are there any React hooks that handle WebSocket connections? List them with file paths"

  Verify error handling:
  gemini -p "@src/ @api/ Is proper error handling implemented for all API endpoints? Show examples of try-catch blocks"

  Check for rate limiting:
  gemini -p "@backend/ @middleware/ Is rate limiting implemented for the API? Show the implementation details"

  Verify caching strategy:
  gemini -p "@src/ @lib/ @services/ Is Redis caching implemented? List all cache-related functions and their usage"

  Check for specific security measures:
  gemini -p "@src/ @api/ Are SQL injection protections implemented? Show how user inputs are sanitized"

  Verify test coverage for features:
  gemini -p "@src/payment/ @tests/ Is the payment processing module fully tested? List all test cases"

  When to Use Gemini CLI

  Use gemini -p when:
  - Analyzing entire codebases or large directories
  - Comparing multiple large files
  - Need to understand project-wide patterns or architecture
  - Current context window is insufficient for the task
  - Working with files totaling more than 100KB
  - Verifying if specific features, patterns, or security measures are implemented
  - Checking for the presence of certain coding patterns across the entire codebase

  Important Notes

  - Paths in @ syntax are relative to your current working directory when invoking gemini
  - The CLI will include file contents directly in the context
  - No need for --yolo flag for read-only analysis
  - Gemini's context window can handle entire codebases that would overflow Gemini's context
  - When checking implementations, be specific about what you're looking for to get accurate results # Using Gemini CLI for Large Codebase Analysis


  When analyzing large codebases or multiple files that might exceed context limits, use the Gemini CLI with its massive
  context window. Use `gemini -p` to leverage Google Gemini's large context capacity.


  ## File and Directory Inclusion Syntax


  Use the `@` syntax to include files and directories in your Gemini prompts. The paths should be relative to WHERE you run the
   gemini command:


  ### Examples:


  **Single file analysis:**
  ```bash
  gemini -p "@src/main.py Explain this file's purpose and structure"


  Multiple files:
  gemini -p "@package.json @src/index.js Analyze the dependencies used in the code"


  Entire directory:
  gemini -p "@src/ Summarize the architecture of this codebase"


  Multiple directories:
  gemini -p "@src/ @tests/ Analyze test coverage for the source code"


  Current directory and subdirectories:
  gemini -p "@./ Give me an overview of this entire project"
  # Or use --all_files flag:
  gemini --all_files -p "Analyze the project structure and dependencies"


  Implementation Verification Examples


  Check if a feature is implemented:
  gemini -p "@src/ @lib/ Has dark mode been implemented in this codebase? Show me the relevant files and functions"


  Verify authentication implementation:
  gemini -p "@src/ @middleware/ Is JWT authentication implemented? List all auth-related endpoints and middleware"


  Check for specific patterns:
  gemini -p "@src/ Are there any React hooks that handle WebSocket connections? List them with file paths"


  Verify error handling:
  gemini -p "@src/ @api/ Is proper error handling implemented for all API endpoints? Show examples of try-catch blocks"


  Check for rate limiting:
  gemini -p "@backend/ @middleware/ Is rate limiting implemented for the API? Show the implementation details"


  Verify caching strategy:
  gemini -p "@src/ @lib/ @services/ Is Redis caching implemented? List all cache-related functions and their usage"


  Check for specific security measures:
  gemini -p "@src/ @api/ Are SQL injection protections implemented? Show how user inputs are sanitized"


  Verify test coverage for features:
  gemini -p "@src/payment/ @tests/ Is the payment processing module fully tested? List all test cases"


  When to Use Gemini CLI


  Use gemini -p when:
  - Analyzing entire codebases or large directories
  - Comparing multiple large files
  - Need to understand project-wide patterns or architecture
  - Current context window is insufficient for the task
  - Working with files totaling more than 100KB
  - Verifying if specific features, patterns, or security measures are implemented
  - Checking for the presence of certain coding patterns across the entire codebase


  Important Notes


  - Paths in @ syntax are relative to your current working directory when invoking gemini
  - The CLI will include file contents directly in the context
  - No need for --yolo flag for read-only analysis
  - Gemini's context window can handle entire codebases that would overflow Gemini's context
  - When checking implementations, be specific about what you're looking for to get accurate results

## Project Overview

UltiCode is a competitive programming platform with problem-solving, contests, rankings, forums, and solution sharing. It's a monorepo with three apps:

- **backend/**: NestJS API server (TypeScript, Prisma ORM, MySQL)
- **frontend/**: Public Vue 3 app (Vite, Tailwind CSS, Pinia)
- **admin-frontend/**: Admin Vue 3 app

## Common Commands

### Development

```bash
npm install                    # Install all dependencies (Node 20+)
npm run dev                    # Start frontend (5173) + backend (3000)
npm run dev:frontend           # Frontend only
npm run dev:backend            # Backend only (runs prisma generate, lint, type-check, format, db:reset first)
```

### Database

```bash
npm run prisma:migrate --prefix backend   # Create/apply migrations
npm run prisma:generate --prefix backend  # Regenerate Prisma client
npm run db:reset --prefix backend         # Reset DB with migrations (force)
npm run db:seed --prefix backend          # Seed test data

```

* Configure `DATABASE_URL` in `backend/.env`
* Default test user: `shadcn` / `password123`

### Quality

```bash
npm run lint                   # Lint both apps (ESLint --fix)
npm run format                 # Format both apps (Prettier)
npm run type-check             # Type check both apps
npm run type-check:backend     # Backend: tsc --noEmit
npm run type-check:frontend    # Frontend: vue-tsc --build

```

### Testing

```bash
npm run test --prefix backend      # Jest unit tests
npm run test:cov --prefix backend  # Jest with coverage
npm run test:e2e --prefix backend  # Jest E2E tests
npm run test --prefix frontend     # Vitest (run once)
npm run test:watch --prefix frontend  # Vitest watch mode

```

### Build

```bash
npm run build --prefix backend     # NestJS build → dist/
npm run build --prefix frontend    # Vite build → dist/
npm start                          # Run production (both apps)

```

## Architecture

### Backend Structure (`backend/src/`)

NestJS modular architecture with standard file naming:

* `*.module.ts` - Module definitions
* `*.controller.ts` - HTTP route handlers
* `*.service.ts` - Business logic
* `*.dto.ts` - Request/response validation (class-validator decorators)
* `*.spec.ts` - Jest unit tests

Key modules: `auth/`, `problem/`, `contest/`, `submission/`, `solution/`, `forum/`, `user/`, `bookmark/`, `notification/`, `i18n/`

Authentication: JWT tokens (7-day expiry), SHA-256 password hashing

### Frontend Structure (`frontend/src/`)

Vue 3 Composition API with `<script setup lang="ts">`:

* `views/` - Page components by feature (auth, contest, forum, problems, personal)
* `components/` - Reusable components (ui/ has shadcn-vue base components)
* `stores/` - Pinia state management
* `api/` - Axios API client wrappers
* `composables/` - Reusable composition functions
* `i18n/locales/` - Translations (en-US, zh-CN)

Path alias: `@/` maps to `frontend/src/`

### Database Schema (`backend/prisma/schema.prisma`)

Core entities: User, Problem, Contest, Submission, Solution, ForumPost, GlobalRanking, ContestRanking, Notification, ProblemList, Bookmark, VirtualContestSession, Translation

Seeds in `backend/prisma/seed/`

## Code Style

* ESLint + Prettier enforced (run via lint/format commands)
* Backend: single quotes, trailing commas
* Vue components: PascalCase files, `<script setup lang="ts">`
* NestJS: kebab-case files (auth.service.ts, user.controller.ts)
* Use DTOs with class-validator for all API inputs
* Business logic in services, not controllers

## Admin Frontend Design Guide

The admin frontend (`admin-frontend/src/template/`) follows a dashboard design pattern with these key elements:

### Layout Structure

```
SidebarProvider (CSS variables: --sidebar-width, --header-height)
├── AppSidebar (collapsible="offcanvas")
│   ├── SidebarHeader - Logo/brand
│   ├── SidebarContent - Navigation sections
│   └── SidebarFooter - User menu
└── SidebarInset
    ├── SiteHeader - Page title, actions
    └── Main content area

```

### Component Library

* **UI Components**: shadcn-vue (Button, Card, Badge, Avatar, Select, Table, Tabs, etc.)
* **Icons**: `@tabler/icons-vue` (IconDashboard, IconChartBar, IconUsers, etc.)
* **Data Table**: `@tanstack/vue-table` with sorting, filtering, pagination
* **Charts**: `@unovis/vue` (VisArea, VisLine, VisAxis, VisXYContainer)
* **Drag & Drop**: `dnd-kit-vue` (useSortable, DragDropProvider)
* **Validation**: `zod` for schema validation

### Card Component Pattern

```vue
<Card class="@container/card">
  <CardHeader>
    <CardDescription>Label</CardDescription>
    <CardTitle class="text-2xl font-semibold tabular-nums">Value</CardTitle>
    <CardAction>
      <Badge variant="outline"><IconTrendingUp />+12.5%</Badge>
    </CardAction>
  </CardHeader>
  <CardFooter class="flex-col items-start gap-1.5 text-sm">
    <div class="line-clamp-1 flex gap-2 font-medium">Trend info</div>
    <div class="text-muted-foreground">Description</div>
  </CardFooter>
</Card>

```

### Responsive Design

* Container queries: `@container/main`, `@container/card`, `@xl/main`, `@5xl/main`
* Responsive padding: `px-4 lg:px-6`
* Grid layouts: `grid-cols-1 @xl/main:grid-cols-2 @5xl/main:grid-cols-4`
* Mobile detection: `useSidebar().isMobile` for conditional rendering

### Styling Patterns

* Gradient cards: `bg-gradient-to-t from-primary/5 to-card`
* Data attributes: `data-[state=open]:bg-sidebar-accent`, `data-[slot=card]:shadow-xs`
* Muted text: `text-muted-foreground`
* Tabular numbers: `tabular-nums` for numeric values
* Truncation: `truncate`, `line-clamp-1`

### Navigation Structure

* **NavMain**: Primary actions with Quick Create button + main menu items
* **NavDocuments**: Document links with hover actions (Open, Share, Delete)
* **NavSecondary**: Settings, Help, Search (pushed to bottom with `mt-auto`)
* **NavUser**: User avatar, dropdown menu with Account, Billing, Notifications, Logout

### Data Table Features

* Drag-and-drop row reordering with DraggableRow/DragHandle
* Column visibility toggle dropdown
* Row selection with checkboxes
* Pagination with page size selector (10, 20, 30, 40, 50)
* Status indicators with icons (IconCircleCheckFilled, IconLoader)
* Action menus per row (Edit, Copy, Favorite, Delete)

## Commit Convention

Conventional Commits: `feat(scope): message`, `fix(scope): message`, `docs:`, `refactor:`, `test:`

Include Prisma migration files when changing schema.

### Quality & Commit Workflow (MANDATORY)

### Quality & Commit Workflow (MANDATORY)

After completing a coding task, you **MUST** follow this exact sequence before ending the turn:

1. **Verify**:
* Run `npm run type-check`
* Run `npm run lint`
* Run `npm run format`


2. **Commit**:
* If verifications pass, you **MUST** create a git commit.
* Run `git add .` (or specific files)
* **FORBIDDEN**: Do NOT include any AI attribution, signatures, "Generated with...", "Co-Authored-By", or robot emojis (🤖). The commit message must appear strictly human-written.
* Run `git commit -m "type(scope): message"`


3. **Finish**:
* You may only mark the task as "Complete" or "Done" **AFTER** the git commit is successfully executed.
  **CRITICAL RULE**: Do not leave code changes uncommitted. Always run `git commit` as the final step of your implementation.