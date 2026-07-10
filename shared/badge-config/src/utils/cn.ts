/**
 * joinClasses — class-name concatenation used by SemanticBadge.
 *
 * Deliberately distinct from the app-level `cn` (clsx + tailwind-merge in
 * shared/auth-core): this helper ships self-contained inside badge-config —
 * no `clsx`/`tailwind-merge` dependency — so the shared module can be consumed
 * by both `console` and `management` without each app wiring up aliases for
 * transitive dependencies.
 *
 * Renamed from `cn` to remove the same-name / different-semantics clash with
 * the auth-core `cn`: this concatenates; that merges conflicting Tailwind
 * classes. `cn` across the codebase now unambiguously means the twMerge version.
 *
 * Sufficient for SemanticBadge because the component composes a known fixed set
 * of utility classes from its own internal maps and does not accept
 * caller-supplied class names that would need tailwind-merge conflict resolution.
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

export function joinClasses(...inputs: ClassValue[]): string {
  return inputs.flatMap(toClassList).filter(Boolean).join(' ')
}
