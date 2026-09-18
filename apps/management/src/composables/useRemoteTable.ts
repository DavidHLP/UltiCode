import {
  computed,
  readonly,
  ref,
  shallowRef,
  toValue,
  type ComputedRef,
  type DeepReadonly,
  type MaybeRefOrGetter,
} from 'vue'
import { tryOnScopeDispose } from '@vueuse/core'

export interface PaginationState {
  pageIndex: number
  pageSize: number
}

export interface RemoteTableQuery<TFilters> {
  search: string
  filters: TFilters
  pagination: PaginationState
}

export interface RemoteTableQueryOptions<TFilters> {
  search?: string
  filters: TFilters
  pagination?: Partial<PaginationState>
}

interface RemoteTableTransition<TFilters> {
  search?: string
  filters?: TFilters
  pagination?: Partial<PaginationState>
}

export interface RemoteTableRouteAdapter<TFilters> {
  read: () => Partial<RemoteTableQuery<TFilters>>
  write?: (query: RemoteTableQuery<TFilters>) => void
  subscribe?: (onChange: (query: Partial<RemoteTableQuery<TFilters>>) => void) => () => void
  writeDebounceMs?: number
}

export interface UseRemoteTableOptions<TData, TFilters, TParams> {
  store: {
    items: MaybeRefOrGetter<readonly (TData | DeepReadonly<TData>)[]>
    total: MaybeRefOrGetter<number>
    isLoading: MaybeRefOrGetter<boolean>
    error: MaybeRefOrGetter<string | null>
    fetch: (params?: TParams, options?: { signal?: AbortSignal }) => Promise<void>
  }
  initialQuery: RemoteTableQueryOptions<TFilters>
  toParams: (params: {
    search: string | undefined
    filters: TFilters
    page: number
    limit: number
  }) => TParams
  debounceMs?: number
  autoLoad?: boolean
  showInitialLoading?: boolean
  route?: RemoteTableRouteAdapter<TFilters>
}

export interface UseRemoteTableReturn<TData, TFilters> {
  query: ComputedRef<DeepReadonly<RemoteTableQuery<TFilters>>>
  searchQuery: ComputedRef<string>
  tablePagination: ComputedRef<PaginationState>
  loading: ComputedRef<boolean>
  data: ComputedRef<TData[]>
  total: ComputedRef<number>
  error: ComputedRef<string | null>
  setSearch: (search: string) => void
  setFilters: (filters: TFilters) => Promise<void> | void
  setPagination: (pagination: PaginationState) => Promise<void> | void
  refresh: () => Promise<void>
}

export function useRemoteTable<
  TData = unknown,
  TFilters = Record<string, never>,
  TParams = unknown,
