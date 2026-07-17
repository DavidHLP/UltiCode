import { describe, it, expect } from 'vitest'
import { mapFlagsToCaseScope, mapCaseScopeToFlags, type CaseScope } from '../test-cases'

describe('test-cases case scope mapping', () => {
  describe('mapFlagsToCaseScope', () => {
    it('returns HIDDEN when isHidden is true regardless of isSample', () => {
      expect(mapFlagsToCaseScope(false, true)).toBe('HIDDEN')
      expect(mapFlagsToCaseScope(undefined, true)).toBe('HIDDEN')
      expect(mapFlagsToCaseScope(null, true)).toBe('HIDDEN')
    })

    it('returns SAMPLE when isHidden is false (or null/undefined) and isSample is true', () => {
      expect(mapFlagsToCaseScope(true, false)).toBe('SAMPLE')
      expect(mapFlagsToCaseScope(true, undefined)).toBe('SAMPLE')
      expect(mapFlagsToCaseScope(true, null)).toBe('SAMPLE')
    })

    it('treats legacy null/undefined pairs as SAMPLE for backward compatibility', () => {
      expect(mapFlagsToCaseScope(undefined, undefined)).toBe('SAMPLE')
      expect(mapFlagsToCaseScope(null, null)).toBe('SAMPLE')
      expect(mapFlagsToCaseScope(false, undefined)).toBe('SAMPLE')
    })
  })

  describe('mapCaseScopeToFlags', () => {
    it('emits HIDDEN as (isSample=false, isHidden=true)', () => {
      expect(mapCaseScopeToFlags('HIDDEN')).toEqual({
        isSample: false,
        isHidden: true,
      })
    })

    it('emits SAMPLE as (isSample=true, isHidden=false)', () => {
      expect(mapCaseScopeToFlags('SAMPLE')).toEqual({
        isSample: true,
        isHidden: false,
      })
    })
  })

  describe('round-trip idempotency', () => {
    it.each<CaseScope>(['SAMPLE', 'HIDDEN'])('%s → flags → scope is stable', (scope) => {
      const flags = mapCaseScopeToFlags(scope)
      const roundTrip = mapFlagsToCaseScope(flags.isSample, flags.isHidden)
      expect(roundTrip).toBe(scope)
    })

    it('every canonical flag pair maps back to a known scope (XOR holds)', () => {
      const combos: Array<[boolean, boolean]> = [
        [true, false],
        [false, true],
      ]
      for (const [isSample, isHidden] of combos) {
        const scope = mapFlagsToCaseScope(isSample, isHidden)
        const back = mapCaseScopeToFlags(scope)
        expect(back).toEqual({ isSample, isHidden })
      }
    })
  })
})
