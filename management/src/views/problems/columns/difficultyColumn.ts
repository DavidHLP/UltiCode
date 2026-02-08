import { h } from 'vue'
import { Badge } from '@/components/ui/badge'
import { IconCheck, IconSparkles, IconTrophy, IconFile } from '@tabler/icons-vue'
import type { ColumnDef } from '@tanstack/vue-table'
import type { Problem, Difficulty } from '@/api/admin/problems'
import { getDifficultyBadgeVariant, getDifficultyColor } from '@/lib/entities/problem'

function getDifficultyIcon(difficulty: Difficulty) {
  switch (difficulty) {
    case 'EASY':
      return IconCheck
    case 'MEDIUM':
      return IconSparkles
    case 'HARD':
      return IconTrophy
    default:
      return IconFile
  }
}

export const difficultyColumn: ColumnDef<Problem> = {
  accessorKey: 'difficulty',
  header: () => 'Difficulty',
  cell: ({ row }) => {
    const difficulty = row.getValue('difficulty') as Difficulty
    const icon = getDifficultyIcon(difficulty)
    const color = getDifficultyColor(difficulty)
    return h('div', { class: 'flex items-center gap-2' }, [
      h(icon, { class: `h-4 w-4 ${color}` }),
      h(Badge, { variant: getDifficultyBadgeVariant(difficulty) }, () => difficulty),
    ])
  },
}
