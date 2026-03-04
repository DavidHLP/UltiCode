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

// Terminal-style status badge renderer
function renderStatusBadge(post: ForumPost) {
  if (post.is_deleted) {
    return h(
      'span',
      {
        class: [
          'font-data text-[11px] font-medium uppercase tracking-[0.05em]',
          'px-2 py-0.5 border rounded-sm',
          'bg-[oklch(0.6_0.2_25/0.15)]',
          'border-[oklch(0.6_0.2_25/0.4)]',
          'text-[var(--terminal-red)]',
        ].join(' '),
      },
      'DELETED',
    )
  }

  if (post.is_flagged) {
    return h(
      'div',
      { class: 'flex items-center gap-2' },
      h(
        'span',
        {
          class: [
            'font-data text-[11px] font-medium uppercase tracking-[0.05em]',
            'px-2 py-0.5 border rounded-sm',
            'bg-[oklch(0.6_0.2_25/0.15)]',
            'border-[oklch(0.6_0.2_25/0.4)]',
            'text-[var(--terminal-red)]',
            'animate-pulse-subtle',
          ].join(' '),
        },
        'FLAGGED',
      ),
    )
  }

  return h('div', { class: 'flex items-center gap-2' }, [
    h('span', {
      class: 'w-1.5 h-1.5 rounded-full bg-[var(--terminal-green)] animate-pulse-subtle',
    }),
    h(
      'span',
      {
        class: [
          'font-data text-[11px] font-medium uppercase tracking-[0.05em]',
          'px-2 py-0.5 border rounded-sm',
          'bg-[oklch(0.7_0.15_145/0.15)]',
          'border-[oklch(0.7_0.15_145/0.4)]',
          'text-[var(--terminal-green)]',
        ].join(' '),
      },
      'ACTIVE',
    ),
  ])
}

// Terminal-style pin/lock badge
function renderPinLockBadge(post: ForumPost, t: (key: string) => string) {
  const badges: ReturnType<typeof h>[] = []

  if (post.is_pinned) {
    badges.push(
      h(
        'span',
        {
          class: [
            'font-data text-[10px] uppercase tracking-wider',
            'px-1.5 py-0.5 border rounded-sm',
            'bg-[oklch(0.7_0.12_195/0.15)]',
            'border-[oklch(0.7_0.12_195/0.4)]',
            'text-[var(--terminal-cyan)]',
            'flex items-center gap-1',
          ].join(' '),
          title: t('forum.status.pinned'),
        },
        [h(IconPin, { class: 'h-3 w-3' }), 'PIN'],
      ),
    )
  }

  if (post.is_locked) {
    badges.push(
      h(
        'span',
        {
          class: [
            'font-data text-[10px] uppercase tracking-wider',
            'px-1.5 py-0.5 border rounded-sm',
            'bg-[oklch(0.75_0.15_85/0.15)]',
            'border-[oklch(0.75_0.15_85/0.4)]',
            'text-[var(--terminal-amber)]',
            'flex items-center gap-1',
          ].join(' '),
          title: t('forum.status.locked'),
        },
        [h(IconLock, { class: 'h-3 w-3' }), 'LOCK'],
      ),
    )
  }

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
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
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
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('forum.columns.stats'),
        ),
      cell: ({ row }) => {
        const post = row.original
        return h('div', { class: 'flex items-center gap-4 text-[var(--silver-400)] text-xs' }, [
          h('div', { class: 'flex items-center gap-1.5' }, [
            h(IconEye, { class: 'h-3.5 w-3.5 text-[var(--terminal-cyan)]' }),
            h('span', { class: 'font-data tabular-nums' }, post.view_count || 0),
          ]),
          h('div', { class: 'flex items-center gap-1.5' }, [
            h(IconThumbUp, { class: 'h-3.5 w-3.5 text-[var(--terminal-green)]' }),
            h('span', { class: 'font-data tabular-nums' }, post.upvotes || 0),
          ]),
        ])
      },
    },
    {
      accessorKey: 'is_flagged',
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('forum.columns.status'),
        ),
      cell: ({ row }) => {
        const post = row.original
        return renderStatusBadge(post)
      },
    },
    {
      accessorKey: 'created_at',
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('forum.columns.created'),
        ),
      cell: ({ row }) => {
        const date = row.getValue('created_at') as string
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
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
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
                            post.is_pinned ? t('forum.actions.unpin') : t('forum.actions.pin'),
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
                            post.is_locked ? t('forum.actions.unlock') : t('forum.actions.lock'),
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
                ? post.is_flagged
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
