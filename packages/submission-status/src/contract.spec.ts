import { describe, it, expect } from 'vitest'
import type { DFormVerdict } from '@ulticode/sandbox-types'
import {
  VERDICT_TO_STATUS_KEY,
  VERDICT_COLOR_MAP,
  VERDICT_STATE,
  VERDICT_IS_INFRA,
  VERDICT_ICON_KEY,
  VERDICT_TO_LABEL_I18N_KEY,
  getVerdictColor,
  getVerdictIconKey,
  verdictToStatusKey,
  normalizeStatusKey,
  getStatusColor,
  getStatusIconKey,
  getStatusState,
  getStatusLabelI18nKey,
  isFinal,
  isPending,
  isInfra,
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

  describe('VERDICT_STATE — every verdict is settled or pending', () => {
    it.each(ALL_VERDICTS)('should classify %s into a stable state', (verdict) => {
      expect(['final', 'pending']).toContain(VERDICT_STATE[verdict])
    })

    it('should mark Accepted and all failure verdicts as final (settled)', () => {
      expect(VERDICT_STATE['Accepted']).toBe('final')
      expect(VERDICT_STATE['Wrong Answer']).toBe('final')
      expect(VERDICT_STATE['Time Limit Exceeded']).toBe('final')
      expect(VERDICT_STATE['Memory Limit Exceeded']).toBe('final')
      expect(VERDICT_STATE['Output Limit Exceeded']).toBe('final')
      expect(VERDICT_STATE['Runtime Error']).toBe('final')
      expect(VERDICT_STATE['Compile Error']).toBe('final')
      expect(VERDICT_STATE['Presentation Error']).toBe('final')
      // System/Sandbox are still settled — they will not change without resubmission.
      expect(VERDICT_STATE['System Error']).toBe('final')
      expect(VERDICT_STATE['Sandbox Error']).toBe('final')
    })

    it('should mark Judging and Pending as pending', () => {
      expect(VERDICT_STATE['Judging']).toBe('pending')
      expect(VERDICT_STATE['Pending']).toBe('pending')
    })
  })

  describe('VERDICT_IS_INFRA — infrastructure classification is on its own axis', () => {
    it.each(ALL_VERDICTS)('should have an infra flag for %s', (verdict) => {
      expect(typeof VERDICT_IS_INFRA[verdict]).toBe('boolean')
    })

    it('should mark System/Sandbox Error as infra (not user-attributable)', () => {
      expect(VERDICT_IS_INFRA['System Error']).toBe(true)
      expect(VERDICT_IS_INFRA['Sandbox Error']).toBe(true)
    })

    it('should mark user-attributable verdicts as non-infra', () => {
      expect(VERDICT_IS_INFRA['Accepted']).toBe(false)
      expect(VERDICT_IS_INFRA['Wrong Answer']).toBe(false)
      expect(VERDICT_IS_INFRA['Time Limit Exceeded']).toBe(false)
      expect(VERDICT_IS_INFRA['Runtime Error']).toBe(false)
      expect(VERDICT_IS_INFRA['Compile Error']).toBe(false)
      expect(VERDICT_IS_INFRA['Presentation Error']).toBe(false)
      expect(VERDICT_IS_INFRA['Judging']).toBe(false)
      expect(VERDICT_IS_INFRA['Pending']).toBe(false)
    })

    it('state and responsibility are independent axes', () => {
      // Sandbox Error is both final (settled) AND infra (not user's fault).
      expect(VERDICT_STATE['Sandbox Error']).toBe('final')
      expect(VERDICT_IS_INFRA['Sandbox Error']).toBe(true)
    })
  })

  describe('VERDICT_ICON_KEY — every verdict has a stable icon-key', () => {
    it.each(ALL_VERDICTS)('should map %s to a non-empty icon-key', (verdict) => {
      const key = VERDICT_ICON_KEY[verdict]
      expect(key).toBeDefined()
      expect(['success', 'error', 'warning', 'pending', 'neutral']).toContain(key)
    })

    it('should map Accepted to success', () => {
      expect(VERDICT_ICON_KEY['Accepted']).toBe('success')
    })

    it('should map user-attributable failures to error', () => {
      expect(VERDICT_ICON_KEY['Wrong Answer']).toBe('error')
      expect(VERDICT_ICON_KEY['Time Limit Exceeded']).toBe('error')
      expect(VERDICT_ICON_KEY['Memory Limit Exceeded']).toBe('error')
      expect(VERDICT_ICON_KEY['Output Limit Exceeded']).toBe('error')
      expect(VERDICT_ICON_KEY['Runtime Error']).toBe('error')
      expect(VERDICT_ICON_KEY['Compile Error']).toBe('error')
    })

    it('should map Pending/Judging to pending (not error)', () => {
      expect(VERDICT_ICON_KEY['Judging']).toBe('pending')
      expect(VERDICT_ICON_KEY['Pending']).toBe('pending')
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

  describe('getVerdictIconKey', () => {
    it('should mirror VERDICT_ICON_KEY for known verdicts', () => {
      expect(getVerdictIconKey('Accepted')).toBe('success')
      expect(getVerdictIconKey('Wrong Answer')).toBe('error')
      expect(getVerdictIconKey('Presentation Error')).toBe('warning')
      expect(getVerdictIconKey('Judging')).toBe('pending')
      expect(getVerdictIconKey('Sandbox Error')).toBe('neutral')
    })
  })

  describe('isFinal / isPending / isInfra', () => {
    it('isFinal returns true for every settled verdict (incl. infra failures)', () => {
      expect(isFinal('Accepted')).toBe(true)
      expect(isFinal('Wrong Answer')).toBe(true)
      expect(isFinal('Compile Error')).toBe(true)
      // Infrastructure verdicts are still final — they won't change.
      expect(isFinal('System Error')).toBe(true)
      expect(isFinal('Sandbox Error')).toBe(true)
    })

    it('isFinal returns false for pending verdicts', () => {
      expect(isFinal('Judging')).toBe(false)
      expect(isFinal('Pending')).toBe(false)
    })

    it('isPending returns true only for Judging/Pending', () => {
      expect(isPending('Judging')).toBe(true)
      expect(isPending('Pending')).toBe(true)
      expect(isPending('Accepted')).toBe(false)
      expect(isPending('Wrong Answer')).toBe(false)
      expect(isPending('Sandbox Error')).toBe(false)
    })

    it('isInfra returns true only for System/Sandbox Error', () => {
      expect(isInfra('System Error')).toBe(true)
      expect(isInfra('Sandbox Error')).toBe(true)
      expect(isInfra('Wrong Answer')).toBe(false)
      expect(isInfra('Accepted')).toBe(false)
      expect(isInfra('Judging')).toBe(false)
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

  describe('normalizeStatusKey', () => {
    it('should normalize mixed casings to UPPERCASE_UNDERSCORE', () => {
      expect(normalizeStatusKey('Accepted')).toBe('ACCEPTED')
      expect(normalizeStatusKey('accepted')).toBe('ACCEPTED')
      expect(normalizeStatusKey('Wrong Answer')).toBe('WRONG_ANSWER')
      expect(normalizeStatusKey('WRONG ANSWER')).toBe('WRONG_ANSWER')
      expect(normalizeStatusKey('wrong_answer')).toBe('WRONG_ANSWER')
      expect(normalizeStatusKey('Time Limit Exceeded')).toBe('TIME_LIMIT_EXCEEDED')
    })
  })

  describe('getStatusColor', () => {
    it('should accept any casing and return the verdict color', () => {
      expect(getStatusColor('Accepted')).toBe('success')
      expect(getStatusColor('ACCEPTED')).toBe('success')
      expect(getStatusColor('Wrong Answer')).toBe('error')
      expect(getStatusColor('wrong_answer')).toBe('error')
      expect(getStatusColor('Runtime Error')).toBe('error')
      expect(getStatusColor('Judging')).toBe('warning')
      expect(getStatusColor('Pending')).toBe('warning')
    })

    it('should cover verdicts the legacy map missed', () => {
      // These four were absent from the old SUBMISSION_STATUS_COLOR_MAP and
      // previously fell back to 'neutral'; they now resolve to a real color.
      expect(getStatusColor('Output Limit Exceeded')).toBe('error')
      expect(getStatusColor('Presentation Error')).toBe('warning')
      // System/Sandbox Error are intentionally 'neutral' per VERDICT_COLOR_MAP.
      expect(getStatusColor('System Error')).toBe('neutral')
      expect(getStatusColor('Sandbox Error')).toBe('neutral')
    })

    it('should fall back to neutral for unknown statuses', () => {
      expect(getStatusColor('UNKNOWN_STATUS')).toBe('neutral')
      expect(getStatusColor('')).toBe('neutral')
    })
  })

  describe('getStatusIconKey', () => {
    it('should accept any casing and return the icon key', () => {
      expect(getStatusIconKey('Accepted')).toBe('success')
      expect(getStatusIconKey('accepted')).toBe('success')
      expect(getStatusIconKey('Wrong Answer')).toBe('error')
      expect(getStatusIconKey('Pending')).toBe('pending')
      expect(getStatusIconKey('Judging')).toBe('pending')
      expect(getStatusIconKey('Sandbox Error')).toBe('neutral')
    })

    it('should fall back to neutral for unknown statuses', () => {
      expect(getStatusIconKey('UNKNOWN_STATUS')).toBe('neutral')
      expect(getStatusIconKey('')).toBe('neutral')
    })
  })

  describe('getStatusState', () => {
    it('should accept any casing and return the verdict state', () => {
      expect(getStatusState('Accepted')).toBe('final')
      expect(getStatusState('Pending')).toBe('pending')
      expect(getStatusState('Sandbox Error')).toBe('final')
    })

    it('should conservatively fall back to final for unknown statuses', () => {
      // Final is the conservative default: a settled verdict, no pending spinner.
      expect(getStatusState('UNKNOWN_STATUS')).toBe('final')
      expect(getStatusState('')).toBe('final')
    })
  })

  describe('no verdict is left behind', () => {
    it('VERDICT_TO_STATUS_KEY should have exactly 12 entries', () => {
      expect(Object.keys(VERDICT_TO_STATUS_KEY)).toHaveLength(12)
    })

    it('VERDICT_COLOR_MAP should have exactly 12 entries', () => {
      expect(Object.keys(VERDICT_COLOR_MAP)).toHaveLength(12)
    })

    it('VERDICT_STATE should have exactly 12 entries', () => {
      expect(Object.keys(VERDICT_STATE)).toHaveLength(12)
    })

    it('VERDICT_IS_INFRA should have exactly 12 entries', () => {
      expect(Object.keys(VERDICT_IS_INFRA)).toHaveLength(12)
    })

    it('VERDICT_ICON_KEY should have exactly 12 entries', () => {
      expect(Object.keys(VERDICT_ICON_KEY)).toHaveLength(12)
    })

    it('all status keys should be unique', () => {
      const keys = Object.values(VERDICT_TO_STATUS_KEY)
      expect(new Set(keys).size).toBe(keys.length)
    })

    it('every verdict has a status-key, color, state, infra flag, and icon-key', () => {
      // Regression guard: a future verdict must not be silently classified as
      // 'final' / 'neutral' just because we forgot to wire it everywhere.
      for (const v of ALL_VERDICTS) {
        expect(VERDICT_TO_STATUS_KEY[v]).toBeDefined()
        expect(VERDICT_COLOR_MAP[v]).toBeDefined()
        expect(VERDICT_STATE[v]).toBeDefined()
        expect(VERDICT_IS_INFRA[v]).toBeDefined()
        expect(VERDICT_ICON_KEY[v]).toBeDefined()
      }
    })
  })

  describe('VERDICT_TO_LABEL_I18N_KEY — every verdict has an i18n label key', () => {
    it.each(ALL_VERDICTS)('should map %s to a non-empty i18n key path', (verdict) => {
      const key = VERDICT_TO_LABEL_I18N_KEY[verdict]
      expect(key).toBeDefined()
      expect(key.length).toBeGreaterThan(0)
      // Full key path under the shared submission.status.* namespace.
      expect(key).toMatch(/^submission\.status\.[a-zA-Z]+$/)
    })

    it('should map Sandbox Error (the historical regression) to a key', () => {
      expect(VERDICT_TO_LABEL_I18N_KEY['Sandbox Error']).toBe(
        'submission.status.sandboxError',
      )
    })

    it('VERDICT_TO_LABEL_I18N_KEY should have exactly 12 entries', () => {
      expect(Object.keys(VERDICT_TO_LABEL_I18N_KEY)).toHaveLength(12)
    })

    it('all label keys should be unique', () => {
      const keys = Object.values(VERDICT_TO_LABEL_I18N_KEY)
      expect(new Set(keys).size).toBe(keys.length)
    })
  })

  describe('getStatusLabelI18nKey', () => {
    it('should accept any casing and return the label key', () => {
      expect(getStatusLabelI18nKey('Accepted')).toBe('submission.status.accepted')
      expect(getStatusLabelI18nKey('accepted')).toBe('submission.status.accepted')
      expect(getStatusLabelI18nKey('Sandbox Error')).toBe(
        'submission.status.sandboxError',
      )
      expect(getStatusLabelI18nKey('SANDBOX_ERROR')).toBe(
        'submission.status.sandboxError',
      )
      expect(getStatusLabelI18nKey('wrong_answer')).toBe(
        'submission.status.wrongAnswer',
      )
    })

    it('should return null for unknown statuses', () => {
      expect(getStatusLabelI18nKey('UNKNOWN_STATUS')).toBeNull()
      expect(getStatusLabelI18nKey('')).toBeNull()
    })
  })

  describe('every supported status resolves to label key, color, icon-key, and state', () => {
    // Regression guard: a future verdict must not silently render a raw
    // English label, a colorless badge, an untyped icon, or an unclassified
    // state. If this fails, add the verdict to every per-verdict map.
    it.each(ALL_VERDICTS)(
      'should resolve %s to a complete presentation bundle',
      (verdict) => {
        expect(getStatusLabelI18nKey(verdict)).not.toBeNull()
        expect(getStatusLabelI18nKey(verdict)!.length).toBeGreaterThan(0)
        expect([
          'success',
          'warning',
          'error',
          'info',
          'purple',
          'electric',
          'neutral',
        ]).toContain(getStatusColor(verdict))
        expect(['success', 'error', 'warning', 'pending', 'neutral']).toContain(
          getStatusIconKey(verdict),
        )
        expect(['final', 'pending']).toContain(getStatusState(verdict))
      },
    )
  })
})
