import { describe, expect, it } from 'vitest'
import zhCN from '../locales/zh-CN'
import { flattenObject } from '@/shared/i18n-completeness/src'

/**
 * Known API field name mapping keys (snake_case, must be preserved).
 * These keys in table.columnNames match backend API response field names
 * and are dynamically referenced via DataTable.vue's resolveColumnName().
 */
// table.columnNames.* keys were normalised to camelCase at the DataTable seam
// (resolveColumnName/toCamelCase, C8), so no snake_case column-id keys remain
// and none need to be allow-listed here.
const API_MAPPED_KEYS = new Set([
  // moderation.entityTypes — match backend enum values
  'moderation.entityTypes.forum_post',
  'moderation.entityTypes.forum_comment',
  'moderation.entityTypes.solution_comment',
  // problems.import — match backend strategy names
  'problems.import.strategyDescriptions.create_new',
])

/** Detect snake_case leaf key (e.g. `joined_at`, `ip_address`) */
function isSnakeCase(key: string): boolean {
  return /^[a-z][a-z0-9]*_[a-z0-9_]+$/.test(key)
}

describe('i18n naming conventions', () => {
  it('should not have snake_case keys except API-mapped ones', () => {
    const allKeys = flattenObject(zhCN as unknown as Record<string, unknown>)
    const violations = allKeys.filter((key) => {
      const leaf = key.split('.').pop()!
      return isSnakeCase(leaf) && !API_MAPPED_KEYS.has(key)
    })
    expect(violations, `Found unexpected snake_case keys: ${violations.join(', ')}`).toEqual([])
  })
})
