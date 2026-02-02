import { h } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconCheck,
  IconDotsVertical,
  IconEyeOff,
  IconFile,
  IconFlag,
  IconTrash,
  IconUser,
  IconCode,
} from '@tabler/icons-vue'

import { Badge } from '@/components/ui/badge'
import { Checkbox } from '@/components/ui/checkbox'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import type { Solution } from '@/api/admin/solutions'
import { formatDate } from '@/lib/format/date'

export interface SolutionActions {
  viewSolution: (id: string) => void
  openFlagDialog: (solution: Solution) => void
  unflagSolution: (id: string) => void
  confirmDelete: (solution: Solution) => void
}

export function createColumns(
  t: (key: string) => string,
  actions: SolutionActions,
  canUpdateSolution: () => boolean,
  canDeleteSolution: () => boolean,
): ColumnDef<Solution>[] {
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
        }),
      cell: ({ row }) =>
        h(Checkbox, {
          modelValue: row.getIsSelected(),
          'onUpdate:modelValue': (value: boolean | 'indeterminate') => row.toggleSelected(!!value),
          'aria-label': t('common.select'),
        }),
      enableSorting: false,
      enableHiding: false,
    },
    {
      accessorKey: 'id',
      header: () => t('solutions.columns.id'),
      cell: ({ row }) => {
        const id = row.getValue('id') as string
        return h('span', { class: 'text-muted-foreground text-xs font-mono' }, id.slice(0, 8))
      },
    },
    {
      accessorKey: 'title',
      header: () => t('solutions.columns.solution'),
      cell: ({ row }) => {
        const solution = row.original
        return h('div', { class: 'flex flex-col' }, [
          h('span', { class: 'font-medium text-sm' }, solution.title),
          h('div', { class: 'flex items-center gap-1 text-xs text-muted-foreground' }, [
            h(IconCode, { class: 'h-3 w-3' }),
            h('span', {}, solution.problem?.title || t('common.noData')),
          ]),
        ])
      },
    },
    {
      accessorKey: 'author',
      header: () => t('solutions.columns.author'),
      cell: ({ row }) => {
        const author = row.original.author
        return h('div', { class: 'flex items-center gap-2' }, [
          h(IconUser, { class: 'h-3 w-3 text-muted-foreground' }),
          h('span', { class: 'text-sm' }, author?.username || t('common.noData')),
        ])
      },
    },
    {
      accessorKey: 'is_flagged',
      header: () => t('solutions.columns.status'),
      cell: ({ row }) => {
        const isFlagged = row.getValue('is_flagged') as boolean
        const isPublished = row.original.is_published
        const isDeleted = row.original.is_deleted

        if (isDeleted) {
          return h(Badge, { variant: 'destructive' }, () => [
            h(IconTrash, { class: 'mr-1 h-3 w-3' }),
            t('solutions.status.deleted'),
          ])
        }

        if (isFlagged) {
          return h(Badge, { variant: 'destructive' }, () => [
            h(IconFlag, { class: 'mr-1 h-3 w-3' }),
            t('solutions.status.flagged'),
          ])
        }

        return h(Badge, { variant: isPublished ? 'default' : 'secondary' }, () => [
          isPublished
            ? h(IconCheck, { class: 'mr-1 h-3 w-3' })
            : h(IconEyeOff, { class: 'mr-1 h-3 w-3' }),
          isPublished ? t('solutions.status.published') : t('solutions.status.unpublished'),
        ])
      },
    },
    {
      accessorKey: 'views',
      header: () => t('solutions.columns.views'),
      cell: ({ row }) => {
        const views = row.getValue('views') as number
        return h('span', { class: 'text-muted-foreground text-sm tabular-nums' }, views.toLocaleString())
      },
    },
    {
      accessorKey: 'created_at',
      header: () => t('solutions.columns.created'),
      cell: ({ row }) => {
        const date = row.getValue('created_at') as string
        return h('span', { class: 'text-muted-foreground text-sm' }, formatDate(date))
      },
    },
    {
      id: 'actions',
      header: () => t('solutions.columns.actions'),
      cell: ({ row }) => {
        const solution = row.original
        return createActionsDropdown(t, solution, actions, canUpdateSolution, canDeleteSolution)
      },
    },
  ]
}

function createActionsDropdown(
  t: (key: string) => string,
  solution: Solution,
  actions: SolutionActions,
  canUpdateSolution: () => boolean,
  canDeleteSolution: () => boolean,
) {
  return h(
    DropdownMenu,
    {},
    {
      default: () => [
        h(
          DropdownMenuTrigger,
          { asChild: true },
          {
            default: () =>
              h(
                Button,
                { variant: 'ghost', size: 'icon', class: 'h-8 w-8 p-0' },
                {
                  default: () => [
                    h('span', { class: 'sr-only' }, t('common.open')),
                    h(IconDotsVertical, { class: 'h-4 w-4' }),
                  ],
                },
              ),
          },
        ),
        h(
          DropdownMenuContent,
          { align: 'end' },
          {
            default: () => [
              h(
                DropdownMenuItem,
                { onClick: () => actions.viewSolution(solution.id) },
                {
                  default: () =>
                    h('div', { class: 'flex items-center gap-2' }, [
                      h(IconFile, { class: 'h-4 w-4' }),
                      t('solutions.actions.viewDetails'),
                    ]),
                },
              ),
              canUpdateSolution()
                ? solution.is_flagged
                  ? h(
                      DropdownMenuItem,
                      { onClick: () => actions.unflagSolution(solution.id) },
                      {
                        default: () =>
                          h('div', { class: 'flex items-center gap-2 text-emerald-600' }, [
                            h(IconCheck, { class: 'h-4 w-4' }),
                            t('solutions.actions.unflag'),
                          ]),
                      },
                    )
                  : h(
                      DropdownMenuItem,
                      { onClick: () => actions.openFlagDialog(solution) },
                      {
                        default: () =>
                          h('div', { class: 'flex items-center gap-2 text-amber-600' }, [
                            h(IconFlag, { class: 'h-4 w-4' }),
                            t('solutions.actions.flag'),
                          ]),
                      },
                    )
                : null,
              h(DropdownMenuSeparator, {}),
              canDeleteSolution()
                ? h(
                    DropdownMenuItem,
                    { onClick: () => actions.confirmDelete(solution) },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2 text-destructive' }, [
                          h(IconTrash, { class: 'h-4 w-4' }),
                          t('solutions.actions.delete'),
                        ]),
                    },
                  )
                : null,
            ],
          },
        ),
      ],
    },
  )
}
