import { h } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconDotsVertical,
  IconEye,
  IconEyeOff,
  IconPencil,
  IconStar,
  IconStarFilled,
  IconTrash,
} from '@tabler/icons-vue'

import { Checkbox } from '@/components/ui/checkbox'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Button } from '@/components/ui/button'
import type { ProblemList } from '@/api/admin/problem-lists'
import { formatDate } from '@/lib/format/date'

export interface ProblemListActions {
  editList: (id: string) => void
  deleteList: (list: ProblemList) => void
}

// Terminal-style visibility badge renderer
function renderVisibilityBadge(isPublic: boolean) {
  if (isPublic) {
    return h('div', { class: 'flex items-center gap-2' }, [
      h(IconEye, { class: 'h-3.5 w-3.5 text-[var(--terminal-green)]' }),
      h(
        'span',
        {
          class: [
            'font-data text-[11px] font-medium uppercase tracking-[0.05em]',
            'px-2 py-0.5 border rounded-sm',
            'bg-[oklch(0.7_0.15_145/0.15)]',
            'border-[oklch(0.7_0.15_145/0.4)]',
            'text-[var(--terminal-green)]',
          ].join(' '),
        },
        'PUBLIC',
      ),
    ])
  }

  return h('div', { class: 'flex items-center gap-2' }, [
    h(IconEyeOff, { class: 'h-3.5 w-3.5 text-[var(--silver-400)]' }),
    h(
      'span',
      {
        class: [
          'font-data text-[11px] font-medium uppercase tracking-[0.05em]',
          'px-2 py-0.5 border rounded-sm',
          'bg-[var(--silver-100)] dark:bg-[var(--silver-800)]',
          'border-[var(--silver-300)] dark:border-[var(--silver-600)]',
          'text-[var(--silver-500)]',
        ].join(' '),
      },
      'PRIVATE',
    ),
  ])
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
    {
      id: 'select',
      header: ({ table }) =>
        h(Checkbox, {
          modelValue:
            table.getIsAllPageRowsSelected() ||
            (table.getIsSomePageRowsSelected() && 'indeterminate'),
          'onUpdate:modelValue': (value: boolean | 'indeterminate') =>
            table.toggleAllPageRowsSelected(!!value),
          'aria-label': 'Select all',
          class:
            'border-[var(--silver-300)] data-[state=checked]:bg-[var(--accent-electric)] data-[state=checked]:border-[var(--accent-electric)]',
        }),
      cell: ({ row }) =>
        h(Checkbox, {
          modelValue: row.getIsSelected(),
          'onUpdate:modelValue': (value: boolean | 'indeterminate') => row.toggleSelected(!!value),
          'aria-label': 'Select row',
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
      accessorKey: 'name',
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
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
      accessorKey: 'is_featured',
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('problemLists.columns.featured'),
        ),
      cell: ({ row }) => {
        const isFeatured = row.getValue('is_featured') as boolean
        return renderFeaturedBadge(isFeatured)
      },
    },
    {
      accessorKey: 'is_public',
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('problemLists.columns.visibility'),
        ),
      cell: ({ row }) => {
        const isPublic = row.getValue('is_public') as boolean
        return renderVisibilityBadge(isPublic)
      },
    },
    {
      accessorKey: 'problem_count',
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('problemLists.columns.problems'),
        ),
      cell: ({ row }) => {
        const count = row.original.problem_count || 0
        return h(
          'span',
          { class: 'font-data text-xs text-[var(--terminal-cyan)] tabular-nums' },
          count.toLocaleString(),
        )
      },
    },
    {
      accessorKey: 'banner_order',
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('problemLists.columns.order'),
        ),
      cell: ({ row }) => {
        const order = row.original.banner_order
        return h(
          'span',
          { class: 'font-data text-xs text-[var(--silver-400)] tabular-nums' },
          order,
        )
      },
    },
    {
      accessorKey: 'updated_at',
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('common.updated'),
        ),
      cell: ({ row }) => {
        const date = row.getValue('updated_at') as string
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
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('common.actions'),
        ),
      cell: ({ row }) => {
        const list = row.original
        return createActionsDropdown(t, list, actions, canUpdate, canDelete)
      },
    },
  ]
}

function createActionsDropdown(
  t: (key: string) => string,
  list: ProblemList,
  actions: ProblemListActions,
  canUpdate: () => boolean,
  canDelete: () => boolean,
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
                {
                  variant: 'ghost',
                  size: 'icon',
                  class:
                    'h-8 w-8 p-0 hover:bg-[var(--silver-100)] dark:hover:bg-[var(--silver-800)]',
                },
                {
                  default: () => [
                    h('span', { class: 'sr-only' }, 'Open menu'),
                    h(IconDotsVertical, { class: 'h-4 w-4 text-[var(--silver-400)]' }),
                  ],
                },
              ),
          },
        ),
        h(
          DropdownMenuContent,
          {
            align: 'end',
            class: 'border-[var(--silver-200)] dark:border-[var(--silver-700)]',
          },
          {
            default: () =>
              [
                canUpdate()
                  ? h(
                      DropdownMenuItem,
                      {
                        onClick: () => actions.editList(list.id),
                        class: 'font-data text-xs cursor-pointer',
                      },
                      {
                        default: () =>
                          h('div', { class: 'flex items-center gap-2' }, [
                            h(IconPencil, { class: 'h-4 w-4 text-[var(--accent-electric)]' }),
                            h('span', t('common.edit')),
                          ]),
                      },
                    )
                  : null,
                canDelete()
                  ? [
                      h(DropdownMenuSeparator, {
                        class: 'bg-[var(--silver-200)] dark:bg-[var(--silver-700)]',
                      }),
                      h(
                        DropdownMenuItem,
                        {
                          onClick: () => actions.deleteList(list),
                          class: 'font-data text-xs cursor-pointer',
                        },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2' }, [
                              h(IconTrash, { class: 'h-4 w-4 text-[var(--terminal-red)]' }),
                              h(
                                'span',
                                { class: 'text-[var(--terminal-red)]' },
                                t('common.delete'),
                              ),
                            ]),
                        },
                      ),
                    ]
                  : null,
              ].filter(Boolean),
          },
        ),
      ],
    },
  )
}
