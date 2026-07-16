import { h, type ComputedRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateByLocale } from '@/i18n/utils'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconAlertTriangle,
  IconCheck,
  IconCircleCheckFilled,
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
import { createEntityActionsMenu } from '@/components/table/entityActions'
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
        return createEntityActionsMenu([
          {
            kind: 'submenu',
            triggerLabel: t('common.view'),
            triggerIcon: IconEye,
            items: [
              {
                label: t('problems.tabs.description'),
                onSelect: () => actions.viewProblem(problem.id),
                icon: IconFile,
              },
              {
                label: t('problems.tabs.code'),
                onSelect: () => actions.viewProblemCode(problem.id),
                icon: IconBrackets,
              },
              {
                label: t('problems.tabs.testCases'),
                onSelect: () => actions.viewProblemCases(problem.id),
                icon: IconFlask,
              },
              {
                label: t('problems.actions.viewFlagInfo'),
                onSelect: () => actions.viewFlagInfo(problem),
                icon: IconAlertTriangle,
                iconClass: 'h-4 w-4 text-[var(--terminal-amber)]',
                hidden: !problem.isFlagged,
              },
              {
                label: t('audit.problemDrawer.button'),
                onSelect: () => actions.openAuditDrawer(problem),
                icon: IconInfoCircle,
                iconClass: 'h-4 w-4 text-[var(--terminal-cyan)]',
              },
            ],
          },
          { kind: 'separator' },
          problem.isFlagged
            ? {
                label: t('moderation.unflag'),
                onSelect: () => actions.unflagProblem(problem.id),
                icon: IconFlagOff,
                iconClass: 'h-4 w-4 text-emerald-600',
                hidden: !canUpdateProblem.value,
              }
            : {
                label: t('moderation.flag'),
                onSelect: () => actions.openFlagDialog(problem),
                icon: IconFlag,
                iconClass: 'h-4 w-4 text-amber-600',
                hidden: !canUpdateProblem.value,
              },
          { kind: 'separator' },
          problem.isPublished
            ? {
                label: t('problems.actions.unpublish'),
                onSelect: () => actions.unpublishProblem(problem.id),
                icon: IconEyeOff,
                labelClass: 'text-amber-600',
                hidden: !canUpdateProblem.value,
              }
            : {
                label: t('problems.actions.publish'),
                onSelect: () => actions.publishProblem(problem.id),
                icon: IconEye,
                labelClass: 'text-emerald-600',
                hidden: !canUpdateProblem.value,
              },
          {
            label: t('common.delete'),
            onSelect: () => actions.confirmDelete(problem),
            icon: IconTrash,
            labelClass: 'text-destructive',
            hidden: !canDeleteProblem.value,
          },
        ])
      },
    },
  ]
}
