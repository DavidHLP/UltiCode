/**
 * i18n Translation Completeness Checker
 *
 * Checks:
 * 1. Locale-to-locale consistency (zh-CN vs en-US)
 * 2. Code-to-locale coverage (static t() keys exist in locale)
 * 3. Dynamic key prefix completeness
 *
 * Usage:
 *   pnpm check:i18n          # human-readable output
 *   pnpm check:i18n --json   # JSON report for CI
 */

import { readdir, readFile } from 'fs/promises'
import { join } from 'path'
import zhCN from './locales/zh-CN'
import enUS from './locales/en-US'

type TranslationObject = Record<string, unknown>

interface CheckReport {
  localeConsistency: {
    zhKeysCount: number
    enKeysCount: number
    missingInEn: string[]
    missingInZh: string[]
    isComplete: boolean
  }
  codeCoverage: {
    totalStaticKeys: number
    missingKeys: Array<{ key: string; file: string }>
    dynamicPrefixes: Array<{ prefix: string; file: string }>
  }
  summary: {
    passed: boolean
    totalIssues: number
  }
}

// ─── Helpers ────────────────────────────────────────────────────────────────

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
 * Build a lookup map from dot-notation key to existence
 */
function buildKeySet(obj: TranslationObject): Set<string> {
  return new Set(flattenObject(obj))
}

/**
 * Check if a dot-notation key exists in a nested object
 */
function keyExists(obj: TranslationObject, keyPath: string): boolean {
  const parts = keyPath.split('.')
  let current: unknown = obj
  for (const part of parts) {
    if (current && typeof current === 'object' && part in (current as Record<string, unknown>)) {
      current = (current as Record<string, unknown>)[part]
    } else {
      return false
    }
  }
  return true
}

// ─── Source scanning ────────────────────────────────────────────────────────

/**
 * Recursively find all .vue and .ts source files, excluding i18n internals
 */
async function findSourceFiles(dir: string): Promise<string[]> {
  const files: string[] = []
  const excludeDirs = ['node_modules', '__tests__', 'locales', '.git']
  // Exclude this checker's own source: its doc-comment examples (e.g. `t('dotted.key')`)
  // are illustrative, not real translations, and would otherwise be reported as missing keys.
  const excludeFiles = ['check.ts']

  let entries
  try {
    entries = await readdir(dir, { withFileTypes: true })
  } catch {
    return files
  }

  for (const entry of entries) {
    const fullPath = join(dir, entry.name)
    if (entry.isDirectory()) {
      if (!excludeDirs.includes(entry.name)) {
        files.push(...(await findSourceFiles(fullPath)))
      }
    } else if (
      entry.isFile() &&
      (entry.name.endsWith('.vue') || entry.name.endsWith('.ts')) &&
      !excludeFiles.includes(entry.name)
    ) {
      files.push(fullPath)
    }
  }
  return files
}

/**
 * Extract static t() keys from file content
 * Matches: t('dotted.key'), t("dotted.key"), t(`dotted.key`)
 * Skips template literals with ${} interpolation
 * Only matches keys containing at least one dot (i18n key pattern)
 * to avoid false positives from API paths, Vue emits, etc.
 *
 * Excludes:
 * - Template interpolation (${...})
 * - Paths starting with / or @/ (API routes, imports)
 * - Paths starting with ./ (relative imports)
 * - Keys containing : (Vue emits, scoped slots)
 * - Keys containing spaces (sentences, not keys)
 * - Keys starting with . (CSS selectors, relative paths)
 */
