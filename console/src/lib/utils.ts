// ---------------------------------------------------------------------------
// cn — className concatenation helper (clsx + tailwind-merge)
//
// Thin re-export from the shared package so `clsx` + `tailwind-merge`
// behavior cannot drift between `console/`, `management/`, and the
// shared packages. Single source of truth lives at
// `shared/auth-core/src/utils.ts` — see that file's header comment.
//
// The `@/shared` alias in `console/vite.config.ts` resolves this path
// to the file under `../shared/auth-core/src/utils.ts`.
// ---------------------------------------------------------------------------
export { cn } from '@/shared/auth-core/src/utils'
export type { ClassValue } from 'clsx'