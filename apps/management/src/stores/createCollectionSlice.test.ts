import { describe, expect, it, vi } from 'vitest'
import { createCollectionSlice } from './createCollectionSlice'

describe('createCollectionSlice', () => {
  it('loads a page into the canonical collection state', async () => {
    const load = vi.fn().mockResolvedValue({ items: ['one', 'two'], total: 2 })
    const slice = createCollectionSlice<string, { page: number }>({ load })

    await slice.fetch({ page: 1 })

    expect(load).toHaveBeenCalledWith({ page: 1 }, expect.any(AbortSignal))
    expect(slice.items.value).toEqual(['one', 'two'])
    expect(slice.total.value).toBe(2)
    expect(slice.isLoading.value).toBe(false)
    expect(slice.error.value).toBeNull()
  })

  it('centralizes a swallowed load failure in error state', async () => {
    const load = vi.fn().mockRejectedValue(new Error('network down'))
    const slice = createCollectionSlice<string, void>({ load })

    await slice.fetch()

    expect(slice.items.value).toEqual([])
    expect(slice.error.value).toBe('network down')
    expect(slice.isLoading.value).toBe(false)
  })

  it('can rethrow a load failure for mutation refreshes', async () => {
    const error = new Error('refresh failed')
    const load = vi.fn().mockRejectedValue(error)
    const slice = createCollectionSlice<string, void>({ load })

    await expect(slice.fetch(undefined, { rethrow: true })).rejects.toBe(error)

    expect(slice.error.value).toBe('refresh failed')
    expect(slice.isLoading.value).toBe(false)
  })

  it('does not surface an intentional cancellation as a collection error', async () => {
    const load = vi.fn().mockRejectedValue(
      Object.assign(new Error('Request canceled'), { code: -1 }),
    )
    const slice = createCollectionSlice<string, void>({ load })

    await slice.fetch()

    expect(slice.error.value).toBeNull()
    expect(slice.isLoading.value).toBe(false)
  })

  it('cancels the current request without clearing collection state', async () => {
    let resolveLoad: (page: { items: string[]; total: number }) => void = () => undefined
    const load = vi.fn(
      (_params?: void, _signal?: AbortSignal) =>
        new Promise<{ items: string[]; total: number }>((resolve) => {
          resolveLoad = resolve
        }),
    )
    const slice = createCollectionSlice<string, void>({ load })
    const request = slice.fetch()
    const signal = load.mock.calls[0][1]!
    slice.items.value = ['existing']
    slice.total.value = 4
    slice.error.value = 'existing error'

    slice.cancel()
    resolveLoad({ items: ['stale'], total: 1 })
    await request

    expect(signal.aborted).toBe(true)
    expect(slice.items.value).toEqual(['existing'])
    expect(slice.total.value).toBe(4)
    expect(slice.error.value).toBe('existing error')
    expect(slice.isLoading.value).toBe(false)
  })

  it('suppresses a canceled rethrow and makes cancel idempotent', async () => {
    let rejectLoad: (error: Error) => void = () => undefined
    const load = vi.fn(
      () =>
        new Promise<never>((_, reject) => {
          rejectLoad = reject
        }),
    )
    const slice = createCollectionSlice<string, void>({ load })
    const request = slice.fetch(undefined, { rethrow: true })
    slice.error.value = 'existing error'

    slice.cancel()
    slice.cancel()
    rejectLoad(new Error('Request canceled'))
    await request

    expect(slice.error.value).toBe('existing error')
    expect(slice.isLoading.value).toBe(false)
  })

  it('cancel preserves independent mutation loading', async () => {
    const slice = createCollectionSlice<string, void>({ load: vi.fn().mockResolvedValue({ items: [], total: 0 }) })
    let finish!: () => void
    const mutation = slice.runMutation(() => new Promise<void>((resolve) => { finish = resolve }), 'Failed')

    slice.cancel()

    expect(slice.mutationLoading.value).toBe(true)
    expect(slice.isLoading.value).toBe(false)
    finish()
    await mutation
    expect(slice.mutationLoading.value).toBe(false)
  })

  it('cancels before reset clears collection state', async () => {
    let resolveLoad: (page: { items: string[]; total: number }) => void = () => undefined
    const load = vi.fn(
      (_params?: void, _signal?: AbortSignal) =>
        new Promise<{ items: string[]; total: number }>((resolve) => {
          resolveLoad = resolve
        }),
    )
    const slice = createCollectionSlice<string, void>({ load })
    const request = slice.fetch()
    const signal = load.mock.calls[0][1]!
    slice.items.value = ['existing']
    slice.total.value = 4
    slice.error.value = 'existing error'

    slice.reset()
    resolveLoad({ items: ['stale'], total: 1 })
    await request

    expect(signal.aborted).toBe(true)
    expect(slice.items.value).toEqual([])
    expect(slice.total.value).toBe(0)
    expect(slice.error.value).toBeNull()
    expect(slice.isLoading.value).toBe(false)
  })
  it('aborts the previous loader when a newer fetch completes', async () => {
    let resolveFirst: (value: { items: string[]; total: number }) => void = () => undefined
    const first = new Promise<{ items: string[]; total: number }>((resolve) => {
      resolveFirst = resolve
    })
    const load = vi
      .fn()
      .mockReturnValueOnce(first)
      .mockResolvedValueOnce({ items: ['new'], total: 1 })
    const slice = createCollectionSlice<string, number>({ load })

    const firstFetch = slice.fetch(1)
    const firstSignal = load.mock.calls[0][1] as AbortSignal
    await slice.fetch(2)
    resolveFirst({ items: ['old'], total: 1 })
    await firstFetch

    expect(firstSignal.aborted).toBe(true)
    expect(slice.items.value).toEqual(['new'])
    expect(slice.isLoading.value).toBe(false)
  })

  it('only applies the newest overlapping fetch', async () => {
    let resolveFirst: (value: { items: string[]; total: number }) => void = () => undefined
    const first = new Promise<{ items: string[]; total: number }>((resolve) => {
      resolveFirst = resolve
    })
    const load = vi
      .fn()
      .mockReturnValueOnce(first)
      .mockResolvedValueOnce({ items: ['new'], total: 1 })
    const slice = createCollectionSlice<string, number>({ load })

    const firstFetch = slice.fetch(1)
    await slice.fetch(2)
    resolveFirst({ items: ['old'], total: 1 })
    await firstFetch

    expect(slice.items.value).toEqual(['new'])
    expect(slice.total.value).toBe(1)
  })

  it('does not let a stale failure overwrite newer success', async () => {
    let rejectFirst: (error: Error) => void = () => undefined
    const first = new Promise<never>((_, reject) => {
      rejectFirst = reject
    })
    const load = vi
      .fn()
      .mockReturnValueOnce(first)
      .mockResolvedValueOnce({ items: ['new'], total: 1 })
    const slice = createCollectionSlice<string, number>({ load })

    const firstFetch = slice.fetch(1)
    await slice.fetch(2)
    rejectFirst(new Error('stale failure'))
    await firstFetch

    expect(slice.items.value).toEqual(['new'])
    expect(slice.error.value).toBeNull()
    expect(slice.isLoading.value).toBe(false)
  })

  it('only applies metadata from the newest overlapping fetch', async () => {
    let resolveFirst: (value: { items: string[]; total: number; metadata: { page: number } }) => void =
      () => undefined
    const first = new Promise<{ items: string[]; total: number; metadata: { page: number } }>(
      (resolve) => {
        resolveFirst = resolve
      },
    )
    const applyMetadata = vi.fn()
    const load = vi
      .fn()
      .mockReturnValueOnce(first)
      .mockResolvedValueOnce({ items: ['new'], total: 1, metadata: { page: 2 } })
    const slice = createCollectionSlice<string, number, { page: number }>({
      load,
      applyMetadata,
    })

    const firstFetch = slice.fetch(1)
    await slice.fetch(2)
    resolveFirst({ items: ['old'], total: 1, metadata: { page: 1 } })
    await firstFetch

    expect(applyMetadata).toHaveBeenCalledTimes(1)
    expect(applyMetadata).toHaveBeenCalledWith({ page: 2 })
  })

  it('keeps alternate collection loads on the same request policy', async () => {
    const load = vi.fn().mockResolvedValue({ items: ['default'], total: 1 })
    const slice = createCollectionSlice<string, void>({ load })

    await slice.fetchWith(async () => ({ items: ['alternate'], total: 1 }))

    expect(slice.items.value).toEqual(['alternate'])
    expect(load).not.toHaveBeenCalled()
  })


  it('forwards an external abort only to its fetch and removes the listener', async () => {
    let resolveFirst!: (page: { items: string[]; total: number }) => void
    let resolveSecond!: (page: { items: string[]; total: number }) => void
    let firstSignal!: AbortSignal
    let secondSignal!: AbortSignal
    const external = new AbortController()
    const addListener = vi.spyOn(external.signal, 'addEventListener')
    const removeListener = vi.spyOn(external.signal, 'removeEventListener')
    const load = vi.fn((_params: number, signal?: AbortSignal) => {
      if (_params === 1) {
        firstSignal = signal!
        return new Promise<{ items: string[]; total: number }>((resolve) => { resolveFirst = resolve })
      }
      secondSignal = signal!
      return new Promise<{ items: string[]; total: number }>((resolve) => { resolveSecond = resolve })
    })
    const slice = createCollectionSlice<string, number>({ load })

    const first = slice.fetch(1, { signal: external.signal })
    external.abort()
    expect(firstSignal.aborted).toBe(true)
    const second = slice.fetch(2)
    expect(secondSignal.aborted).toBe(false)
    resolveFirst({ items: ['stale'], total: 1 })
    resolveSecond({ items: ['current'], total: 1 })
    await Promise.all([first, second])

    expect(slice.items.value).toEqual(['current'])
    expect(addListener).toHaveBeenCalledTimes(1)
    expect(removeListener).toHaveBeenCalledTimes(1)
  })

  it('uses each fetch error message for its own failure', async () => {
    const failure = {}
    const slice = createCollectionSlice<string, void>({ load: vi.fn().mockRejectedValue(failure) })
    await slice.fetch(undefined, { errorMessage: 'Custom collection failure' })
    expect(slice.error.value).toBe('Custom collection failure')
  })

})
