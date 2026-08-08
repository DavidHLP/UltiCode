import { h } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconEye,
  IconEyeOff,
  IconPencil,
  IconStar,
  IconStarFilled,
  IconTrash,
} from '@tabler/icons-vue'

import { createSelectionColumn } from '@/components/table/selectionColumn'
import { createEntityActionsMenu } from '@/components/table/entityActions'
import type { ProblemList } from '@/api/admin/problem-lists'
import { formatDate } from '@/lib/format/date'
import { badge } from '@/components/ui/terminal'

export interface ProblemListActions {
  editList: (id: string) => void
  deleteList: (list: ProblemList) => void
}

function renderVisibilityBadge(isPublic: boolean, t: (key: string) => string) {
  if (isPublic) {
    return badge({ color: 'success', label: t('problemLists.visibility.public'), icon: IconEye })
  }
  return badge({ color: 'neutral', label: t('problemLists.visibility.private'), icon: IconEyeOff })
}

// Terminal-style featured badge renderer
function renderFeaturedBadge(isFeatured: boolean) {
  if (isFeatured) {
    return h('div', { class: 'flex items-center gap-2' }, [
      h('span', {
        class: 'w-1.5 h-1.5 rounded-full bg-[var(--terminal-amber)] animate-pulse-subtle',
      }),
      h(IconStarFilled, {
        class: 'h-4 w-4 text-[var(--terminal-amber)]',
      }),
    ])
  }

  return h(IconStar, {
    class: 'h-4 w-4 text-[var(--silver-300)] dark:text-[var(--silver-600)]',
  })
}

export function createColumns(
  t: (key: string) => string,
  actions: ProblemListActions,
  canUpdate: () => boolean,
  canDelete: () => boolean,
): ColumnDef<ProblemList>[] {
  return [
    ...createSelectionColumn<ProblemList>(t, {
      checkboxClass:
        'border-[var(--silver-300)] data-[state=checked]:bg-[var(--accent-electric)] data-[state=checked]:border-[var(--accent-electric)]',
    }),
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
      accessorKey: 'name',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('problemLists.columns.name'),
        ),
      cell: ({ row }) => {
        const list = row.original
        return h('div', { class: 'flex flex-col gap-0.5 py-1' }, [
          h('span', { class: 'font-medium text-sm text-[var(--foreground)]' }, list.name),
          h(
            'span',
            { class: 'font-data text-xs text-[var(--silver-400)] line-clamp-1' },
            list.description || t('common.noData'),
          ),
        ])
      },
    },
    {
      accessorKey: 'isFeatured',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('problemLists.columns.featured'),
        ),
      cell: ({ row }) => {
        const isFeatured = row.getValue('isFeatured') as boolean
        return renderFeaturedBadge(isFeatured)
      },
    },
    {
      accessorKey: 'isPublic',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('problemLists.columns.visibility'),
        ),
      cell: ({ row }) => {
        const isPublic = row.getValue('isPublic') as boolean
        return renderVisibilityBadge(isPublic, t)
      },
    },
    {
      accessorKey: 'problemCount',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('problemLists.columns.problems'),
        ),
      cell: ({ row }) => {
        const count = row.original.problemCount || 0
        return h(
          'span',
          { class: 'font-data text-xs text-[var(--terminal-cyan)] tabular-nums' },
          count.toLocaleString(),
        )
      },
    },
    {
      accessorKey: 'authorName',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('table.columnNames.authorName'),
        ),
      cell: ({ row }) => {
        const authorName = row.original.authorName
        return h('span', { class: 'font-data text-xs text-[var(--silver-400)]' }, authorName || '-')
      },
    },
    {
      accessorKey: 'bannerOrder',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('problemLists.columns.order'),
        ),
      cell: ({ row }) => {
        const order = row.original.bannerOrder
        return h(
          'span',
          { class: 'font-data text-xs text-[var(--silver-400)] tabular-nums' },
          order,
        )
      },
    },
    {
      accessorKey: 'updatedAt',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('common.updated'),
        ),
      cell: ({ row }) => {
        const date = row.getValue('updatedAt') as string
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
          t('common.actions.label'),
        ),
      cell: ({ row }) => {
        const list = row.original
        return createEntityActionsMenu(
          [
            {
              label: t('common.edit'),
              onSelect: () => actions.editList(list.id),
              icon: IconPencil,
              iconClass: 'h-4 w-4 text-[var(--accent-electric)]',
              hidden: !canUpdate(),
            },
            { kind: 'separator', hidden: !canDelete() },
            {
              label: t('common.delete'),
              onSelect: () => actions.deleteList(list),
              icon: IconTrash,
              iconClass: 'h-4 w-4 text-[var(--terminal-red)]',
              labelClass: 'text-[var(--terminal-red)]',
              hidden: !canDelete(),
            },
          ],
          {
            triggerClass:
              'h-8 w-8 p-0 hover:bg-[var(--silver-100)] dark:hover:bg-[var(--silver-800)]',
            triggerIconClass: 'h-4 w-4 text-[var(--silver-400)]',
            contentClass: 'border-[var(--silver-200)] dark:border-[var(--silver-700)]',
            itemClass: 'font-data text-xs cursor-pointer',
            separatorClass: 'bg-[var(--silver-200)] dark:bg-[var(--silver-700)]',
          },
        )
      },
    },
  ]
}