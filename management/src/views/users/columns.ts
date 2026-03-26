import { h } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconBan,
  IconCheck,
  IconDotsVertical,
  IconLock,
  IconShield,
  IconUser,
} from '@tabler/icons-vue'

import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Checkbox } from '@/components/ui/checkbox'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Button } from '@/components/ui/button'
import type { User } from '@/api/admin/users'
import { formatDate } from '@/lib/format/date'

export interface UserActions {
  viewUser: (user: User) => void
  editUser: (user: User) => void
  resetPassword: (user: User) => void
  startBanUser: (user: User) => void
  unbanUser: (id: string) => void
}

// Terminal-style role badge renderer
function renderRoleBadge(role: string) {
  const roleStyles: Record<string, { bg: string; border: string; text: string }> = {
    SUPER_ADMIN: {
      bg: 'bg-[oklch(0.65_0.15_250/0.15)]',
      border: 'border-[oklch(0.65_0.15_250/0.4)]',
      text: 'text-[var(--accent-electric)]',
    },
    ADMIN: {
      bg: 'bg-[oklch(0.7_0.12_195/0.15)]',
      border: 'border-[oklch(0.7_0.12_195/0.4)]',
      text: 'text-[var(--terminal-cyan)]',
    },
    MODERATOR: {
      bg: 'bg-[oklch(0.75_0.15_85/0.15)]',
      border: 'border-[oklch(0.75_0.15_85/0.4)]',
      text: 'text-[var(--terminal-amber)]',
    },
    USER: {
      bg: 'bg-[var(--silver-100)] dark:bg-[var(--silver-800)]',
      border: 'border-[var(--silver-300)] dark:border-[var(--silver-600)]',
      text: 'text-[var(--silver-600)] dark:text-[var(--silver-400)]',
    },
  }

  const defaultStyle = {
    bg: 'bg-[var(--silver-100)]',
    border: 'border-[var(--silver-300)]',
    text: 'text-[var(--silver-600)]',
  }
  const style = roleStyles[role] ?? defaultStyle
  const displayRole = role.replace('_', ' ')

  return h(
    'span',
    {
      class: [
        'font-data text-[11px] font-medium uppercase tracking-[0.05em]',
        'px-2 py-0.5 border',
        style.bg,
        style.border,
        style.text,
      ].join(' '),
    },
    displayRole,
  )
}

// Terminal-style status badge renderer
function renderStatusBadge(user: User) {
  if (user.isBanned) {
    return h(
      'div',
      { class: 'flex items-center gap-2' },
      h(
        'span',
        {
          class: [
            'font-data text-[11px] font-medium uppercase tracking-[0.05em]',
            'px-2 py-0.5 border',
            'bg-[oklch(0.6_0.2_25/0.15)]',
            'border-[oklch(0.6_0.2_25/0.4)]',
            'text-[var(--terminal-red)]',
            'animate-pulse-subtle',
          ].join(' '),
        },
        'BANNED',
      ),
    )
  }

  if (!user.isActive) {
    return h(
      'span',
      {
        class: [
          'font-data text-[11px] font-medium uppercase tracking-[0.05em]',
          'px-2 py-0.5 border',
          'bg-[var(--silver-100)] dark:bg-[var(--silver-800)]',
          'border-[var(--silver-300)] dark:border-[var(--silver-600)]',
          'text-[var(--silver-500)]',
        ].join(' '),
      },
      'INACTIVE',
    )
  }

  return h('div', { class: 'flex items-center gap-2' }, [
    h('span', {
      class: 'w-1.5 h-1.5 bg-[var(--terminal-green)] animate-pulse-subtle',
    }),
    h(
      'span',
      {
        class: [
          'font-data text-[11px] font-medium uppercase tracking-[0.05em]',
          'px-2 py-0.5 border',
          'bg-[oklch(0.7_0.15_145/0.15)]',
          'border-[oklch(0.7_0.15_145/0.4)]',
          'text-[var(--terminal-green)]',
        ].join(' '),
      },
      'ACTIVE',
    ),
  ])
}

