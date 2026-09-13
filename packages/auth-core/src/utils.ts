// ---------------------------------------------------------------------------
// cn — className concatenation helper (clsx + tailwind-merge)
//
// Single source of truth shared between the console and management apps and
// `auth-ui/src/components/cn.ts`.
// Apps re-export from here so the behavior cannot drift.
//
// No Vue dependency: safe to import from any framework-light or server
// context.
// ---------------------------------------------------------------------------
import type { ClassValue } from 'clsx'
import { clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
