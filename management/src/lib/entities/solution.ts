import { h, type VNode } from 'vue'
import { Badge } from '@/components/ui/badge'
import type { BadgeVariant } from './user'
import { IconCircleCheckFilled, IconEye, IconEyeOff } from '@tabler/icons-vue'

/**
 * Solution visibility status
 */
export type SolutionVisibility = 'PUBLIC' | 'PRIVATE' | 'HIDDEN'

/**
 * Returns the badge variant for a solution visibility
 */
export function getSolutionVisibilityBadgeVariant(visibility: SolutionVisibility): BadgeVariant {
  switch (visibility) {
    case 'PUBLIC':
      return 'default'
    case 'PRIVATE':
      return 'secondary'
    case 'HIDDEN':
      return 'outline'
    default:
      return 'outline'
  }
}

/**
 * Returns the icon component for a solution visibility
 */
export function getSolutionVisibilityIcon(visibility: SolutionVisibility): VNode {
  switch (visibility) {
    case 'PUBLIC':
      return h(IconEye, { class: 'h-4 w-4 text-emerald-500' })
    case 'PRIVATE':
      return h(IconEyeOff, { class: 'h-4 w-4 text-amber-500' })
    case 'HIDDEN':
      return h(IconEyeOff, { class: 'h-4 w-4 text-muted-foreground' })
    default:
      return h(IconEyeOff, { class: 'h-4 w-4 text-muted-foreground' })
  }
}

/**
 * Returns the badge component for a solution visibility
 * @param t - i18n translation function
 */
export function getSolutionVisibilityBadge(
  visibility: SolutionVisibility,
  t: (key: string, fallback?: string) => string,
): VNode {
  const variant = getSolutionVisibilityBadgeVariant(visibility)
  return h(Badge, { variant }, () =>
    t(`solutions.visibility.${visibility.toLowerCase()}`, visibility),
  )
}

/**
 * Returns the badge variant for solution approval status
 */
export function getSolutionApprovalBadgeVariant(isApproved: boolean | null): BadgeVariant {
  if (isApproved === true) return 'default'
  if (isApproved === false) return 'destructive'
  return 'secondary'
}

/**
 * Returns the badge component for solution approval status
 * @param t - i18n translation function
 */
export function getSolutionApprovalBadge(
  isApproved: boolean | null,
  t: (key: string) => string,
): VNode {
  if (isApproved === true) {
    return h(Badge, { variant: 'default' }, () => [
      h(IconCircleCheckFilled, { class: 'mr-1 h-3 w-3' }),
      t('solutions.approval.approved'),
    ])
  }
  if (isApproved === false) {
    return h(Badge, { variant: 'destructive' }, () => t('solutions.approval.rejected'))
  }
  return h(Badge, { variant: 'secondary' }, () => t('solutions.approval.pending'))
}
