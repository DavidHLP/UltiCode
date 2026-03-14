import type { ColumnDef } from '@tanstack/vue-table'
import type { SubmissionListItem } from '@/api/admin/submissions'
import { Checkbox } from '@/components/ui/checkbox'
import { TerminalBadge } from '@/components/ui/terminal'
import { Button } from '@/components/ui/button'
import { IconEye, IconRefresh } from '@tabler/icons-vue'
import { h } from 'vue'
import { formatDistanceToNow } from 'date-fns'

export interface SubmissionActions {
  viewSubmission: (id: string) => void
  openRejudgeDialog: (id: string) => void
}

function getStatusBadgeVariant(
  status: string,
): 'success' | 'warning' | 'error' | 'info' | 'default' {
  if (status === 'ACCEPTED') return 'success'
  if (status === 'PENDING' || status === 'JUDGING') return 'warning'
  if (
    status === 'WRONG_ANSWER' ||
    status === 'TIME_LIMIT_EXCEEDED' ||
    status === 'MEMORY_LIMIT_EXCEEDED' ||
    status === 'RUNTIME_ERROR' ||
    status === 'COMPILE_ERROR'
  )
    return 'error'
  return 'default'
}

function shouldPulse(status: string): boolean {
  return status === 'PENDING' || status === 'JUDGING'
}

function formatRuntime(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}

function formatMemory(kb: number): string {
  if (kb < 1024) return `${kb}KB`
  return `${(kb / 1024).toFixed(1)}MB`
}

export function createColumns(
  t: (key: string) => string,
  actions: SubmissionActions,
): ColumnDef<SubmissionListItem>[] {
  return [
    {
      id: 'select',
      header: ({ table }) =>
        h(Checkbox, {
          checked: table.getIsAllPageRowsSelected(),
          indeterminate: table.getIsSomePageRowsSelected(),
          'onUpdate:checked': (value: boolean) => table.toggleAllPageRowsSelected(!!value),
          class: 'translate-y-0.5',
        }),
      cell: ({ row }) =>
        h(Checkbox, {
          checked: row.getIsSelected(),
          'onUpdate:checked': (value: boolean) => row.toggleSelected(!!value),
          class: 'translate-y-0.5',
        }),
      enableSorting: false,
      enableHiding: false,
    },
    {
      accessorKey: 'id',
      header: () => t('submissions.id'),
      cell: ({ row }) =>
        h(
          'span',
          { class: 'font-data text-xs text-[var(--terminal-cyan)]' },
          row.original.id.slice(0, 8),
        ),
      enableHiding: false,
    },
    {
      accessorKey: 'problemTitle',
      header: () => t('submissions.problem'),
      cell: ({ row }) =>
        h('div', {}, [
          h('div', { class: 'font-medium text-sm' }, row.original.problemTitle),
          h(
            'div',
            { class: 'text-xs text-[var(--silver-400)] font-data' },
            row.original.problemSlug,
          ),
        ]),
    },
    {
      accessorKey: 'username',
      header: () => t('submissions.user'),
      cell: ({ row }) => h('span', { class: 'text-sm' }, row.original.username),
    },
    {
      accessorKey: 'language',
      header: () => t('submissions.language'),
      cell: ({ row }) =>
        h(
          'span',
          {
            class:
              'font-data text-xs text-[var(--silver-500)] border border-[var(--silver-200)] dark:border-[var(--silver-300)] px-2 py-0.5 rounded-sm',
          },
          row.original.language,
        ),
    },
    {
      accessorKey: 'status',
      header: () => t('submissions.status'),
      cell: ({ row }) =>
        h(TerminalBadge, {
          variant: getStatusBadgeVariant(row.original.status),
          pulse: shouldPulse(row.original.status),
          label: row.original.status,
        }),
    },
    {
      accessorKey: 'runtime',
      header: () => t('submissions.runtime'),
      cell: ({ row }) =>
        h('span', { class: 'font-data text-sm tabular-nums' }, formatRuntime(row.original.runtime)),
    },
    {
      accessorKey: 'memory',
      header: () => t('submissions.memory'),
      cell: ({ row }) =>
        h('span', { class: 'font-data text-sm tabular-nums' }, formatMemory(row.original.memory)),
    },
    {
      accessorKey: 'createdAt',
      header: () => t('submissions.submittedAt'),
      cell: ({ row }) =>
        h(
          'span',
          { class: 'text-sm text-[var(--silver-500)]' },
          formatDistanceToNow(new Date(row.original.createdAt), { addSuffix: true }),
        ),
    },
    {
      id: 'actions',
      header: () => t('common.actions'),
      cell: ({ row }) =>
        h('div', { class: 'flex justify-end gap-1' }, [
          h(
            Button,
            {
              variant: 'terminal',
              size: 'sm',
              class:
                'h-8 w-8 p-0 border-[var(--silver-300)] hover:border-[var(--terminal-cyan)] hover:text-[var(--terminal-cyan)]',
              onClick: () => actions.viewSubmission(row.original.id),
            },
            () => h(IconEye, { class: 'h-3.5 w-3.5' }),
          ),
          h(
            Button,
            {
              variant: 'terminal',
              size: 'sm',
              class:
                'h-8 w-8 p-0 border-[var(--silver-300)] hover:border-[var(--terminal-amber)] hover:text-[var(--terminal-amber)]',
              onClick: () => actions.openRejudgeDialog(row.original.id),
            },
            () => h(IconRefresh, { class: 'h-3.5 w-3.5' }),
          ),
        ]),
      enableSorting: false,
      enableHiding: false,
    },
  ]
}
