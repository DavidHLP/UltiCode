import { h, type Component } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconAlertTriangle,
  IconCheck,
  IconCircleCheckFilled,
  IconDotsVertical,
  IconEye,
  IconEyeOff,
  IconFile,
  IconFlag,
  IconFlagOff,
  IconFlask,
  IconBrackets,
  IconLoader,
  IconPencil,
  IconSparkles,
  IconTrophy,
  IconTrash,
  IconX,
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
  DropdownMenuSub,
  DropdownMenuSubTrigger,
  DropdownMenuSubContent,
} from '@/components/ui/dropdown-menu'
import { Difficulty, type Problem } from '@/api/admin/problems'
import { getDifficultyBadgeVariant, getDifficultyColor } from '@/lib/entities/problem'
import { formatDate } from '@/lib/format/date'

export interface ProblemActions {
  viewProblem: (id: string) => void
  viewProblemCode: (id: string) => void
  viewProblemCases: (id: string) => void
  editProblem: (id: string) => void
  editProblemCode: (id: string) => void
  editProblemCases: (id: string) => void
  publishProblem: (id: string) => void
  unpublishProblem: (id: string) => void
  flagProblem: (id: string) => void
  unflagProblem: (id: string) => void
  confirmDelete: (problem: Problem) => void
}

function getDifficultyIcon(difficulty: Difficulty): Component {
  switch (difficulty) {
    case 'EASY':
      return IconCheck
    case 'MEDIUM':
      return IconSparkles
    case 'HARD':
      return IconTrophy
    default:
      return IconFile
  }
}

