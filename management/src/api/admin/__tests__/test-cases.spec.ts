import { describe, it, expect } from 'vitest'
import { mapFlagsToCaseScope, mapCaseScopeToFlags, type CaseScope } from '../test-cases'

describe('test-cases case scope mapping', () => {
  describe('mapFlagsToCaseScope', () => {
    it('returns HIDDEN when is_hidden is true regardless of is_sample', () => {
      expect(mapFlagsToCaseScope(false, true)).toBe('HIDDEN')
      expect(mapFlagsToCaseScope(undefined, true)).toBe('HIDDEN')
      expect(mapFlagsToCaseScope(null, true)).toBe('HIDDEN')
    })

    it('returns SAMPLE when is_hidden is false (or null/undefined) and is_sample is true', () => {
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
        is_sample: false,
        is_hidden: true,
      })
    })

    it('emits SAMPLE as (isSample=true, isHidden=false)', () => {
      expect(mapCaseScopeToFlags('SAMPLE')).toEqual({
        is_sample: true,
        is_hidden: false,
      })
    })
  })

  describe('round-trip idempotency', () => {
    it.each<CaseScope>(['SAMPLE', 'HIDDEN'])('%s → flags → scope is stable', (scope) => {
      const flags = mapCaseScopeToFlags(scope)
      const roundTrip = mapFlagsToCaseScope(flags.is_sample, flags.is_hidden)
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
        expect(back).toEqual({ is_sample: isSample, is_hidden: isHidden })
      }
    })
  })
})
