import { describe, it, expect } from 'vitest'
import type { DFormVerdict } from '@ulticode/sandbox-types'
import {
  VERDICT_TO_STATUS_KEY,
  VERDICT_COLOR_MAP,
  getVerdictColor,
  verdictToStatusKey,
  SUBMISSION_STATUS_COLOR_MAP,
} from './index'

/**
 * Contract test — verifies the submission-status seam between
 * badge-config (UPPERCASE keys) and sandbox-types (Title Case verdicts).
 *
 * If this test fails, someone added or renamed a verdict without updating
 * the color mapping. That is exactly the silent drift this module prevents.
 */
describe('submission-status contract', () => {
  // Exhaustive list of every DFormVerdict — if sandbox-types adds a new
  // verdict, this array must be updated, and the tests below will catch it.
  const ALL_VERDICTS: DFormVerdict[] = [
    'Accepted',
    'Wrong Answer',
    'Time Limit Exceeded',
    'Memory Limit Exceeded',
    'Output Limit Exceeded',
    'Runtime Error',
    'Compile Error',
    'Presentation Error',
    'System Error',
    'Sandbox Error',
    'Judging',
    'Pending',
  ]

  describe('VERDICT_TO_STATUS_KEY — every verdict has a status key', () => {
    it.each(ALL_VERDICTS)('should map %s to a non-empty status key', (verdict) => {
      const key = VERDICT_TO_STATUS_KEY[verdict]
      expect(key).toBeDefined()
      expect(key.length).toBeGreaterThan(0)
      // Status keys must be UPPERCASE_UNDERSCORE
      expect(key).toMatch(/^[A-Z][A-Z_]*$/)
    })
  })

  describe('VERDICT_COLOR_MAP — every verdict has a color', () => {
    it.each(ALL_VERDICTS)('should have a SemanticColor for %s', (verdict) => {
      const color = VERDICT_COLOR_MAP[verdict]
      expect(color).toBeDefined()
      expect([
        'success',
        'warning',
        'error',
        'info',
        'purple',
        'electric',
        'neutral',
      ]).toContain(color)
    })
  })

  describe('getVerdictColor', () => {
    it('should return success for Accepted', () => {
      expect(getVerdictColor('Accepted')).toBe('success')
    })

    it('should return error for all error-class verdicts', () => {
      expect(getVerdictColor('Wrong Answer')).toBe('error')
      expect(getVerdictColor('Runtime Error')).toBe('error')
      expect(getVerdictColor('Compile Error')).toBe('error')
      expect(getVerdictColor('Time Limit Exceeded')).toBe('error')
      expect(getVerdictColor('Memory Limit Exceeded')).toBe('error')
      expect(getVerdictColor('Output Limit Exceeded')).toBe('error')
    })

    it('should return warning for pending-class verdicts', () => {
      expect(getVerdictColor('Judging')).toBe('warning')
      expect(getVerdictColor('Pending')).toBe('warning')
    })

    it('should return neutral for system-class verdicts', () => {
      expect(getVerdictColor('System Error')).toBe('neutral')
      expect(getVerdictColor('Sandbox Error')).toBe('neutral')
    })
  })

  describe('verdictToStatusKey', () => {
    it('should convert Title Case to UPPERCASE_UNDERSCORE', () => {
      expect(verdictToStatusKey('Accepted')).toBe('ACCEPTED')
      expect(verdictToStatusKey('Wrong Answer')).toBe('WRONG_ANSWER')
      expect(verdictToStatusKey('Time Limit Exceeded')).toBe(
        'TIME_LIMIT_EXCEEDED',
      )
    })
  })

  describe('cross-package consistency with SUBMISSION_STATUS_COLOR_MAP', () => {
    /**
     * For every verdict whose status key EXISTS in the original
     * SUBMISSION_STATUS_COLOR_MAP, the color must match.
     *
     * Verdicts that were newly added (Output Limit Exceeded, Presentation
     * Error, System Error, Sandbox Error) have no entry in the original map
     * — that gap is what this module fills.
     */
    it('should agree with SUBMISSION_STATUS_COLOR_MAP on shared keys', () => {
      for (const verdict of ALL_VERDICTS) {
        const statusKey = VERDICT_TO_STATUS_KEY[verdict]
        const originalColor = SUBMISSION_STATUS_COLOR_MAP[statusKey]

        if (originalColor !== undefined) {
          // The original map has this key — colors must match
          expect(VERDICT_COLOR_MAP[verdict]).toBe(originalColor)
        }
      }
    })

    it('should cover all keys in SUBMISSION_STATUS_COLOR_MAP', () => {
      // Every key in the original color map should map back to a verdict
      const statusKeys = Object.values(VERDICT_TO_STATUS_KEY)
      for (const key of Object.keys(SUBMISSION_STATUS_COLOR_MAP)) {
        expect(statusKeys).toContain(key)
      }
    })
  })

  describe('no verdict is left behind', () => {
    it('VERDICT_TO_STATUS_KEY should have exactly 12 entries', () => {
      expect(Object.keys(VERDICT_TO_STATUS_KEY)).toHaveLength(12)
    })

    it('VERDICT_COLOR_MAP should have exactly 12 entries', () => {
      expect(Object.keys(VERDICT_COLOR_MAP)).toHaveLength(12)
    })

    it('all status keys should be unique', () => {
      const keys = Object.values(VERDICT_TO_STATUS_KEY)
      expect(new Set(keys).size).toBe(keys.length)
    })
  })
})
