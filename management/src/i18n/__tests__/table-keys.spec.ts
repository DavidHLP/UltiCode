import { describe, expect, it } from 'vitest'
import zhTable from '../locales/zh-CN/modules/table'
import enTable from '../locales/en-US/modules/table'

describe('table i18n key consistency', () => {
  it('zh-CN and en-US columnNames must have the same keys', () => {
    const zhKeys = Object.keys(zhTable.columnNames).sort()
    const enKeys = Object.keys(enTable.columnNames).sort()

    expect(enKeys).toEqual(zhKeys)
  })

  it('zh-CN columnNames should not have missing translations', () => {
    const keys = Object.keys(zhTable.columnNames)
    for (const key of keys) {
      const value = zhTable.columnNames[key as keyof typeof zhTable.columnNames]
      expect(value).not.toBe('')
      expect(value).not.toBeUndefined()
    }
  })

  it('en-US columnNames should not have missing translations', () => {
    const keys = Object.keys(enTable.columnNames)
    for (const key of keys) {
      const value = enTable.columnNames[key as keyof typeof enTable.columnNames]
      expect(value).not.toBe('')
      expect(value).not.toBeUndefined()
    }
  })

  // C8: column ids are normalised to camelCase at the DataTable seam
  // (resolveColumnName/toCamelCase), so every columnNames key MUST be
  // camelCase — a snake_case column id is routed to the camelCase key, not
  // duplicated. This guard prevents the double-key regression.
  it.each([
    ['zh-CN', zhTable],
    ['en-US', enTable],
  ])('%s columnNames keys must be camelCase (no snake_case)', (_name, table) => {
    const keys = Object.keys(table.columnNames)
    const camelCase = /^[a-z][a-zA-Z0-9]*$/
    const violations = keys.filter((key) => !camelCase.test(key))
    expect(
      violations,
      `Found non-camelCase columnNames keys (snake_case must be normalised at the seam): ${violations.join(', ')}`,
    ).toEqual([])
  })
})
