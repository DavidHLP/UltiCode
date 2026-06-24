import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

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
