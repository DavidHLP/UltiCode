/**
 * Difficulty levels for problems
 */
export type Difficulty = 'Easy' | 'Medium' | 'Hard'

export const Difficulty = {
  EASY: 'Easy' as Difficulty,
  MEDIUM: 'Medium' as Difficulty,
  HARD: 'Hard' as Difficulty,
} as const

export function isDifficulty(value: string): value is Difficulty {
  return ['Easy', 'Medium', 'Hard'].includes(value)
}
