import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { fetchProblemListsOverview } from "@/api/problem-list";
import { ApiError } from "@/utils/request";
import { useAuthStore } from "@/stores/auth";
import type { UserProblemListsResponse } from "@/types/problem-list";

/**
 * Shared problem-lists overview state.
 *
 * `FeaturedBanners` (题库页头部轮播) and the sidebar `useSidebarLists` composable
 * (题库页/问题页左下"我的题单") both consume this store instead of issuing
 * independent requests to `/problem-lists/overview`. This guarantees a single
 * source of truth: when the request fails, every consumer sees the same
 * `loadError` and shows a consistent failure state.
 */
export const useProblemListsStore = defineStore("problem-lists", () => {
  const data = ref<UserProblemListsResponse>({
    ownLists: [],
    savedLists: [],
    featuredLists: [],
    categories: [],
  });
  const isLoading = ref(false);
  const loadError = ref(false);
  let inflight: Promise<void> | null = null;

  async function loadOverview(force = false): Promise<void> {
    if (inflight) return inflight;

    if (
      !force &&
      !isLoading.value &&
      data.value.ownLists.length === 0 &&
      data.value.savedLists.length === 0 &&
      data.value.featuredLists.length === 0 &&
      data.value.categories.length === 0
    ) {
      // cold cache; fall through to fetch
    } else if (!force) {
      // already populated; skip unless caller forces a refresh
      return;
    }

    const authStore = useAuthStore();
    if (!authStore.isAuthenticated) {
      isLoading.value = false;
      loadError.value = false;
      return;
    }

    isLoading.value = true;
    loadError.value = false;
    const promise = (async () => {
      try {
        data.value = await fetchProblemListsOverview();
      } catch (e) {
        if (e instanceof ApiError && e.code === -1) return;
        console.error("Failed to load problem lists overview", e);
        loadError.value = true;
      } finally {
        isLoading.value = false;
      }
    })();
    inflight = promise;
    try {
      await promise;
    } finally {
      inflight = null;
    }
  }

  function reset() {
    data.value = {
      ownLists: [],
      savedLists: [],
      featuredLists: [],
      categories: [],
    };
    isLoading.value = false;
    loadError.value = false;
    inflight = null;
  }

  const ownLists = computed(() => data.value.ownLists);
  const savedLists = computed(() => data.value.savedLists);
  const featuredLists = computed(() => data.value.featuredLists);
  const categories = computed(() => data.value.categories);

  return {
    data,
    ownLists,
    savedLists,
    featuredLists,
    categories,
    isLoading,
    loadError,
    loadOverview,
    reset,
  };
});