import { h } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconAlertTriangle,
  IconDotsVertical,
  IconEye,
  IconEyeOff,
  IconFile,
  IconFlag,
  IconFlagOff,
  IconFlask,
  IconBrackets,
  IconPencil,
  IconTrash,
} from '@tabler/icons-vue'

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

// Terminal-style difficulty badge renderer (matches role badge style)
function renderDifficultyBadge(difficulty: Difficulty, t: (key: string) => string) {
  const difficultyStyles: Record<Difficulty, { bg: string; border: string; text: string }> = {
    EASY: {
      bg: 'bg-[oklch(0.7_0.15_145/0.15)]',
      border: 'border-[oklch(0.7_0.15_145/0.4)]',
      text: 'text-[var(--terminal-green)]',
    },
    MEDIUM: {
      bg: 'bg-[oklch(0.75_0.15_85/0.15)]',
      border: 'border-[oklch(0.75_0.15_85/0.4)]',
      text: 'text-[var(--terminal-amber)]',
    },
    HARD: {
      bg: 'bg-[oklch(0.6_0.2_25/0.15)]',
      border: 'border-[oklch(0.6_0.2_25/0.4)]',
      text: 'text-[var(--terminal-red)]',
    },
  }

  const style = difficultyStyles[difficulty]

  return h('div', { class: 'flex items-center gap-2' }, [
    h('span', {
      class: ['w-1.5 h-1.5 rounded-full', style.text.replace('text-', 'bg-')].join(' '),
    }),
    h(
      'span',
      {
        class: [
          'font-data text-[11px] font-medium uppercase tracking-[0.05em]',
          'px-2 py-0.5 border',
          style.bg,
          style.border,
          style.text,
        ].join(' '),
      },
      t(`problems.difficulty.${difficulty}`),
    ),
  ])
}

// Terminal-style published status badge renderer (matches role badge style)
function renderPublishedBadge(
  isPublished: boolean,
  isDeleted: boolean,
  t: (key: string) => string,
) {
  if (isDeleted) {
    return h(
      'span',
      {
        class: [
          'font-data text-[11px] font-medium uppercase tracking-[0.05em]',
          'px-2 py-0.5 border',
          'bg-[oklch(0.6_0.2_25/0.15)]',
          'border-[oklch(0.6_0.2_25/0.4)]',
          'text-[var(--terminal-red)]',
        ].join(' '),
      },
      t('problems.published.deleted'),
    )
  }

  if (isPublished) {
    return h('div', { class: 'flex items-center gap-2' }, [
      h('span', {
        class: 'w-1.5 h-1.5 bg-[var(--terminal-green)] animate-pulse-subtle',
      }),
      h(
        'span',
        {
          class: [
            'font-data text-[11px] font-medium uppercase tracking-[0.05em]',
            'px-2 py-0.5 border',
            'bg-[oklch(0.7_0.15_145/0.15)]',
            'border-[oklch(0.7_0.15_145/0.4)]',
            'text-[var(--terminal-green)]',
          ].join(' '),
        },
        t('problems.published.published'),
      ),
    ])
  }

  return h(
    'span',
    {
      class: [
        'font-data text-[11px] font-medium uppercase tracking-[0.05em]',
        'px-2 py-0.5 border',
        'bg-[var(--silver-100)] dark:bg-[var(--silver-800)]',
        'border-[var(--silver-300)] dark:border-[var(--silver-600)]',
        'text-[var(--silver-500)]',
      ].join(' '),
    },
    t('problems.published.draft'),
  )
}

