import { effectScope, ref, type Ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuditLog, AuditStats } from '@/api/admin/audit'
import {
  toAuditLogQueryParams,
  toAuditStatsQueryParams,
  useAuditReadWorkspace,
  type AuditFilters,
} from './useAuditReadWorkspace'

interface MockAuditStore {
  logs: Ref<AuditLog[]>
  total: Ref<number>
  loading: Ref<boolean>
  error: Ref<string | null>
  stats: Ref<AuditStats | null>
  statsLoading: Ref<boolean>
  statsError: Ref<string | null>
  fetchLogs: ReturnType<typeof vi.fn>
  fetchStats: ReturnType<typeof vi.fn>
  cancelStats: ReturnType<typeof vi.fn>
  cancel: ReturnType<typeof vi.fn>
}

const mockedStore = vi.hoisted(() => ({ current: null as MockAuditStore | null }))

vi.mock('pinia', async (importOriginal) => {
  const actual = await importOriginal<typeof import('pinia')>()
  return {
    ...actual,
    storeToRefs: (store: MockAuditStore) => store,
  }
})

vi.mock('@/stores/admin/audit', () => ({
  useAuditStore: () => mockedStore.current,
}))

function createStore(): MockAuditStore {
  return {
    logs: ref([]),
    total: ref(0),
    loading: ref(false),
    error: ref(null),
    stats: ref(null),
    statsLoading: ref(false),
    statsError: ref(null),
    fetchLogs: vi.fn().mockResolvedValue(undefined),
    fetchStats: vi.fn().mockResolvedValue(null),
    cancelStats: vi.fn(),
    cancel: vi.fn(),
  }
}

function runWorkspace(store: MockAuditStore) {
  mockedStore.current = store
  const scope = effectScope()
  let workspace!: ReturnType<typeof useAuditReadWorkspace>
  scope.run(() => {
    workspace = useAuditReadWorkspace()
  })
  return { scope, workspace }
}

afterEach(() => {
  vi.useRealTimers()
  mockedStore.current = null
})

describe('toAuditLogQueryParams', () => {
  it('normalizes sentinels, dates, and table pagination in one place', () => {
    const params = toAuditLogQueryParams({
      search: ' rotate ',
      filters: {
        action: 'all',
        entityType: 'USER',
        startDate: '2026-09-01',
        endDate: '2026-09-19',
        performerId: '',
        userId: 'user-1',
      },
      pagination: { pageIndex: 2, pageSize: 25 },
    })

    expect(params).toEqual({
      search: ' rotate ',
      action: undefined,
      entityType: 'USER',
      startDate: '2026-09-01T00:00:00',
      endDate: '2026-09-20T00:00:00',
      performerId: undefined,
      userId: 'user-1',
      page: 3,
      limit: 25,
    })
  })
})

describe('toAuditStatsQueryParams', () => {
  const filters: AuditFilters = {
    action: 'all',
    entityType: 'USER',
    startDate: '2026-09-01',
    endDate: '2026-09-19',
    performerId: '',
    userId: 'user-1',
  }

  it('applies the same sentinel, blank and date rules as the log query', () => {
    const stats = toAuditStatsQueryParams({ search: 'rotate', filters })

    expect(stats).toEqual({
      search: 'rotate',
      action: undefined,
      entityType: 'USER',
      startDate: '2026-09-01T00:00:00',
      endDate: '2026-09-20T00:00:00',
      performerId: undefined,
      userId: 'user-1',
    })
    const logs = toAuditLogQueryParams({
      search: 'rotate',
      filters,
      pagination: { pageIndex: 0, pageSize: 50 },
    })
    expect(logs).toEqual({ ...stats, page: 1, limit: 50 })
  })

  it('drops every unset filter', () => {
    expect(
      toAuditStatsQueryParams({
        search: '',
        filters: {
          action: 'all',
          entityType: 'all',
          startDate: '',
          endDate: '',
          performerId: '',
          userId: '',
        },
      }),
    ).toEqual({
      search: undefined,
      action: undefined,
      entityType: undefined,
      startDate: undefined,
      endDate: undefined,
      performerId: undefined,
      userId: undefined,
    })
  })
})

describe('useAuditReadWorkspace', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  it('cancels stale statistics on query changes and when its scope is disposed', async () => {
    const store = createStore()
    const { scope, workspace } = runWorkspace(store)

    await vi.advanceTimersByTimeAsync(500)
    expect(store.fetchStats).toHaveBeenCalledTimes(1)

    await workspace.setFilters({
      action: 'UPDATE_USER',
      entityType: 'all',
      startDate: '',
      endDate: '',
      performerId: '',
      userId: '',
    })
    expect(store.cancelStats).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(500)
    expect(store.fetchStats).toHaveBeenCalledTimes(2)

    scope.stop()
    expect(store.cancelStats).toHaveBeenCalledTimes(2)
  })

  it('refreshes logs and statistics with the current normalized query', async () => {
    const store = createStore()
    const { scope, workspace } = runWorkspace(store)
    const filters: AuditFilters = {
      action: 'CREATE_USER',
      entityType: 'all',
      startDate: '',
      endDate: '',
      performerId: '',
      userId: '',
    }

    await workspace.setFilters(filters)
    await vi.advanceTimersByTimeAsync(500)
    store.fetchLogs.mockClear()
    store.fetchStats.mockClear()

    await workspace.refresh()

    expect(store.fetchLogs).toHaveBeenCalledWith({
      search: undefined,
      action: 'CREATE_USER',
      entityType: undefined,
      startDate: undefined,
      endDate: undefined,
      performerId: undefined,
      userId: undefined,
      page: 1,
      limit: 50,
    })
    expect(store.fetchStats).toHaveBeenCalledWith({
      search: undefined,
      action: 'CREATE_USER',
      entityType: undefined,
      startDate: undefined,
      endDate: undefined,
      performerId: undefined,
      userId: undefined,
      page: 1,
      limit: 50,
    })

    scope.stop()
  })

  it('keeps statistics loading separate from the table loading state', async () => {
    const store = createStore()
    const { scope, workspace } = runWorkspace(store)

    await vi.waitFor(() => expect(workspace.loading.value).toBe(false))
    store.statsLoading.value = true

    expect(workspace.loading.value).toBe(false)
    expect(workspace.statsLoading.value).toBe(true)

    store.loading.value = true
    expect(workspace.loading.value).toBe(true)
    scope.stop()
  })
})
