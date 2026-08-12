import { h, type VNode } from 'vue'
import { badge, PROBLEM_LIST_VISIBILITY_COLOR_MAP } from '@/components/ui/terminal'
import { IconEye, IconEyeOff, IconLock, IconWorld } from '@tabler/icons-vue'

/**
 * Problem list visibility
 */
export type ProblemListVisibility = 'PUBLIC' | 'PRIVATE' | 'UNLISTED'

/**
 * Returns the icon component for a problem list visibility
 */
export function getProblemListVisibilityIcon(visibility: ProblemListVisibility): VNode {
  switch (visibility) {
    case 'PUBLIC':
      return h(IconWorld, { class: 'h-4 w-4 text-foreground-strong' })
    case 'PRIVATE':
      return h(IconLock, { class: 'h-4 w-4 text-foreground-strong' })
    case 'UNLISTED':
      return h(IconEyeOff, { class: 'h-4 w-4 text-muted-foreground' })
    default:
      return h(IconEye, { class: 'h-4 w-4 text-muted-foreground' })
  }
}

/**
 * Returns the badge component for a problem list visibility
 * @param t - i18n translation function
 */
export function getProblemListVisibilityBadge(
  visibility: ProblemListVisibility,
  t: (key: string, fallback?: string) => string,
): VNode {
  return badge({
    color: PROBLEM_LIST_VISIBILITY_COLOR_MAP[visibility] ?? 'neutral',
    label: t(`problemLists.visibility.${visibility.toLowerCase()}`, visibility),
  })
}
