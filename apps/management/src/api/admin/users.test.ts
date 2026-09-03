import { describe, expect, it } from 'vitest'
import { canWriteUserPermissions } from './users'

describe('user detail degradation guard', () => {
  it('allows permission writes only for a proven OK section', () => {
    expect(canWriteUserPermissions({ permissionsStatus: 'OK' })).toBe(true)
    expect(canWriteUserPermissions({ permissionsStatus: 'PARTIAL' })).toBe(false)
    expect(canWriteUserPermissions({ permissionsStatus: 'UNAVAILABLE' })).toBe(false)
    expect(canWriteUserPermissions(null)).toBe(false)
    expect(canWriteUserPermissions(undefined)).toBe(false)
  })
})
