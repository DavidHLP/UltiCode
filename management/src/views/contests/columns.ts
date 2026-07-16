import { h } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconCalendar,
  IconCircleCheckFilled,
  IconClock,
  IconEye,
  IconLoader,
  IconPlayerPlay,
  IconPlayerStop,
  IconTrash,
  IconTrophy,
  IconUsers,
} from '@tabler/icons-vue'

import { Checkbox } from '@/components/ui/checkbox'
import { createEntityActionsMenu } from '@/components/table/entityActions'
import type { Contest } from '@/api/admin/contests'
import { formatDate } from '@/lib/format/date'
import { badge, CONTEST_TYPE_COLOR_MAP, CONTEST_STATUS_COLOR_MAP } from '@/components/ui/terminal'

export interface ContestActions {
  viewContest: (contest: Contest) => void
  startContest: (contest: Contest) => void
  endContest: (contest: Contest) => void
  startDeleteContest: (contest: Contest) => void
}

const CONTEST_STATUS_ICON_MAP: Record<string, typeof IconCircleCheckFilled> = {
  RUNNING: IconCircleCheckFilled,
  FINISHED: IconCircleCheckFilled,
  UPCOMING: IconLoader,
  DRAFT: IconCircleCheckFilled,
  CANCELLED: IconCircleCheckFilled,
}

function renderTypeBadge(type: string, t: (key: string, fallback?: string) => string) {
  return badge({
    color: CONTEST_TYPE_COLOR_MAP[type] ?? 'neutral',
    label: t(`contests.type.${type}`, type),
  })
}

function renderStatusBadge(
  status: string,
  t: (key: string, params?: string | Record<string, unknown>) => string,
) {
  const color = CONTEST_STATUS_COLOR_MAP[status] ?? 'neutral'
  const icon = CONTEST_STATUS_ICON_MAP[status]
  return badge({
    color,
    label: t(`contests.status.${status.toLowerCase()}`, status),
    icon,
    dot: status === 'RUNNING',
    pulse: status === 'RUNNING',
  })
}

export function createColumns(
  t: (key: string, params?: string | Record<string, unknown>) => string,
  actions: ContestActions,
  canUpdate: () => boolean,
  canDelete: () => boolean,
): ColumnDef<Contest>[] {
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
      accessorKey: 'title',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('contests.columns.contest'),
        ),
      cell: ({ row }) => {
        const contest = row.original
        return h('div', { class: 'flex items-center gap-3 py-1' }, [
          h(
            'div',
            {
              'data-testid': 'contest-title-icon',
              class: [
                'h-9 w-9 border flex items-center justify-center',
                'bg-[var(--surface-sunken)]',
                'border-[color-mix(in_oklch,_var(--accent-electric)_28%,_var(--silver-200))]',
                'dark:border-[color-mix(in_oklch,_var(--accent-electric)_38%,_var(--silver-300))]',
                'text-[var(--accent-electric)]',
              ].join(' '),
            },
            [h(IconTrophy, { class: 'h-4 w-4' })],
          ),
          h('div', { class: 'flex flex-col gap-0.5' }, [
            h(
              'span',
              {
                'data-testid': 'contest-title',
                class:
                  'font-medium text-sm text-[var(--foreground)] cursor-pointer hover:text-[var(--accent-electric)] transition-colors',
                onClick: () => actions.viewContest(contest),
              },
              contest.title,
            ),
            h(
              'span',
              {
                'data-testid': 'contest-slug',
                class: 'font-data text-xs text-[var(--muted-foreground)]',
              },
              contest.slug,
            ),
          ]),
        ])
      },
    },
    {
      accessorKey: 'contestType',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('contests.columns.type'),
        ),
      cell: ({ row }) => {
        const type = row.original.contestType
        return renderTypeBadge(type, t)
      },
    },
    {
      accessorKey: 'status',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('contests.columns.status'),
        ),
      cell: ({ row }) => {
        const status = row.original.status
        return renderStatusBadge(status, t)
      },
    },
    {
      accessorKey: 'startTime',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('contests.columns.schedule'),
        ),
      cell: ({ row }) => {
        const contest = row.original
        const startDate = formatDate(contest.startTime)
        return h('div', { class: 'flex flex-col gap-1' }, [
          h('div', { class: 'flex items-center gap-1.5 text-[var(--silver-400)]' }, [
            h(IconCalendar, { class: 'h-3.5 w-3.5' }),
            h('span', { class: 'font-data text-xs tabular-nums' }, startDate),
          ]),
          h('div', { class: 'flex items-center gap-1.5 text-[var(--silver-400)]' }, [
            h(IconClock, { class: 'h-3.5 w-3.5' }),
            h(
              'span',
              { class: 'font-data text-xs tabular-nums' },
              t('contests.scheduleStep.minutes', { minutes: contest.duration }),
            ),
          ]),
        ])
      },
    },
    {
      accessorKey: 'participantCount',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('contests.columns.participants'),
        ),
      cell: ({ row }) => {
        return h('div', { class: 'flex items-center gap-2 text-[var(--silver-400)]' }, [
          h(IconUsers, { class: 'h-4 w-4' }),
          h(
            'span',
            { class: 'font-data text-sm tabular-nums' },
            row.original.participantCount || 0,
          ),
        ])
      },
    },
    {
      id: 'actions',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('contests.columns.actions'),
        ),
      cell: ({ row }) => {
        const contest = row.original
        return createEntityActionsMenu(
          [
            {
              label: t('contests.actions.viewDetails'),
              onSelect: () => actions.viewContest(contest),
              icon: IconEye,
              iconClass: 'h-4 w-4 text-[var(--terminal-cyan)]',
            },
            {
              label: t('contests.actions.startContest'),
              onSelect: () => actions.startContest(contest),
              icon: IconPlayerPlay,
              iconClass: 'h-4 w-4 text-[var(--terminal-green)]',
              labelClass: 'text-[var(--terminal-green)]',
              hidden: !(canUpdate() && contest.status === 'UPCOMING'),
            },
            {
              label: t('contests.actions.endContest'),
              onSelect: () => actions.endContest(contest),
              icon: IconPlayerStop,
              iconClass: 'h-4 w-4 text-[var(--terminal-amber)]',
              labelClass: 'text-[var(--terminal-amber)]',
              hidden: !(canUpdate() && contest.status === 'RUNNING'),
            },
            { kind: 'separator' },
            {
              label: t('contests.actions.delete'),
              onSelect: () => actions.startDeleteContest(contest),
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
