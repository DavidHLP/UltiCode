import { h, type ComputedRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateByLocale } from '@/i18n/utils'
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
  IconInfoCircle,
  IconLoader,
  IconSparkles,
  IconTrophy,
  IconTrash,
  IconX,
} from '@tabler/icons-vue'

import { Badge } from '@/components/ui/badge'
import { badge, DIFFICULTY_COLOR_MAP } from '@/components/ui/terminal'
import { Checkbox } from '@/components/ui/checkbox'
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
import { Button } from '@/components/ui/button'
import { Difficulty, type Problem } from '@/api/admin/problems'

function getDifficultyIcon(difficulty: Difficulty) {
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

export interface ProblemActions {
  viewProblem: (id: string) => void
  viewProblemCode: (id: string) => void
  viewProblemCases: (id: string) => void
  viewFlagInfo: (problem: Problem) => void
  openFlagDialog: (problem: Problem) => void
  openAuditDrawer: (problem: Problem) => void
  unflagProblem: (id: string) => void
  publishProblem: (id: string) => void
  unpublishProblem: (id: string) => void
  confirmDelete: (problem: Problem) => void
}

export function useProblemColumns(
  canUpdateProblem: ComputedRef<boolean>,
  canDeleteProblem: ComputedRef<boolean>,
  actions: ProblemActions,
): ColumnDef<Problem>[] {
  const { t } = useI18n()

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
        const id = row.getValue('id')
        return h(
          'span',
          { class: 'text-muted-foreground text-xs font-mono' },
          String(id ?? '').slice(0, 8),
        )
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
        return badge({
          color: DIFFICULTY_COLOR_MAP[difficulty] ?? 'neutral',
          label: t(`problems.difficulty.${difficulty.toUpperCase()}`, difficulty),
          dot: true,
          icon: getDifficultyIcon(difficulty),
        })
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
        const label = t(
          `problems.status.${isSolved ? 'solved' : isAttempted ? 'attempted' : 'todo'}`,
        )
        return h('div', { class: 'flex items-center gap-2' }, [
          icon
            ? h(icon, { class: 'h-4 w-4 text-emerald-500' })
            : h(IconLoader, { class: 'h-4 w-4 animate-spin text-muted-foreground' }),
          h(Badge, { variant }, () => label),
        ])
      },
    },
    {
      id: 'isPublished',
      accessorFn: (row) => row.isPublished ?? row.is_published,
      header: () => t('problems.columns.published'),
      cell: ({ row }) => {
        const isPublished = row.getValue('isPublished') as boolean
        const isDeleted = row.original.isDeleted ?? row.original.is_deleted
        if (isDeleted)
          return badge({
            color: 'error',
            label: t('problems.published.deleted'),
            icon: IconX,
          })

        return badge({
          color: isPublished ? 'success' : 'neutral',
          label: isPublished ? t('problems.published.published') : t('problems.published.draft'),
          icon: isPublished ? IconCheck : IconEyeOff,
        })
      },
    },
    {
      id: 'isFlagged',
      accessorFn: (row) => row.isFlagged ?? row.is_flagged,
      header: () => t('problems.columns.flagged'),
      cell: ({ row }) => {
        const problem = row.original
        const isFlagged = row.getValue('isFlagged') as boolean
        if (!isFlagged) {
          return h('span', { class: 'font-data text-xs text-[var(--silver-400)] italic' }, '\u2014')
        }
        const flagStatus = problem.flagStatus || problem.flag_status || ('PENDING' as const)
        const statusColors: Record<string, string> = {
          PENDING: 'text-[var(--terminal-red)]',
          REVIEWED: 'text-[var(--terminal-amber)]',
          RESOLVED: 'text-[var(--terminal-green)]',
          DISMISSED: 'text-[var(--silver-500)]',
        }
        const colorClass = statusColors[flagStatus] ?? statusColors.PENDING
        const statusKey = `moderation.status${flagStatus.charAt(0).toUpperCase() + flagStatus.slice(1).toLowerCase()}`
        return h(
          'div',
          {
            class: 'flex items-center gap-1',
            title: `${t(statusKey)}${problem.flagReason || problem.flag_reason ? `: ${problem.flagReason || problem.flag_reason}` : ''}`,
          },
          [
            h(IconFlag, {
              class: [
                'h-4 w-4',
                colorClass,
                flagStatus === 'PENDING' ? 'animate-pulse-subtle' : '',
              ].join(' '),
            }),
            h(
              'span',
              { class: ['font-data text-2xs uppercase', colorClass].join(' ') },
              t(statusKey).slice(0, 3),
            ),
          ],
        )
      },
    },
    {
      id: 'submissionCount',
      accessorFn: (row) => row.submissionCount ?? row.submission_count,
      header: () => t('problems.columns.submissions'),
      cell: ({ row }) =>
        h(
          'span',
          { class: 'text-muted-foreground text-sm tabular-nums' },
          (Number(row.getValue('submissionCount')) || 0).toLocaleString(),
        ),
    },
    {
      accessorKey: 'tags',
      header: () => t('problems.columns.tags'),
      cell: ({ row }) => {
        const tags = row.original.tags || []
        if (tags.length === 0)
          return h('span', { class: 'text-muted-foreground text-sm' }, '\u2014')
        return h(
          'div',
          { class: 'flex items-center gap-1 flex-wrap' },
          tags
            .slice(0, 3)
            .map((tag) =>
              h(Badge, { variant: 'outline', class: 'text-xs' }, { default: () => tag.label }),
            ),
        )
      },
    },
    {
      accessorKey: 'createdAt',
      header: () => t('common.created'),
      cell: ({ row }) =>
        h(
          'span',
          { class: 'text-muted-foreground text-sm' },
          formatDateByLocale(row.getValue('createdAt') as Date),
        ),
    },
    {
      id: 'actions',
      header: () => t('common.actions.label'),
      cell: ({ row }) => {
        const problem = row.original
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
                                problem.isFlagged
                                  ? h(
                                      DropdownMenuItem,
                                      { onClick: () => actions.viewFlagInfo(problem) },
                                      {
                                        default: () =>
                                          h('div', { class: 'flex items-center gap-2' }, [
                                            h(IconAlertTriangle, {
                                              class: 'h-4 w-4 text-[var(--terminal-amber)]',
                                            }),
                                            t('problems.actions.viewFlagInfo'),
                                          ]),
                                      },
                                    )
                                  : null,
                                h(
                                  DropdownMenuItem,
                                  { onClick: () => actions.openAuditDrawer(problem) },
                                  {
                                    default: () =>
                                      h('div', { class: 'flex items-center gap-2' }, [
                                        h(IconInfoCircle, {
                                          class: 'h-4 w-4 text-[var(--terminal-cyan)]',
                                        }),
                                        t('audit.problemDrawer.button'),
                                      ]),
                                  },
                                ),
                              ],
                            },
                          ),
                        ],
                      },
                    ),
                    h(DropdownMenuSeparator, {}),
                    canUpdateProblem.value
                      ? h(
                          DropdownMenuItem,
                          {
                            onClick: () =>
                              problem.isFlagged
                                ? actions.unflagProblem(problem.id)
                                : actions.openFlagDialog(problem),
                          },
                          {
                            default: () =>
                              h('div', { class: 'flex items-center gap-2' }, [
                                problem.isFlagged
                                  ? h(IconFlagOff, { class: 'h-4 w-4 text-emerald-600' })
                                  : h(IconFlag, { class: 'h-4 w-4 text-amber-600' }),
                                problem.isFlagged ? t('moderation.unflag') : t('moderation.flag'),
                              ]),
                          },
                        )
                      : null,
                    h(DropdownMenuSeparator, {}),
                    canUpdateProblem.value
                      ? problem.isPublished
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
                    canDeleteProblem.value
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
      },
    },
  ]
}
