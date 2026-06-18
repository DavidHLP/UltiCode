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
import { badge, USER_ROLE_COLOR_MAP } from '@/components/ui/terminal'
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
function renderRoleBadge(role: string, t: (key: string) => string) {
  // Try i18n translation first (e.g., 'users.filters.role.SUPER_ADMIN')
  const i18nKey = `users.filters.role.${role}` as const
  const displayRole = t(i18nKey) !== i18nKey ? t(i18nKey) : role.replace('_', ' ')
  return badge({ color: USER_ROLE_COLOR_MAP[role] ?? 'neutral', label: displayRole })
}

// Terminal-style status badge renderer
function renderStatusBadge(isBanned: boolean, isActive: boolean, t: (key: string) => string) {
  if (isBanned) return badge({ color: 'error', label: t('users.status.banned'), pulse: true })
  if (!isActive) return badge({ color: 'neutral', label: t('users.status.inactive') })
  return badge({ color: 'success', label: t('users.status.active'), dot: true, pulse: true })
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
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
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

        return h('div', { class: 'flex items-center gap-2.5 py-0.5' }, [
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
                { class: 'h-8 w-8' },
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
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('users.columns.role'),
        ),
      cell: ({ row }) => {
        const role = row.getValue('role') as string
        return renderRoleBadge(role, t)
      },
    },
    {
      accessorKey: 'status',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('common.status'),
        ),
      cell: ({ row }) => {
        const user = row.original
        return renderStatusBadge(user.isBanned, user.isActive, t)
      },
    },
    {
      accessorKey: 'joinedAt',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
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
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
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
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--silver-500)]' },
          t('common.actions.label'),
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
