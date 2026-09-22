import { describe, it, expect } from 'vitest'
import { ModerationActionType, type ModeratableEntityType } from '@/api/admin/moderation'
import {
  ACTION_CATALOG,
  entityRoute,
  ENTITY_ROUTES,
} from '../moderationPresentation'

describe('moderationPresentation.ACTION_CATALOG', () => {
  it('covers every action exactly once and keeps label/color presentation data', () => {
    const catalogValues = new Set(ACTION_CATALOG.map((action) => action.value))

    for (const actionType of Object.values(ModerationActionType)) {
      expect(catalogValues.has(actionType)).toBe(true)
    }

    expect(ACTION_CATALOG.length).toBe(catalogValues.size)
    for (const action of ACTION_CATALOG) {
      expect(action.labelKey).toMatch(/^moderation\.actions\./)
      expect(action.color).toBeTruthy()
    }
  })

  it('marks TEMP_BANNED as the only action requiring duration', () => {
    expect(
      ACTION_CATALOG.filter((action) => action.requiresDuration).map((action) => action.value),
    ).toEqual([ModerationActionType.TEMP_BANNED])
  })
})

describe('moderationPresentation.entityRoute', () => {
  it('resolves every known entity type', () => {
    const expected: Array<[ModeratableEntityType, string]> = [
      ['forum_post', '/forum/posts/42'],
      ['forum_comment', '/comments/forum/42'],
      ['solution', '/solutions/42'],
      ['solution_comment', '/comments/solution/42'],
      ['problem', '/problems/42'],
    ]

    for (const [entityType, route] of expected) {
      expect(entityRoute(entityType, '42')).toBe(route)
      expect(ENTITY_ROUTES[entityType]('42')).toBe(route)
    }
  })

  it('passes UUID and numeric IDs through verbatim', () => {
    expect(entityRoute('forum_post', 'c1d2e3f4-aaaa-bbbb-cccc-1234567890ab')).toBe(
      '/forum/posts/c1d2e3f4-aaaa-bbbb-cccc-1234567890ab',
    )
    expect(entityRoute('problem', '12345')).toBe('/problems/12345')
  })
})
