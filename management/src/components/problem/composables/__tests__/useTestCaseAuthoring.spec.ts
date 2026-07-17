import { describe, expect, it } from 'vitest'
import {
  isValidCaseScopeFlags,
  mapCaseScopeToFlags,
  mapFlagsToCaseScope,
} from '../../../../api/admin/test-cases'

/**
 * Contract test for the deep Problem test-case authoring seam.
 *
 * <p>The backend enforces {@code isSample XOR isHidden} on every
 * {@code CreateTestCaseDto}. The authoring surface must never produce
 * a dto that violates that invariant; these tests pin the local
 * canonical truth so the bulk-import parser, toggle handlers, and
 * single-create form cannot drift apart.
 */
describe('test-case authoring — CaseScope invariant', () => {
  it('mapFlagsToCaseScope resolves isHidden=true to HIDDEN', () => {
    expect(mapFlagsToCaseScope(false, true)).toBe('HIDDEN')
  })

  it('mapFlagsToCaseScope resolves isHidden=false to SAMPLE', () => {
    expect(mapFlagsToCaseScope(true, false)).toBe('SAMPLE')
  })

  it('mapCaseScopeToFlags produces a valid flag pair for SAMPLE', () => {
    expect(mapCaseScopeToFlags('SAMPLE')).toEqual({
      isSample: true,
      isHidden: false,
    })
  })

  it('mapCaseScopeToFlags produces a valid flag pair for HIDDEN', () => {
    expect(mapCaseScopeToFlags('HIDDEN')).toEqual({
      isSample: false,
      isHidden: true,
    })
  })

  it('every CaseScope roundtrips through caseScopeToFlags + mapFlagsToCaseScope', () => {
    for (const scope of ['SAMPLE', 'HIDDEN'] as const) {
      const flags = mapCaseScopeToFlags(scope)
      expect(mapFlagsToCaseScope(flags.isSample, flags.isHidden)).toBe(scope)
    }
  })

  it('isValidCaseScopeFlags accepts the two well-formed pairs', () => {
    expect(isValidCaseScopeFlags({ isSample: true, isHidden: false })).toBe(true)
    expect(isValidCaseScopeFlags({ isSample: false, isHidden: true })).toBe(true)
  })

  it('isValidCaseScopeFlags rejects both-true and both-false', () => {
    expect(isValidCaseScopeFlags({ isSample: true, isHidden: true })).toBe(false)
    expect(isValidCaseScopeFlags({ isSample: false, isHidden: false })).toBe(false)
  })

  it('every flag pair produced by mapCaseScopeToFlags is valid', () => {
    for (const scope of ['SAMPLE', 'HIDDEN'] as const) {
      expect(isValidCaseScopeFlags(mapCaseScopeToFlags(scope))).toBe(true)
    }
  })
})
