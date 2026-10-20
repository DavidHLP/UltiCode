import { computed, ref } from "vue";
import { defineStore } from "pinia";
import {
  fetchFeaturedProblemLists,
  fetchProblemListsOverview,
} from "@/api/problem-list";
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
  const emptyOverview = (): UserProblemListsResponse => ({
    ownLists: [],
    savedLists: [],
    featuredLists: [],
    categories: [],
  });
  const data = ref<UserProblemListsResponse>(emptyOverview());
  const isLoading = ref(false);
  const loadError = ref(false);
  let inflight: Promise<void> | null = null;
  let inflightUserId: string | null | undefined;
  let loadedUserId: string | null | undefined;
  let requestId = 0;

  async function loadOverview(force = false): Promise<void> {
    const authStore = useAuthStore();
    const userId = authStore.fetchCurrentUserId();

    if (inflight && inflightUserId === userId) return inflight;
    if (!force && !inflight && loadedUserId === userId) return;
    if (loadedUserId !== userId) {
      data.value = emptyOverview();
      loadedUserId = undefined;
    }

    isLoading.value = true;
    loadError.value = false;
    const currentRequestId = ++requestId;
    const promise = (async () => {
      try {
        const nextData = userId
          ? await fetchProblemListsOverview()
          : {
              ...emptyOverview(),
              featuredLists: await fetchFeaturedProblemLists(),
            };
        if (
          currentRequestId !== requestId ||
          authStore.fetchCurrentUserId() !== userId
        ) {
          return;
        }
        data.value = nextData;
        loadedUserId = userId;
      } catch (error) {
        if (
          currentRequestId !== requestId ||
          authStore.fetchCurrentUserId() !== userId
        ) {
          return;
        }
        if (error instanceof ApiError && error.code === -1) return;
        console.error("Failed to load problem lists overview", error);
        loadError.value = true;
      } finally {
        if (currentRequestId === requestId) isLoading.value = false;
      }
    })();
    inflight = promise;
    inflightUserId = userId;
    try {
      await promise;
    } finally {
      if (currentRequestId === requestId) {
        inflight = null;
        inflightUserId = undefined;
      }
    }
  }

  function reset() {
    requestId += 1;
    data.value = emptyOverview();
    isLoading.value = false;
    loadError.value = false;
    inflight = null;
    inflightUserId = undefined;
    loadedUserId = undefined;
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