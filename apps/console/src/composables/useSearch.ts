import { ref } from "vue";
import { searchApi } from "@/api/search";
import type { SearchResult } from "@/types/search";

export interface UseSearchOptions {
  debounceMs?: number;
  limit?: number;
}

export interface UseSearchReturn {
  query: ReturnType<typeof ref<string>>;
  results: ReturnType<typeof ref<SearchResult[]>>;
  loading: ReturnType<typeof ref<boolean>>;
  error: ReturnType<typeof ref<string | null>>;
  total: ReturnType<typeof ref<number>>;
  selectedIndex: ReturnType<typeof ref<number>>;
  isOpen: ReturnType<typeof ref<boolean>>;
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

  const search = async (q: string) => {
    query.value = q;

    if (!q.trim()) {
      results.value = [];
      total.value = 0;
      return;
    }

    // Clear previous timer
    if (debounceTimer) {
      clearTimeout(debounceTimer);
    }

    // Debounce the search
    debounceTimer = setTimeout(async () => {
      loading.value = true;
      error.value = null;

      try {
        const response = await searchApi.search({
          query: q,
          limit,
        });

        results.value = response.results;
        total.value = response.total;
        selectedIndex.value = 0;
      } catch (err) {
        error.value = err instanceof Error ? err.message : "Search failed";
        results.value = [];
        total.value = 0;
      } finally {
        loading.value = false;
      }
    }, debounceMs);
  };

  const open = () => {
    isOpen.value = true;
  };

  const close = () => {
    isOpen.value = false;
    selectedIndex.value = 0;
  };

  const selectNext = () => {
    if (results.value.length === 0) return;
    selectedIndex.value = (selectedIndex.value + 1) % results.value.length;
  };

  const selectPrev = () => {
    if (results.value.length === 0) return;
    selectedIndex.value =
      (selectedIndex.value - 1 + results.value.length) % results.value.length;
  };

  const selectCurrent = () => {
    const selected = results.value[selectedIndex.value];
    if (selected) {
      window.location.href = selected.url;
      close();
    }
  };

  const clear = () => {
    query.value = "";
    results.value = [];
    total.value = 0;
    selectedIndex.value = 0;
    error.value = null;
  };

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
