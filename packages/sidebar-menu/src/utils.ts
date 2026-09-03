import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

/**
 * `cn` — className concatenation helper (clsx + tailwind-merge).
 *
 * Vendored locally like `packages/auth-ui` and `packages/design-system`:
 * `@ulticode/auth-core` is a runtime auth module and must not be imported by
 * visual packages (the former `@/shared/auth-core/src/utils` re-export never
 * resolved in this package's standalone tsconfig).
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs))
}

export type { ClassValue }

export type SidebarItemActiveFn = (url?: string) => boolean

export interface SidebarUser {
  name: string
  email?: string
  avatar?: string
  role?: string
}

export function isExactOrStartsWith(
  currentPath: string,
  url: string | undefined,
  exactUrls: string[] = [],
): boolean {
  if (!url) return false
  if (exactUrls.includes(url)) return currentPath === url
  return currentPath === url || currentPath.startsWith(url + '/')
}
