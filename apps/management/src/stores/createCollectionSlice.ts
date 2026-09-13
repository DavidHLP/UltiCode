import { ref, type Ref } from 'vue'
import { extractApiErrorMessage } from '@/utils/error'

export interface CollectionPage<T, TMetadata = never> {
  items: T[]
  total: number
  metadata?: TMetadata
}

export interface CollectionSlice<T, TParams> {
  items: Ref<T[]>
  total: Ref<number>
  isLoading: Ref<boolean>
  error: Ref<string | null>
  fetch: (params?: TParams, options?: CollectionFetchOptions) => Promise<void>
  fetchWith: (
    load: CollectionLoader<T, TParams, never>,
    params?: TParams,
    options?: CollectionFetchOptions,
  ) => Promise<void>
  updateItems: (update: (items: T[]) => T[]) => void
  setTotal: (total: number) => void
  clearError: () => void
  reset: () => void
}

export interface CollectionFetchOptions {
  rethrow?: boolean
  errorMessage?: string
}

interface CreateCollectionSliceOptions<T, TParams, TMetadata> {
  load: CollectionLoader<T, TParams, TMetadata>
  applyMetadata?: (metadata: TMetadata) => void
}

type CollectionLoader<T, TParams, TMetadata> = (
  params: TParams,
) => Promise<CollectionPage<T, TMetadata> | undefined>

function isCancellationError(err: unknown): boolean {
  const candidate = (err ?? {}) as { name?: unknown; code?: unknown; message?: unknown }
  return (
    candidate.name === 'AbortError' ||
    candidate.name === 'CanceledError' ||
    candidate.code === 'ERR_CANCELED' ||
    candidate.code === -1 ||
    (typeof candidate.message === 'string' && candidate.message.toLowerCase().includes('canceled'))
  )
}

/**
 * Shared state and failure policy for paginated collection loads.
 *
 * A loader may return undefined when it intentionally ignores an aborted
 * request. The newest fetch owns the loading/error state and the applied page.
 */
export function createCollectionSlice<T, TParams, TMetadata = never>(
  options: CreateCollectionSliceOptions<T, TParams, TMetadata>,
): CollectionSlice<T, TParams> {
  const items = ref<T[]>([]) as Ref<T[]>
  const total = ref(0)
  const isLoading = ref(false)
  const error = ref<string | null>(null)
  let requestSequence = 0

  async function runFetch<TLoadMetadata>(
    load: CollectionLoader<T, TParams, TLoadMetadata>,
    params?: TParams,
    fetchOptions?: CollectionFetchOptions,
    applyMetadata?: (metadata: TLoadMetadata) => void,
  ): Promise<void> {
    const request = ++requestSequence
    isLoading.value = true
    error.value = null

    try {
      const page = await load(params as TParams)
      if (request !== requestSequence || !page) return
      items.value = page.items
      total.value = page.total
      if (page.metadata !== undefined) {
        applyMetadata?.(page.metadata)
      }
    } catch (err: unknown) {
      if (request !== requestSequence || isCancellationError(err)) return
      error.value = extractApiErrorMessage(
        err,
        fetchOptions?.errorMessage ?? 'Failed to load collection',
      )
      console.error('Failed to load collection:', err)
      if (fetchOptions?.rethrow) throw err
    } finally {
      if (request === requestSequence) {
        isLoading.value = false
      }
    }
  }

  function fetch(params?: TParams, fetchOptions?: CollectionFetchOptions): Promise<void> {
    return runFetch(options.load, params, fetchOptions, options.applyMetadata)
  }

  function fetchWith(
    load: CollectionLoader<T, TParams, never>,
    params?: TParams,
    fetchOptions?: CollectionFetchOptions,
  ): Promise<void> {
    return runFetch(load, params, fetchOptions)
  }

  function updateItems(update: (currentItems: T[]) => T[]): void {
    items.value = update(items.value)
  }

  function setTotal(nextTotal: number): void {
    total.value = nextTotal
  }

  function clearError(): void {
    error.value = null
  }

  function reset(): void {
    requestSequence += 1
    items.value = []
    total.value = 0
    isLoading.value = false
    error.value = null
  }

  return {
    items,
    total,
    isLoading,
    error,
    fetch,
    fetchWith,
    updateItems,
    setTotal,
    clearError,
    reset,
  }
}
