import { type ComputedRef } from 'vue'
import { PERM } from '@/constants/permissions'
import { usePermissionMap } from './usePermissionMap'

export interface ForumPermissionMap {
  moderatePost: ComputedRef<boolean>
  deletePost: ComputedRef<boolean>
}

export function useForumPermissions(): { can: { forum: ForumPermissionMap } } {
  const forum: ForumPermissionMap = usePermissionMap({
    moderatePost: PERM.MODERATE_FORUM_POST,
    deletePost: PERM.DELETE_FORUM_POST,
  })

  return { can: { forum } }
}
