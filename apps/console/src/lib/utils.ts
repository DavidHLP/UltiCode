// ---------------------------------------------------------------------------
// cn — className concatenation helper (clsx + tailwind-merge)
//
// Thin re-export from the shared package so `clsx` + `tailwind-merge`
// behavior cannot drift between `console/`, `management/`, and the
// shared packages. Single source of truth lives at
// `shared/auth-core/src/utils.ts` — see that file's header comment.
//
// The package entry keeps this app-local utility compatible with existing callers.
// to the public `@ulticode/auth-core` entry.
// ---------------------------------------------------------------------------
export { cn } from '@ulticode/auth-core'
export type { ClassValue } from 'clsx'
