import { h } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  Check as IconCheck,
  EyeOff as IconEyeOff,
  File as IconFile,
  Flag as IconFlag,
  Trash as IconTrash,
  User as IconUser,
  Code as IconCode,
} from '@lucide/vue'

import { createSelectionColumn } from '@/components/table/selectionColumn'
import { createEntityActionsMenu } from '@/components/table/entityActions'
import { badge } from '@ulticode/badge-config'
import type { SolutionListItem } from '@/api/admin/solutions'
import { formatDate } from '@/lib/format/date'
import type { SolutionPermissionMap } from '@/composables/domainPermissionMaps'

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
  can: SolutionPermissionMap,
): ColumnDef<SolutionListItem>[] {
  return [
    ...createSelectionColumn<SolutionListItem>(t, {
      checkboxClass:
        'border-[var(--border)] data-[state=checked]:bg-[var(--primary)] data-[state=checked]:border-[var(--primary)]',
    }),
    {
      id: 'row_num',
      header: () => '#',
      cell: ({ row, table }) => {
        const pageIndex = table.getState().pagination.pageIndex
        const pageSize = table.getState().pagination.pageSize
        const rowNum = pageIndex * pageSize + row.index + 1
        return h(
          'span',
          { class: 'text-xs tabular-nums text-muted-foreground' },
          String(rowNum).padStart(2, '0'),
        )
      },
      enableSorting: false,
      enableHiding: false,
    },
    {
      accessorKey: 'id',
      header: () =>
        h(
          'span',
          { class: 'font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]' },
          t('solutions.columns.id'),
        ),
      cell: ({ row }) => {
        const id = row.getValue('id') as string
        return h(
          'span',
          { class: 'font-mono text-xs text-[var(--muted-foreground)]' },
          id.slice(0, 8),
        )
      },
    },
    {
      accessorKey: 'title',
      header: () =>
        h(
          'span',
          { class: 'font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]' },
          t('solutions.columns.solution'),
        ),
      cell: ({ row }) => {
        const solution = row.original
        return h('div', { class: 'flex flex-col gap-0.5' }, [
          h('span', { class: 'font-medium text-sm text-[var(--foreground)]' }, solution.title),
          h('div', { class: 'flex items-center gap-1 text-xs text-[var(--muted-foreground)]' }, [
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
          { class: 'font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]' },
          t('solutions.columns.author'),
        ),
      cell: ({ row }) => {
        const author = row.original.author
        return h('div', { class: 'flex items-center gap-2' }, [
          h(IconUser, { class: 'h-3.5 w-3.5 text-foreground' }),
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
          { class: 'font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]' },
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
          { class: 'font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]' },
          t('solutions.columns.views'),
        ),
      cell: ({ row }) => {
        const views = row.getValue('views') as number
        return h(
          'span',
          { class: 'font-mono text-xs text-foreground tabular-nums' },
          views.toLocaleString(),
        )
      },
    },
    {
      accessorKey: 'createdAt',
      header: () =>
        h(
          'span',
          { class: 'font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]' },
          t('solutions.columns.created'),
        ),
      cell: ({ row }) => {
        const date = row.getValue('createdAt') as string
        return h(
          'span',
          { class: 'font-mono text-xs text-[var(--muted-foreground)] tabular-nums' },
          formatDate(date),
        )
      },
    },
    {
      id: 'actions',
      header: () =>
        h(
          'span',
          { class: 'font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]' },
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
              iconClass: 'h-4 w-4 text-foreground',
            },
            solution.isFlagged
              ? {
                  label: t('solutions.actions.unflag'),
                  onSelect: () => actions.unflagSolution(solution.id),
                  icon: IconCheck,
                  iconClass: 'h-4 w-4 text-foreground',
                  labelClass: 'text-[var(--foreground)]',
                  hidden: !can.moderate.value,
                }
              : {
                  label: t('solutions.actions.flag'),
                  onSelect: () => actions.openFlagDialog(solution),
                  icon: IconFlag,
                  iconClass: 'h-4 w-4 text-foreground',
                  labelClass: 'text-[var(--foreground)]',
                  hidden: !can.moderate.value,
                },
            { kind: 'separator' },
            {
              label: t('solutions.actions.delete'),
              onSelect: () => actions.confirmDelete(solution),
              icon: IconTrash,
              iconClass: 'h-4 w-4 text-foreground',
              labelClass: 'text-[var(--foreground)]',
              hidden: !can.delete.value,
            },
          ],
          {
            triggerClass: 'h-8 w-8 p-0 hover:bg-[var(--accent)] dark:hover:bg-[var(--foreground)]',
            triggerIconClass: 'h-4 w-4 text-[var(--muted-foreground)]',
            contentClass: 'border-[var(--border)] dark:border-[var(--foreground)]',
            itemClass: 'font-mono text-xs cursor-pointer',
            separatorClass: 'bg-[var(--border)] dark:bg-[var(--foreground)]',
            srLabel: t('common.open'),
          },
        )
      },
    },
  ]
}
