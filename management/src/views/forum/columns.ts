import { h } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconCheck,
  IconDotsVertical,
  IconFlag,
  IconTrash,
  IconUser,
  IconEye,
  IconThumbUp,
  IconPin,
  IconLock,
} from '@tabler/icons-vue'

import { Checkbox } from '@/components/ui/checkbox'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { badge } from '@/components/ui/terminal'
import type { ForumPost } from '@/api/admin/forum'
import { formatDate } from '@/lib/format/date'

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
  if (post.isFlagged) return badge({ color: 'error', label: t('forum.status.flagged'), pulse: true })
  return badge({ color: 'success', label: t('forum.status.active'), dot: true, pulse: true })
}

function renderPinLockBadge(post: ForumPost, t: (key: string) => string) {
  const badges: ReturnType<typeof h>[] = []
  if (post.isPinned) badges.push(badge({ color: 'info', label: t('forum.actions.pin'), size: 'xs', icon: IconPin }))
  if (post.isLocked)
    badges.push(badge({ color: 'warning', label: t('forum.actions.lock'), size: 'xs', icon: IconLock }))
  return badges.length > 0 ? h('div', { class: 'flex items-center gap-1.5' }, badges) : null
}

export function createColumns(
  t: (key: string) => string,
  actions: ForumPostActions,
  canModerate: () => boolean,
): ColumnDef<ForumPost>[] {
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
          'aria-label': t('table.selectAll'),
          class:
            'border-[var(--silver-300)] data-[state=checked]:bg-[var(--accent-electric)] data-[state=checked]:border-[var(--accent-electric)]',
        }),
      cell: ({ row }) =>
        h(Checkbox, {
          modelValue: row.getIsSelected(),
          'onUpdate:modelValue': (value: boolean | 'indeterminate') => row.toggleSelected(!!value),
          'aria-label': t('common.select'),
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
          t('forum.columns.title'),
        ),
      cell: ({ row }) => {
        const post = row.original
        return h('div', { class: 'flex flex-col gap-1.5 py-1' }, [
          h('div', { class: 'flex items-center gap-2' }, [
            h('span', { class: 'font-medium text-sm text-[var(--foreground)]' }, post.title),
            renderPinLockBadge(post, t),
          ]),
          h('div', { class: 'flex items-center gap-1.5 text-xs text-[var(--silver-400)]' }, [
            h(IconUser, { class: 'h-3 w-3' }),
            h('span', { class: 'font-data' }, post.author?.username || t('forum.overview.unknown')),
            h('span', { class: 'text-[var(--silver-500)]' }, '›'),
            h(
              'span',
              { class: 'font-data text-[var(--silver-500)]' },
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
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('forum.columns.stats'),
        ),
      cell: ({ row }) => {
        const post = row.original
        return h('div', { class: 'flex items-center gap-4 text-[var(--silver-400)] text-xs' }, [
          h('div', { class: 'flex items-center gap-1.5' }, [
            h(IconEye, { class: 'h-3.5 w-3.5 text-[var(--terminal-cyan)]' }),
            h('span', { class: 'font-data tabular-nums' }, post.viewCount || 0),
          ]),
          h('div', { class: 'flex items-center gap-1.5' }, [
            h(IconThumbUp, { class: 'h-3.5 w-3.5 text-[var(--terminal-green)]' }),
            h('span', { class: 'font-data tabular-nums' }, post.upvotes || 0),
          ]),
        ])
      },
    },
    {
      accessorKey: 'isFlagged',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
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
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('forum.columns.created'),
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
          t('forum.columns.actions'),
        ),
      cell: ({ row }) => {
        const post = row.original
        return createActionsDropdown(t, post, actions, canModerate)
      },
    },
  ]
}

function createActionsDropdown(
  t: (key: string) => string,
  post: ForumPost,
  actions: ForumPostActions,
  canModerate: () => boolean,
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
                {
                  variant: 'ghost',
                  size: 'icon',
                  class:
                    'h-8 w-8 p-0 hover:bg-[var(--silver-100)] dark:hover:bg-[var(--silver-800)]',
                },
                {
                  default: () => [
                    h('span', { class: 'sr-only' }, t('common.open')),
                    h(IconDotsVertical, { class: 'h-4 w-4 text-[var(--silver-400)]' }),
                  ],
                },
              ),
          },
        ),
        h(
          DropdownMenuContent,
          {
            align: 'end',
            class: 'border-[var(--silver-200)] dark:border-[var(--silver-700)]',
          },
          {
            default: () => [
              h(
                DropdownMenuItem,
                {
                  onClick: () => actions.viewPostDetails(post),
                  class: 'font-data text-xs cursor-pointer',
                },
                {
                  default: () =>
                    h('div', { class: 'flex items-center gap-2' }, [
                      h(IconEye, { class: 'h-4 w-4 text-[var(--terminal-cyan)]' }),
                      h('span', t('forum.actions.viewDetails')),
                    ]),
                },
              ),
              canModerate()
                ? h(DropdownMenuSeparator, {
                    class: 'bg-[var(--silver-200)] dark:bg-[var(--silver-700)]',
                  })
                : null,
              canModerate()
                ? h(
                    DropdownMenuItem,
                    {
                      onClick: () => actions.togglePin(post),
                      class: 'font-data text-xs cursor-pointer',
                    },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2' }, [
                          h(IconPin, { class: 'h-4 w-4 text-[var(--terminal-cyan)]' }),
                          h(
                            'span',
                            post.isPinned ? t('forum.actions.unpin') : t('forum.actions.pin'),
                          ),
                        ]),
                    },
                  )
                : null,
              canModerate()
                ? h(
                    DropdownMenuItem,
                    {
                      onClick: () => actions.toggleLock(post),
                      class: 'font-data text-xs cursor-pointer',
                    },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2' }, [
                          h(IconLock, { class: 'h-4 w-4 text-[var(--terminal-amber)]' }),
                          h(
                            'span',
                            post.isLocked ? t('forum.actions.unlock') : t('forum.actions.lock'),
                          ),
                        ]),
                    },
                  )
                : null,
              canModerate()
                ? h(DropdownMenuSeparator, {
                    class: 'bg-[var(--silver-200)] dark:bg-[var(--silver-700)]',
                  })
                : null,
              canModerate()
                ? post.isFlagged
                  ? h(
                      DropdownMenuItem,
                      {
                        onClick: () => actions.unflagPost(post.id),
                        class: 'font-data text-xs cursor-pointer',
                      },
                      {
                        default: () =>
                          h('div', { class: 'flex items-center gap-2' }, [
                            h(IconCheck, { class: 'h-4 w-4 text-[var(--terminal-green)]' }),
                            h(
                              'span',
                              { class: 'text-[var(--terminal-green)]' },
                              t('forum.actions.unflag'),
                            ),
                          ]),
                      },
                    )
                  : h(
                      DropdownMenuItem,
                      {
                        onClick: () => actions.openFlagDialog(post),
                        class: 'font-data text-xs cursor-pointer',
                      },
                      {
                        default: () =>
                          h('div', { class: 'flex items-center gap-2' }, [
                            h(IconFlag, { class: 'h-4 w-4 text-[var(--terminal-amber)]' }),
                            h(
                              'span',
                              { class: 'text-[var(--terminal-amber)]' },
                              t('forum.actions.flag'),
                            ),
                          ]),
                      },
                    )
                : null,
              canModerate()
                ? h(DropdownMenuSeparator, {
                    class: 'bg-[var(--silver-200)] dark:bg-[var(--silver-700)]',
                  })
                : null,
              canModerate()
                ? h(
                    DropdownMenuItem,
                    {
                      onClick: () => actions.confirmDelete(post),
                      class: 'font-data text-xs cursor-pointer',
                    },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2' }, [
                          h(IconTrash, { class: 'h-4 w-4 text-[var(--terminal-red)]' }),
                          h('span', { class: 'text-[var(--terminal-red)]' }, t('common.delete')),
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
