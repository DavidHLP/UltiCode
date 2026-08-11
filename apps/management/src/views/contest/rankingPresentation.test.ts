import { describe, expect, it } from 'vitest'
import { getRankAccentClass, getRankIcon, RANK_TEXT_CLASS } from './rankingPresentation'

describe('ranking presentation', () => {
  it('keeps rank text neutral and applies semantic accents to icons', () => {
    expect(RANK_TEXT_CLASS).toBe('text-foreground')
    expect(getRankAccentClass(1)).toBe('text-rank-first')
    expect(getRankAccentClass(2)).toBe('text-rank-second')
    expect(getRankAccentClass(3)).toBe('text-rank-third')
    expect(getRankIcon(1)).not.toBeNull()
    expect(getRankIcon(4)).toBeNull()
  })
})
