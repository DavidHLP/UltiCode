import {
  ref,
  shallowRef,
  watch,
  type Ref,
  type ComputedRef,
  computed,
  toValue,
  type MaybeRefOrGetter,
} from 'vue'
import { watchDebounced } from '@vueuse/core'

export interface PaginationState {
  pageIndex: number
  pageSize: number
}

// Simplified store interface - user passes the actual store object
// Accepts Ref, ComputedRef, or getter functions - toValue() handles unwrapping
export interface UseDataTableOptions<TData, TFilters, TParams> {
  store: {
    data: MaybeRefOrGetter<TData[]>
    total: MaybeRefOrGetter<number>
    isLoading: MaybeRefOrGetter<boolean>
    error: MaybeRefOrGetter<string | null>
    fetch: (params: TParams) => Promise<void>
  }
  filters?: MaybeRefOrGetter<TFilters>
  transformParams: (params: {
    search: string | undefined
    filters: TFilters
    page: number
    limit: number
  }) => TParams
  debounceMs?: number
  autoLoad?: boolean
}

export interface UseDataTableReturn<TData> {
  searchQuery: Ref<string>
  tablePagination: Ref<PaginationState>
  selectedRows: Ref<TData[]>
  loading: ComputedRef<boolean>
  data: ComputedRef<TData[]>
  total: ComputedRef<number>
  error: ComputedRef<string | null>
  loadEntities: () => Promise<void>
}

export function useDataTable<
  TData = unknown,
  TFilters extends Record<string, unknown> = Record<string, never>,
  TParams = unknown,
>(options: UseDataTableOptions<TData, TFilters, TParams>): UseDataTableReturn<TData> {
  const {
    store,
    filters = {} as MaybeRefOrGetter<TFilters>,
    transformParams,
    debounceMs = 500,
    autoLoad = false,
  } = options

  const searchQuery = ref('')
  const tablePagination = ref<PaginationState>({ pageIndex: 0, pageSize: 10 })
  const selectedRows = shallowRef<TData[]>([])

  // Initial load state - ensures loading state is true on first page load
  // This prevents showing empty state before data arrives
  const initialLoad = ref(true)

  // Use toValue() to properly unwrap nested refs (handles both ref and computed)
  // Include initialLoad to ensure skeleton is shown during first data fetch
  const loading = computed(() => initialLoad.value || toValue(store.isLoading) || false)
  const data = computed(() => toValue(store.data) || [])
  const total = computed(() => toValue(store.total) || 0)
  const error = computed(() => toValue(store.error) || null)

  const loadEntities = async () => {
    const currentFilters = toValue(filters)
    const params = transformParams({
      search: searchQuery.value || undefined,
      filters: currentFilters,
      page: tablePagination.value.pageIndex + 1,
      limit: tablePagination.value.pageSize,
    })
    try {
      await store.fetch(params as TParams)
    } finally {
      // Clear initial load state after first fetch completes
      // This ensures subsequent loads only show actual loading state
      if (initialLoad.value) {
        initialLoad.value = false
      }
    }
  }

  // Debounced search watcher
  watchDebounced(
    searchQuery,
    () => {
      tablePagination.value.pageIndex = 0
      loadEntities()
    },
    { debounce: debounceMs },
  )

  // Watch filters and reset page - use getter to support reactive refs
  watch(
    () => toValue(filters),
    () => {
      tablePagination.value.pageIndex = 0
      loadEntities()
    },
    { deep: true },
  )

  // Watch pagination changes
  watch(
    () => tablePagination.value,
    () => loadEntities(),
    { deep: true },
  )

  // Auto-load on mount if requested
  if (autoLoad) {
    loadEntities()
  }

  return {
    searchQuery,
    tablePagination,
    selectedRows,
    loading,
    data,
    total,
    error,
    loadEntities,
  }
}
