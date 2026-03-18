/**
 * i18n Translation Completeness Checker
 *
 * Run this script to check for missing translations between locales.
 * Usage: npx tsx src/i18n/check.ts
 */

import zhCN from './locales/zh-CN'
import enUS from './locales/en-US'

type TranslationObject = Record<string, unknown>

/**
 * Flatten nested object to dot-notation keys
 */
function flattenObject(obj: TranslationObject, prefix = ''): string[] {
  const keys: string[] = []

  for (const [key, value] of Object.entries(obj)) {
    const fullKey = prefix ? `${prefix}.${key}` : key

    if (value && typeof value === 'object' && !Array.isArray(value)) {
      keys.push(...flattenObject(value as TranslationObject, fullKey))
    } else {
      keys.push(fullKey)
    }
  }

  return keys
}

/**
 * Check translation completeness
 */
function checkTranslations() {
  const zhKeys = new Set(flattenObject(zhCN as unknown as TranslationObject))
  const enKeys = new Set(flattenObject(enUS as unknown as TranslationObject))

  const missingInEn: string[] = []
  const missingInZh: string[] = []

  // Check for keys in zh-CN but not in en-US
  for (const key of zhKeys) {
    if (!enKeys.has(key)) {
      missingInEn.push(key)
    }
  }

  // Check for keys in en-US but not in zh-CN
  for (const key of enKeys) {
    if (!zhKeys.has(key)) {
      missingInZh.push(key)
    }
  }

  console.log('\n=== i18n Translation Completeness Check ===\n')

  console.log(`Total keys in zh-CN: ${zhKeys.size}`)
  console.log(`Total keys in en-US: ${enKeys.size}`)

  if (missingInEn.length > 0) {
    console.log(`\n❌ Missing in en-US (${missingInEn.length} keys):`)
    missingInEn.slice(0, 20).forEach((key) => console.log(`   - ${key}`))
    if (missingInEn.length > 20) {
      console.log(`   ... and ${missingInEn.length - 20} more`)
    }
  }

  if (missingInZh.length > 0) {
    console.log(`\n❌ Missing in zh-CN (${missingInZh.length} keys):`)
    missingInZh.slice(0, 20).forEach((key) => console.log(`   - ${key}`))
    if (missingInZh.length > 20) {
      console.log(`   ... and ${missingInZh.length - 20} more`)
    }
  }

  if (missingInEn.length === 0 && missingInZh.length === 0) {
    console.log('\n✅ All translations are complete!')
  }

  console.log('\n')

  return {
    zhKeysCount: zhKeys.size,
    enKeysCount: enKeys.size,
    missingInEn,
    missingInZh,
    isComplete: missingInEn.length === 0 && missingInZh.length === 0,
  }
}

// Run the check
checkTranslations()

export { checkTranslations }
