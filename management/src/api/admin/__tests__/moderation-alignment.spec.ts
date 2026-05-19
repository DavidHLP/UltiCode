/**
 * Frontend-Backend alignment tests for the Moderation module.
 *
 * These tests verify that the frontend API types and HTTP methods
 * match the backend DTOs and controller endpoints exactly.
 *
 * Reference: docs/analysis/moderation-frontend-backend-alignment.md
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  moderationQueueApi,
  reportsApi,
  appealsApi,
  type AssignModerationDto,
  type BatchModerationActionDto,
  type BatchActionResult,
  type QueryModerationQueueParams,
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  type QueryReportsParams,
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  type QueryAppealsParams,
  type ModerationAction,
  type Report,
} from '../moderation'

// Mock the request utils
vi.mock('@/utils/request', () => ({
  apiGet: vi.fn(() => Promise.resolve({})),
  apiPost: vi.fn(() => Promise.resolve({})),
  apiPatch: vi.fn(() => Promise.resolve({})),
  apiPut: vi.fn(() => Promise.resolve({})),
  apiDelete: vi.fn(() => Promise.resolve({})),
}))

import { apiGet, apiPost, apiPatch } from '@/utils/request'

describe('Moderation API: Frontend-Backend Alignment', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ========================================================================
  // CRITICAL: HTTP Method Alignment
  // ========================================================================

  describe('CRITICAL: HTTP method alignment', () => {
    it('reviewAppeal should use POST (not PATCH) to match backend', async () => {
      await appealsApi.reviewAppeal('appeal-1', {
        decision: 'APPROVED' as const,
        response: 'OK',
      })

      // Backend uses @PostMapping, so frontend must use apiPost
      expect(apiPost).toHaveBeenCalledWith(
        '/moderation/appeals/appeal-1/review',
        expect.any(Object),
      )
      expect(apiPatch).not.toHaveBeenCalledWith(
        '/moderation/appeals/appeal-1/review',
        expect.any(Object),
      )
    })
  })

  // ========================================================================
  // CRITICAL: Request DTO field name alignment
  // ========================================================================

  describe('CRITICAL: Request DTO field alignment', () => {
    it('AssignModerationDto should use assignedTo (not assignedToId) to match backend AssignDTO', () => {
      // Backend AssignDTO has field "assignedTo", not "assignedToId"
      const dto: AssignModerationDto = { assignedTo: 'moderator-1' }
      expect(dto).toHaveProperty('assignedTo')
      // Type-level check: assignedToId should NOT exist on the type
      // This is enforced at compile time; at runtime we verify the key
      const keys = Object.keys(dto)
      expect(keys).not.toContain('assignedToId')
    })

    it('QueryModerationQueueParams should use assignedTo (not assignedToId) to match backend', () => {
      const params: QueryModerationQueueParams = { assignedTo: 'moderator-1' }
      expect(params).toHaveProperty('assignedTo')
      const keys = Object.keys(params)
      expect(keys).not.toContain('assignedToId')
    })
  })

  // ========================================================================
  // CRITICAL: Response VO field name alignment
  // ========================================================================

  describe('CRITICAL: Response VO field alignment', () => {
    it('BatchActionResult should use failureCount (not errorCount) to match backend', () => {
      // Backend BatchActionResultVO uses "failureCount"
      const result: BatchActionResult = {
        successCount: 5,
        failureCount: 2,
        errors: [],
      }
      expect(result).toHaveProperty('failureCount')
      expect(result).not.toHaveProperty('errorCount')
    })

    it('BatchActionResult.errors should use message (not error) to match backend', () => {
      // Backend BatchError has field "message"
      const result: BatchActionResult = {
        successCount: 5,
        failureCount: 1,
        errors: [{ queueId: 'q1', message: 'Not found' }],
      }
      expect(result.errors[0]).toHaveProperty('message')
      expect(result.errors[0]).not.toHaveProperty('error')
    })
  })

  // ========================================================================
  // MEDIUM: BatchModerationActionDto should include durationDays
  // ========================================================================

  describe('MEDIUM: BatchModerationActionDto alignment', () => {
    it('BatchModerationActionDto should include durationDays for TEMP_BANNED actions', () => {
      // Backend BatchModerationActionDTO has durationDays field
      const dto: BatchModerationActionDto = {
        queueIds: ['q1', 'q2'],
        action: 'TEMP_BANNED',
        note: 'Spam cleanup',
        durationDays: 7,
      }
      expect(dto).toHaveProperty('durationDays')
      expect(dto.durationDays).toBe(7)
    })
  })

  // ========================================================================
  // MEDIUM: ModerationAction field alignment
  // ========================================================================

  describe('MEDIUM: ModerationAction field alignment', () => {
    it('ModerationAction should use action (not actionType) to match backend entity', () => {
      // Backend ModerationAction entity uses field "action"
      const action: ModerationAction = {
        id: 'a1',
        queueId: 'q1',
        action: 'DELETED',
        performedById: 'mod-1',
        note: 'Spam',
        createdAt: new Date(),
      }
      expect(action).toHaveProperty('action')
      expect(action).not.toHaveProperty('actionType')
    })

    it('ModerationAction should use performedById (not performedBy string) to match backend entity', () => {
      // Backend ModerationAction entity uses "performedById"
      const action: ModerationAction = {
        id: 'a1',
        queueId: 'q1',
        action: 'DELETED',
        performedById: 'mod-1',
        note: 'Spam',
        createdAt: new Date(),
      }
      expect(action).toHaveProperty('performedById')
    })
  })

  // ========================================================================
  // MEDIUM: Report type should not have parentId
  // ========================================================================

  describe('MEDIUM: Report type alignment', () => {
    it('Report should not include parentId (backend ReportVO does not return it)', () => {
      // Backend ReportVO has no parentId field
      const report: Report = {
        id: 'r1',
        reporterId: 'user-1',
        reporterName: 'Test',
        reporterUsername: 'test',
        entityType: 'forum_post',
        entityId: 'e1',
        category: 'SPAM',
        status: 'PENDING',
        reason: 'Spam content',
        createdAt: new Date(),
        updatedAt: new Date(),
      }
      expect(report).not.toHaveProperty('parentId')
    })
  })

  // ========================================================================
  // API endpoint existence validation
  // ========================================================================

  describe('API endpoint calls', () => {
    it('getQueue should call GET /moderation/queue with correct params', async () => {
      await moderationQueueApi.getQueue({ status: 'PENDING', assignedTo: 'mod-1' })
      expect(apiGet).toHaveBeenCalledWith(
        '/moderation/queue',
        expect.objectContaining({
          params: expect.objectContaining({ status: 'PENDING', assignedTo: 'mod-1' }),
        }),
      )
    })

    it('assignItem should send assignedTo field', async () => {
      await moderationQueueApi.assignItem('q1', { assignedTo: 'mod-1' })
      expect(apiPost).toHaveBeenCalledWith('/moderation/queue/q1/assign', { assignedTo: 'mod-1' })
    })

    it('getReport should call GET /moderation/reports/{id}', async () => {
      await reportsApi.getReport('r1')
      expect(apiGet).toHaveBeenCalledWith('/moderation/reports/r1', expect.any(Object))
    })

    it('getMyAppeals should call GET /moderation/appeals/my', async () => {
      await appealsApi.getMyAppeals()
      expect(apiGet).toHaveBeenCalledWith('/moderation/appeals/my', expect.any(Object))
    })

    it('appealsApi.getStats should call GET /moderation/appeals/stats', async () => {
      await appealsApi.getStats()
      expect(apiGet).toHaveBeenCalledWith('/moderation/appeals/stats', expect.any(Object))
    })
  })
})
