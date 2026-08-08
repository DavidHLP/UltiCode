import { describe, it, expect } from 'vitest'
import {
  ModerationActionType,
  ModerationStatus,
  type ModeratableEntityType,
  type ModerationQueueItem,
} from '@/api/admin/moderation'
import {
  ACTION_CATALOG,
  findAction,
  isTerminalStatus,
  isActionable,
  entityRoute,
  ENTITY_ROUTES,
  TERMINAL_STATUSES,
} from '../moderationWorkflow'

/**
 * Focused unit tests for the moderation workflow seam. The seam owns:
 *   - the action catalog (icon, color, requiresDuration, terminal, label key);
 *   - the entity route resolver;
 *   - the terminal-status reconciliation rule;
 *   - the actionability predicate.
 *
 * Before the seam these concerns lived inline in three different views and
 * the store, with subtle drift (the store's `performAction` hard-coded
 * RESOLVED/DISMISSED as terminal; ReportsView duplicated the entity route
 * map; reviewAppeal skipped stats refresh). These tests pin the seam so
 * future drift fails the build.
 */

describe('moderationWorkflow.ACTION_CATALOG', () => {
  it('exposes exactly one descriptor per known ModerationActionType', () => {
    const catalogValues = new Set(ACTION_CATALOG.map((a) => a.value))
    // every action type in the enum is represented
    for (const t of Object.values(ModerationActionType)) {
      expect(catalogValues.has(t)).toBe(true)
    }
    // no duplicates
    expect(ACTION_CATALOG.length).toBe(catalogValues.size)
  })

  it('marks only the actions that leave the queue in a final state as terminal', () => {
    const terminal = ACTION_CATALOG.filter((a) => a.terminal).map((a) => a.value)
    // WARNED and APPEAL_PENDING keep the item in flight
    expect(terminal).not.toContain(ModerationActionType.WARNED)
    expect(terminal).not.toContain(ModerationActionType.APPEAL_PENDING)
    // RESOLVED, DISMISSED, DELETED, HIDDEN, RESTORED, all bans, appeal decisions
    // all finalize the item
    expect(terminal).toContain(ModerationActionType.RESOLVED)
    expect(terminal).toContain(ModerationActionType.DISMISSED)
    expect(terminal).toContain(ModerationActionType.DELETED)
    expect(terminal).toContain(ModerationActionType.HIDDEN)
    expect(terminal).toContain(ModerationActionType.RESTORED)
    expect(terminal).toContain(ModerationActionType.TEMP_BANNED)
    expect(terminal).toContain(ModerationActionType.PERM_BANNED)
    expect(terminal).toContain(ModerationActionType.APPEAL_APPROVED)
    expect(terminal).toContain(ModerationActionType.APPEAL_REJECTED)
  })

  it('marks TEMP_BANNED as requiresDuration and only that one', () => {
    const requiresDuration = ACTION_CATALOG
      .filter((a) => a.requiresDuration)
      .map((a) => a.value)
    expect(requiresDuration).toEqual([ModerationActionType.TEMP_BANNED])
  })

  it('looks up descriptors by action type via findAction', () => {
    for (const a of ACTION_CATALOG) {
      expect(findAction(a.value)).toBe(a)
    }
    // unknown types return undefined (defensive default)
    expect(findAction('NOT_A_REAL_ACTION' as ModerationActionType)).toBeUndefined()
  })
})

describe('moderationWorkflow.terminal status', () => {
  it('classifies RESOLVED and DISMISSED as terminal and nothing else', () => {
    expect(TERMINAL_STATUSES.size).toBe(2)
    expect(isTerminalStatus(ModerationStatus.RESOLVED)).toBe(true)
    expect(isTerminalStatus(ModerationStatus.DISMISSED)).toBe(true)
    expect(isTerminalStatus(ModerationStatus.PENDING)).toBe(false)
    expect(isTerminalStatus(ModerationStatus.UNDER_REVIEW)).toBe(false)
    expect(isTerminalStatus(ModerationStatus.APPEAL_PENDING)).toBe(false)
  })
})

describe('moderationWorkflow.isActionable', () => {
  it('gates on PENDING or UNDER_REVIEW', () => {
    const make = (status: ModerationStatus): ModerationQueueItem => ({
      id: 'q1', entityType: 'problem', entityId: '1', status,
      priority: 0, primaryCategory: 'SPAM' as never, reportCount: 0,
      createdAt: new Date(), updatedAt: new Date(),
    } as ModerationQueueItem)
    expect(isActionable(make(ModerationStatus.PENDING))).toBe(true)
    expect(isActionable(make(ModerationStatus.UNDER_REVIEW))).toBe(true)
    expect(isActionable(make(ModerationStatus.RESOLVED))).toBe(false)
    expect(isActionable(make(ModerationStatus.DISMISSED))).toBe(false)
    expect(isActionable(make(ModerationStatus.APPEAL_PENDING))).toBe(false)
  })
})

describe('moderationWorkflow.entityRoute', () => {
  it('resolves every known entity type', () => {
    const expected: Array<[ModeratableEntityType, RegExp]> = [
      ['forum_post', /^\/forum\/posts\/42$/],
      ['forum_comment', /^\/comments\/forum\/42$/],
      ['solution', /^\/solutions\/42$/],
      ['solution_comment', /^\/comments\/solution\/42$/],
      ['problem', /^\/problems\/42$/],
    ]
    for (const [type, regex] of expected) {
      expect(entityRoute(type, '42')).toMatch(regex)
      expect(ENTITY_ROUTES[type]('42')).toMatch(regex)
    }
  })

  it('passes the entity id through verbatim (UUIDs and numeric ids both work)', () => {
    expect(entityRoute('forum_post', 'c1d2e3f4-aaaa-bbbb-cccc-1234567890ab'))
      .toBe('/forum/posts/c1d2e3f4-aaaa-bbbb-cccc-1234567890ab')
    expect(entityRoute('problem', '12345')).toBe('/problems/12345')
  })
})
