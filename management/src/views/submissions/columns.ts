import type { ColumnDef } from '@tanstack/vue-table'
import type { SubmissionListItem } from '@/api/admin/submissions'
import { Checkbox } from '@/components/ui/checkbox'
import { badge } from '@/components/ui/terminal'
import { getStatusColor, normalizeStatusKey } from '@/shared/submission-status/src'
import { Button } from '@/components/ui/button'
import { IconEye, IconRefresh } from '@tabler/icons-vue'
import { h } from 'vue'
import { formatDistanceToNow } from 'date-fns'

export interface SubmissionActions {
  viewSubmission: (id: string) => void
  openRejudgeDialog: (id: string) => void
}

/**
 * Get translated status label with fallback to original status
 * Handles cases where i18n key might not exist
 */
function getStatusLabel(t: (key: string) => string, status: string): string {
  const normalizedStatus = normalizeStatusKey(status)
  const key = `submissions.statusLabels.${normalizedStatus}`
  const translated = t(key)
  // If translation returns the key itself, it means translation is missing
  // Return the normalized status as fallback
  return translated === key ? normalizedStatus : translated
}

/**
 * Format runtime from milliseconds to human-readable string
 * Handles null/undefined values
 */
export function formatRuntime(ms: number | null | undefined): string {
  if (ms == null) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}

/**
 * Format memory from MB to human-readable string
 * Backend returns memory in MB, NOT KB
 * Handles null/undefined values
 */
export function formatMemory(mb: number | null | undefined): string {
  if (mb == null) return '-'
  if (mb < 1) return `${(mb * 1024).toFixed(0)}KB`
  if (mb < 1024) return `${mb.toFixed(1)}MB`
  return `${(mb / 1024).toFixed(2)}GB`
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
              'font-data text-xs text-[var(--silver-500)] border border-[var(--silver-200)] dark:border-[var(--silver-300)] px-2 py-0.5 rounded-none',
          },
          row.original.language,
        ),
    },
    {
      accessorKey: 'status',
      header: () => t('submissions.status'),
      cell: ({ row }) => {
        const normalizedStatus = normalizeStatusKey(row.original.status)
        return badge({
          color: getStatusColor(row.original.status),
          label: getStatusLabel(t, row.original.status),
          pulse: normalizedStatus === 'PENDING' || normalizedStatus === 'JUDGING',
        })
      },
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
      accessorKey: 'codeLength',
      header: () => t('submissions.codeLength'),
      cell: ({ row }) =>
        h('span', { class: 'font-data text-sm tabular-nums' }, row.original.codeLength ?? '-'),
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
      header: () => t('common.actions.label'),
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
