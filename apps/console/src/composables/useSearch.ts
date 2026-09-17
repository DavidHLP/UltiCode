import { onScopeDispose, ref, type Ref } from "vue";
import { searchApi } from "@/api/search";
import type { SearchResult } from "@/types/search";

export interface UseSearchOptions {
  debounceMs?: number;
  limit?: number;
}

export interface UseSearchReturn {
  query: Ref<string>;
  results: Ref<SearchResult[]>;
  loading: Ref<boolean>;
  error: Ref<string | null>;
  total: Ref<number>;
  selectedIndex: Ref<number>;
  isOpen: Ref<boolean>;
  search: (q: string) => void;
  open: () => void;
  close: () => void;
  selectNext: () => void;
  selectPrev: () => void;
  selectCurrent: () => void;
  clear: () => void;
}

export function useSearch(options: UseSearchOptions = {}): UseSearchReturn {
  const { debounceMs = 300, limit = 10 } = options;

  const query = ref("");
  const results = ref<SearchResult[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);
  const total = ref(0);
  const selectedIndex = ref(0);
  const isOpen = ref(false);

  let debounceTimer: ReturnType<typeof setTimeout> | null = null;
  let activeController: AbortController | null = null;
  let latestRequest = 0;

  function cancelPendingSearch(): void {
    latestRequest += 1;

    if (debounceTimer) {
      clearTimeout(debounceTimer);
      debounceTimer = null;
    }

    activeController?.abort();
    activeController = null;
    loading.value = false;
  }

  const search = (q: string): void => {
    cancelPendingSearch();
    const requestId = latestRequest;

    query.value = q;
    error.value = null;
    results.value = [];
    total.value = 0;
    selectedIndex.value = 0;

    if (!q.trim()) {
      return;
    }

    debounceTimer = setTimeout(() => {
      debounceTimer = null;
      if (requestId !== latestRequest) {
        return;
      }

      const controller = new AbortController();
      activeController = controller;
      loading.value = true;

      void searchApi
        .search({ query: q, limit }, controller.signal)
        .then((response) => {
          if (requestId !== latestRequest || controller.signal.aborted) {
            return;
          }

          results.value = response.results;
          total.value = response.total;
          selectedIndex.value = 0;
        })
        .catch((err: unknown) => {
          if (requestId !== latestRequest || controller.signal.aborted) {
            return;
          }

          error.value = err instanceof Error ? err.message : "Search failed";
          results.value = [];
          total.value = 0;
        })
        .finally(() => {
          if (requestId === latestRequest) {
            loading.value = false;
            if (activeController === controller) {
              activeController = null;
            }
          }
        });
    }, debounceMs);
  };

  const open = (): void => {
    isOpen.value = true;
  };

  const close = (): void => {
    cancelPendingSearch();
    isOpen.value = false;
    selectedIndex.value = 0;
  };

  const selectNext = (): void => {
    if (results.value.length === 0) return;
    selectedIndex.value = (selectedIndex.value + 1) % results.value.length;
  };

  const selectPrev = (): void => {
    if (results.value.length === 0) return;
    selectedIndex.value =
      (selectedIndex.value - 1 + results.value.length) % results.value.length;
  };

  const selectCurrent = (): void => {
    const selected = results.value[selectedIndex.value];
    if (selected) {
      window.location.href = selected.url;
      close();
    }
  };

  const clear = (): void => {
    cancelPendingSearch();
    query.value = "";
    results.value = [];
    total.value = 0;
    selectedIndex.value = 0;
    error.value = null;
  };

  onScopeDispose(cancelPendingSearch);

  return {
    query,
    results,
    loading,
    error,
    total,
    selectedIndex,
    isOpen,
    search,
    open,
    close,
    selectNext,
    selectPrev,
    selectCurrent,
    clear,
  };
}
