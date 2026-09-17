import { effectScope, ref } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  useRemoteTable,
  type RemoteTableQuery,
  type UseRemoteTableOptions,
} from './useRemoteTable'

interface Row {
  id: string
  title: string
}

interface Filters {
  status: string
}

interface Params {
  search?: string
  status?: string
  page: number
  limit: number
}

function createStore() {
  return {
    items: ref<Row[]>([]),
    total: ref(0),
    isLoading: ref(false),
    error: ref<string | null>(null),
    fetch: vi.fn().mockResolvedValue(undefined),
  }
}

function createTable(
  options: Partial<UseRemoteTableOptions<Row, Filters, Params>> = {},
) {
  const store = createStore()
  const table = useRemoteTable<Row, Filters, Params>({
    store,
    initialQuery: { filters: { status: 'all' }, pagination: { pageIndex: 2, pageSize: 10 } },
    toParams: ({ search, filters, page, limit }) => ({
      search,
      status: filters.status === 'all' ? undefined : filters.status,
      page,
      limit,
    }),
    ...options,
  })
  return { store, table }
}

afterEach(() => {
  vi.useRealTimers()
})

describe('useRemoteTable', () => {
  it('keeps the initial query and skeleton loading until the first fetch', () => {
    const { store, table } = createTable()
    expect(table).not.toHaveProperty('transition')
    expect(table).not.toHaveProperty('reset')
    expect(table.query.value.pagination).toEqual({ pageIndex: 2, pageSize: 10 })
    expect(table.query.value.filters).toEqual({ status: 'all' })
    expect(store.fetch).not.toHaveBeenCalled()
    expect(table.loading.value).toBe(true)
  })

  it('debounces search and resets the page through one transition', async () => {
    vi.useFakeTimers()
    const { store, table } = createTable()

    table.setSearch('graphs')
    expect(table.query.value).toMatchObject({
      search: 'graphs',
      pagination: { pageIndex: 0, pageSize: 10 },
    })
    expect(store.fetch).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(500)

    expect(store.fetch).toHaveBeenCalledTimes(1)
    expect(store.fetch).toHaveBeenCalledWith({
      search: 'graphs',
      status: undefined,
      page: 1,
      limit: 10,
    })
  })

  it('coalesces a pending search with a filter transition', async () => {
    vi.useFakeTimers()
    const { store, table } = createTable()

    table.setSearch('graphs')
    await table.setFilters({ status: 'published' })
    expect(store.fetch).toHaveBeenCalledTimes(1)
    expect(store.fetch).toHaveBeenCalledWith({
      search: 'graphs',
      status: 'published',
      page: 1,
      limit: 10,
    })

    await vi.advanceTimersByTimeAsync(500)
    expect(store.fetch).toHaveBeenCalledTimes(1)
  })

  it('resets pagination once when filters change', async () => {
    const { store, table } = createTable()

    await table.setFilters({ status: 'published' })

    expect(table.query.value.pagination.pageIndex).toBe(0)
    expect(store.fetch).toHaveBeenCalledTimes(1)
    expect(store.fetch).toHaveBeenCalledWith({
      search: undefined,
      status: 'published',
      page: 1,
      limit: 10,
    })
  })

  it('refreshes the current page without changing query state', async () => {
    const { store, table } = createTable()

    await table.refresh()

    expect(table.query.value.pagination).toEqual({ pageIndex: 2, pageSize: 10 })
    expect(store.fetch).toHaveBeenCalledWith({
      search: undefined,
      status: undefined,
      page: 3,
      limit: 10,
    })
  })

  it('uses route state and debounces route writes', async () => {
    vi.useFakeTimers()
    const writes: RemoteTableQuery<Filters>[] = []
    const { table } = createTable({
      route: {
        read: () => ({
          search: 'route search',
          filters: { status: 'draft' },
          pagination: { pageIndex: 1, pageSize: 10 },
        }),
        write: (query) => writes.push(query),
        writeDebounceMs: 300,
      },
    })

    expect(table.query.value).toMatchObject({
      search: 'route search',
      filters: { status: 'draft' },
      pagination: { pageIndex: 1, pageSize: 10 },
    })

    await table.setFilters({ status: 'published' })
    expect(writes).toHaveLength(0)
    await vi.advanceTimersByTimeAsync(300)
    expect(writes).toHaveLength(1)
    expect(writes[0].filters).toEqual({ status: 'published' })
    expect(writes[0].pagination.pageIndex).toBe(0)

    await table.setPagination({ pageIndex: 4, pageSize: 10 })
    await vi.advanceTimersByTimeAsync(300)
    expect(writes).toHaveLength(1)
  })

  it('keeps a pending route write across pagination and cancels it for external navigation', async () => {
    vi.useFakeTimers()
    const writes: RemoteTableQuery<Filters>[] = []
    let onRouteChange:
      | ((query: Partial<RemoteTableQuery<Filters>>) => void)
      | undefined
    const { table } = createTable({
      route: {
        read: () => ({}),
        write: (query) => writes.push(query),
        subscribe: (onChange) => {
          onRouteChange = onChange
          return () => undefined
        },
        writeDebounceMs: 300,
      },
    })

    await table.setFilters({ status: 'draft' })
    await table.setPagination({ pageIndex: 4, pageSize: 10 })
    await vi.advanceTimersByTimeAsync(300)
    expect(writes).toHaveLength(1)
    expect(writes[0].filters).toEqual({ status: 'draft' })
    expect(writes[0].pagination.pageIndex).toBe(4)

    await table.setFilters({ status: 'published' })
    onRouteChange?.({ filters: { status: 'external' } })
    await vi.advanceTimersByTimeAsync(300)
    expect(writes).toHaveLength(1)
    expect(table.query.value.filters).toEqual({ status: 'external' })
  })

  it('applies external route changes without writing them back', async () => {
    let onRouteChange:
      | ((query: Partial<RemoteTableQuery<Filters>>) => void)
      | undefined
    const writes: RemoteTableQuery<Filters>[] = []
    const { store, table } = createTable({
      route: {
        read: () => ({}),
        write: (query) => writes.push(query),
        subscribe: (onChange) => {
          onRouteChange = onChange
          return () => undefined
        },
      },
    })

    onRouteChange?.({
      search: 'external',
      filters: { status: 'draft' },
      pagination: { pageIndex: 4, pageSize: 20 },
    })
    await vi.waitFor(() => expect(store.fetch).toHaveBeenCalledTimes(1))

    expect(table.query.value).toEqual({
      search: 'external',
      filters: { status: 'draft' },
      pagination: { pageIndex: 4, pageSize: 20 },
    })
    expect(writes).toHaveLength(0)
  })

  it('does not clear initial loading for a stale request', async () => {
    const pending: Array<() => void> = []
    const store = createStore()
    store.fetch.mockImplementation(
      () => new Promise<void>((resolve) => pending.push(resolve)),
    )
    const table = useRemoteTable<Row, Filters, Params>({
      store,
      initialQuery: { filters: { status: 'all' } },
      toParams: ({ filters, page, limit }) => ({
        status: filters.status,
        page,
        limit,
      }),
    })

    const first = table.setFilters({ status: 'draft' })
    const second = table.setFilters({ status: 'published' })
    expect(table.loading.value).toBe(true)

    pending[0]()
    await first
    expect(table.loading.value).toBe(true)

    pending[1]()
    await second
    expect(table.loading.value).toBe(false)
  })

  it('aborts the active request when the table scope is disposed', async () => {
    const scope = effectScope()
    const store = createStore()
    let resolveFetch!: () => void
    const fetchDone = new Promise<void>((resolve) => {
      resolveFetch = resolve
    })
    store.fetch.mockImplementation(
      (_params, options?: { signal?: AbortSignal }) => {
        expect(options?.signal).toBeDefined()
        return fetchDone
      },
    )

    let table!: ReturnType<typeof useRemoteTable<Row, Filters, Params>>
    scope.run(() => {
      table = useRemoteTable<Row, Filters, Params>({
        store,
        initialQuery: { filters: { status: 'all' } },
        toParams: ({ filters, page, limit }) => ({
          status: filters.status,
          page,
          limit,
        }),
      })
    })

    const loading = table.setFilters({ status: 'draft' })
    const fetchOptions = store.fetch.mock.calls[0]?.[1] as
      | { signal?: AbortSignal }
      | undefined
    expect(fetchOptions?.signal?.aborted).toBe(false)

    scope.stop()

    expect(fetchOptions?.signal?.aborted).toBe(true)
    resolveFetch()
    await loading
  })

  it('exposes reactive data and loading from the collection', () => {
    const { store, table } = createTable()
    store.items.value = [{ id: '1', title: 'First' }]
    store.total.value = 1
    store.isLoading.value = true

    expect(table.data.value).toEqual([{ id: '1', title: 'First' }])
    expect(table.total.value).toBe(1)
    expect(table.loading.value).toBe(true)
    expect(table.searchQuery.value).toBe('')
  })
})
