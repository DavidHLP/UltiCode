# Global Style Guide

## Personal Views Design System

This section documents the standardized design patterns used across the Personal Views (`frontend/src/views/personal/**`).

### 1. Page Layout Structure
All personal pages should be wrapped in the standardized shell components to ensure consistent spacing, alignment, and entrance animations.

- **Container (`PersonalPageShell`):**
  - Max width: `max-w-7xl`
  - Centering: `mx-auto`
  - Vertical spacing: `space-y-8`
  - Bottom padding: `pb-10`
  - Animation: `animate-in fade-in slide-in-from-bottom-4 duration-500`

- **Header (`PersonalPageHeader`):**
  - Title: `text-3xl font-bold tracking-tight`
  - Description: `text-muted-foreground`
  - Separator: Included automatically at the bottom.
  - Actions: Placed in the `#actions` slot, typically `rounded-full` buttons.

### 2. Card Styling
Cards are the primary container for content. 

- **Standard Card:**
  - Border: `border-muted/60` (or `border-none shadow-none bg-muted/20` for secondary/settings panels)
  - Radius: `rounded-2xl` (Strictly enforced)
  - Shadow: `shadow-sm` (default), `hover:shadow-md` (interactive)
  - Transition: `transition-all duration-300`

- **Card Content:**
  - Card Titles: `text-lg font-bold` (avoid `font-black` for better readability)
  - Secondary Text: `text-sm text-muted-foreground`
  - Headers: Often use `pb-3` or `pb-4`. For distinct sections, use `border-b bg-muted/20`.

### 3. Empty States
A unified design for states with no content (No posts, No lists, Not logged in).

- **Container:**
  ```html
  <div class="flex flex-col items-center justify-center py-24 rounded-2xl border-2 border-dashed border-muted/50 bg-muted/5 text-center px-6">
  ```
- **Icon Wrapper:**
  ```html
  <div class="flex h-16 w-16 items-center justify-center rounded-2xl bg-muted/50 mb-4">
    <Icon class="h-8 w-8 text-muted-foreground/50" />
  </div>
  ```
- **Typography:**
  - Title: `text-xl font-bold`
  - Description: `text-sm text-muted-foreground mt-2 max-w-[300px]`
- **Action Button:** `rounded-full px-8 h-10 font-bold`

### 4. Interactive Elements

- **Buttons:**
  - Primary/Secondary actions: `rounded-full`
  - Icon buttons (Ghost): `rounded-full h-8 w-8`

- **Tabs:**
  - **Vertical/Sidebar Tabs (e.g., Account Settings):**
    - Triggers: `rounded-xl`, `justify-start`, `px-4 py-3`.
    - Active state: `data-[state=active]:bg-muted/60 data-[state=active]:shadow-none`.
  - **Horizontal/Pill Tabs (e.g., Problem Lists):**
    - List: `bg-muted/50 p-1 rounded-full`.
    - Triggers: `rounded-full`, `font-bold`.

- **Badges:**
  - Radius: `rounded-md` (standard) or `rounded-full` (for counts/status).
  - Typography: `font-bold`, often `uppercase tracking-widest` for status labels.
  - Sizing: `text-[10px]` or `text-xs`.

### 5. Input Fields
- **Search/Text Inputs:** `rounded-lg` (standard) or `rounded-full` (search bars).
- **Textareas:** `rounded-lg resize-none`.

### 6. Semantic Colors
- **Success/Solved:** `text-emerald-500`, `bg-emerald-500/10`
- **Error/Danger:** `text-rose-500`, `bg-rose-500/10`, `border-destructive/20`
- **Warning/Featured:** `text-amber-500`, `bg-amber-500/10`
- **Info/Saved:** `text-blue-500`, `bg-blue-500/10`
