import { h, type Component } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconBan,
  IconCheck,
  IconDotsVertical,
  IconLock,
  IconShield,
  IconUser,
} from '@tabler/icons-vue'

import { Badge } from '@/components/ui/badge'
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
import { getRoleBadgeVariant, getStatusIcon, getStatusBadge } from '@/lib/entities/user'
import type { User } from '@/api/admin/users'
import { formatDate } from '@/lib/format/date'

export interface UserActions {
  viewUser: (user: User) => void
  editUser: (user: User) => void
  resetPassword: (user: User) => void
  startBanUser: (user: User) => void
  unbanUser: (id: string) => void
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
        }),
      cell: ({ row }) =>
        h(Checkbox, {
          modelValue: row.getIsSelected(),
          'onUpdate:modelValue': (value: boolean | 'indeterminate') => row.toggleSelected(!!value),
          'aria-label': 'Select row',
        }),
      enableSorting: false,
      enableHiding: false,
    },
    {
      accessorKey: 'username',
      header: () => t('users.columns.user'),
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

        return h('div', { class: 'flex items-center gap-3' }, [
          h(
            Avatar,
            { class: 'h-9 w-9' },
            {
              default: () => [
                h(AvatarImage, { src: user.avatar ?? '' }),
                h(AvatarFallback, {}, () => initials),
              ],
            },
          ),
          h('div', { class: 'flex flex-col' }, [
            h('span', { class: 'font-medium text-sm' }, displayName),
            h('span', { class: 'text-muted-foreground text-xs' }, displayEmail),
          ]),
        ])
      },
    },
    {
      accessorKey: 'role',
      header: () => t('users.columns.role'),
      cell: ({ row }) => {
        const role = row.getValue('role') as string
        const icon: Component = role === 'USER' ? IconUser : IconShield
        return h('div', { class: 'flex items-center gap-2' }, [
          h(icon, { class: 'h-4 w-4 text-muted-foreground' }),
          h(Badge, { variant: getRoleBadgeVariant(role) }, () => role.replace('_', ' ')),
        ])
      },
    },
    {
      accessorKey: 'status',
      header: () => t('common.status'),
      cell: ({ row }) => {
        const user = row.original
        return h('div', { class: 'flex items-center gap-2' }, [
          getStatusIcon(user),
          getStatusBadge(user, t),
        ])
      },
    },
    {
      accessorKey: 'joined_at',
      header: () => t('users.columns.joined'),
      cell: ({ row }) => {
        const date = row.getValue('joined_at') as string
        return h('span', { class: 'text-muted-foreground text-sm' }, formatDate(date))
      },
    },
    {
      accessorKey: 'last_login_at',
      header: () => t('users.columns.lastLogin'),
      cell: ({ row }) => {
        const lastLogin = row.getValue('last_login_at') as string | undefined
        if (!lastLogin) {
          return h('span', { class: 'text-muted-foreground text-sm' }, t('common.never'))
        }
        return h('span', { class: 'text-muted-foreground text-sm' }, formatDate(lastLogin))
      },
    },
    {
      id: 'actions',
      header: () => t('common.actions'),
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
                { variant: 'ghost', size: 'icon', class: 'h-8 w-8 p-0' },
                {
                  default: () => [
                    h('span', { class: 'sr-only' }, 'Open menu'),
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
                { onClick: () => actions.viewUser(user) },
                {
                  default: () =>
                    h('div', { class: 'flex items-center gap-2' }, [
                      h(IconUser, { class: 'h-4 w-4' }),
                      t('users.actions.viewDetails'),
                    ]),
                },
              ),
              h(
                DropdownMenuItem,
                { onClick: () => actions.editUser(user) },
                {
                  default: () =>
                    h('div', { class: 'flex items-center gap-2' }, [
                      h(IconShield, { class: 'h-4 w-4' }),
                      t('users.actions.editProfile'),
                    ]),
                },
              ),
              h(
                DropdownMenuItem,
                { onClick: () => actions.resetPassword(user) },
                {
                  default: () =>
                    h('div', { class: 'flex items-center gap-2' }, [
                      h(IconLock, { class: 'h-4 w-4' }),
                      t('users.actions.resetPassword'),
                    ]),
                },
              ),
              h(DropdownMenuSeparator, {}),
              canModerateUser()
                ? user.is_banned
                  ? h(
                      DropdownMenuItem,
                      { onClick: () => actions.unbanUser(user.id) },
                      {
                        default: () =>
                          h('div', { class: 'flex items-center gap-2 text-emerald-600' }, [
                            h(IconCheck, { class: 'h-4 w-4' }),
                            t('users.actions.unbanUser'),
                          ]),
                      },
                    )
                  : h(
                      DropdownMenuItem,
                      { onClick: () => actions.startBanUser(user) },
                      {
                        default: () =>
                          h('div', { class: 'flex items-center gap-2 text-destructive' }, [
                            h(IconBan, { class: 'h-4 w-4' }),
                            t('users.actions.banUser'),
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
