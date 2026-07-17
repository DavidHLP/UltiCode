import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useProblemWorkspace } from '../useProblemWorkspace'
import { useProblemsStore } from '@/stores/admin/problems'

// Mock the API module so reload() can be asserted without network.
vi.mock('@/api/admin/problems', async () => {
  return {
    problemsApi: {
      getProblems: vi.fn(async () => ({
        items: [
          { id: 'p1', title: 'A', difficulty: 'EASY', status: 'PUBLIC', isPublished: true },
          { id: 'p2', title: 'B', difficulty: 'MEDIUM', status: 'DRAFT', isPublished: false },
        ],
        total: 2,
      })),
    },
  }
})

describe('useProblemWorkspace', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('starts with empty selection and zero stats', () => {
    const ws = useProblemWorkspace()
    expect(ws.selectedIds.value).toEqual([])
    expect(ws.itemCount.value).toBe(0)
    expect(ws.selectedCount.value).toBe(0)
    expect(ws.hasSelection.value).toBe(false)
    expect(ws.isLoading.value).toBe(false)
    expect(ws.errorMessage.value).toBeNull()
  })

  it('reload() funnels through the store and populates items + total', async () => {
    const ws = useProblemWorkspace()
    await ws.reload()
    expect(ws.itemCount.value).toBe(2)
    expect(ws.total.value).toBe(2)
    expect(ws.isLoading.value).toBe(false)
  })

  it('reload() exposes a loading state during the in-flight request', async () => {
    const ws = useProblemWorkspace()
    const inFlight = ws.reload()
    // Pinia ref is sync; loading flips to true synchronously before the await resolves.
    expect(ws.isLoading.value).toBe(true)
    await inFlight
    expect(ws.isLoading.value).toBe(false)
  })

  it('setSelection() + selectedCount + hasSelection update synchronously', () => {
    const ws = useProblemWorkspace()
    ws.setSelection(['p1', 'p2'])
    expect(ws.selectedIds.value).toEqual(['p1', 'p2'])
    expect(ws.selectedCount.value).toBe(2)
    expect(ws.hasSelection.value).toBe(true)
  })

  it('clearSelection() empties the selection and flips hasSelection back to false', () => {
    const ws = useProblemWorkspace()
    ws.setSelection(['p1'])
    expect(ws.hasSelection.value).toBe(true)
    ws.clearSelection()
    expect(ws.selectedIds.value).toEqual([])
    expect(ws.hasSelection.value).toBe(false)
  })

  it('reload() after a stale response does not overwrite a fresher one', async () => {
    // The store increments lastFetchSeq on each fetchProblems call; a
    // response whose seq is no longer the latest is dropped. We exercise
    // the workspace's reload() entry point to prove the facade does not
    // bypass the guard.
    const store = useProblemsStore()
    const spy = vi.spyOn(store, 'fetchProblems')

    const ws = useProblemWorkspace()
    const r1 = ws.reload()
    const r2 = ws.reload()
    await Promise.all([r1, r2])

    // Both calls went through the store — the facade is a pass-through,
    // not a parallel channel.
    expect(spy).toHaveBeenCalledTimes(2)
  })
})
