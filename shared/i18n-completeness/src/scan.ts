import { readdir } from 'fs/promises'
import { join } from 'path'

/** Directory names skipped while walking the source tree. */
export const DEFAULT_EXCLUDE_DIRS = ['node_modules', '__tests__', 'locales', '.git']

/**
 * Recursively find all `.vue` and `.ts` source files, excluding i18n internals.
 */
export async function findSourceFiles(
  dir: string,
  excludeDirs: string[] = DEFAULT_EXCLUDE_DIRS,
  excludeFiles: string[] = [],
): Promise<string[]> {
  const files: string[] = []
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
        files.push(...(await findSourceFiles(fullPath, excludeDirs, excludeFiles)))
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
 * Extract static `t()` keys from source content.
 *
 * Matches `t('dotted.key')`, `t("dotted.key")`, and `` t(`dotted.key`) `` while
 * skipping template interpolation, API paths, relative imports, Vue emits,
 * CSS selectors, and free-form sentences.
 */
export function extractStaticKeys(content: string): string[] {
  const pattern = /t\s*\(\s*['"`]([^'"`]+)['"`]/g
  const keys: string[] = []
  let match: RegExpExecArray | null
  while ((match = pattern.exec(content)) !== null) {
    const key = match[1]
    if (key === undefined) {
      continue
    }
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
 * Extract dynamic key prefixes from template-literal `t()` calls, e.g.
 * `` t(`moderation.status.${status}`) `` → `moderation.status`.
 */
export function extractDynamicKeyPrefixes(content: string): string[] {
  const pattern = /t\s*\(\s*`([^`]*\$\{[^`]+)`/g
  const prefixes: string[] = []
  let match: RegExpExecArray | null
  while ((match = pattern.exec(content)) !== null) {
    const raw = match[1]
    if (raw === undefined) {
      continue
    }
    const prefix = raw.replace(/\$\{.*$/, '').replace(/\.$/, '')
    if (prefix && prefix.includes('.') && !prefix.startsWith('/')) {
      prefixes.push(prefix)
    }
  }
  return prefixes
}
