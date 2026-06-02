/**
 * Tailwind class merge utility used by SemanticBadge.
 *
 * Mirrors `cn` from app-level lib/utils but ships self-contained inside the
 * shared package — it does not depend on `clsx` or `tailwind-merge` so the
 * shared badge-config module can be consumed by both `console` and
 * `management` without each app having to wire up aliases for transitive
 * dependencies.
 *
 * The implementation is sufficient for SemanticBadge because the component
 * composes a known fixed set of utility classes from its own internal maps
 * and does not accept caller-supplied class names that would need
 * tailwind-merge style conflict resolution.
 */
export type ClassValue = string | number | boolean | null | undefined | ClassValue[] | Record<string, unknown>

function toClassList(value: ClassValue): string[] {
  if (!value && value !== 0) return []
  if (typeof value === 'string' || typeof value === 'number') return [String(value)]
  if (Array.isArray(value)) return value.flatMap(toClassList)
  if (typeof value === 'object') {
    return Object.entries(value)
      .filter(([, enabled]) => Boolean(enabled))
      .map(([className]) => className)
  }
  return []
}

export function cn(...inputs: ClassValue[]): string {
  return inputs.flatMap(toClassList).filter(Boolean).join(' ')
}
