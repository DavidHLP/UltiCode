import { describe, it, expect, vi } from 'vitest'

vi.mock('@/router', () => ({
  default: {
    push: vi.fn(),
    replace: vi.fn(),
    back: vi.fn(),
  },
}))

vi.mock('@/utils/request', () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPatch: vi.fn(),
  apiDelete: vi.fn(),
  apiDownload: vi.fn(),
}))

import { problemDescriptionSchema } from './problemDescription'

describe('problemDescriptionSchema', () => {
  const validData = {
    title: 'Two Sum',
    slug: 'two-sum',
    difficulty: 'EASY',
    status: 'todo',
    isPremium: false,
    isPublished: true,
    summary: 'Find two numbers that add up to a target',
    content: 'Given an array of integers...',
    examples: [
      {
        input: '[2,7,11,15], target=9',
        output: '[0,1]',
        explanation: 'Because nums[0] + nums[1] == 9',
      },
    ],
    constraints: ['2 <= nums.length <= 10^4'],
    hints: [],
    tags: ['array', 'hash-table'],
  }

  it('valid data passes', () => {
    const result = problemDescriptionSchema.safeParse(validData)
    expect(result.success).toBe(true)
  })

  it('rejects slug with uppercase', () => {
    const result = problemDescriptionSchema.safeParse({
      ...validData,
      slug: 'Two-Sum',
    })
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.errors[0].message).toContain('lowercase')
    }
  })

  it('rejects slug with special characters', () => {
    const result = problemDescriptionSchema.safeParse({
      ...validData,
      slug: 'two_sum!',
    })
    expect(result.success).toBe(false)
  })

  it('rejects empty title', () => {
    const result = problemDescriptionSchema.safeParse({
      ...validData,
      title: '',
    })
    expect(result.success).toBe(false)
  })

  it('rejects empty content', () => {
    const result = problemDescriptionSchema.safeParse({
      ...validData,
      content: '',
    })
    expect(result.success).toBe(false)
  })

  it('rejects empty examples array', () => {
    const result = problemDescriptionSchema.safeParse({
      ...validData,
      examples: [],
    })
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.errors[0].message).toContain('example')
    }
  })

  it('rejects empty constraints array', () => {
    const result = problemDescriptionSchema.safeParse({
      ...validData,
      constraints: [],
    })
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.errors[0].message).toContain('constraint')
    }
  })

  it('accepts valid slug with hyphens and numbers', () => {
    const result = problemDescriptionSchema.safeParse({
      ...validData,
      slug: 'two-sum-2',
    })
    expect(result.success).toBe(true)
  })

  it('accepts optional summary', () => {
    const result = problemDescriptionSchema.safeParse({
      ...validData,
      summary: undefined,
    })
    expect(result.success).toBe(true)
  })

  it('rejects summary over 500 characters', () => {
    const result = problemDescriptionSchema.safeParse({
      ...validData,
      summary: 'a'.repeat(501),
    })
    expect(result.success).toBe(false)
  })
})
