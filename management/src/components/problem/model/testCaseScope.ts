/**
 * Canonical author intent for a Problem test case.
 *
 * SAMPLE cases may be shown to users; HIDDEN cases are admin-only judge data.
 * Every write maps the scope to an explicit XOR flag pair for the backend.
 */
export type CaseScope = 'SAMPLE' | 'HIDDEN'

export interface CaseScopeFlags {
  isSample: boolean
  isHidden: boolean
}

/**
 * Read legacy rows defensively. Explicitly hidden always wins; older null flag
 * pairs remain SAMPLE so existing rows stay editable.
 */
export function mapFlagsToCaseScope(
  _isSample: boolean | null | undefined,
  isHidden: boolean | null | undefined,
): CaseScope {
  return isHidden === true ? 'HIDDEN' : 'SAMPLE'
}

/** Convert author intent to the only two valid persisted flag pairs. */
export function mapCaseScopeToFlags(scope: CaseScope): CaseScopeFlags {
  return scope === 'HIDDEN'
    ? { isSample: false, isHidden: true }
    : { isSample: true, isHidden: false }
}
