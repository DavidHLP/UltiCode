import { readFile } from 'fs/promises'
import { buildKeySet, keyExists } from './keys'
import { DEFAULT_EXCLUDE_DIRS, extractDynamicKeyPrefixes, extractStaticKeys, findSourceFiles } from './scan'
import type {
  CheckReport,
  CodeCoverage,
  I18nCheckOptions,
  LocaleConsistency,
  TranslationObject,
} from './types'

/**
 * Check 1 — locale-to-locale consistency between the primary (zh-CN) and
 * secondary (en-US) locale trees.
 */
export function checkLocaleConsistency(
  zhLocale: TranslationObject,
  enLocale: TranslationObject,
): LocaleConsistency {
  const zhKeys = buildKeySet(zhLocale)
  const enKeys = buildKeySet(enLocale)

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
 * Check 2 — code-to-locale coverage. Scans the source root for static `t()`
 * keys and verifies each exists in the primary locale, also recording dynamic
 * key prefixes for manual verification.
 */
async function checkCodeCoverage(opts: I18nCheckOptions): Promise<CodeCoverage> {
  const excludeDirs = opts.excludeDirs ?? DEFAULT_EXCLUDE_DIRS
  const excludeFiles = opts.excludeFiles ?? []
  const files = await findSourceFiles(opts.srcDir, excludeDirs, excludeFiles)
  const zhLocale = opts.zhLocale

  const missingKeys: Array<{ key: string; file: string }> = []
  const dynamicPrefixes: Array<{ prefix: string; file: string }> = []
  const seenMissing = new Set<string>()
  const seenPrefixes = new Set<string>()
  let totalStaticKeys = 0
  const root = `${process.cwd()}/`

  for (const file of files) {
    const content = await readFile(file, 'utf-8')
    const relPath = file.startsWith(root) ? file.slice(root.length) : file

    const staticKeys = extractStaticKeys(content)
    totalStaticKeys += staticKeys.length
    for (const key of staticKeys) {
      if (!keyExists(zhLocale, key) && !seenMissing.has(key)) {
        seenMissing.add(key)
        missingKeys.push({ key, file: relPath })
      }
    }

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

/**
 * Run both checks and assemble a {@link CheckReport}. Each application supplies
 * its locale trees and source root through {@link I18nCheckOptions}.
 */
export async function runI18nCheck(opts: I18nCheckOptions): Promise<CheckReport> {
  const localeConsistency = checkLocaleConsistency(opts.zhLocale, opts.enLocale)
  const codeCoverage = await checkCodeCoverage(opts)

  const totalIssues =
    localeConsistency.missingInEn.length +
    localeConsistency.missingInZh.length +
    codeCoverage.missingKeys.length

  return {
    localeConsistency,
    codeCoverage,
    summary: {
      passed: totalIssues === 0,
      totalIssues,
    },
  }
}