// Terminal-style problem status badge renderer (matches role badge style)
function renderProblemStatusBadge(status: string, t: (key: string) => string) {
  const isSolved = status === 'solved'
  const isAttempted = status === 'attempted'
  const label = t(`problems.status.${isSolved ? 'solved' : isAttempted ? 'attempted' : 'todo'}`)

  if (isSolved) {
    return h('div', { class: 'flex items-center gap-2' }, [
      h('span', {
        class: 'w-1.5 h-1.5 bg-[var(--terminal-green)] animate-pulse-subtle',
      }),
      h(
        'span',
        {
          class: [
            'font-data text-[11px] font-medium uppercase tracking-[0.05em]',
            'px-2 py-0.5 border',
            'bg-[oklch(0.7_0.15_145/0.15)]',
            'border-[oklch(0.7_0.15_145/0.4)]',
            'text-[var(--terminal-green)]',
          ].join(' '),
        },
        label,
      ),
    ])
  }

  if (isAttempted) {
    return h('div', { class: 'flex items-center gap-2' }, [
      h('span', {
        class: 'w-1.5 h-1.5 bg-[var(--terminal-amber)]',
      }),
      h(
        'span',
        {
          class: [
            'font-data text-[11px] font-medium uppercase tracking-[0.05em]',
            'px-2 py-0.5 border',
            'bg-[oklch(0.75_0.15_85/0.15)]',
            'border-[oklch(0.75_0.15_85/0.4)]',
            'text-[var(--terminal-amber)]',
          ].join(' '),
        },
        label,
      ),
    ])
  }

  return h(
    'span',
    {
      class: [
        'font-data text-[11px] font-medium uppercase tracking-[0.05em]',
        'px-2 py-0.5 border',
        'bg-[var(--silver-100)] dark:bg-[var(--silver-800)]',
        'border-[var(--silver-300)] dark:border-[var(--silver-600)]',
        'text-[var(--silver-500)]',
      ].join(' '),
    },
    label,
  )
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
      accessorKey: 'id',
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]',
          },
          t('problems.columns.id'),
        ),
      cell: ({ row }) => {
        const id = row.getValue('id') as string
        return h('span', { class: 'text-muted-foreground text-xs font-mono' }, id.slice(0, 8))
      },
    },
    {
      accessorKey: 'title',
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]',
          },
          t('problems.columns.problem'),
        ),
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
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]',
          },
          t('problems.columns.difficulty'),
        ),
      cell: ({ row }) => {
        const difficulty = row.getValue('difficulty') as Difficulty
        return renderDifficultyBadge(difficulty, t)
      },
    },
    {
      accessorKey: 'status',
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]',
          },
          t('common.status'),
        ),
      cell: ({ row }) => {
        const status = row.getValue('status') as string
        return renderProblemStatusBadge(status, t)
      },
    },
    {
      accessorKey: 'is_published',
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]',
          },
          t('problems.columns.published'),
        ),
      cell: ({ row }) => {
        const isPublished = row.getValue('is_published') as boolean
        const isDeleted = row.original.is_deleted
        return renderPublishedBadge(isPublished, isDeleted, t)
      },
    },
    {
      accessorKey: 'is_flagged',
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]',
          },
          t('problems.columns.flagged'),
        ),
      cell: ({ row }) => {
        const isFlagged = row.original.is_flagged
        if (!isFlagged) {
          return h('span', { class: 'font-data text-xs text-[var(--silver-400)] italic' }, '—')
        }
        const classes =
          'font-data text-[11px] uppercase px-2 py-0.5 border rounded-sm bg-[oklch(0.6_0.2_25/0.15)] border-[oklch(0.6_0.2_25/0.4)] text-[var(--terminal-red)] animate-pulse-subtle'
        return h('span', { class: `inline-flex items-center gap-1 ${classes}` }, [
          h(IconAlertTriangle, { class: 'h-3 w-3' }),
          t('moderation.statusPending'),
        ])
      },
    },
    {
      accessorKey: 'submission_count',
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]',
          },
          t('problems.columns.submissions'),
        ),
      cell: ({ row }) => {
        const count = row.original.submission_count || 0
        return h(
          'span',
          {
            class:
              'font-data text-sm text-[var(--silver-600)] dark:text-[var(--silver-400)] tabular-nums',
          },
          count.toLocaleString(),
        )
      },
    },
    {
      accessorKey: 'tags',
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]',
          },
          t('problems.columns.tags'),
        ),
      cell: ({ row }) => {
        const tags = row.original.tags || []
        if (tags.length === 0) {
          return h('span', { class: 'font-data text-xs text-[var(--silver-400)] italic' }, '—')
        }
        return h(
          'div',
          { class: 'flex items-center gap-1 flex-wrap' },
          tags.slice(0, 3).map((tag) =>
            h(
              'span',
              {
                class:
                  'font-data text-[10px] uppercase px-1.5 py-0.5 border border-[var(--silver-200)] dark:border-[var(--silver-300)] text-[var(--silver-600)] dark:text-[var(--silver-400)] rounded-sm',
              },
              tag.label,
            ),
          ),
        )
      },
    },
    {
      accessorKey: 'created_at',
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]',
          },
          t('common.created'),
        ),
      cell: ({ row }) => {
        const date = row.getValue('created_at') as string
        return h(
          'span',
          { class: 'font-data text-xs text-[var(--silver-600)] dark:text-[var(--silver-400)]' },
          formatDate(date),
        )
      },
    },
    {
      id: 'actions',
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]',
          },
          t('common.actions'),
        ),
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
                          {
                            default: () => [h(IconPencil, { class: 'h-4 w-4' }), t('common.edit')],
                          },
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
