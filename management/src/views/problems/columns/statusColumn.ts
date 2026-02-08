import { h } from 'vue'
import { Badge } from '@/components/ui/badge'
import { IconCircleCheckFilled, IconLoader } from '@tabler/icons-vue'
import type { ColumnDef } from '@tanstack/vue-table'
import type { Problem } from '@/api/admin/problems'

export const statusColumn: ColumnDef<Problem> = {
  accessorKey: 'status',
  header: () => 'Status',
  cell: ({ row }) => {
    const status = row.getValue('status') as string
    const isSolved = status === 'solved'
    const isAttempted = status === 'attempted'
    const icon = isSolved ? IconCircleCheckFilled : undefined
    const variant = isSolved
      ? ('default' as const)
      : isAttempted
        ? ('secondary' as const)
        : ('outline' as const)
    const label = isSolved ? 'Solved' : isAttempted ? 'Attempted' : 'Todo'
    return h('div', { class: 'flex items-center gap-2' }, [
      icon
        ? h(icon, { class: 'h-4 w-4 text-emerald-500' })
        : h(IconLoader, { class: 'h-4 w-4 animate-spin text-muted-foreground' }),
      h(Badge, { variant }, () => label),
    ])
  },
}

export const publishedColumn: ColumnDef<Problem> = {
  accessorKey: 'is_published',
  header: () => 'Published',
  cell: ({ row }) => {
    const isPublished = row.getValue('is_published') as boolean
    const isDeleted = row.original.is_deleted
    if (isDeleted) {
      return h(Badge, { variant: 'destructive' }, { default: () => ['Deleted'] })
    }
    return h(
      Badge,
      { variant: isPublished ? 'default' : 'secondary' },
      { default: () => [isPublished ? 'Published' : 'Draft'] },
    )
  },
}
