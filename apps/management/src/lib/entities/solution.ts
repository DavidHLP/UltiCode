import { h, type VNode } from 'vue'
import { badge, type SemanticColor } from '@/components/ui/terminal'
import { IconCircleCheckFilled, IconEye, IconEyeOff } from '@tabler/icons-vue'

/**
 * Solution visibility status
 */
export type SolutionVisibility = 'PUBLIC' | 'PRIVATE' | 'HIDDEN'

const VISIBILITY_COLOR_MAP: Record<string, SemanticColor> = {
  PUBLIC: 'success',
  PRIVATE: 'warning',
  HIDDEN: 'neutral',
}

/**
 * Returns the icon component for a solution visibility
 */
export function getSolutionVisibilityIcon(visibility: SolutionVisibility): VNode {
  switch (visibility) {
    case 'PUBLIC':
      return h(IconEye, { class: 'h-4 w-4 text-[var(--terminal-green)]' })
    case 'PRIVATE':
      return h(IconEyeOff, { class: 'h-4 w-4 text-[var(--terminal-amber)]' })
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
  return badge({
    color: VISIBILITY_COLOR_MAP[visibility] ?? 'neutral',
    label: t(`solutions.visibility.${visibility.toLowerCase()}`, visibility),
  })
}

/**
 * Returns the badge component for solution approval status
 * @param t - i18n translation function
 */
export function getSolutionApprovalBadge(
  isApproved: boolean | null,
  t: (key: string) => string,
): VNode {
  if (isApproved === true)
    return badge({
      color: 'success',
      label: t('solutions.approval.approved'),
      icon: IconCircleCheckFilled,
    })
  if (isApproved === false)
    return badge({ color: 'error', label: t('solutions.approval.rejected') })
  return badge({ color: 'warning', label: t('solutions.approval.pending') })
}
