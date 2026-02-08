import { h } from 'vue'
import { Checkbox } from '@/components/ui/checkbox'
import type { ColumnDef } from '@tanstack/vue-table'
import type { Problem } from '@/api/admin/problems'

export const selectColumn: ColumnDef<Problem> = {
  id: 'select',
  header: ({ table }) =>
    h(Checkbox, {
      modelValue:
        table.getIsAllPageRowsSelected() || (table.getIsSomePageRowsSelected() && 'indeterminate'),
      'onUpdate:modelValue': (value: boolean | 'indeterminate') =>
        table.toggleAllPageRowsSelected(!!value),
      'aria-label': 'Select all',
    }),
  cell: ({ row }) =>
    h(Checkbox, {
      modelValue: row.getIsSelected(),
      'onUpdate:modelValue': (value: boolean | 'indeterminate') => row.toggleSelected(!!value),
      'aria-label': 'Select row',
    }),
  enableSorting: false,
  enableHiding: false,
}

export const idColumn: ColumnDef<Problem> = {
  accessorKey: 'id',
  header: () => 'ID',
  cell: ({ row }) => {
    const id = row.getValue('id') as string
    return h('span', { class: 'text-muted-foreground text-xs font-mono' }, id.slice(0, 8))
  },
}

export const titleColumn: ColumnDef<Problem> = {
  accessorKey: 'title',
  header: () => 'Problem',
  cell: ({ row }) => {
    const problem = row.original
    return h('div', { class: 'flex flex-col' }, [
      h('span', { class: 'font-medium text-sm' }, problem.title),
      h('span', { class: 'text-muted-foreground text-xs' }, problem.slug),
    ])
  },
}
