# UltiCode Project Context

## Project Overview

**UltiCode** (working directory `UltiCode-Public`) is a comprehensive competitive programming platform. It features a modern problem-solving interface, contest management system (weekly/biweekly/special), global user rankings, community forums, and a solution sharing system.

The project is structured as a monorepo containing:
*   **`backend`**: A NestJS API server managing logic, database interactions, and authentication.
*   **`frontend`**: A public-facing Vue 3 application for users to solve problems and participate in contests.
*   **`admin-frontend`**: A Vue 3 application for platform administration.

## Technology Stack

### Backend (`backend`)
*   **Framework**: [NestJS](https://nestjs.com/) (Node.js)
*   **Database**: MySQL (via [Prisma ORM](https://www.prisma.io/))
*   **Authentication**: JWT-based, with passwords hashed via SHA-256.
*   **Queues**: BullMQ
*   **Testing**: Jest (Unit & E2E)
*   **Validation**: `class-validator`, `class-transformer`

### Frontend (`frontend` & `admin-frontend`)
*   **Framework**: [Vue 3](https://vuejs.org/) (Composition API, `<script setup>`)
*   **Build Tool**: [Vite](https://vitejs.dev/)
*   **Styling**: [Tailwind CSS](https://tailwindcss.com/)
*   **State Management**: [Pinia](https://pinia.vuejs.org/)
*   **Routing**: Vue Router
*   **UI Components**: `shadcn-vue` (implied by components structure), `radix-icons`, `lucide-vue-next`.
*   **Editor**: Monaco Editor (in `frontend` for code submission)
*   **Visualization**: ECharts, `@unovis/vue`
*   **Math**: KaTeX

## Project Structure

```text
/
├── backend/                # NestJS API Server
│   ├── src/                # Source code (Controllers, Modules, Services)
│   ├── prisma/             # Database schema and seeds
│   └── test/               # E2E tests
├── frontend/               # Public Vue 3 App
│   ├── src/
│   │   ├── components/     # Reusable UI components
│   │   ├── views/          # Page views
│   │   ├── stores/         # Pinia stores
│   │   └── api/            # API client wrappers
│   └── vite.config.ts
├── admin-frontend/         # Admin Vue 3 App
│   ├── src/
│   └── vite.config.ts
└── package.json            # Root scripts for monorepo management
```

## Setup & Development

### Prerequisites
*   Node.js (v20+ recommended)
*   MySQL Database

### Installation
```bash
npm install
```

### Database Setup
1.  Configure `DATABASE_URL` in `backend/.env`.
2.  Run migrations and seed data:
    ```bash
    npm run prisma:migrate --prefix backend
    npm run db:seed --prefix backend
    ```

**Default Test User:**
*   Username: `shadcn`
*   Password: `password123`

### Running the Project
*   **Development (All):**
    ```bash
    npm run dev
    # Starts:
    # - Backend: http://localhost:3000
    # - Frontend: http://localhost:5173
    ```
*   **Backend Only:** `npm run dev:backend`
*   **Frontend Only:** `npm run dev:frontend`

### Testing
*   **Backend:** `npm run test --prefix backend`
*   **Frontend:** `npm run test --prefix frontend`

## Development Conventions

*   **Code Style**: Prettier and ESLint are configured. Run `npm run lint` or `npm run format` to ensure consistency.
*   **Backend Architecture**: Follows standard NestJS modular architecture.
    *   **DTOs**: Use Data Transfer Objects for all API inputs/outputs, validated with decorators.
    *   **Services**: Business logic resides here, not in controllers.
*   **Frontend Architecture**:
    *   **Components**: Use functional components with `<script setup lang="ts">`.
    *   **Stores**: Use Pinia for global state (e.g., user session, theme).
    *   **API**: Abstract API calls into `src/api/` modules using Axios.
*   **Database**:
    *   Always use Prisma migrations (`npm run prisma:migrate`) for schema changes.
    *   Reflect schema changes in the `schema.prisma` file.

## Key Database Models
*   `User`: Core user entity.
*   `Problem`: Competitive programming problems with details, examples, and test cases.
*   `Contest`: Timed competitions grouping multiple problems.
*   `Submission`: User code submissions linked to problems and optionally contests.
*   `ForumPost` / `ForumCommunity`: Community interaction features.

## Admin Frontend Design Guide

The admin frontend uses a dashboard template (`admin-frontend/src/template/`) with these design patterns:

### Layout Architecture
```
SidebarProvider
├── AppSidebar (collapsible offcanvas)
│   ├── SidebarHeader (Logo/Brand)
│   ├── SidebarContent
│   │   ├── NavMain (Dashboard, Analytics, Projects, Team)
│   │   ├── NavDocuments (Data Library, Reports)
│   │   └── NavSecondary (Settings, Help, Search)
│   └── SidebarFooter (NavUser)
└── SidebarInset
    ├── SiteHeader (Page title + actions)
    └── Content (Cards, Charts, Tables)
```

### Technology Stack
*   **UI Components**: `shadcn-vue` (Button, Card, Badge, Avatar, Select, Table, Tabs, DropdownMenu)
*   **Icons**: `@tabler/icons-vue`
*   **Data Tables**: `@tanstack/vue-table` with sorting, filtering, pagination
*   **Charts**: `@unovis/vue` (Area charts, Line charts)
*   **Drag & Drop**: `dnd-kit-vue`
*   **Schema Validation**: `zod`

### Component Patterns

#### Metric Cards
```vue
<Card class="@container/card">
  <CardHeader>
    <CardDescription>Total Revenue</CardDescription>
    <CardTitle class="text-2xl font-semibold tabular-nums">$1,250.00</CardTitle>
    <CardAction>
      <Badge variant="outline"><IconTrendingUp />+12.5%</Badge>
    </CardAction>
  </CardHeader>
  <CardFooter class="flex-col items-start gap-1.5 text-sm">
    <div class="flex gap-2 font-medium">Trending up <IconTrendingUp /></div>
    <div class="text-muted-foreground">Last 6 months</div>
  </CardFooter>
</Card>
```

#### Data Tables
*   Drag-and-drop reordering via `DraggableRow` + `DragHandle`
*   Column visibility toggle
*   Row selection with checkboxes
*   Pagination controls (10/20/30/40/50 rows per page)
*   Status indicators with icons
*   Row action menus (Edit, Copy, Delete)

### Responsive Design Patterns
*   Container queries: `@container/main`, `@container/card`
*   Breakpoints: `@xl/main:grid-cols-2`, `@5xl/main:grid-cols-4`
*   Responsive padding: `px-4 lg:px-6`
*   Mobile detection: `useSidebar().isMobile`

### Styling Conventions
*   Gradient backgrounds: `bg-gradient-to-t from-primary/5 to-card`
*   Data attribute selectors: `data-[state=open]:bg-sidebar-accent`
*   Muted foreground: `text-muted-foreground`
*   Tabular numbers: `tabular-nums`
*   Text truncation: `truncate`, `line-clamp-1`
*   Shadow: `shadow-xs` for subtle elevation
