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
})
