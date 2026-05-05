## ConstraintsEditor Component (T2)

### Implementation
- Created ConstraintsEditor.vue at management/src/views/problems/components/ConstraintsEditor.vue
- Uses useFieldArray from vee-validate for dynamic constraint management
- Each constraint is an Input row with delete button
- Empty state shows helpful message when no constraints exist
- Add new constraint button at bottom

### Reactivity Quirk
- useFieldArray's fields ref doesn't trigger template re-renders in jsdom test environment when mutated in-place
- Workaround: added renderTrigger ref that increments on add/delete to force Vue re-render
- The hidden span ensures reactivity without affecting UI
- In real browser, useFieldArray works correctly without the trigger

### Testing
- Tests use @vue/test-utils with Form wrapper from vee-validate
- @vue/test-utils was not installed in management project - had to add as devDependency
- Tests cover: empty state, initial constraints, add/delete, empty state toggle
- All 7 tests pass

### UI Patterns
- Uses @tabler/icons-vue (matches existing problems components convention)
- shadcn-vue Input and Button components
- OKLCH colors, sharp corners
- Uses v-model on Input for two-way binding with field array values

## 2025-05-04: LivePreviewPanel Implementation

### What was built
- Created `management/src/views/problems/components/LivePreviewPanel.vue`
- Creates `management/src/views/problems/components/LivePreviewPanel.spec.ts` (19 tests, all passing)

### Key design decisions
- **Reused existing `DescriptionMarkdown.vue`** from `management/src/components/problems/` instead of copying console logic. Management already has an adapted version with identical rendering pipeline (`markdown.ts` → `sanitize-markdown.ts`).
- **Hints rendered separately as collapsible** using shadcn-vue `Collapsible` components, rather than passing as `followUp` to `DescriptionMarkdown.vue`. This satisfies the "hints (collapsible)" requirement.
- **Terminal style applied**: uses `SemanticBadge` with `DIFFICULTY_COLOR_MAP`, sharp corners (global `--radius: 0`), OKLCH theme variables.
- **Props**: accepts `ProblemDescriptionFormData` from `@/lib/schemas/problemDescription`.
- **Layout**: flex column with scrollable content area (`overflow-y-auto`), fixed-height header.

### Testing patterns learned
- Mock `vue-i18n` with both `useI18n` AND `createI18n` when any transitive import pulls in `i18n/index.ts` (via `@/api/admin/problems` → `@/utils/request`).
- Mock `reka-ui` based components (`Separator`, `Collapsible`) in jsdom to avoid `Cannot read properties of null (reading 'ce')` errors.
- Use scoped selectors (e.g., `collapsibleContent.querySelectorAll('li')`) to avoid false matches from markdown-rendered elements.

### Files created
- `management/src/views/problems/components/LivePreviewPanel.vue`
- `management/src/views/problems/components/LivePreviewPanel.spec.ts`

## 2025-05-04: DescriptionForm Refactor (T2 - Main Form)

### What was built
- Completely refactored `management/src/views/problems/components/DescriptionForm.vue`
- Integrated all editor components: `ExamplesEditor`, `ConstraintsEditor`, `HintsEditor`, `TagsSelector`
- Added `LivePreviewPanel` in sticky right sidebar
- Wrapped each section in collapsible Card via Accordion

### Architecture
- **Vee-validate + Zod**: `useForm` with `toTypedSchema(problemDescriptionSchema)` for validation
- **FormField pattern**: All fields wrapped in `<FormField>` with `<FormItem>`, `<FormLabel>`, `<FormControl>`, `<FormMessage>`
- **Array editors**: `ExamplesEditor`, `ConstraintsEditor`, `HintsEditor` use `useFieldArray` internally, bound via `name` prop
- **TagsSelector**: Uses `v-model` — bridged to vee-validate via `useField<string[]>('tags')`
- **Select binding**: `v-bind="componentField"` on shadcn-vue Select works automatically (reka-ui handles v-model)

### Layout
```
Left (col-8):  Form sections in Accordion
  ├─ Basic Info (title, slug, difficulty, status, isPremium, isPublished)
  ├─ Description (summary, content/MarkdownEditor)
  ├─ Examples (ExamplesEditor)
  ├─ Constraints (ConstraintsEditor)
  ├─ Hints (HintsEditor)
  └─ Tags (TagsSelector)
Right (col-4): LivePreviewPanel (sticky)
```

### Backward Compatibility
- `DescriptionFormData` changed from `interface` to `type` alias of `ProblemDescriptionFormData`
- `ProblemData` interface extended with optional `examples`, `constraints`, `hints`, `tags`
- Parent `EditDescriptionView.vue` requires no changes — extra fields ignored, `setLoading` exposed same way
- Form defaults empty arrays for new fields when parent doesn't provide them

### Gotchas
- `defineModel` is NOT used anywhere in this project — stick with `ref` for local state
- Empty interface extending another interface triggers `@typescript-eslint/no-empty-object-type` — use type alias instead
- `CardHeader` import removed since AccordionTrigger replaces it as section header
- MarkdownEditor uses explicit `:model-value` / `@update:model-value` binding instead of `v-bind="componentField"` to avoid potential prop mismatch

### Files modified
- `management/src/views/problems/components/DescriptionForm.vue`

### Verification
- `pnpm vue-tsc --noEmit --skipLibCheck` passes
- `pnpm eslint src/views/problems/components/DescriptionForm.vue` passes (0 errors)
