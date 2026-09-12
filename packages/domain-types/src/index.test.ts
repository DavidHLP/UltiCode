import { describe, expect, it } from 'vitest'
import { normalizeAdminProblem, normalizePublicProblem } from './index'

describe('Problem boundary normalization', () => {
  it('normalizes the public wire shape once and preserves the legacy rate key', () => {
    const problem = normalizePublicProblem({
      id: '7',
      title: 'Two Sum',
      slug: 'two-sum',
      difficulty: 'EASY',
      acceptance_rate: '42.5',
      is_premium: true,
      has_solution: false,
      completed_time: '2026-01-01T00:00:00Z',
      tags: [{ id: '1', label: 'array' }],
    })

    expect(problem.id).toBe(7)
    expect(problem.acceptanceRate).toBe(42.5)
    expect(problem.acceptance_rate).toBe(42.5)
    expect(problem.isPremium).toBe(true)
    expect(problem.hasSolution).toBe(false)
    expect(problem.completedTime).toBe('2026-01-01T00:00:00Z')
    expect(problem.tags).toEqual(['array'])
  })

  it('does not expose admin or unknown fields through the public shape', () => {
    const problem = normalizePublicProblem({
      id: 7,
      title: 'Two Sum',
      slug: 'two-sum',
      difficulty: 'EASY',
      acceptanceRate: 42.5,
      tags: [],
      is_deleted: true,
      flag_reason: 'internal',
      internalOnly: 'secret',
    })

    expect(problem).not.toHaveProperty('isDeleted')
    expect(problem).not.toHaveProperty('flagReason')
    expect(problem).not.toHaveProperty('internalOnly')
  })

  it('normalizes admin fields and converts wire timestamps to Dates', () => {
    const problem = normalizeAdminProblem({
      id: 7,
      slug: 'two-sum',
      title: 'Two Sum',
      difficulty: 'EASY',
      acceptance_rate: '42.5',
      status: 'TODO',
      is_premium: true,
      has_solution: false,
      is_published: false,
      is_deleted: false,
      created_at: '2026-01-01T00:00:00Z',
      updated_at: '2026-01-02T00:00:00Z',
      tags: [{ id: '1', label: 'array' }],
      flag_notes: '',
    })

    expect(problem.id).toBe('7')
    expect(problem.isPremium).toBe(true)
    expect(problem.hasSolution).toBe(false)
    expect(problem.acceptanceRate).toBe(42.5)
    expect(problem.createdAt).toEqual(new Date('2026-01-01T00:00:00Z'))
    expect(problem.updatedAt).toEqual(new Date('2026-01-02T00:00:00Z'))
    expect(problem.tags).toEqual([{ id: '1', label: 'array' }])
    expect(problem.flagNotes).toBe('')
    expect(problem).not.toHaveProperty('is_premium')
  })

  it('does not expose unknown fields through the admin shape', () => {
    const problem = normalizeAdminProblem({
      id: 7,
      slug: 'two-sum',
      title: 'Two Sum',
      difficulty: 'EASY',
      status: 'TODO',
      isPremium: false,
      hasSolution: false,
      isPublished: false,
      isDeleted: false,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-02T00:00:00Z',
      tags: [],
      internalOnly: 'secret',
    })

    expect(problem).not.toHaveProperty('internalOnly')
  })

  it('rejects malformed public payloads at the API boundary', () => {
    expect(() => normalizePublicProblem(null)).toThrow('must be an object')
    expect(() => normalizePublicProblem({ id: 1 })).toThrow('title')
    expect(() => normalizePublicProblem({
      id: 1,
      title: 'Two Sum',
      slug: 'two-sum',
      difficulty: 'EASY',
      tags: [],
    })).toThrow('acceptanceRate')
    expect(() => normalizePublicProblem({
      id: 1,
      title: 'Two Sum',
      slug: 'two-sum',
      difficulty: 'EASY',
      acceptanceRate: 'not-a-number',
      tags: [],
    })).toThrow('acceptanceRate')
    expect(() => normalizePublicProblem({
      id: 1,
      title: 'Two Sum',
      slug: 'two-sum',
      difficulty: 'EASY',
      acceptanceRate: 42,
      status: 'unknown',
      tags: [],
    })).toThrow('status')
    expect(() => normalizePublicProblem({
      id: 1,
      title: 'Two Sum',
      slug: 'two-sum',
      difficulty: 'EASY',
      acceptanceRate: 42,
      tags: 'array',
    })).toThrow('tags array')
  })

  it('rejects malformed admin payloads instead of casting them', () => {
    expect(() => normalizeAdminProblem({
      id: 1,
      slug: 'two-sum',
      title: 'Two Sum',
      difficulty: 'EASY',
      status: 'unknown',
      isPremium: false,
      hasSolution: false,
      isPublished: false,
      isDeleted: false,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-02T00:00:00Z',
      tags: [],
    })).toThrow('status')
    expect(() => normalizeAdminProblem({
      id: 1,
      slug: 'two-sum',
      title: 'Two Sum',
      difficulty: 'EASY',
      status: 'TODO',
      isPremium: false,
      hasSolution: false,
      isPublished: false,
      isDeleted: false,
      createdAt: 'not-a-date',
      updatedAt: '2026-01-02T00:00:00Z',
      tags: [],
    })).toThrow('createdAt')
  })
})
