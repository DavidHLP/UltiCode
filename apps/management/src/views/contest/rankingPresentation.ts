import { IconMedal, IconTrophy } from '@tabler/icons-vue'

export const RANK_TEXT_CLASS = 'text-foreground'

export function getRankAccentClass(rank: number): string {
  if (rank === 1) return 'text-rank-first'
  if (rank === 2) return 'text-rank-second'
  if (rank === 3) return 'text-rank-third'
  return 'text-foreground'
}

export function getRankIcon(rank: number) {
  if (rank === 1) return IconTrophy
  if (rank <= 3) return IconMedal
  return null
}
