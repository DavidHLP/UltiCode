import { h } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconCalendar,
  IconCircleCheckFilled,
  IconCircleXFilled,
  IconClock,
  IconDotsVertical,
  IconEye,
  IconLoader,
  IconPlayerPlay,
  IconPlayerStop,
  IconTrash,
  IconTrophy,
  IconUsers,
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
import type { Contest } from '@/api/admin/contests'
import { getContestTypeBadgeVariant } from '@/lib/entities/contest'
import { formatDate } from '@/lib/format/date'

export interface ContestActions {
  viewContest: (contest: Contest) => void
  startContest: (contest: Contest) => void
  endContest: (contest: Contest) => void
  startDeleteContest: (contest: Contest) => void
}

export function createColumns(
  t: (key: string, params?: Record<string, unknown>) => string,
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
      accessorKey: 'title',
      header: () => t('contests.columns.contest'),
      cell: ({ row }) => {
        const contest = row.original
        return h('div', { class: 'flex items-center gap-3' }, [
          h(
            'div',
            {
              class:
                'h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center text-primary',
            },
            [h(IconTrophy, { class: 'h-4 w-4' })],
          ),
          h('div', { class: 'flex flex-col' }, [
            h(
              'span',
              {
                class: 'font-medium text-sm cursor-pointer hover:underline',
                onClick: () => actions.viewContest(contest),
              },
              contest.title,
            ),
            h('span', { class: 'text-muted-foreground text-xs' }, contest.slug),
          ]),
        ])
      },
    },
    {
      accessorKey: 'contest_type',
      header: () => t('contests.columns.type'),
      cell: ({ row }) => {
        const type = row.original.contest_type
        return h('div', { class: 'flex items-center gap-2' }, [
          h(Badge, { variant: getContestTypeBadgeVariant(type) }, () => t(`contests.type.${type}`)),
        ])
      },
    },
    {
      accessorKey: 'status',
      header: () => t('contests.columns.status'),
      cell: ({ row }) => {
        const status = row.original.status
        return h('div', { class: 'flex items-center gap-2' }, [
          getStatusIcon(status),
          getStatusBadge(status, t),
        ])
      },
    },
    {
      accessorKey: 'start_time',
      header: () => t('contests.columns.schedule'),
      cell: ({ row }) => {
        const contest = row.original
        const startDate = formatDate(contest.start_time)
        return h('div', { class: 'flex flex-col text-sm' }, [
          h('div', { class: 'flex items-center gap-1.5 text-muted-foreground' }, [
            h(IconCalendar, { class: 'h-3.5 w-3.5' }),
            h('span', {}, startDate),
          ]),
          h('div', { class: 'flex items-center gap-1.5 text-muted-foreground' }, [
            h(IconClock, { class: 'h-3.5 w-3.5' }),
            h(
              'span',
              {},
              t('contests.scheduleStep.minutes', { minutes: contest.duration_minutes }),
            ),
          ]),
        ])
      },
    },
    {
      accessorKey: 'participant_count',
      header: () => t('contests.columns.participants'),
      cell: ({ row }) => {
        return h('div', { class: 'flex items-center gap-2 text-muted-foreground text-sm' }, [
          h(IconUsers, { class: 'h-4 w-4' }),
          h('span', {}, row.original.participant_count || 0),
        ])
      },
    },
    {
      id: 'actions',
      header: () => t('contests.columns.actions'),
      cell: ({ row }) => {
        const contest = row.original
        return createActionsDropdown(t, contest, actions, canUpdate, canDelete)
      },
    },
  ]
}

function getStatusIcon(status: string) {
  switch (status) {
    case 'RUNNING':
      return h(IconCircleCheckFilled, { class: 'h-4 w-4 text-emerald-500' })
    case 'FINISHED':
      return h(IconCircleXFilled, { class: 'h-4 w-4 text-muted-foreground' })
    default:
      return h(IconLoader, { class: 'h-4 w-4 animate-spin text-blue-500' })
  }
}

function getStatusBadge(
  status: string,
  t: (key: string, params?: Record<string, unknown>) => string,
) {
  switch (status) {
    case 'RUNNING':
      return h(Badge, { variant: 'default' }, () => t('contests.status.running'))
    case 'FINISHED':
      return h(Badge, { variant: 'secondary' }, () => t('contests.status.finished'))
    default:
      return h(Badge, { variant: 'outline' }, () => t('contests.status.upcoming'))
  }
}

function createActionsDropdown(
  t: (key: string, params?: Record<string, unknown>) => string,
  contest: Contest,
  actions: ContestActions,
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
                DropdownMenuItem,
                { onClick: () => actions.viewContest(contest) },
                {
                  default: () =>
                    h('div', { class: 'flex items-center gap-2' }, [
                      h(IconEye, { class: 'h-4 w-4' }),
                      t('contests.actions.viewDetails'),
                    ]),
                },
              ),
              canUpdate() && contest.status === 'UPCOMING'
                ? h(
                    DropdownMenuItem,
                    { onClick: () => actions.startContest(contest) },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2 text-emerald-600' }, [
                          h(IconPlayerPlay, { class: 'h-4 w-4' }),
                          t('contests.actions.startContest'),
                        ]),
                    },
                  )
                : null,
              canUpdate() && contest.status === 'RUNNING'
                ? h(
                    DropdownMenuItem,
                    { onClick: () => actions.endContest(contest) },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2 text-amber-600' }, [
                          h(IconPlayerStop, { class: 'h-4 w-4' }),
                          t('contests.actions.endContest'),
                        ]),
                    },
                  )
                : null,
              h(DropdownMenuSeparator, {}),
              canDelete()
                ? h(
                    DropdownMenuItem,
                    { onClick: () => actions.startDeleteContest(contest) },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2 text-destructive' }, [
                          h(IconTrash, { class: 'h-4 w-4' }),
                          t('contests.actions.delete'),
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
