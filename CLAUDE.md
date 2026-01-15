# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

- Configure `DATABASE_URL` in `backend/.env`
- Default test user: `shadcn` / `password123`

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

- `*.module.ts` - Module definitions
- `*.controller.ts` - HTTP route handlers
- `*.service.ts` - Business logic
- `*.dto.ts` - Request/response validation (class-validator decorators)
- `*.spec.ts` - Jest unit tests

Key modules: `auth/`, `problem/`, `contest/`, `submission/`, `solution/`, `forum/`, `user/`, `bookmark/`, `notification/`, `i18n/`

Authentication: JWT tokens (7-day expiry), SHA-256 password hashing

### Frontend Structure (`frontend/src/`)

Vue 3 Composition API with `<script setup lang="ts">`:

- `views/` - Page components by feature (auth, contest, forum, problems, personal)
- `components/` - Reusable components (ui/ has shadcn-vue base components)
- `stores/` - Pinia state management
- `api/` - Axios API client wrappers
- `composables/` - Reusable composition functions
- `i18n/locales/` - Translations (en-US, zh-CN)

Path alias: `@/` maps to `frontend/src/`

### Database Schema (`backend/prisma/schema.prisma`)

Core entities: User, Problem, Contest, Submission, Solution, ForumPost, GlobalRanking, ContestRanking, Notification, ProblemList, Bookmark, VirtualContestSession, Translation

Seeds in `backend/prisma/seed/`

## Code Style

- ESLint + Prettier enforced (run via lint/format commands)
- Backend: single quotes, trailing commas
- Vue components: PascalCase files, `<script setup lang="ts">`
- NestJS: kebab-case files (auth.service.ts, user.controller.ts)
- Use DTOs with class-validator for all API inputs
- Business logic in services, not controllers

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

- **UI Components**: shadcn-vue (Button, Card, Badge, Avatar, Select, Table, Tabs, etc.)
- **Icons**: `@tabler/icons-vue` (IconDashboard, IconChartBar, IconUsers, etc.)
- **Data Table**: `@tanstack/vue-table` with sorting, filtering, pagination
- **Charts**: `@unovis/vue` (VisArea, VisLine, VisAxis, VisXYContainer)
- **Drag & Drop**: `dnd-kit-vue` (useSortable, DragDropProvider)
- **Validation**: `zod` for schema validation

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

- Container queries: `@container/main`, `@container/card`, `@xl/main`, `@5xl/main`
- Responsive padding: `px-4 lg:px-6`
- Grid layouts: `grid-cols-1 @xl/main:grid-cols-2 @5xl/main:grid-cols-4`
- Mobile detection: `useSidebar().isMobile` for conditional rendering

### Styling Patterns

- Gradient cards: `bg-gradient-to-t from-primary/5 to-card`
- Data attributes: `data-[state=open]:bg-sidebar-accent`, `data-[slot=card]:shadow-xs`
- Muted text: `text-muted-foreground`
- Tabular numbers: `tabular-nums` for numeric values
- Truncation: `truncate`, `line-clamp-1`

### Navigation Structure

- **NavMain**: Primary actions with Quick Create button + main menu items
- **NavDocuments**: Document links with hover actions (Open, Share, Delete)
- **NavSecondary**: Settings, Help, Search (pushed to bottom with `mt-auto`)
- **NavUser**: User avatar, dropdown menu with Account, Billing, Notifications, Logout

### Data Table Features

- Drag-and-drop row reordering with DraggableRow/DragHandle
- Column visibility toggle dropdown
- Row selection with checkboxes
- Pagination with page size selector (10, 20, 30, 40, 50)
- Status indicators with icons (IconCircleCheckFilled, IconLoader)
- Action menus per row (Edit, Copy, Favorite, Delete)

## Commit Convention

Conventional Commits: `feat(scope): message`, `fix(scope): message`, `docs:`, `refactor:`, `test:`

Include Prisma migration files when changing schema.

## Quality & Commit Workflow (MANDATORY)

After completing a coding task, you **MUST** follow this exact sequence before ending the turn:

### Pre-Implementation: Discovery with Gemini (Optional but Recommended)

For complex tasks involving multiple files or modules:

1. **Use Gemini for codebase analysis**:
   - "use gemini to analyze @backend/src/[module] and identify all affected files"
   - "ask gemini to find existing patterns for [feature] across the codebase"
   - "use gemini to understand the dependencies between these modules"

2. **Plan your approach** based on Gemini's findings:
   - Identify all files that need modification
   - Understand existing patterns to follow
   - Verify functionality doesn't already exist

### Implementation Phase

