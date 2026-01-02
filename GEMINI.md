# UltiCode - Competitive Programming Platform

**UltiCode** is a full-stack competitive programming platform (similar to LeetCode) built with a Vue 3 frontend and a NestJS backend.

## Project Structure

- **Backend** (`/backend`): NestJS framework using Prisma ORM with MySQL.
- **Frontend** (`/frontend`): Vue 3 application built with Vite, Tailwind CSS v4, and Shadcn Vue.
- **Root**: Contains orchestration scripts to manage both applications simultaneously.

## Getting Started

### Prerequisites
- Node.js: `^20.19.0` or `>=22.12.0`
- MySQL Database (Connection details configured in `backend/src/app.module.ts`)

### Key Commands (Root)

Run these commands from the project root:

- **Start Development:** `npm run dev`
  - Runs both frontend and backend concurrently.
  - **Note:** The backend `start:dev` script **resets the database** and re-runs migrations/seeds on every startup.
- **Lint:** `npm run lint` (Frontend & Backend)
- **Format:** `npm run format` (Frontend & Backend)
- **Type Check:** `npm run type-check` (Frontend & Backend)

## Architecture & Conventions

### Backend (NestJS)
- **Database:** Prisma is the source of truth (`backend/prisma/schema.prisma`).
- **Resets:** The `npm run start:dev` command automatically runs `db:reset`, which wipes the database and applies seeds. Use `start:debug` or run generic nest commands if you wish to preserve data between restarts.
- **Modules:** Organized by feature (User, Problem, Contest, Forum, etc.).
- **BigInt:** Global patch applied in `main.ts` to handle BigInt serialization.

### Frontend (Vue 3)
- **Components:** Uses Shadcn Vue primitives and Tailwind CSS v4.
- **Layout:** Sidebar-based layout with `SidebarProvider`.
- **State:** Pinia for state management.

## Frontend Design System & Layout Guidelines

### Core Frameworks
- **Vue 3** (Composition API, `<script setup lang="ts">`)
- **Tailwind CSS v4** (Utility-first, Container Queries `@container`)
- **Shadcn Vue** (UI Primitives, Radix Vue)
- **Tabler Icons** (`@tabler/icons-vue`)

### Layout Structure
The application uses a **Sidebar Layout** pattern (`SidebarProvider`, `SidebarInset`).

#### 1. Sidebar (`AppSidebar.vue`)
- **Structure:** `SidebarHeader` (Logo/Brand), `SidebarContent` (Navigation), `SidebarFooter` (User Profile).
- **Navigation Groups:**
  - **Main:** Dashboard, Projects, Analytics.
  - **Documents:** Library, Reports.
  - **Secondary:** Settings, Help.
- **Styling:** Collapsible, standard shadcn sidebar components.

#### 2. Header (`SiteHeader.vue`)
- **Height:** Fixed `h-(--header-height)`.
- **Content:** Sidebar trigger, Breadcrumbs/Title, Global Actions (GitHub link).
- **Style:** Border-bottom, flex alignment.

#### 3. Dashboard (`dashboard/index.vue`)
- **Grid Layout:** Responsive grid using **Container Queries** (`@xl`, `@5xl`).
- **Section Cards:** Stat cards with gradients (`from-primary/5`), icons, and trend indicators.
- **Charts:** Interactive charts using `unovis` + `ChartContainer`.
- **Data Table:** Complex table with:
  - Drag-and-drop rows (`dnd-kit-vue`).
  - Column visibility & sorting.
  - Tabs for different data views.
  - Pagination & Selection.

### UI Components & Styling Patterns

#### Cards
- **Usage:** Statistics, Charts.
- **Style:** `Card`, `CardHeader`, `CardTitle` (2xl semibold), `CardFooter` (text-sm muted).
- **Visuals:** Subtle gradients for background `bg-gradient-to-t`.

#### Tables
- **Library:** `@tanstack/vue-table`.
- **Features:** Draggable rows, custom cell rendering (Badges, Status icons).
- **Interactions:** Dropdown menus for row actions, Select inputs for inline editing.

#### Typography & Icons
- **Text:** `text-muted-foreground` for secondary text.
- **Icons:** Consistent use of Tabler icons (e.g., `IconTrendingUp`, `IconUsers`).

### Code Convention
- **Imports:** path alias `@/` for `src/`.
- **Types:** TypeScript interfaces for props and data.
- **Reusability:** Extract sub-components (e.g., `NavMain`, `NavUser`) for sidebar sections.

## Important Context Files

- **`CLAUDE.md`**: Comprehensive developer guide, including detailed architecture notes and module descriptions.
- **`AGENTS.md`**: Guidelines for coding style, testing, and git conventions.
- **`backend/prisma/schema.prisma`**: Database schema definition.

## Troubleshooting

- **Database Connection:** If the backend fails to connect, check the hardcoded credentials in `backend/src/app.module.ts` (default: `localhost`, `root`, `123456`, `ulticode`).
- **File Not Found Errors:** If standard files seem missing, ensure you are running commands from the correct directory (Root vs `/backend` vs `/frontend`).