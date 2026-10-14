import { h } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  Check as IconCheck,
  X as IconX,
  Clock as IconClock,
  EllipsisVertical as IconDotsVertical,
  Eye as IconEye,
  Scale as IconScale,
  User as IconUser,
} from '@lucide/vue'

import { createSelectionColumn } from '@/components/table/selectionColumn'
import { Button } from '@ulticode/design-system/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@ulticode/design-system/ui/dropdown-menu'
import { badge } from '@ulticode/badge-config'
import type { SemanticColor } from '@ulticode/badge-config'
import { type Appeal, AppealStatus } from '@/api/admin/moderation'
import { formatDate } from '@/lib/format/date'

export interface AppealActions {
  viewAppeal: (appeal: Appeal) => void
  approveAppeal: (appeal: Appeal) => void
  rejectAppeal: (appeal: Appeal) => void
}

// ========== Status Maps ==========
const APPEAL_STATUS_ICON_MAP: Record<AppealStatus, typeof IconScale> = {
  PENDING: IconClock,
  UNDER_REVIEW: IconEye,
  APPROVED: IconCheck,
  REJECTED: IconX,
}

const APPEAL_STATUS_COLOR_MAP: Record<AppealStatus, SemanticColor> = {
  PENDING: 'warning',
  UNDER_REVIEW: 'info',
  APPROVED: 'success',
  REJECTED: 'error',
}

function renderStatusBadge(status: AppealStatus, t: (key: string, fallback?: string) => string) {
  return badge({
    color: APPEAL_STATUS_COLOR_MAP[status],
    label: t(`moderation.appealStatus.${status}`),
    icon: APPEAL_STATUS_ICON_MAP[status],
  })
}

function truncateText(text: string, maxLength: number): string {
  if (!text || text.length <= maxLength) return text
  return text.slice(0, maxLength) + '...'
}

/**
 * Creates column definitions for the appeals DataTable.
 */
export function createAppealsColumns(
  t: (key: string, fallback?: string) => string,
  actions: AppealActions,
): ColumnDef<Appeal>[] {
  return [
    // Selection column
    ...createSelectionColumn<Appeal>(t, {
      checkboxClass:
        'border-[var(--border)] data-[state=checked]:bg-[var(--primary)] data-[state=checked]:border-[var(--primary)]',
    }),
    // Row number
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
    // Appellant
    {
      accessorKey: 'appellant',
      size: 120,
      minSize: 100,
      header: () =>
        h(
          'span',
          { class: 'font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]' },
          t('moderation.appeals.appellant'),
        ),
      cell: ({ row }) => {
        const appellantName = row.original.appellantName
        const appellantUsername = row.original.appellantUsername
        return h('div', { class: 'flex items-center gap-2' }, [
          h(IconUser, { class: 'h-3.5 w-3.5 text-[var(--muted-foreground)]' }),
          h(
            'span',
            { class: 'text-sm truncate' },
            appellantName || appellantUsername || t('moderation.unknownReporter'),
          ),
        ])
      },
    },
    // Queue ID
    {
      accessorKey: 'queueId',
      minSize: 150,
      header: () =>
        h(
          'span',
          { class: 'font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]' },
          'Queue ID',
        ),
      cell: ({ row }) => {
        const queueId = row.original.queueId
        return h(
          'span',
          {
            class: 'font-mono text-xs text-muted-foreground block truncate',
            title: queueId,
          },
          truncateText(queueId, 24),
        )
      },
    },
    // Reason
    {
      accessorKey: 'reason',
      minSize: 200,
      header: () =>
        h(
          'span',
          { class: 'font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]' },
          t('moderation.appeals.reason'),
        ),
      cell: ({ row }) => {
        const reason = row.original.reason
        return h(
          'span',
          { class: 'text-sm text-muted-foreground block truncate', title: reason },
          truncateText(reason, 40),
        )
      },
    },
    // Status
    {
      accessorKey: 'status',
      size: 140,
      header: () =>
        h(
          'span',
          { class: 'font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]' },
          t('moderation.columns.status'),
        ),
      cell: ({ row }) => {
        const status = row.original.status as AppealStatus
        return renderStatusBadge(status, t)
      },
    },
    // Reviewed By
    {
      accessorKey: 'reviewer',
      size: 120,
      header: () =>
        h(
          'span',
          { class: 'font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]' },
          t('moderation.appeals.reviewedBy'),
        ),
      cell: ({ row }) => {
        const reviewerName = row.original.reviewedByName
        if (!reviewerName) {
          return h(
            'span',
            { class: 'font-mono text-xs text-[var(--muted-foreground)] italic' },
            '-',
          )
        }
        return h('span', { class: 'text-sm truncate block' }, reviewerName)
      },
    },
    // Created at
    {
      accessorKey: 'createdAt',
      size: 100,
      header: () =>
        h(
          'span',
          { class: 'font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]' },
          t('moderation.columns.createdAt'),
        ),
      cell: ({ row }) => {
        const date = row.original.createdAt
        return h(
          'span',
          { class: 'font-mono text-xs text-[var(--muted-foreground)] tabular-nums' },
          date ? formatDate(date) : '—',
        )
      },
    },
    // Actions
    {
      id: 'actions',
      header: () =>
        h(
          'span',
          { class: 'font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]' },
          t('moderation.columns.actions'),
        ),
      cell: ({ row }) => {
        const appeal = row.original
        const isPending =
          appeal.status === AppealStatus.PENDING || appeal.status === AppealStatus.UNDER_REVIEW

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
                          h(IconDotsVertical, { class: 'h-4 w-4 text-[var(--muted-foreground)]' }),
                        ],
                      },
                    ),
                },
              ),
              h(
                DropdownMenuContent,
                { align: 'end', class: 'border-[var(--border)]' },
                {
                  default: () =>
                    [
                      h(
                        DropdownMenuItem,
                        {
                          onClick: () => actions.viewAppeal(appeal),
                          class: 'font-mono text-xs cursor-pointer',
                        },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2' }, [
                              h(IconEye, { class: 'h-4 w-4 text-foreground' }),
                              h('span', t('moderation.appeals.reviewAppeal')),
                            ]),
                        },
                      ),
                      isPending
                        ? h(
                            DropdownMenuItem,
                            {
                              onClick: () => actions.approveAppeal(appeal),
                              class: 'font-mono text-xs cursor-pointer',
                            },
                            {
                              default: () =>
                                h('div', { class: 'flex items-center gap-2' }, [
                                  h(IconCheck, { class: 'h-4 w-4 text-foreground' }),
                                  h(
                                    'span',
                                    { class: 'text-foreground' },
                                    t('moderation.appeals.approveAppeal'),
                                  ),
                                ]),
                            },
                          )
                        : null,
                      isPending
                        ? h(
                            DropdownMenuItem,
                            {
                              onClick: () => actions.rejectAppeal(appeal),
                              class: 'font-mono text-xs cursor-pointer',
                            },
                            {
                              default: () =>
                                h('div', { class: 'flex items-center gap-2' }, [
                                  h(IconX, { class: 'h-4 w-4 text-foreground' }),
                                  h(
                                    'span',
                                    { class: 'text-foreground' },
                                    t('moderation.appeals.rejectAppeal'),
                                  ),
                                ]),
                            },
                          )
                        : null,
                    ].filter(Boolean),
                },
              ),
            ],
          },
        )
      },
    },
  ]
}
