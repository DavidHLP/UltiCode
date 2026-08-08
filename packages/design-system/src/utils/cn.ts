import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

/**
 * `cn` — className concatenation helper (clsx + tailwind-merge).
 *
 * Self-contained copy so the design-system module is not a transitive
 * consumer of `@ulticode/auth-core` (which is a runtime auth module, not a
 * style utility). The behavior is identical to `auth-core/src/utils.ts`'s
 * `cn`; if the two diverge, that is a bug.
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs))
}

export type { ClassValue }
