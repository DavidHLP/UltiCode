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
        }),
      cell: ({ row }) =>
        h(Checkbox, {
          modelValue: row.getIsSelected(),
          'onUpdate:modelValue': (value: boolean | 'indeterminate') => row.toggleSelected(!!value),
          'aria-label': t('common.select'),
        }),
      enableSorting: false,
      enableHiding: false,
    },
    {
      accessorKey: 'title',
      header: () => t('forum.columns.title'),
      cell: ({ row }) => {
        const post = row.original
        return h('div', { class: 'flex flex-col gap-1' }, [
          h('div', { class: 'flex items-center gap-2' }, [
            h('span', { class: 'font-medium text-sm' }, post.title),
            post.is_pinned &&
              h(IconPin, {
                class: 'h-3 w-3 text-blue-500',
                'aria-label': t('forum.status.pinned'),
              }),
            post.is_locked &&
              h(IconLock, {
                class: 'h-3 w-3 text-amber-500',
                'aria-label': t('forum.status.locked'),
              }),
          ]),
          h('div', { class: 'flex items-center gap-1 text-xs text-muted-foreground' }, [
            h(IconUser, { class: 'h-3 w-3' }),
            h('span', {}, post.author?.username || t('forum.overview.unknown')),
            h('span', { class: 'mx-1' }, '•'),
            h('span', {}, post.community?.name || t('forum.drawer.unknownCommunity')),
          ]),
        ])
      },
    },
    {
      accessorKey: 'stats',
      header: () => t('forum.columns.stats'),
      cell: ({ row }) => {
        const post = row.original
        return h('div', { class: 'flex items-center gap-3 text-muted-foreground text-xs' }, [
          h('div', { class: 'flex items-center gap-1' }, [
            h(IconEye, { class: 'h-3 w-3' }),
            h('span', {}, post.view_count || 0),
          ]),
          h('div', { class: 'flex items-center gap-1' }, [
            h(IconThumbUp, { class: 'h-3 w-3' }),
            h('span', {}, post.upvotes || 0),
          ]),
        ])
      },
    },
    {
      accessorKey: 'is_flagged',
      header: () => t('forum.columns.status'),
      cell: ({ row }) => {
        const isFlagged = row.getValue('is_flagged') as boolean
        const isDeleted = row.original.is_deleted

        if (isDeleted) {
          return h(Badge, { variant: 'destructive' }, () => [
            h(IconTrash, { class: 'mr-1 h-3 w-3' }),
            t('forum.status.deleted'),
          ])
        }

        if (isFlagged) {
          return h(Badge, { variant: 'destructive' }, () => [
            h(IconFlag, { class: 'mr-1 h-3 w-3' }),
            t('forum.status.flagged'),
          ])
        }

        return h(Badge, { variant: 'secondary' }, () => t('forum.status.active'))
      },
    },
    {
      accessorKey: 'created_at',
      header: () => t('forum.columns.created'),
      cell: ({ row }) => {
        const date = row.getValue('created_at') as string
        return h('span', { class: 'text-muted-foreground text-sm' }, formatDate(date))
      },
    },
    {
      id: 'actions',
      header: () => t('forum.columns.actions'),
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
                { variant: 'ghost', size: 'icon', class: 'h-8 w-8 p-0' },
                {
                  default: () => [
                    h('span', { class: 'sr-only' }, t('common.open')),
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
                { onClick: () => actions.viewPostDetails(post) },
                {
                  default: () =>
                    h('div', { class: 'flex items-center gap-2' }, [
                      h(IconEye, { class: 'h-4 w-4' }),
                      t('forum.actions.viewDetails'),
                    ]),
                },
              ),
              canModerate() ? h(DropdownMenuSeparator, {}) : null,
              canModerate()
                ? h(
                    DropdownMenuItem,
                    { onClick: () => actions.togglePin(post) },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2' }, [
                          h(IconPin, { class: 'h-4 w-4' }),
                          post.is_pinned ? t('forum.actions.unpin') : t('forum.actions.pin'),
                        ]),
                    },
                  )
                : null,
              canModerate()
                ? h(
                    DropdownMenuItem,
                    { onClick: () => actions.toggleLock(post) },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2' }, [
                          h(IconLock, { class: 'h-4 w-4' }),
                          post.is_locked ? t('forum.actions.unlock') : t('forum.actions.lock'),
                        ]),
                    },
                  )
                : null,
              canModerate() ? h(DropdownMenuSeparator, {}) : null,
              canModerate()
                ? post.is_flagged
                  ? h(
                      DropdownMenuItem,
                      { onClick: () => actions.unflagPost(post.id) },
                      {
                        default: () =>
                          h('div', { class: 'flex items-center gap-2 text-emerald-600' }, [
                            h(IconCheck, { class: 'h-4 w-4' }),
                            t('forum.actions.unflag'),
                          ]),
                      },
                    )
                  : h(
                      DropdownMenuItem,
                      { onClick: () => actions.openFlagDialog(post) },
                      {
                        default: () =>
                          h('div', { class: 'flex items-center gap-2 text-amber-600' }, [
                            h(IconFlag, { class: 'h-4 w-4' }),
                            t('forum.actions.flag'),
                          ]),
                      },
                    )
                : null,
              canModerate() ? h(DropdownMenuSeparator, {}) : null,
              canModerate()
                ? h(
                    DropdownMenuItem,
                    { onClick: () => actions.confirmDelete(post) },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2 text-destructive' }, [
                          h(IconTrash, { class: 'h-4 w-4' }),
                          t('common.delete'),
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
