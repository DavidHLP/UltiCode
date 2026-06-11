import { ref, computed } from "vue";
import { followApi } from "@/api/follow";

/**
 * Composable for managing follow status state.
 * Provides reactive follow state with optimistic UI updates.
 */
export function useFollowStatus(
  targetUserId: string,
  initialIsFollowing = false,
) {
  const isFollowing = ref(initialIsFollowing);
  const loading = ref(false);

  async function fetchStatus() {
    if (!targetUserId) return;
    try {
      const result = await followApi.getFollowStatus(targetUserId);
      isFollowing.value = result.following;
    } catch {
      // Silently fail — follow button defaults to "Follow" state
    }
  }

  async function toggleFollow() {
    if (loading.value) return;

    // Optimistic update
    const previousState = isFollowing.value;
    isFollowing.value = !previousState;
    loading.value = true;

    try {
      if (previousState) {
        await followApi.unfollowUser(targetUserId);
      } else {
        await followApi.followUser(targetUserId);
      }
    } catch {
      // Rollback on error
      isFollowing.value = previousState;
      throw new Error("Failed to update follow status");
    } finally {
      loading.value = false;
    }
  }

  return {
    isFollowing: computed(() => isFollowing.value),
    loading: computed(() => loading.value),
    fetchStatus,
    toggleFollow,
  };
}
