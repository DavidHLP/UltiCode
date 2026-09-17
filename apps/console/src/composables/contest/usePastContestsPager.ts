import { computed, ref, watch, type ComputedRef, type Ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useContestBrowseStore } from "@/stores/contestBrowse";

export const PAST_CONTESTS_PAGE_SIZE = 10;

export interface PastContestsPager {
  page: Ref<number>;
  pageSize: number;
  totalPages: ComputedRef<number>;
  loading: Ref<boolean>;
  error: Ref<string | null>;
  loadPage: (nextPage?: number) => Promise<void>;
  loadInitialPage: () => Promise<void>;
}

function parsePage(value: unknown): number {
  const rawValue = Array.isArray(value) ? value[0] : value;
  const parsedPage = Number(rawValue);

  return Number.isInteger(parsedPage) && parsedPage > 0 ? parsedPage : 1;
}

export function usePastContestsPager(): PastContestsPager {
  const route = useRoute();
  const router = useRouter();
  const contestStore = useContestBrowseStore();

  const page = ref(parsePage(route.query.page));
  const loading = ref(false);
  const error = ref<string | null>(null);
  const totalPages = computed(() =>
    Math.ceil(contestStore.pastContestsTotal / PAST_CONTESTS_PAGE_SIZE),
  );

  let initialized = false;
  let skipPageWatch: number | null = null;

  async function transition(
    nextPage: number,
    syncRoute: boolean,
  ): Promise<void> {
    const resolvedPage = parsePage(nextPage);
    if (page.value !== resolvedPage) {
      skipPageWatch = resolvedPage;
      page.value = resolvedPage;
    }

    loading.value = true;
    error.value = null;
    try {
      await contestStore.loadPastContests(
        resolvedPage,
        PAST_CONTESTS_PAGE_SIZE,
      );
      if (syncRoute) {
        await router.replace({
          query: { ...route.query, page: resolvedPage },
        });
      }
    } catch (err) {
      error.value =
        err instanceof Error && err.message
          ? err.message
          : "Failed to load past contests";
      throw err;
    } finally {
      loading.value = false;
    }
  }

  async function loadPage(nextPage: number = page.value): Promise<void> {
    await transition(nextPage, true);
  }

  watch(page, (nextPage, previousPage) => {
    if (skipPageWatch === nextPage) {
      skipPageWatch = null;
      return;
    }

    if (!initialized || nextPage === previousPage) {
      return;
    }

    void loadPage(nextPage).catch(() => {
      // The pager owns the user-visible transition error state.
    });
  });

  async function loadInitialPage(): Promise<void> {
    initialized = true;
    await transition(page.value, false);
  }

  return {
    page,
    pageSize: PAST_CONTESTS_PAGE_SIZE,
    totalPages,
    loading,
    error,
    loadPage,
    loadInitialPage,
  };
}