export function createColumns(
  t: (key: string) => string,
  actions: ProblemActions,
  canUpdateProblem: () => boolean,
  canDeleteProblem: () => boolean,
): ColumnDef<Problem>[] {
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
        }),
      cell: ({ row }) =>
        h(Checkbox, {
          modelValue: row.getIsSelected(),
          'onUpdate:modelValue': (value: boolean | 'indeterminate') => row.toggleSelected(!!value),
          'aria-label': 'Select row',
        }),
      enableSorting: false,
      enableHiding: false,
    },
    {
      accessorKey: 'id',
      header: () => t('problems.columns.id'),
      cell: ({ row }) => {
        const id = row.getValue('id') as string
        return h('span', { class: 'text-muted-foreground text-xs font-mono' }, id.slice(0, 8))
      },
    },
    {
      accessorKey: 'title',
      header: () => t('problems.columns.problem'),
      cell: ({ row }) => {
        const problem = row.original
        return h('div', { class: 'flex flex-col' }, [
          h('span', { class: 'font-medium text-sm' }, problem.title),
          h('span', { class: 'text-muted-foreground text-xs' }, problem.slug),
        ])
      },
    },
    {
      accessorKey: 'difficulty',
      header: () => t('problems.columns.difficulty'),
      cell: ({ row }) => {
        const difficulty = row.getValue('difficulty') as Difficulty
        const icon = getDifficultyIcon(difficulty)
        const color = getDifficultyColor(difficulty)
        return h('div', { class: 'flex items-center gap-2' }, [
          h(icon, { class: `h-4 w-4 ${color}` }),
          h(Badge, { variant: getDifficultyBadgeVariant(difficulty) }, () =>
            t(`problems.difficulty.${difficulty}`),
          ),
        ])
      },
    },
    {
      accessorKey: 'status',
      header: () => t('common.status'),
      cell: ({ row }) => {
        const status = row.getValue('status') as string
        const isSolved = status === 'solved'
        const isAttempted = status === 'attempted'
        const icon = isSolved ? IconCircleCheckFilled : undefined
        const variant = isSolved
          ? ('default' as const)
          : isAttempted
            ? ('secondary' as const)
            : ('outline' as const)
        const label = t(`problems.status.${isSolved ? 'solved' : isAttempted ? 'attempted' : 'todo'}`)
        return h('div', { class: 'flex items-center gap-2' }, [
          icon
            ? h(icon, { class: 'h-4 w-4 text-emerald-500' })
            : h(IconLoader, { class: 'h-4 w-4 animate-spin text-muted-foreground' }),
          h(Badge, { variant }, () => label),
        ])
      },
    },
    {
      accessorKey: 'is_published',
      header: () => t('problems.columns.published'),
      cell: ({ row }) => {
        const isPublished = row.getValue('is_published') as boolean
        const isDeleted = row.original.is_deleted
        if (isDeleted) {
          return h(
            Badge,
            { variant: 'destructive' },
            {
              default: () => [h(IconX, { class: 'mr-1 h-3 w-3' }), t('problems.published.deleted')],
            },
          )
        }
        return h(
          Badge,
          { variant: isPublished ? 'default' : 'secondary' },
          {
            default: () => [
              isPublished
                ? h(IconCheck, { class: 'mr-1 h-3 w-3' })
                : h(IconEyeOff, { class: 'mr-1 h-3 w-3' }),
              isPublished ? t('problems.published.published') : t('problems.published.draft'),
            ],
          },
        )
      },
    },
    {
      accessorKey: 'is_flagged',
      header: () => t('problems.columns.flagged'),
      cell: ({ row }) => {
        const isFlagged = row.original.is_flagged
        if (!isFlagged) {
          return h('span', { class: 'text-muted-foreground text-sm' }, '—')
        }
        return h(
          Badge,
          { variant: 'destructive', class: 'gap-1' },
          {
            default: () => [h(IconAlertTriangle, { class: 'h-3 w-3' }), t('moderation.statusPending')],
          },
        )
      },
    },
    {
      accessorKey: 'submission_count',
      header: () => t('problems.columns.submissions'),
      cell: ({ row }) => {
        const count = row.original.submission_count || 0
        return h('span', { class: 'text-muted-foreground text-sm tabular-nums' }, count.toLocaleString())
      },
    },
    {
      accessorKey: 'tags',
      header: () => t('problems.columns.tags'),
      cell: ({ row }) => {
        const tags = row.original.tags || []
        if (tags.length === 0) {
          return h('span', { class: 'text-muted-foreground text-sm' }, '—')
        }
        return h(
          'div',
          { class: 'flex items-center gap-1 flex-wrap' },
          tags.slice(0, 3).map((tag) =>
            h(Badge, { variant: 'outline', class: 'text-xs' }, { default: () => tag.label }),
          ),
        )
      },
    },
    {
      accessorKey: 'created_at',
      header: () => t('common.created'),
      cell: ({ row }) => {
        const date = row.getValue('created_at') as string
        return h('span', { class: 'text-muted-foreground text-sm' }, formatDate(date))
      },
    },
    {
      id: 'actions',
      header: () => t('common.actions'),
      cell: ({ row }) => {
        const problem = row.original
        return createActionsDropdown(t, problem, actions, canUpdateProblem, canDeleteProblem)
      },
    },
  ]
}

