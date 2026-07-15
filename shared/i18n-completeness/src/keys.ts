import type { TranslationObject } from './types'

/**
 * Flatten a nested translation object to dot-notation keys.
 * Arrays and non-plain values become leaf keys.
 */
export function flattenObject(obj: TranslationObject, prefix = ''): string[] {
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

/** Build a lookup set of every dot-notation key present in a locale. */
export function buildKeySet(obj: TranslationObject): Set<string> {
  return new Set(flattenObject(obj))
}

/** Whether a dot-notation key resolves inside a nested locale object. */
export function keyExists(obj: TranslationObject, keyPath: string): boolean {
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
