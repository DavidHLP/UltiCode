# Phase 04: Frontend Quality - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-15
**Phase:** 04-frontend-quality
**Mode:** --auto (all decisions auto-selected)
**Areas discussed:** Co-location pattern, Split strategy, Composable scope, Communication pattern, Target sizing

---

## Co-location Pattern

| Option | Description | Selected |
|--------|-------------|----------|
| Co-located sub-components | Sub-components in `components/` dir next to parent | ✓ |
| Shared components directory | All extracted components in shared `components/` folder | |
| Flat directory | All files in same directory as parent | |

**Auto-selected:** Co-located sub-components — follows Vue community convention, keeps related files together, only promotes to global when reused across 3+ views

---

## Split Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Tab/section-per-component | Each tab or major section becomes own component | ✓ |
| Feature-per-component | Group by feature (e.g., all CRUD operations together) | |
| Layer-based | Separate into container/presentational components | |

**Auto-selected:** Tab/section-per-component — matches the structure of the largest components (ProblemListsView 4 tabs, AnalyticsView multiple reports, SettingsView 5 sections). Dialogs extracted as separate components.

---

## Composable Extraction Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Data fetching + state only | API calls, complex state, pagination into composables | ✓ |
| Maximum extraction | Everything possible into composables (formatters, validators, etc.) | |
| Minimal extraction | Only shared/reusable logic into composables | |

**Auto-selected:** Data fetching + state only — UI helpers (formatters, validators) are tightly coupled to templates and rarely worth abstracting. Focus on the heavy script sections (ProblemDetailView 609 lines script, ProblemsListView 916 lines script).

---

## Communication Pattern

| Option | Description | Selected |
|--------|-------------|----------|
| Props down, events up | Standard Vue props/emit, explicit and traceable | ✓ |
| provide/inject heavy | Use dependency injection for deep hierarchies | |
| New Pinia stores | Component-local state in new stores | |

**Auto-selected:** Props down, events up — standard Vue pattern, most maintainable. provide/inject only for 3+ level nesting. No new stores for component-local state.

---

## Target Sizing

| Option | Description | Selected |
|--------|-------------|----------|
| 100-300 lines per sub-component | Fine-grained, focused components | ✓ |
| 300-500 lines per sub-component | Larger components, fewer files | |
| No target, just under 500 | Flexible sizing | |

**Auto-selected:** 100-300 lines per sub-component — keeps files focused and readable. Parent orchestrators can go up to 450. Hard cap 500 for any file.

---

## Claude's Discretion

- Exact naming of extracted components and composables
- Order of extraction within a component
- Whether to extract shared utility vs duplicate <10 lines
- Specific emit event names and prop interfaces
- Script setup vs defineComponent choice (follow existing convention)

## Deferred Ideas

None — discussion stayed within phase scope
