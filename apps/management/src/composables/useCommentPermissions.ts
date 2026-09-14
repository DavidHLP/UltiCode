import { type ComputedRef } from 'vue'
import { PERM } from '@/constants/permissions'
import { usePermissionMap } from './usePermissionMap'

export interface CommentPermissionMap {
  moderateForum: ComputedRef<boolean>
  moderateSolution: ComputedRef<boolean>
}

export function useCommentPermissions(): { can: { comment: CommentPermissionMap } } {
  const comment: CommentPermissionMap = usePermissionMap({
    moderateForum: PERM.MODERATE_FORUM_COMMENT,
    moderateSolution: PERM.MODERATE_SOLUTION_COMMENT,
  })

  return { can: { comment } }
}
