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
}

export interface CollectionFetchOptions {
  rethrow?: boolean
}

interface CreateCollectionSliceOptions<T, TParams, TMetadata> {
  load: (params: TParams) => Promise<CollectionPage<T, TMetadata> | undefined>
  applyMetadata?: (metadata: TMetadata) => void
}

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

  async function fetch(params?: TParams, fetchOptions?: CollectionFetchOptions): Promise<void> {
    const request = ++requestSequence
    isLoading.value = true
    error.value = null

    try {
      const page = await options.load(params as TParams)
      if (request !== requestSequence || !page) return
      items.value = page.items
      total.value = page.total
      if (page.metadata !== undefined) {
        options.applyMetadata?.(page.metadata)
      }
    } catch (err: unknown) {
      if (request !== requestSequence || isCancellationError(err)) return
      error.value = extractApiErrorMessage(err, 'Failed to load collection')
      console.error('Failed to load collection:', err)
      if (fetchOptions?.rethrow) throw err
    } finally {
      if (request === requestSequence) {
        isLoading.value = false
      }
    }
  }

  return { items, total, isLoading, error, fetch }
}
