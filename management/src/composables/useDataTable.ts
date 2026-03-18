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
export interface UseDataTableOptions<TData, TFilters, TParams> {
  store: {
    data: Ref<TData[]>
    total: Ref<number>
    isLoading: Ref<boolean>
    error: Ref<string | null>
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

  // Wrap store refs in computed to avoid type issues with Pinia
  const loading = computed(() => store.isLoading.value || false)
  const data = computed(() => store.data.value || [])
  const total = computed(() => store.total.value || 0)
  const error = computed(() => store.error.value || null)

  const loadEntities = async () => {
    const currentFilters = toValue(filters)
    const params = transformParams({
      search: searchQuery.value || undefined,
      filters: currentFilters,
      page: tablePagination.value.pageIndex + 1,
      limit: tablePagination.value.pageSize,
    })
    await store.fetch(params as TParams)
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
