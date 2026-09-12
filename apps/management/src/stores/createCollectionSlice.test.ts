import { describe, expect, it, vi } from 'vitest'
import { createCollectionSlice } from './createCollectionSlice'

describe('createCollectionSlice', () => {
  it('loads a page into the canonical collection state', async () => {
    const load = vi.fn().mockResolvedValue({ items: ['one', 'two'], total: 2 })
    const slice = createCollectionSlice<string, { page: number }>({ load })

    await slice.fetch({ page: 1 })

    expect(load).toHaveBeenCalledWith({ page: 1 })
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

  it('does not surface an intentional cancellation as a collection error', async () => {
    const load = vi.fn().mockRejectedValue(
      Object.assign(new Error('Request canceled'), { code: -1 }),
    )
    const slice = createCollectionSlice<string, void>({ load })

    await slice.fetch()

    expect(slice.error.value).toBeNull()
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
})