3. **Make targeted changes**:
   - Use Claude's Read/Edit/Write tools for precise modifications
   - Run tests frequently during development

### Post-Implementation: Verification & Commit (MANDATORY)

4. **Verify**:

   - Run `npm run type-check`
   - Run `npm run lint`
   - Run `npm run format`
   - Run `npm run test --prefix backend` (ensure unit tests pass)

5. **Optional: Gemini pattern verification** (for complex changes):
   - "use gemini to verify @modified-files/ follow project patterns"
   - "ask gemini to check if all occurrences of [pattern] were updated"

6. **Commit**:

   - If verifications pass, you **MUST** create a git commit.
   - Run `git add .` (or specific files)
   - **FORBIDDEN**: Do NOT include any AI attribution, signatures, "Generated with...", "Co-Authored-By", or robot emojis. The commit message must appear strictly human-written.
   - Run `git commit -m "type(scope): message"`

7. **Finish**:
   - You may only mark the task as "Complete" or "Done" **AFTER** the git commit is successfully executed.

**CRITICAL RULE**: Do not leave code changes uncommitted. Always run `git commit` as the final step of your implementation.

**Workflow Summary**: Gemini for understanding → Claude for implementing → Tests for verification → Git for committing

## Using Gemini MCP Tool for Large Codebase Analysis

This project integrates the Gemini MCP Tool, which allows Claude to leverage Google Gemini's massive token context window for analyzing large files and codebases that would otherwise exceed context limits.

### Setup

The Gemini MCP Tool should be configured in your Claude Code environment. Verify installation by typing `/mcp` in Claude Code to confirm `gemini-cli` is active.

### Usage via MCP Tool (Recommended)

Use the MCP tool directly through Claude:

**Natural language requests:**
- "use gemini to explain backend/src/auth/"
- "ask gemini to analyze the frontend architecture"
- "use gemini to search for authentication patterns"

**With file references (using @ syntax):**
- "use gemini to analyze @backend/src/auth/ and explain the authentication flow"
- "ask gemini to compare @frontend/src/stores/ with @admin-frontend/src/stores/"
- "use gemini to verify @backend/src/problem/ for proper error handling"

**Sandbox mode for safe testing:**
- "use gemini sandbox to test this Python script"
- "ask gemini to safely create and run a data processing script"

### When to Use Gemini

Use Gemini during these phases:

**Planning Phase (Before Implementation):**
- Analyzing entire modules to understand architecture before changes
- Identifying all files affected by a planned feature
- Discovering existing patterns to follow or avoid
- Verifying if functionality already exists before implementing
- Understanding dependencies and cross-module interactions

**Research/Discovery:**
- Finding all usages of a specific pattern across the codebase
- Locating where specific features are implemented
- Understanding how similar problems were solved previously
- Identifying potential impact areas for changes

**Verification (After Implementation):**
- Checking if new code follows existing patterns
- Verifying all occurrences of a pattern were updated
- Ensuring consistency across similar modules

**Use Gemini when:**
- Analyzing directories with >10 files or >100KB total
- Comparing multiple large files side-by-side
- Searching for patterns across entire modules
- Context window constraints prevent full analysis
- Need comprehensive project-wide understanding

### Integration with Task Planning

When starting a new task, follow this enhanced workflow:

1. **Discovery Phase** (Use Gemini for large-scale analysis):
   - "use gemini to analyze @backend/src/[module] and describe the architecture"
   - "ask gemini to find all files related to [feature] in @frontend/src/"
   - "use gemini to identify the authentication flow across the codebase"

2. **Planning Phase** (Use insights from Gemini):
   - Create task list based on comprehensive understanding
   - Identify all affected files upfront
   - Follow existing patterns discovered

3. **Implementation Phase** (Use Claude's tools):
   - Make targeted changes to specific files
   - Run tests, lint, type-check
   - Use Gemini for verification if needed

4. **Verification Phase** (Optional Gemini check):
   - "use gemini to verify @backend/src/[modified-files] follow project patterns"

### MCP Tool Capabilities

**ask-gemini**: General analysis and questions
- Supports @ syntax for file/directory inclusion
- Optional: specify model (default: gemini-2.5-pro)
- Optional: sandbox mode for safe execution

**brainstorm**: Generate creative ideas
- Methodology options: divergent, convergent, scamper, design-thinking
- Domain context integration
- Feasibility analysis

**sandbox-test**: Safe code execution
- Isolated environment for testing scripts
- No risk to local files

### Important Notes

- @ syntax paths are relative to current working directory
- Gemini excels at broad analysis; Claude excels at targeted changes
- Use Gemini for understanding, Claude for implementation
- No special flags needed for read-only analysis via MCP
