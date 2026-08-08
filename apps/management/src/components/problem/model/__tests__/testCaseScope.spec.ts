import { describe, expect, it } from 'vitest'
import {
  mapCaseScopeToFlags,
  mapFlagsToCaseScope,
  type CaseScope,
} from '../testCaseScope'

describe('Problem test-case CaseScope', () => {
  it('reads explicit HIDDEN and canonical SAMPLE rows', () => {
    expect(mapFlagsToCaseScope(false, true)).toBe('HIDDEN')
    expect(mapFlagsToCaseScope(true, false)).toBe('SAMPLE')
  })

  it('treats legacy null flag rows as SAMPLE', () => {
    expect(mapFlagsToCaseScope(null, null)).toBe('SAMPLE')
    expect(mapFlagsToCaseScope(undefined, undefined)).toBe('SAMPLE')
  })

  it.each<CaseScope>(['SAMPLE', 'HIDDEN'])(
    '%s round-trips through canonical XOR flags',
    (scope) => {
      const flags = mapCaseScopeToFlags(scope)
      expect(flags.isSample).not.toBe(flags.isHidden)
      expect(mapFlagsToCaseScope(flags.isSample, flags.isHidden)).toBe(scope)
    },
  )
})
