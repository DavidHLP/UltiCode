import { h } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconBan,
  IconCheck,
  IconLock,
  IconShield,
  IconUser,
} from '@tabler/icons-vue'

import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { createSelectionColumn } from '@/components/table/selectionColumn'
import { createEntityActionsMenu } from '@/components/table/entityActions'
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
    ...createSelectionColumn<User>(t, {
      checkboxClass:
        'border-[var(--border-subtle)] data-[state=checked]:bg-[var(--primary)] data-[state=checked]:border-[var(--primary)]',
    }),
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
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--foreground-muted)]' },
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
                  ? 'ring-2 ring-[var(--status-error-mark)] ring-offset-2 ring-offset-background'
                  : user.isActive
                    ? 'ring-2 ring-[var(--status-success-mark)] ring-offset-2 ring-offset-background'
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
                          'font-data text-xs bg-[var(--surface-highlight)] dark:bg-[var(--foreground-strong)]',
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
            h('span', { class: 'font-data text-xs text-[var(--foreground-muted)]' }, displayEmail),
          ]),
        ])
      },
    },
    {
      accessorKey: 'role',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--foreground-muted)]' },
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
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--foreground-muted)]' },
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
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--foreground-muted)]' },
          t('users.columns.joined'),
        ),
      cell: ({ row }) => {
        const date = row.getValue('joinedAt') as string
        return h(
          'span',
          { class: 'font-data text-xs text-[var(--foreground-muted)] tabular-nums' },
          formatDate(date),
        )
      },
    },
    {
      accessorKey: 'lastLoginAt',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--foreground-muted)]' },
          t('users.columns.lastLogin'),
        ),
      cell: ({ row }) => {
        const lastLogin = row.getValue('lastLoginAt') as string | undefined
        if (!lastLogin) {
          return h('span', { class: 'font-data text-xs text-[var(--foreground-muted)] italic' }, '—')
        }
        return h(
          'span',
          { class: 'font-data text-xs text-[var(--foreground-muted)] tabular-nums' },
          formatDate(lastLogin),
        )
      },
    },
    {
      id: 'actions',
      header: () =>
        h(
          'span',
          { class: 'font-data text-2xs uppercase tracking-widest text-[var(--foreground-muted)]' },
          t('common.actions.label'),
        ),
      cell: ({ row }) => {
        const user = row.original
        const canModerateRow = canModerateUser()
        return createEntityActionsMenu(
          [
            {
              label: t('users.actions.viewDetails'),
              onSelect: () => actions.viewUser(user),
              icon: IconUser,
              iconClass: 'h-4 w-4 text-foreground-strong',
            },
            {
              label: t('users.actions.editProfile'),
              onSelect: () => actions.editUser(user),
              icon: IconShield,
              iconClass: 'h-4 w-4 text-[var(--primary)]',
            },
            {
              label: t('users.actions.resetPassword'),
              onSelect: () => actions.resetPassword(user),
              icon: IconLock,
              iconClass: 'h-4 w-4 text-foreground-strong',
            },
            { kind: 'separator' },
            user.isBanned
              ? {
                  label: t('users.actions.unbanUser'),
                  onSelect: () => actions.unbanUser(user.id),
                  icon: IconCheck,
                  iconClass: 'h-4 w-4 text-foreground-strong',
                  labelClass: 'text-[var(--foreground-strong)]',
                  hidden: !canModerateRow,
                }
              : {
                  label: t('users.actions.banUser'),
                  onSelect: () => actions.startBanUser(user),
                  icon: IconBan,
                  iconClass: 'h-4 w-4 text-foreground-strong',
                  labelClass: 'text-[var(--foreground-strong)]',
                  hidden: !canModerateRow,
                },
          ],
          {
            triggerClass:
              'h-8 w-8 p-0 hover:bg-[var(--surface-highlight)] dark:hover:bg-[var(--foreground-strong)]',
            triggerIconClass: 'h-4 w-4 text-[var(--foreground-muted)]',
            contentClass: 'border-[var(--border-subtle)] dark:border-[var(--foreground-strong)]',
            itemClass: 'font-data text-xs cursor-pointer',
            separatorClass: 'bg-[var(--border-subtle)] dark:bg-[var(--foreground-strong)]',
          },
        )
      },
    },
  ]
}