function extractStaticKeys(content: string): string[] {
  const pattern = /t\s*\(\s*['"`]([^'"`]+)['"`]/g
  const keys: string[] = []
  let match
  while ((match = pattern.exec(content)) !== null) {
    const key = match[1]
    if (
      key.includes('.') &&
      !key.includes('${') &&
      !key.startsWith('/') &&
      !key.startsWith('@/') &&
      !key.startsWith('./') &&
      !key.startsWith('.') &&
      !key.endsWith('.') &&
      !key.includes(':') &&
      !key.includes(' ')
    ) {
      keys.push(key)
    }
  }
  return keys
}

/**
 * Extract dynamic key prefixes from template literal t() calls
 * e.g. t(`moderation.status.${status}`) → 'moderation.status'
 * Only matches dotted prefixes (i18n key pattern), skips API paths
 */
function extractDynamicKeyPrefixes(content: string): string[] {
  const pattern = /t\s*\(\s*`([^`]*\$\{[^`]+)`/g
  const prefixes: string[] = []
  let match
  while ((match = pattern.exec(content)) !== null) {
    const prefix = match[1].replace(/\$\{.*$/, '').replace(/\.$/, '')
    // Must contain a dot (i18n key pattern) and not start with / (API path)
    if (prefix && prefix.includes('.') && !prefix.startsWith('/')) {
      prefixes.push(prefix)
    }
  }
  return prefixes
}

// ─── Checks ─────────────────────────────────────────────────────────────────

/**
 * Check 1: Locale-to-locale consistency
 */
function checkLocaleConsistency() {
  const zhKeys = buildKeySet(zhCN as unknown as TranslationObject)
  const enKeys = buildKeySet(enUS as unknown as TranslationObject)

  const missingInEn: string[] = []
  const missingInZh: string[] = []

  for (const key of zhKeys) {
    if (!enKeys.has(key)) {
      missingInEn.push(key)
    }
  }
  for (const key of enKeys) {
    if (!zhKeys.has(key)) {
      missingInZh.push(key)
    }
  }

  return {
    zhKeysCount: zhKeys.size,
    enKeysCount: enKeys.size,
    missingInEn,
    missingInZh,
    isComplete: missingInEn.length === 0 && missingInZh.length === 0,
  }
}

/**
 * Check 2: Code-to-locale coverage
 * Scans src/ for static t() keys and verifies they exist in zh-CN locale
 */
async function checkCodeCoverage(): Promise<CheckReport['codeCoverage']> {
  const srcDir = join(process.cwd(), 'src')
  const files = await findSourceFiles(srcDir)
  const zhLocale = zhCN as unknown as TranslationObject

  const missingKeys: Array<{ key: string; file: string }> = []
  const dynamicPrefixes: Array<{ prefix: string; file: string }> = []
  const seenMissing = new Set<string>()
  const seenPrefixes = new Set<string>()
  let totalStaticKeys = 0

  for (const file of files) {
    const content = await readFile(file, 'utf-8')
    const relPath = file.replace(process.cwd() + '/', '')

    // Static keys
    const staticKeys = extractStaticKeys(content)
    totalStaticKeys += staticKeys.length
    for (const key of staticKeys) {
      if (!keyExists(zhLocale, key) && !seenMissing.has(key)) {
        seenMissing.add(key)
        missingKeys.push({ key, file: relPath })
      }
    }

    // Dynamic key prefixes
    const prefixes = extractDynamicKeyPrefixes(content)
    for (const prefix of prefixes) {
      if (!seenPrefixes.has(prefix)) {
        seenPrefixes.add(prefix)
        dynamicPrefixes.push({ prefix, file: relPath })
      }
    }
  }

  return { totalStaticKeys, missingKeys, dynamicPrefixes }
}

// ─── Main ───────────────────────────────────────────────────────────────────

async function main() {
  const isJson = process.argv.includes('--json')

  const localeConsistency = checkLocaleConsistency()
  const codeCoverage = await checkCodeCoverage()

  const totalIssues =
    localeConsistency.missingInEn.length +
    localeConsistency.missingInZh.length +
    codeCoverage.missingKeys.length

  const report: CheckReport = {
    localeConsistency,
    codeCoverage,
    summary: {
      passed: totalIssues === 0,
      totalIssues,
    },
  }

  if (isJson) {
    console.log(JSON.stringify(report, null, 2))
    process.exit(totalIssues === 0 ? 0 : 1)
    return
  }

  // ─── Human-readable output ──────────────────────────────────────────────

  console.log('\n=== i18n Translation Completeness Check ===\n')

  // Locale consistency
  console.log(`zh-CN keys: ${localeConsistency.zhKeysCount}`)
  console.log(`en-US keys: ${localeConsistency.enKeysCount}`)

  if (localeConsistency.missingInEn.length > 0) {
    console.log(`\n❌ Missing in en-US (${localeConsistency.missingInEn.length} keys):`)
    localeConsistency.missingInEn.slice(0, 20).forEach((key) => console.log(`   - ${key}`))
    if (localeConsistency.missingInEn.length > 20) {
      console.log(`   ... and ${localeConsistency.missingInEn.length - 20} more`)
    }
  }

  if (localeConsistency.missingInZh.length > 0) {
    console.log(`\n❌ Missing in zh-CN (${localeConsistency.missingInZh.length} keys):`)
    localeConsistency.missingInZh.slice(0, 20).forEach((key) => console.log(`   - ${key}`))
    if (localeConsistency.missingInZh.length > 20) {
      console.log(`   ... and ${localeConsistency.missingInZh.length - 20} more`)
    }
  }

  if (localeConsistency.isComplete) {
    console.log('\n✅ Locale-to-locale consistency: PASS')
  }

  // Code coverage
  console.log(`\n--- Code-to-Locale Coverage ---`)
  console.log(`Static t() keys found in source: ${codeCoverage.totalStaticKeys}`)

  if (codeCoverage.missingKeys.length > 0) {
    console.log(
      `\n❌ Keys used in code but missing in zh-CN locale (${codeCoverage.missingKeys.length}):`,
    )
    codeCoverage.missingKeys
      .slice(0, 30)
      .forEach(({ key, file }) => console.log(`   - ${key}  (${file})`))
    if (codeCoverage.missingKeys.length > 30) {
      console.log(`   ... and ${codeCoverage.missingKeys.length - 30} more`)
    }
  } else {
    console.log('\n✅ Code-to-locale coverage: PASS')
  }

  // Dynamic prefixes (informational)
  if (codeCoverage.dynamicPrefixes.length > 0) {
    console.log(
      `\nℹ️  Dynamic key prefixes (${codeCoverage.dynamicPrefixes.length}) — verify sub-keys exist:`,
    )
    codeCoverage.dynamicPrefixes.forEach(({ prefix, file }) =>
      console.log(`   - ${prefix}.*  (${file})`),
    )
  }

  // Summary
  console.log('\n' + '='.repeat(45))
  if (totalIssues === 0) {
    console.log('✅ All i18n checks passed!')
  } else {
    console.log(`❌ ${totalIssues} issue(s) found. Fix before merging.`)
  }
  console.log('')

  process.exit(totalIssues === 0 ? 0 : 1)
}

main()

export { checkLocaleConsistency, flattenObject }