export function createColumns(
  t: (key: string) => string,
  actions: UserActions,
  canModerateUser: () => boolean,
): ColumnDef<User>[] {
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
      accessorKey: 'username',
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('users.columns.user'),
        ),
      cell: ({ row }) => {
        const user = row.original
        const initials =
          user.name
            ?.split(' ')
            .map((n: string) => n[0])
            .join('')
            .toUpperCase()
            .slice(0, 2) || user.username.slice(0, 2).toUpperCase()

        const displayName = user.name || user.username
        const displayEmail = user.email ?? user.username

        return h('div', { class: 'flex items-center gap-3 py-1' }, [
          // Avatar with ring
          h(
            'div',
            {
              class: [
                'relative',
                user.isBanned
                  ? 'ring-2 ring-[var(--terminal-red)] ring-offset-2 ring-offset-background'
                  : user.isActive
                    ? 'ring-2 ring-[var(--terminal-green)] ring-offset-2 ring-offset-background'
                    : '',
              ].join(' '),
            },
            [
              h(
                Avatar,
                { class: 'h-10 w-10' },
                {
                  default: () => [
                    h(AvatarImage, { src: user.avatar ?? '' }),
                    h(
                      AvatarFallback,
                      {
                        class:
                          'font-data text-xs bg-[var(--silver-100)] dark:bg-[var(--silver-800)]',
                      },
                      () => initials,
                    ),
                  ],
                },
              ),
            ],
          ),
          // Two-line display
          h('div', { class: 'flex flex-col gap-0.5' }, [
            h('span', { class: 'font-medium text-sm text-[var(--foreground)]' }, displayName),
            h('span', { class: 'font-data text-xs text-[var(--silver-400)]' }, displayEmail),
          ]),
        ])
      },
    },
    {
      accessorKey: 'role',
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('users.columns.role'),
        ),
      cell: ({ row }) => {
        const role = row.getValue('role') as string
        return renderRoleBadge(role)
      },
    },
    {
      accessorKey: 'status',
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('common.status'),
        ),
      cell: ({ row }) => {
        const user = row.original
        return renderStatusBadge(user)
      },
    },
    {
      accessorKey: 'joinedAt',
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('users.columns.joined'),
        ),
      cell: ({ row }) => {
        const date = row.getValue('joinedAt') as string
        return h(
          'span',
          { class: 'font-data text-xs text-[var(--silver-400)] tabular-nums' },
          formatDate(date),
        )
      },
    },
    {
      accessorKey: 'lastLoginAt',
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('users.columns.lastLogin'),
        ),
      cell: ({ row }) => {
        const lastLogin = row.getValue('lastLoginAt') as string | undefined
        if (!lastLogin) {
          return h('span', { class: 'font-data text-xs text-[var(--silver-400)] italic' }, '—')
        }
        return h(
          'span',
          { class: 'font-data text-xs text-[var(--silver-400)] tabular-nums' },
          formatDate(lastLogin),
        )
      },
    },
    {
      id: 'actions',
      header: () =>
        h(
          'span',
          { class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]' },
          t('common.actions'),
        ),
      cell: ({ row }) => {
        const user = row.original
        return createActionsDropdown(t, user, actions, canModerateUser)
      },
    },
  ]
}

function createActionsDropdown(
  t: (key: string) => string,
  user: User,
  actions: UserActions,
  canModerateUser: () => boolean,
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
                    h('span', { class: 'sr-only' }, 'Open menu'),
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
                  onClick: () => actions.viewUser(user),
                  class: 'font-data text-xs cursor-pointer',
                },
                {
                  default: () =>
                    h('div', { class: 'flex items-center gap-2' }, [
                      h(IconUser, { class: 'h-4 w-4 text-[var(--terminal-cyan)]' }),
                      h('span', t('users.actions.viewDetails')),
                    ]),
                },
              ),
              h(
                DropdownMenuItem,
                {
                  onClick: () => actions.editUser(user),
                  class: 'font-data text-xs cursor-pointer',
                },
                {
                  default: () =>
                    h('div', { class: 'flex items-center gap-2' }, [
                      h(IconShield, { class: 'h-4 w-4 text-[var(--accent-electric)]' }),
                      h('span', t('users.actions.editProfile')),
                    ]),
                },
              ),
              h(
                DropdownMenuItem,
                {
                  onClick: () => actions.resetPassword(user),
                  class: 'font-data text-xs cursor-pointer',
                },
                {
                  default: () =>
                    h('div', { class: 'flex items-center gap-2' }, [
                      h(IconLock, { class: 'h-4 w-4 text-[var(--terminal-amber)]' }),
                      h('span', t('users.actions.resetPassword')),
                    ]),
                },
              ),
              h(DropdownMenuSeparator, {
                class: 'bg-[var(--silver-200)] dark:bg-[var(--silver-700)]',
              }),
              canModerateUser()
                ? user.isBanned
                  ? h(
                      DropdownMenuItem,
                      {
                        onClick: () => actions.unbanUser(user.id),
                        class: 'font-data text-xs cursor-pointer',
                      },
                      {
                        default: () =>
                          h('div', { class: 'flex items-center gap-2' }, [
                            h(IconCheck, { class: 'h-4 w-4 text-[var(--terminal-green)]' }),
                            h(
                              'span',
                              { class: 'text-[var(--terminal-green)]' },
                              t('users.actions.unbanUser'),
                            ),
                          ]),
                      },
                    )
                  : h(
                      DropdownMenuItem,
                      {
                        onClick: () => actions.startBanUser(user),
                        class: 'font-data text-xs cursor-pointer',
                      },
                      {
                        default: () =>
                          h('div', { class: 'flex items-center gap-2' }, [
                            h(IconBan, { class: 'h-4 w-4 text-[var(--terminal-red)]' }),
                            h(
                              'span',
                              { class: 'text-[var(--terminal-red)]' },
                              t('users.actions.banUser'),
                            ),
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
