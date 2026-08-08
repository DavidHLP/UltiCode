import type { ColumnDef } from '@tanstack/vue-table'
import { h } from 'vue'
import { Checkbox } from '@/components/ui/checkbox'

/**
 * Creates a TanStack Table selection column using a tri-state Checkbox.
 *
 * The header checkbox reflects:
 *   - `true`           → all rows on the current page are selected
 *   - `false`          → no rows are selected
 *   - `'indeterminate'` → some rows are selected but not all
 *
 * Uses the modern reka-ui Checkbox contract (`modelValue` + `onUpdate:modelValue`,
 * NOT the deprecated `onUpdate:checked`). Always includes explicit aria-labels.
 *
 * Styling (CSS class) is caller-owned: pass `opts.checkboxClass` to apply a class;
 * if omitted the Checkbox renders without an explicit class.
 *
 * @param t                          - i18n function; resolves `table.selectAll` and
 *                                     `common.select` as aria-labels when no custom
 *                                     labels are provided.
 * @param opts.selectAllAriaLabel    - custom aria-label for the header checkbox.
 * @param opts.selectRowAriaLabel    - custom aria-label for each row checkbox.
 * @param opts.checkboxClass         - CSS class string applied to every checkbox in
 *                                     this column (caller-owned; no default).
 */
export function createSelectionColumn<T>(
  t: (key: string) => string,
  opts?: {
    selectAllAriaLabel?: string
    selectRowAriaLabel?: string
    /** CSS class applied to every checkbox in this column. No default — caller passes what is needed. */
    checkboxClass?: string
  },
): ColumnDef<T>[] {
  const selectAllLabel = opts?.selectAllAriaLabel ?? t('table.selectAll')
  const selectRowLabel = opts?.selectRowAriaLabel ?? t('common.select')

  return [
    {
      id: 'select',
      enableSorting: false,
      enableHiding: false,
      header: ({ table }) =>
        h(Checkbox, {
          modelValue:
            table.getIsAllPageRowsSelected() ||
            (table.getIsSomePageRowsSelected() ? 'indeterminate' : false),
          'onUpdate:modelValue': (value: boolean | 'indeterminate') =>
            table.toggleAllPageRowsSelected(!!value),
          'aria-label': selectAllLabel,
          class: opts?.checkboxClass,
        }),
      cell: ({ row }) =>
        h(Checkbox, {
          modelValue: row.getIsSelected(),
          'onUpdate:modelValue': (value: boolean | 'indeterminate') =>
            row.toggleSelected(!!value),
          'aria-label': selectRowLabel,
          class: opts?.checkboxClass,
        }),
    },
  ]
}
