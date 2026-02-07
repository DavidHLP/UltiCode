# Dashboard Refactoring Plan

## 1. Design Overview & Philosophy

**Objective:** Modernize the dashboard layout to improve data "glanceability" and operational efficiency without altering the existing color system.
**Aesthetic Style:** "Bento Grid" / Modular Design. Focus on clean separation of concerns, high-density information with adequate breathing room, and a strict visual hierarchy.
**Color Constraints:** Strictly adhere to the project's existing Tailwind CSS variables (e.g., `--primary`, `--muted`, `--card`, `--border`) to ensure seamless integration.

## 2. Layout Restructuring (Grid System)

Move from a simple flex/grid hybrid to a robust CSS Grid layout that adapts to screen sizes.

**Proposed Grid Structure (Desktop):**

- **Top Row (Header):** Full width.
- **Second Row (Metrics):** 4 Columns (Stats Cards).
- **Third Row (Main Content):**
  - **Left Column (approx. 66%):** Main Growth/Analytics Chart.
  - **Right Column (approx. 33%):** "Action Center" (Combined Quick Actions & Critical Notifications).
- **Fourth Row (Details):**
  - Full width or split, focused on Recent Activity/Audit Logs in a "Timeline" format.

## 3. Detailed Component Refactoring

### 3.1 Header Section

- **Current:** Title stacked with Welcome message + separate Role badge.
- **Proposed Refactor:**
  - **Layout:** Flex row with "Space Between".
  - **Left:** "Good Morning, [Name]" (H2) with the role badge moved next to the name as a subtle pill tag.
  - **Right:** A "Data Context" indicator (e.g., "Last updated: Just now") or a refresh button to indicate system liveness.

### 3.2 Key Metrics (StatCards)

- **Current:** Simple cards with value and trend.
- **Proposed Refactor:**
  - **Visuals:** Introduce subtle, opacity-reduced background icons in the bottom-right of each card (e.g., a large faint User icon for the Users card).
  - **Typography:** Increase the font weight of the numeric value (`text-3xl font-bold`).
  - **Trend Indicator:** encapsulate the trend text (`+5%`) in a small pill/badge with specific background colors (green/red/gray based on existing semantic colors) rather than just colored text.
  - **Interaction:** Make the entire card a clickable "surface" with a hover elevation effect (`hover:shadow-md transition-all`), linking to the respective list views (Users, Problems, etc.).

### 3.3 Analytics Chart (AreaChart)

- **Current:** Standard Card with Chart.
- **Proposed Refactor:**
  - **Header:** Integrate a time-period selector toggle (e.g., [7d | 30d | 90d]) directly into the Card Header (visually only, until backend supports it).
  - **Content:** Maximize chart height. Remove internal padding on the bottom to let the chart "bleed" to the edge if aesthetic permits, or frame it with a subtle border.

### 3.4 Action Center & Activity Feed (Right Column)

- **Current:** Simple list of text logs.
- **Proposed Refactor - "Action Center"**:
  - Combine "Flagged Content" status and "Recent Activity" into a vertical stack.
  - **Top Block (Priority):** If there are flagged items, show a "Moderation Required" alert card prominently at the top of this column.
  - **Bottom Block (Timeline):** Transform the text list into a **Visual Timeline**:
    - Vertical line connecting items.
    - Icons replacing text labels (e.g., specific icons for 'Login', 'Edit', 'Delete').
    - Use `text-xs` for timestamps to save space.

## 4. Typography & Spacing

- **Spacing:** Increase global gap from `gap-4` to `gap-6` to reduce visual clutter.
- **Headings:** Use tighter tracking (`tracking-tight`) for headings to feel more modern.
- **Borders:** Use `border-border/50` for a lighter, more subtle separation between cards.

## 5. Implementation Roadmap

1.  **Skeleton Update:** Rewrite `DashboardView.vue` template to use a named grid area layout.
2.  **Component Enhancement:**
    - Update `StatCards.vue` to accept `icon` props and render the new card style.
    - Refactor the Activity list in `DashboardView.vue` into a new `DashboardTimeline.vue` local component for cleaner code.
3.  **Visual Polish:** Apply the existing `muted-foreground` color class to secondary text to ensure high contrast for primary data.
