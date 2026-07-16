import { h } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconCheck,
  IconEye,
  IconFlag,
  IconMessage,
  IconFileText,
  IconTrash,
  IconUser,
} from '@tabler/icons-vue'

import { Checkbox } from '@/components/ui/checkbox'
import { createEntityActionsMenu } from '@/components/table/entityActions'
import { badge, type SemanticColor } from '@/components/ui/terminal'
import type { Comment, CommentType } from '@/api/admin/comments'
import { formatDate } from '@/lib/format/date'

import { renderInlineContent } from '@/utils/comment-renderer'

export interface CommentActions {
  viewCommentDetails: (comment: Comment) => void
  unflagComment: (comment: Comment) => void
  openFlagDialog: (comment: Comment) => void
  confirmDelete: (comment: Comment) => void
}

function renderTypeBadge(type: CommentType) {
  const color: SemanticColor = type === 'forum' ? 'info' : 'success'
  const icon = type === 'forum' ? IconMessage : IconFileText
  return badge({ color, label: type, icon })
}

function renderStatusBadge(comment: Comment, t: (key: string) => string) {
  if (comment.isDeleted)
    return badge({ color: 'error', label: t('comments.status.deleted'), dot: true, pulse: true })
  if (comment.isFlagged)
    return badge({ color: 'warning', label: t('comments.status.flagged'), dot: true, pulse: true })
  return badge({ color: 'success', label: t('comments.status.active'), dot: true, pulse: true })
}

export function createColumns(
  t: (key: string) => string,
  actions: CommentActions,
  canModerate: (comment: Comment) => boolean,
): ColumnDef<Comment>[] {
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
      accessorKey: 'content',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('comments.columns.comment'),
        ),
      cell: ({ row }) => {
        const comment = row.original
        const truncated =
          comment.content.length > 80 ? comment.content.slice(0, 80) + '...' : comment.content

        return h('div', { class: 'flex flex-col gap-1.5 py-1.5' }, [
          h(
            'div',
            {
              class:
                'text-sm text-[var(--foreground)] leading-relaxed flex flex-wrap items-center gap-y-1',
            },
            renderInlineContent(truncated),
          ),
          h('div', { class: 'flex items-center gap-1.5 text-xs text-[var(--silver-400)]' }, [
            comment.type === 'forum'
              ? h(IconMessage, { class: 'h-3 w-3 text-[var(--terminal-cyan)]' })
              : h(IconFileText, { class: 'h-3 w-3 text-[var(--terminal-green)]' }),
            h(
              'span',
              { class: 'hover:text-[var(--foreground)] transition-colors' },
              comment.parentTitle || t('comments.type.unknown'),
            ),
          ]),
        ])
      },
    },
    {
      accessorKey: 'author',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('comments.columns.author'),
        ),
      cell: ({ row }) => {
        const author = row.original.author
        return h('div', { class: 'flex items-center gap-2 py-1' }, [
          h(IconUser, { class: 'h-3.5 w-3.5 text-[var(--silver-400)]' }),
          h(
            'span',
            { class: 'text-sm text-[var(--foreground)]' },
            author?.username || t('comments.status.unknown'),
          ),
        ])
      },
    },
    {
      accessorKey: 'type',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('comments.columns.type'),
        ),
      cell: ({ row }) => {
        const type = row.getValue('type') as CommentType
        return renderTypeBadge(type)
      },
    },
    {
      accessorKey: 'status',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('comments.columns.status'),
        ),
      cell: ({ row }) => {
        const comment = row.original
        return renderStatusBadge(comment, t)
      },
    },
    {
      accessorKey: 'createdAt',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('comments.columns.created'),
        ),
      cell: ({ row }) => {
        const date = row.getValue('createdAt') as string
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
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('common.actions.label'),
        ),
      cell: ({ row }) => {
        const comment = row.original
        const canModerateRow = canModerate(comment)
        return createEntityActionsMenu(
          [
            {
              label: t('comments.actions.viewDetails'),
              onSelect: () => actions.viewCommentDetails(comment),
              icon: IconEye,
              iconClass: 'h-4 w-4 text-[var(--terminal-cyan)]',
            },
            { kind: 'separator' },
            comment.isFlagged
              ? {
                  label: t('comments.actions.unflag'),
                  onSelect: () => actions.unflagComment(comment),
                  icon: IconCheck,
                  iconClass: 'h-4 w-4 text-[var(--terminal-green)]',
                  labelClass: 'text-[var(--terminal-green)]',
                  hidden: !canModerateRow,
                }
              : {
                  label: t('comments.actions.flag'),
                  onSelect: () => actions.openFlagDialog(comment),
                  icon: IconFlag,
                  iconClass: 'h-4 w-4 text-[var(--terminal-amber)]',
                  labelClass: 'text-[var(--terminal-amber)]',
                  hidden: !canModerateRow,
                },
            { kind: 'separator', hidden: !canModerateRow },
            {
              label: t('comments.actions.delete'),
              onSelect: () => actions.confirmDelete(comment),
              icon: IconTrash,
              iconClass: 'h-4 w-4 text-[var(--terminal-red)]',
              labelClass: 'text-[var(--terminal-red)]',
              hidden: !canModerateRow,
            },
            {
              label: t('comments.actions.noPermission'),
              onSelect: () => {},
              disabled: true,
              itemClass: 'font-data text-xs text-[var(--silver-400)]',
              hidden: canModerateRow,
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
