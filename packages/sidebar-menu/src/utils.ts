export { cn } from '@/shared/auth-core/src/utils'
export type { ClassValue } from 'clsx'

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
