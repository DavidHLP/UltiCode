import { describe, expect, it } from 'vitest'
import { readdir, readFile } from 'fs/promises'
import { join } from 'path'
import zhCN from '../locales/zh-CN'

type TranslationObject = Record<string, unknown>

/**
 * Flatten nested translation object to dot-notation key list
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

/**
 * Recursively find source files, excluding i18n internals and test files
 */
async function findSourceFiles(dir: string): Promise<string[]> {
  const files: string[] = []
  const excludeDirs = ['node_modules', '__tests__', 'locales', '.git']

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
    } else if (entry.isFile() && (entry.name.endsWith('.vue') || entry.name.endsWith('.ts'))) {
      files.push(fullPath)
    }
  }
  return files
}

/**
 * Extract static t() keys from file content
 * Only matches dotted keys (i18n pattern), skips API paths and Vue emits
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
      !key.includes(':') &&
      !key.includes(' ')
    ) {
      keys.push(key)
    }
  }
  return keys
}

/**
 * Scan all source files and return keys used in code but missing from locale
 */
async function findMissingKeys(): Promise<Array<{ key: string; file: string }>> {
  const srcDir = join(process.cwd(), 'src')
  const files = await findSourceFiles(srcDir)
  const zhLocale = zhCN as unknown as TranslationObject
  const missingKeys: Array<{ key: string; file: string }> = []
  const seen = new Set<string>()

  for (const file of files) {
    const content = await readFile(file, 'utf-8')
    const relPath = file.replace(process.cwd() + '/', '')
    const staticKeys = extractStaticKeys(content)

    for (const key of staticKeys) {
      if (!keyExists(zhLocale, key) && !seen.has(key)) {
        seen.add(key)
        missingKeys.push({ key, file: relPath })
      }
    }
  }

  return missingKeys
}

describe('i18n coverage', () => {
  it('zh-CN and en-US should have the same key count', async () => {
    const enUS = (await import('../locales/en-US')).default as unknown as TranslationObject
    const zhKeys = flattenObject(zhCN as unknown as TranslationObject)
    const enKeys = flattenObject(enUS)
    expect(enKeys.length).toBe(zhKeys.length)
  })

  it('should have no new missing i18n keys beyond known baseline', async () => {
    const missingKeys = await findMissingKeys()

    // Known pre-existing missing keys (from inline <i18n> blocks and Phase 3 scope)
    // These will be fixed in Phase 3. Add new keys here only if they are pre-existing.
    const knownMissingPrefixes = [
      'testCases.', // inline <i18n> block in ViewCasesView/ViewCodeView/ViewDescriptionView
      'problems.bulkEdit.', // Phase 3: bulk edit UI text
      'problems.flagInfo.', // Phase 3: flag info display
      'problems.difficulty.', // Phase 3: difficulty enum display
      'common.delete', // Phase 3: delete confirmation
      'common.flag', // Phase 3: flag actions
      'common.reason', // Phase 3: reason prompts
      'audit.filters.', // Phase 3: audit filter labels
      'problemLists.status.', // Phase 3: problem list status
      'problemLists.form.', // Phase 3: problem list form
      'tags.toast.', // Phase 3: tag operation toasts
      'users.actions.', // Phase 3: user action descriptions
    ]

    const knownMissingKeys = new Set([
      'common.optional', // from TestCaseForm.vue inline <i18n>
      'common.deleteDescriptionWithName', // Phase 3
      'users.typeConfirmLabel', // Phase 3
      'dotted.key', // example in check.ts comments, not real usage
      'forum.auditActions.', // trailing dot, likely a bug in source
    ])

    const newMissing = missingKeys.filter(
      ({ key }) =>
        !knownMissingPrefixes.some((prefix) => key.startsWith(prefix)) &&
        !knownMissingKeys.has(key),
    )

    // This test acts as a regression gate: if someone adds a new t() call with a key
    // that doesn't exist in the locale, this test will fail.
    // To fix: add the key to both zh-CN and en-US locale modules.
    if (newMissing.length > 0) {
      const summary = newMissing.map((m) => `  ${m.key}  (${m.file})`).join('\n')
      expect.fail(
        `Found ${newMissing.length} t() key(s) used in code but missing from zh-CN locale.\n` +
          `Add these keys to the appropriate locale module:\n${summary}`,
      )
    }
  })
})
