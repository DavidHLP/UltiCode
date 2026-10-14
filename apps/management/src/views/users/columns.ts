import { h } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  Ban as IconBan,
  Check as IconCheck,
  Lock as IconLock,
  Shield as IconShield,
  User as IconUser,
} from '@lucide/vue'

import { Avatar, AvatarFallback, AvatarImage } from '@ulticode/design-system/ui/avatar'
import { createSelectionColumn } from '@/components/table/selectionColumn'
import { createEntityActionsMenu } from '@/components/table/entityActions'
import { badge, USER_ROLE_COLOR_MAP } from '@ulticode/badge-config'
import type { User } from '@/api/admin/users'
import { formatDate } from '@/lib/format/date'
import type { UserPermissionMap } from '@/composables/domainPermissionMaps'

export interface UserActions {
  viewUser: (user: User) => void
  editUser: (user: User) => void
  resetPassword: (user: User) => void
  startBanUser: (user: User) => void
  unbanUser: (id: string) => void
}

// Semantic role badge renderer
function renderRoleBadge(role: string, t: (key: string) => string) {
  // Try i18n translation first (e.g., 'users.filters.role.SUPER_ADMIN')
  const i18nKey = `users.filters.role.${role}` as const
  const displayRole = t(i18nKey) !== i18nKey ? t(i18nKey) : role.replace('_', ' ')
  return badge({ color: USER_ROLE_COLOR_MAP[role] ?? 'neutral', label: displayRole })
}

// Semantic status badge renderer
function renderStatusBadge(isBanned: boolean, isActive: boolean, t: (key: string) => string) {
  if (isBanned) return badge({ color: 'error', label: t('users.status.banned'), pulse: true })
  if (!isActive) return badge({ color: 'neutral', label: t('users.status.inactive') })
  return badge({ color: 'success', label: t('users.status.active'), dot: true, pulse: true })
}

export function createColumns(
  t: (key: string) => string,
  actions: UserActions,
  can: UserPermissionMap,
): ColumnDef<User>[] {
  return [
    ...createSelectionColumn<User>(t, {
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
      accessorKey: 'username',
      header: () =>
        h(
          'span',
          { class: 'font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]' },
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
                        class: 'font-mono text-xs bg-[var(--accent)] dark:bg-[var(--foreground)]',
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
            h('span', { class: 'font-mono text-xs text-[var(--muted-foreground)]' }, displayEmail),
          ]),
        ])
      },
    },
    {
      accessorKey: 'role',
      header: () =>
        h(
          'span',
          { class: 'font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]' },
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
          { class: 'font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]' },
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
          { class: 'font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]' },
          t('users.columns.joined'),
        ),
      cell: ({ row }) => {
        const date = row.getValue('joinedAt') as string
        return h(
          'span',
          { class: 'font-mono text-xs text-[var(--muted-foreground)] tabular-nums' },
          formatDate(date),
        )
      },
    },
    {
      accessorKey: 'lastLoginAt',
      header: () =>
        h(
          'span',
          { class: 'font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]' },
          t('users.columns.lastLogin'),
        ),
      cell: ({ row }) => {
        const lastLogin = row.getValue('lastLoginAt') as string | undefined
        if (!lastLogin) {
          return h(
            'span',
            { class: 'font-mono text-xs text-[var(--muted-foreground)] italic' },
            '—',
          )
        }
        return h(
          'span',
          { class: 'font-mono text-xs text-[var(--muted-foreground)] tabular-nums' },
          formatDate(lastLogin),
        )
      },
    },
    {
      id: 'actions',
      header: () =>
        h(
          'span',
          { class: 'font-mono text-xs uppercase tracking-widest text-[var(--muted-foreground)]' },
          t('common.actions.label'),
        ),
      cell: ({ row }) => {
        const user = row.original
        const canModerateRow = can.moderate.value
        return createEntityActionsMenu(
          [
            {
              label: t('users.actions.viewDetails'),
              onSelect: () => actions.viewUser(user),
              icon: IconUser,
              iconClass: 'h-4 w-4 text-foreground',
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
              iconClass: 'h-4 w-4 text-foreground',
            },
            { kind: 'separator' },
            user.isBanned
              ? {
                  label: t('users.actions.unbanUser'),
                  onSelect: () => actions.unbanUser(user.id),
                  icon: IconCheck,
                  iconClass: 'h-4 w-4 text-foreground',
                  labelClass: 'text-[var(--foreground)]',
                  hidden: !canModerateRow,
                }
              : {
                  label: t('users.actions.banUser'),
                  onSelect: () => actions.startBanUser(user),
                  icon: IconBan,
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
          },
        )
      },
    },
  ]
}
