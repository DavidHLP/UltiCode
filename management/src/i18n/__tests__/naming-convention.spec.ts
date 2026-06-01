import { describe, expect, it } from 'vitest'
import zhCN from '../locales/zh-CN'

/**
 * Known API field name mapping keys (snake_case, must be preserved).
 * These keys in table.columnNames match backend API response field names
 * and are dynamically referenced via DataTable.vue's resolveColumnName().
 */
const API_MAPPED_KEYS = new Set([
  // table.columnNames — match backend API response field names
  'table.columnNames.joined_at',
  'table.columnNames.last_login_at',
  'table.columnNames.is_active',
  'table.columnNames.is_banned',
  'table.columnNames.start_time',
  'table.columnNames.end_time',
  'table.columnNames.ip_address',
  'table.columnNames.entity_type',
  'table.columnNames.primary_category',
  'table.columnNames.report_count',
  'table.columnNames.assigned_to',
  'table.columnNames.created_at',
  'table.columnNames.submitted_at',
  'table.columnNames.reviewed_by',
  'table.columnNames.reviewed_at',
  'table.columnNames.usage_count',
  'table.columnNames.code_length',
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

/** Flatten nested translation object to dot-notation key list */
function flattenObject(obj: Record<string, unknown>, prefix = ''): string[] {
  const keys: string[] = []
  for (const [key, value] of Object.entries(obj)) {
    const fullKey = prefix ? `${prefix}.${key}` : key
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      keys.push(...flattenObject(value as Record<string, unknown>, fullKey))
    } else {
      keys.push(fullKey)
    }
  }
  return keys
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