function createActionsDropdown(
  t: (key: string) => string,
  problem: Problem,
  actions: ProblemActions,
  canUpdateProblem: () => boolean,
  canDeleteProblem: () => boolean,
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
                    h('span', { class: 'sr-only' }, 'Open menu'),
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
              // View Sub-menu
              h(
                DropdownMenuSub,
                {},
                {
                  default: () => [
                    h(
                      DropdownMenuSubTrigger,
                      { class: 'gap-2' },
                      { default: () => [h(IconEye, { class: 'h-4 w-4' }), t('common.view')] },
                    ),
                    h(
                      DropdownMenuSubContent,
                      {},
                      {
                        default: () => [
                          h(
                            DropdownMenuItem,
                            { onClick: () => actions.viewProblem(problem.id) },
                            {
                              default: () =>
                                h('div', { class: 'flex items-center gap-2' }, [
                                  h(IconFile, { class: 'h-4 w-4' }),
                                  t('problems.tabs.description'),
                                ]),
                            },
                          ),
                          h(
                            DropdownMenuItem,
                            { onClick: () => actions.viewProblemCode(problem.id) },
                            {
                              default: () =>
                                h('div', { class: 'flex items-center gap-2' }, [
                                  h(IconBrackets, { class: 'h-4 w-4' }),
                                  t('problems.tabs.code'),
                                ]),
                            },
                          ),
                          h(
                            DropdownMenuItem,
                            { onClick: () => actions.viewProblemCases(problem.id) },
                            {
                              default: () =>
                                h('div', { class: 'flex items-center gap-2' }, [
                                  h(IconFlask, { class: 'h-4 w-4' }),
                                  t('problems.tabs.testCases'),
                                ]),
                            },
                          ),
                        ],
                      },
                    ),
                  ],
                },
              ),
              // Edit Sub-menu
              canUpdateProblem()
                ? h(
                    DropdownMenuSub,
                    {},
                    {
                      default: () => [
                        h(
                          DropdownMenuSubTrigger,
                          { class: 'gap-2' },
                          { default: () => [h(IconPencil, { class: 'h-4 w-4' }), t('common.edit')] },
                        ),
                        h(
                          DropdownMenuSubContent,
                          {},
                          {
                            default: () => [
                              h(
                                DropdownMenuItem,
                                { onClick: () => actions.editProblem(problem.id) },
                                {
                                  default: () =>
                                    h('div', { class: 'flex items-center gap-2' }, [
                                      h(IconFile, { class: 'h-4 w-4' }),
                                      t('problems.tabs.description'),
                                    ]),
                                },
                              ),
                              h(
                                DropdownMenuItem,
                                { onClick: () => actions.editProblemCode(problem.id) },
                                {
                                  default: () =>
                                    h('div', { class: 'flex items-center gap-2' }, [
                                      h(IconBrackets, { class: 'h-4 w-4' }),
                                      t('problems.tabs.code'),
                                    ]),
                                },
                              ),
                              h(
                                DropdownMenuItem,
                                { onClick: () => actions.editProblemCases(problem.id) },
                                {
                                  default: () =>
                                    h('div', { class: 'flex items-center gap-2' }, [
                                      h(IconFlask, { class: 'h-4 w-4' }),
                                      t('problems.tabs.testCases'),
                                    ]),
                                },
                              ),
                            ],
                          },
                        ),
                      ],
                    },
                  )
                : null,
              h(DropdownMenuSeparator, {}),
              // Flag/Unflag action
              canUpdateProblem()
                ? h(
                    DropdownMenuItem,
                    {
                      onClick: () =>
                        problem.is_flagged
                          ? actions.unflagProblem(problem.id)
                          : actions.flagProblem(problem.id),
                    },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2' }, [
                          problem.is_flagged
                            ? h(IconFlagOff, { class: 'h-4 w-4 text-emerald-600' })
                            : h(IconFlag, { class: 'h-4 w-4 text-amber-600' }),
                          problem.is_flagged ? t('moderation.unflag') : t('moderation.flag'),
                        ]),
                    },
                  )
                : null,
              h(DropdownMenuSeparator, {}),
              canUpdateProblem()
                ? problem.is_published
                  ? h(
                      DropdownMenuItem,
                      { onClick: () => actions.unpublishProblem(problem.id) },
                      {
                        default: () =>
                          h('div', { class: 'flex items-center gap-2 text-amber-600' }, [
                            h(IconEyeOff, { class: 'h-4 w-4' }),
                            t('problems.actions.unpublish'),
                          ]),
                      },
                    )
                  : h(
                      DropdownMenuItem,
                      { onClick: () => actions.publishProblem(problem.id) },
                      {
                        default: () =>
                          h('div', { class: 'flex items-center gap-2 text-emerald-600' }, [
                            h(IconEye, { class: 'h-4 w-4' }),
                            t('problems.actions.publish'),
                          ]),
                      },
                    )
                : null,
              canDeleteProblem()
                ? h(
                    DropdownMenuItem,
                    { onClick: () => actions.confirmDelete(problem) },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2 text-destructive' }, [
                          h(IconTrash, { class: 'h-4 w-4' }),
                          t('common.delete'),
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
