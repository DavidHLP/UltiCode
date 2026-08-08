/**
 * i18n Key Validation Script
 *
 * Scans Vue components for t() calls and validates against locale definitions.
 * Usage: node scripts/validate-i18n-keys.js
 */

import { readdir, readFile } from 'fs/promises'
import { join } from 'path'

const COMPONENTS_DIR = join(process.cwd(), 'src/components')
const LOCALES_DIR = join(process.cwd(), 'src/i18n/locales')
const LOCALES = ['zh-CN', 'en-US']

/**
 * Recursively find all .vue files in a directory
 */
async function findVueFiles(dir) {
  const files = []
  const entries = await readdir(dir, { withFileTypes: true })

  for (const entry of entries) {
    const fullPath = join(dir, entry.name)
    if (entry.isDirectory()) {
      files.push(...(await findVueFiles(fullPath)))
    } else if (entry.isFile() && entry.name.endsWith('.vue')) {
      files.push(fullPath)
    }
  }

  return files
}

/**
 * Extract t() calls from Vue file content
 * Matches: t('key'), t("key"), t(`key`)
 * Skips template literals with ${} interpolation (dynamic keys)
 */
function extractI18nKeys(content) {
  const pattern = /t\s*\(\s*['"`]([^'"`]+)['"`]/g
  const keys = new Set()
  let match

  while ((match = pattern.exec(content)) !== null) {
    const key = match[1]
    // Skip template literals with interpolation (dynamic keys)
    if (key.includes('${')) {
      continue
    }
    keys.add(key)
  }

  return keys
}

/**
 * Load locale file and extract versionHistory keys
 */
async function loadLocaleKeys(locale) {
  try {
    const localePath = join(LOCALES_DIR, locale, 'modules', 'problems.ts')
    const content = await readFile(localePath, 'utf-8')

    // Extract the versionHistory section using regex
    const versionHistoryMatch = content.match(/versionHistory:\s*\{([\s\S]*?)\n  \},?\n/)
    if (!versionHistoryMatch) {
      return {}
    }

    // Use eval-like approach to parse the object
    // Create a temporary object with the versionHistory content
    const versionHistoryContent = versionHistoryMatch[0]
    const objMatch = versionHistoryContent.match(/versionHistory:\s*\{([\s\S]*?)\n  \}/)

    if (!objMatch) {
      return {}
    }

    // Manually parse the nested object structure
    const keys = {}
    const innerContent = objMatch[1]

    // Parse nested objects (action, etc.)
    const actionMatch = innerContent.match(/action:\s*\{([^}]+)\}/)
    if (actionMatch) {
      keys['action'] = {}
      const actionContent = actionMatch[1]
      const actionPairs = actionContent.match(/(\w+):\s*'([^']+)'/g) || []
      for (const pair of actionPairs) {
        const [key, value] = pair.split(/:\s*'/)
        const cleanKey = key.trim()
        const cleanValue = value.replace(/'.*$/, '').trim()
        if (cleanKey && cleanValue) {
          keys['action'][cleanKey] = cleanValue
        }
      }
    }

    // Parse simple key-value pairs
    const simplePairs = innerContent.match(/(\w+):\s*'([^']*?)'(?:\s*,?\s*\n|$)/g) || []
    for (const pair of simplePairs) {
      const colonIndex = pair.indexOf(':')
      if (colonIndex > -1) {
        const key = pair.substring(0, colonIndex).trim()
        const valueMatch = pair.match(/:\s*'([^']*)'/)
        if (valueMatch && key !== 'action') {
          keys[key] = valueMatch[1]
        }
      }
    }

    return keys
  } catch (error) {
    console.error(`Error loading locale ${locale}:`, error.message)
    return {}
  }
}

/**
 * Get all versionHistory keys in dot notation from locale
 */
function getVersionHistoryKeys(localeObj, prefix = 'problems.versionHistory') {
  const keys = []

  for (const [key, value] of Object.entries(localeObj)) {
    const fullKey = `${prefix}.${key}`

    if (value && typeof value === 'object' && !Array.isArray(value)) {
      keys.push(...getVersionHistoryKeys(value, fullKey))
    } else {
      keys.push(fullKey)
    }
  }

  return keys
}

/**
 * Get referenced keys from components that match problems.versionHistory.*
 */
function getReferencedVersionHistoryKeys(referencedKeys) {
  const versionHistoryKeys = []

  for (const key of referencedKeys) {
    if (key.startsWith('problems.versionHistory.')) {
      versionHistoryKeys.push(key)
    }
  }

  return versionHistoryKeys
}

/**
 * Check if a key exists in locale structure
 */
function keyExistsInLocale(localeObj, keyPath) {
  const parts = keyPath.split('.')
  let current = localeObj

  for (const part of parts) {
    if (current && typeof current === 'object' && part in current) {
      current = current[part]
    } else {
      return false
    }
  }

  return true
}

/**
 * Main validation function
 */
async function validate() {
  console.log('Checking i18n keys...\n')

  // Find all Vue components
  const vueFiles = await findVueFiles(COMPONENTS_DIR)
  console.log(`Found ${vueFiles.length} Vue component(s)\n`)

  // Collect all t() keys from all components
  const allReferencedKeys = new Set()
  for (const file of vueFiles) {
    const content = await readFile(file, 'utf-8')
    const keys = extractI18nKeys(content)
    for (const key of keys) {
      allReferencedKeys.add(key)
    }
  }

  // Get versionHistory referenced keys
  const referencedVersionHistoryKeys = getReferencedVersionHistoryKeys(allReferencedKeys)
  console.log(`Found ${referencedVersionHistoryKeys.length} problems.versionHistory.* key(s) in components\n`)

  // Check each locale
  const missingKeysByLocale = {}

  for (const locale of LOCALES) {
    const localeObj = await loadLocaleKeys(locale)
    const localeVersionHistoryKeys = getVersionHistoryKeys({ versionHistory: localeObj }, 'problems.versionHistory')
    const missingKeys = []

    for (const refKey of referencedVersionHistoryKeys) {
      // Check if key exists (allowing for partial matches like action.CREATE)
      let exists = false

      // Try exact match first
      if (localeVersionHistoryKeys.includes(refKey)) {
        exists = true
      } else {
        // Try nested key lookup (e.g., problems.versionHistory.action.CREATE)
        exists = keyExistsInLocale(localeObj, refKey.replace('problems.versionHistory.', ''))
      }

      if (!exists) {
        missingKeys.push(refKey)
      }
    }

    if (missingKeys.length > 0) {
      missingKeysByLocale[locale] = [...new Set(missingKeys)]
    }
  }

  // Report results
  let hasErrors = false

  for (const [locale, keys] of Object.entries(missingKeysByLocale)) {
    if (keys.length > 0) {
      hasErrors = true
      console.log(`Missing in ${locale}: ${keys.sort().join(', ')}`)
    }
  }

  if (hasErrors) {
    console.log('\n❌ Validation failed - missing i18n keys')
    process.exit(1)
  } else {
    console.log('All keys found! ✓')
    process.exit(0)
  }
}

validate().catch((error) => {
  console.error('Validation error:', error)
  process.exit(1)
})