import { h } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconCheck,
  IconEyeOff,
  IconFile,
  IconFlag,
  IconTrash,
  IconUser,
  IconCode,
} from '@tabler/icons-vue'

import { Checkbox } from '@/components/ui/checkbox'
import { createEntityActionsMenu } from '@/components/table/entityActions'
import { badge } from '@/components/ui/terminal'
import type { SolutionListItem } from '@/api/admin/solutions'
import { formatDate } from '@/lib/format/date'

export interface SolutionActions {
  viewSolution: (id: string) => void
  openFlagDialog: (solution: SolutionListItem) => void
  unflagSolution: (id: string) => void
  confirmDelete: (solution: SolutionListItem) => void
}

function renderStatusBadge(solution: SolutionListItem, t: (key: string) => string) {
  if (solution.isDeleted)
    return badge({ color: 'error', label: t('solutions.status.deleted'), icon: IconTrash })
  if (solution.isFlagged)
    return badge({
      color: 'warning',
      label: t('solutions.status.flagged'),
      icon: IconFlag,
      pulse: true,
    })
  if (solution.isPublished)
    return badge({ color: 'success', label: t('solutions.status.published'), icon: IconCheck })
  return badge({ color: 'neutral', label: t('solutions.status.unpublished'), icon: IconEyeOff })
}

export function createColumns(
  t: (key: string) => string,
  actions: SolutionActions,
  canUpdateSolution: () => boolean,
  canDeleteSolution: () => boolean,
): ColumnDef<SolutionListItem>[] {
  return [
    {
      id: 'select',
      header: ({ table }) =>
        h(Checkbox, {
          modelValue:
            table.getIsAllPageRowsSelected() ||
            (table.getIsSomePageRowsSelected() && 'indeterminate'),
          'onUpdate:modelValue': (value: boolean | 'indeterminate') =>
            table.toggleAllPageRowsSelected(!!value),
          'aria-label': t('table.selectAll'),
          class:
            'border-[var(--silver-300)] data-[state=checked]:bg-[var(--accent-electric)] data-[state=checked]:border-[var(--accent-electric)]',
        }),
      cell: ({ row }) =>
        h(Checkbox, {
          modelValue: row.getIsSelected(),
          'onUpdate:modelValue': (value: boolean | 'indeterminate') => row.toggleSelected(!!value),
          'aria-label': t('common.select'),
          class:
            'border-[var(--silver-300)] data-[state=checked]:bg-[var(--accent-electric)] data-[state=checked]:border-[var(--accent-electric)]',
        }),
      enableSorting: false,
      enableHiding: false,
    },
    {
      id: 'row_num',
      header: () => '#',
      cell: ({ row, table }) => {
        const pageIndex = table.getState().pagination.pageIndex
        const pageSize = table.getState().pagination.pageSize
        const rowNum = pageIndex * pageSize + row.index + 1
        return h('span', { class: 'terminal-row-num' }, String(rowNum).padStart(2, '0'))
      },
      enableSorting: false,
      enableHiding: false,
    },
    {
      accessorKey: 'id',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('solutions.columns.id'),
        ),
      cell: ({ row }) => {
        const id = row.getValue('id') as string
        return h('span', { class: 'font-data text-xs text-[var(--silver-400)]' }, id.slice(0, 8))
      },
    },
    {
      accessorKey: 'title',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('solutions.columns.solution'),
        ),
      cell: ({ row }) => {
        const solution = row.original
        return h('div', { class: 'flex flex-col gap-0.5' }, [
          h('span', { class: 'font-medium text-sm text-[var(--foreground)]' }, solution.title),
          h('div', { class: 'flex items-center gap-1 text-xs text-[var(--silver-400)]' }, [
            h(IconCode, { class: 'h-3 w-3' }),
            h('span', {}, solution.problem?.title || t('common.noData')),
          ]),
        ])
      },
    },
    {
      accessorKey: 'author',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('solutions.columns.author'),
        ),
      cell: ({ row }) => {
        const author = row.original.author
        return h('div', { class: 'flex items-center gap-2' }, [
          h(IconUser, { class: 'h-3.5 w-3.5 text-[var(--terminal-cyan)]' }),
          h(
            'span',
            { class: 'text-sm text-[var(--foreground)]' },
            author?.username || t('common.noData'),
          ),
        ])
      },
    },
    {
      accessorKey: 'isFlagged',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('solutions.columns.status'),
        ),
      cell: ({ row }) => {
        const solution = row.original
        return renderStatusBadge(solution, t)
      },
    },
    {
      accessorKey: 'views',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('solutions.columns.views'),
        ),
      cell: ({ row }) => {
        const views = row.getValue('views') as number
        return h(
          'span',
          { class: 'font-data text-xs text-[var(--terminal-cyan)] tabular-nums' },
          views.toLocaleString(),
        )
      },
    },
    {
      accessorKey: 'createdAt',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('solutions.columns.created'),
        ),
      cell: ({ row }) => {
        const date = row.getValue('createdAt') as string
        return h(
          'span',
          { class: 'font-data text-xs text-[var(--silver-400)] tabular-nums' },
          formatDate(date),
        )
      },
    },
    {
      id: 'actions',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('solutions.columns.actions'),
        ),
      cell: ({ row }) => {
        const solution = row.original
        return createEntityActionsMenu(
          [
            {
              label: t('solutions.actions.viewDetails'),
              onSelect: () => actions.viewSolution(solution.id),
              icon: IconFile,
              iconClass: 'h-4 w-4 text-[var(--terminal-cyan)]',
            },
            solution.isFlagged
              ? {
                  label: t('solutions.actions.unflag'),
                  onSelect: () => actions.unflagSolution(solution.id),
                  icon: IconCheck,
                  iconClass: 'h-4 w-4 text-[var(--terminal-green)]',
                  labelClass: 'text-[var(--terminal-green)]',
                  hidden: !canUpdateSolution(),
                }
              : {
                  label: t('solutions.actions.flag'),
                  onSelect: () => actions.openFlagDialog(solution),
                  icon: IconFlag,
                  iconClass: 'h-4 w-4 text-[var(--terminal-amber)]',
                  labelClass: 'text-[var(--terminal-amber)]',
                  hidden: !canUpdateSolution(),
                },
            { kind: 'separator' },
            {
              label: t('solutions.actions.delete'),
              onSelect: () => actions.confirmDelete(solution),
              icon: IconTrash,
              iconClass: 'h-4 w-4 text-[var(--terminal-red)]',
              labelClass: 'text-[var(--terminal-red)]',
              hidden: !canDeleteSolution(),
            },
          ],
          {
            triggerClass:
              'h-8 w-8 p-0 hover:bg-[var(--silver-100)] dark:hover:bg-[var(--silver-800)]',
            triggerIconClass: 'h-4 w-4 text-[var(--silver-400)]',
            contentClass: 'border-[var(--silver-200)] dark:border-[var(--silver-700)]',
            itemClass: 'font-data text-xs cursor-pointer',
            separatorClass: 'bg-[var(--silver-200)] dark:bg-[var(--silver-700)]',
            srLabel: t('common.open'),
          },
        )
      },
    },
  ]
}
