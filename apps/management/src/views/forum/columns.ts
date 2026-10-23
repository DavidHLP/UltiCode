import { h } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  Check as IconCheck,
  Flag as IconFlag,
  Trash as IconTrash,
  User as IconUser,
  Eye as IconEye,
  ThumbsUp as IconThumbUp,
  Pin as IconPin,
  Lock as IconLock,
} from '@lucide/vue'

import { createSelectionColumn } from '@/components/table/selectionColumn'
import { createEntityActionsMenu } from '@/components/table/entityActions'
import { badge } from '@ulticode/badge-config'
import type { ForumPost } from '@/api/admin/forum'
import { formatDate } from '@/lib/format/date'
import type { ForumPermissionMap } from '@/composables/domainPermissionMaps'

export interface ForumPostActions {
  viewPostDetails: (post: ForumPost) => void
  togglePin: (post: ForumPost) => void
  toggleLock: (post: ForumPost) => void
  openFlagDialog: (post: ForumPost) => void
  unflagPost: (id: string) => void
  confirmDelete: (post: ForumPost) => void
}

function renderStatusBadge(post: ForumPost, t: (key: string) => string) {
  if (post.isDeleted) return badge({ color: 'error', label: t('forum.status.deleted') })
  if (post.isFlagged)
    return badge({ color: 'error', label: t('forum.status.flagged'), pulse: true })
  return badge({ color: 'success', label: t('forum.status.active'), dot: true, pulse: true })
}

function renderPinLockBadge(post: ForumPost, t: (key: string) => string) {
  const badges: ReturnType<typeof h>[] = []
  if (post.isPinned)
    badges.push(badge({ color: 'info', label: t('forum.actions.pin'), size: 'xs', icon: IconPin }))
  if (post.isLocked)
    badges.push(
      badge({ color: 'warning', label: t('forum.actions.lock'), size: 'xs', icon: IconLock }),
    )
  return badges.length > 0 ? h('div', { class: 'flex items-center gap-1.5' }, badges) : null
}

export function createColumns(
  t: (key: string) => string,
  actions: ForumPostActions,
  can: ForumPermissionMap,
): ColumnDef<ForumPost>[] {
  return [
    ...createSelectionColumn<ForumPost>(t, {
      checkboxClass:
        'border-[var(--border)] data-[state=checked]:bg-[var(--primary)] data-[state=checked]:border-[var(--primary)]',
    }),
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
    {
      accessorKey: 'title',
      header: () =>
        h(
          'span',
          { class: 'font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]' },
          t('forum.columns.title'),
        ),
      cell: ({ row }) => {
        const post = row.original
        return h('div', { class: 'flex flex-col gap-1.5 py-1' }, [
          h('div', { class: 'flex items-center gap-2' }, [
            h('span', { class: 'font-medium text-sm text-[var(--foreground)]' }, post.title),
            renderPinLockBadge(post, t),
          ]),
          h('div', { class: 'flex items-center gap-1.5 text-xs text-[var(--muted-foreground)]' }, [
            h(IconUser, { class: 'h-3 w-3' }),
            h('span', { class: 'font-mono' }, post.author?.username || t('forum.overview.unknown')),
            h('span', { class: 'text-[var(--muted-foreground)]' }, '›'),
            h(
              'span',
              { class: 'font-mono text-[var(--muted-foreground)]' },
              post.community?.name || t('forum.drawer.unknownCommunity'),
            ),
          ]),
        ])
      },
    },
    {
      accessorKey: 'stats',
      header: () =>
        h(
          'span',
          { class: 'font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]' },
          t('forum.columns.stats'),
        ),
      cell: ({ row }) => {
        const post = row.original
        return h(
          'div',
          { class: 'flex items-center gap-4 text-[var(--muted-foreground)] text-xs' },
          [
            h('div', { class: 'flex items-center gap-1.5' }, [
              h(IconEye, { class: 'h-3.5 w-3.5 text-foreground' }),
              h('span', { class: 'font-mono tabular-nums' }, post.viewCount || 0),
            ]),
            h('div', { class: 'flex items-center gap-1.5' }, [
              h(IconThumbUp, { class: 'h-3.5 w-3.5 text-foreground' }),
              h('span', { class: 'font-mono tabular-nums' }, post.upvotes || 0),
            ]),
          ],
        )
      },
    },
    {
      accessorKey: 'isFlagged',
      header: () =>
        h(
          'span',
          { class: 'font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]' },
          t('forum.columns.status'),
        ),
      cell: ({ row }) => {
        const post = row.original
        return renderStatusBadge(post, t)
      },
    },
    {
      accessorKey: 'createdAt',
      header: () =>
        h(
          'span',
          { class: 'font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]' },
          t('forum.columns.created'),
        ),
      cell: ({ row }) => {
        const date = row.getValue('createdAt') as string
        return h(
          'span',
          { class: 'font-mono text-xs text-[var(--muted-foreground)] tabular-nums' },
          formatDate(date),
        )
      },
    },
    {
      id: 'actions',
      header: () =>
        h(
          'span',
          { class: 'font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]' },
          t('forum.columns.actions'),
        ),
      cell: ({ row }) => {
        const post = row.original
        const canModerateRow = can.moderatePost.value
        return createEntityActionsMenu(
          [
            {
              label: t('forum.actions.viewDetails'),
              onSelect: () => actions.viewPostDetails(post),
              icon: IconEye,
              iconClass: 'h-4 w-4 text-foreground',
            },
            { kind: 'separator', hidden: !canModerateRow },
            {
              label: post.isPinned ? t('forum.actions.unpin') : t('forum.actions.pin'),
              onSelect: () => actions.togglePin(post),
              icon: IconPin,
              iconClass: 'h-4 w-4 text-foreground',
              hidden: !canModerateRow,
            },
            {
              label: post.isLocked ? t('forum.actions.unlock') : t('forum.actions.lock'),
              onSelect: () => actions.toggleLock(post),
              icon: IconLock,
              iconClass: 'h-4 w-4 text-foreground',
              hidden: !canModerateRow,
            },
            { kind: 'separator', hidden: !canModerateRow },
            post.isFlagged
              ? {
                  label: t('forum.actions.unflag'),
                  onSelect: () => actions.unflagPost(post.id),
                  icon: IconCheck,
                  iconClass: 'h-4 w-4 text-foreground',
                  labelClass: 'text-[var(--foreground)]',
                  hidden: !canModerateRow,
                }
              : {
                  label: t('forum.actions.flag'),
                  onSelect: () => actions.openFlagDialog(post),
                  icon: IconFlag,
                  iconClass: 'h-4 w-4 text-foreground',
                  labelClass: 'text-[var(--foreground)]',
                  hidden: !canModerateRow,
                },
            { kind: 'separator', hidden: !canModerateRow },
            {
              label: t('common.delete'),
              onSelect: () => actions.confirmDelete(post),
              icon: IconTrash,
              iconClass: 'h-4 w-4 text-foreground',
              labelClass: 'text-[var(--foreground)]',
              hidden: !canModerateRow,
            },
          ],
          {
            triggerClass: 'h-8 w-8 p-0 hover:bg-[var(--accent)] dark:hover:bg-[var(--foreground)]',
            triggerIconClass: 'h-4 w-4 text-[var(--muted-foreground)]',
            contentClass: 'border-[var(--border)] dark:border-[var(--foreground)]',
            itemClass: 'font-mono text-xs cursor-pointer',
            separatorClass: 'bg-[var(--border)] dark:bg-[var(--foreground)]',
            srLabel: t('common.open'),
          },
        )
      },
    },
  ]
}