>(
  options: UseRemoteTableOptions<TData, TFilters, TParams>,
): UseRemoteTableReturn<TData, TFilters> {
  const {
    store,
    initialQuery,
    toParams,
    debounceMs = 500,
    autoLoad = false,
    showInitialLoading = true,
    route,
  } = options

  const routeQuery = route?.read() ?? {}
  const initialFilters = routeQuery.filters ?? initialQuery.filters
  const initialPagination = {
    pageIndex: 0,
    pageSize: 10,
    ...initialQuery.pagination,
    ...routeQuery.pagination,
  }
  const query = shallowRef<RemoteTableQuery<TFilters>>({
    search: routeQuery.search ?? initialQuery.search ?? '',
    filters: initialFilters,
    pagination: initialPagination,
  })
  const readonlyQuery = computed<DeepReadonly<RemoteTableQuery<TFilters>>>(
    () => readonly(query.value) as DeepReadonly<RemoteTableQuery<TFilters>>,
  )
  const initialLoad = ref(showInitialLoading)
  const pendingRequests = ref(0)
  let searchTimer: ReturnType<typeof setTimeout> | undefined
  let routeTimer: ReturnType<typeof setTimeout> | undefined
  let requestSequence = 0
  const activeControllers = new Set<AbortController>()

  const loading = computed(
    () => initialLoad.value || pendingRequests.value > 0 || toValue(store.isLoading) || false,
  )
  const data = computed<TData[]>(() => {
    const items = toValue(store.items) ?? []
    return [...items] as TData[]
  })
  const total = computed(() => toValue(store.total) ?? 0)
  const error = computed(() => toValue(store.error) || null)
  const searchQuery = computed({
    get: () => query.value.search,
    set: (search: string) => setSearch(search),
  })
  const tablePagination = computed({
    get: () => ({ ...query.value.pagination }),
    set: (pagination: PaginationState) => setPagination(pagination),
  })

  function cancelSearchTimer(): void {
    if (searchTimer) {
      clearTimeout(searchTimer)
      searchTimer = undefined
    }
  }

  function cancelRouteTimer(): void {
    if (routeTimer) {
      clearTimeout(routeTimer)
      routeTimer = undefined
    }
  }

  function abortActiveRequests(): void {
    requestSequence += 1
    // Keep controllers until each loader settles so disposal also covers
    // loaders that do not honor the signal immediately.
    for (const controller of activeControllers) controller.abort()
  }

  function disposeActiveRequests(): void {
    abortActiveRequests()
    activeControllers.clear()
  }

  async function loadCurrent(): Promise<void> {
    const request = ++requestSequence
    const controller = new AbortController()
    activeControllers.add(controller)
    pendingRequests.value += 1
    const current = query.value
    const params = toParams({
      search: current.search || undefined,
      filters: current.filters,
      page: current.pagination.pageIndex + 1,
      limit: current.pagination.pageSize,
    })

    try {
      await store.fetch(params, { signal: controller.signal })
    } finally {
      activeControllers.delete(controller)
      pendingRequests.value -= 1
      if (request === requestSequence) initialLoad.value = false
    }
  }

  function writeRoute(): void {
    if (!route?.write) return
    cancelRouteTimer()
    const write = () => {
      routeTimer = undefined
      // Read the latest query when the timer fires so a pagination change that
      // lands inside the debounce window is still written together with the
      // search/filter change that scheduled this write.
      route.write?.(query.value)
    }
    if (route.writeDebounceMs) routeTimer = setTimeout(write, route.writeDebounceMs)
    else write()
  }

  function transition(
    patch: RemoteTableTransition<TFilters>,
    transitionOptions: {
      debounce?: boolean
      resetPage?: boolean
      writeRoute?: boolean
    } = {},
  ): Promise<void> | void {
    abortActiveRequests()
    const current = query.value
    const nextQuery: RemoteTableQuery<TFilters> = {
      search: patch.search ?? current.search,
      filters: patch.filters ?? current.filters,
      pagination: {
        ...current.pagination,
        ...patch.pagination,
      },
    }
    if (transitionOptions.resetPage) nextQuery.pagination.pageIndex = 0

    query.value = nextQuery
    // Transitions that must not write the route themselves (pagination,
    // refresh, route-driven updates) leave an already pending debounced write
    // alive: that write reads the latest query when it fires, so the URL
    // converges to the combined search + filters + page state instead of being
    // left with the pre-transition state.
    if (transitionOptions.writeRoute !== false) writeRoute()
    if (transitionOptions.debounce) {
      cancelSearchTimer()
      searchTimer = setTimeout(() => {
        searchTimer = undefined
        void loadCurrent()
      }, debounceMs)
      return
    }
    cancelSearchTimer()
    return loadCurrent()
  }

  function setSearch(search: string): void {
    transition({ search }, { debounce: true, resetPage: true })
  }

  function setFilters(filters: TFilters): Promise<void> | void {
    return transition({ filters }, { resetPage: true })
  }

  function setPagination(pagination: PaginationState): Promise<void> | void {
    return transition({ pagination }, { writeRoute: false })
  }


  function refresh(): Promise<void> {
    abortActiveRequests()
    cancelSearchTimer()
    return loadCurrent()
  }

  const stopRoute = route?.subscribe?.((nextQuery) => {
    cancelSearchTimer()
    cancelRouteTimer()
    transition(nextQuery, { writeRoute: false })
  })

  tryOnScopeDispose(() => {
    cancelSearchTimer()
    cancelRouteTimer()
    disposeActiveRequests()
    stopRoute?.()
  })

  if (autoLoad) void loadCurrent()

  return {
    query: readonlyQuery,
    searchQuery,
    tablePagination,
    loading,
    data,
    total,
    error,
    setSearch,
    setFilters,
    setPagination,
    refresh,
  }
}
