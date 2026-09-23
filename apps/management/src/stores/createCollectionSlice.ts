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
  mutationLoading: Ref<boolean>
  mutationError: Ref<string | null>
  runMutation: <T>(run: () => Promise<T>, errorMessage: string) => Promise<T>
  fetch: (params?: TParams, options?: CollectionFetchOptions) => Promise<void>
  fetchWith: (
    load: CollectionLoader<T, TParams, never>,
    params?: TParams,
    options?: CollectionFetchOptions,
  ) => Promise<void>
  updateItems: (update: (items: T[]) => T[]) => void
  setTotal: (total: number) => void
  clearError: () => void
  cancel: () => void
  reset: () => void
}

export interface CollectionFetchOptions {
  rethrow?: boolean
  errorMessage?: string
  signal?: AbortSignal
}

interface CreateCollectionSliceOptions<T, TParams, TMetadata> {
  load: CollectionLoader<T, TParams, TMetadata>
  applyMetadata?: (metadata: TMetadata) => void
}

type CollectionLoader<T, TParams, TMetadata> = (
  params: TParams,
  signal?: AbortSignal,
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
  const mutationLoading = ref(false)
  const mutationError = ref<string | null>(null)
  let requestSequence = 0
  let currentController: AbortController | null = null

  async function runFetch<TLoadMetadata>(
    load: CollectionLoader<T, TParams, TLoadMetadata>,
    params?: TParams,
    fetchOptions?: CollectionFetchOptions,
    applyMetadata?: (metadata: TLoadMetadata) => void,
  ): Promise<void> {
    currentController?.abort()
    const controller = new AbortController()
    currentController = controller
    const request = ++requestSequence
    const externalSignal = fetchOptions?.signal
    const abortFromExternal = () => controller.abort()
    externalSignal?.addEventListener('abort', abortFromExternal, { once: true })
    if (externalSignal?.aborted) controller.abort()
    isLoading.value = true
    error.value = null

    try {
      const page = await load(params as TParams, controller.signal)
      if (request !== requestSequence || controller.signal.aborted || !page) return
      items.value = page.items
      total.value = page.total
      if (page.metadata !== undefined) {
        applyMetadata?.(page.metadata)
      }
    } catch (err: unknown) {
      if (request !== requestSequence || controller.signal.aborted || isCancellationError(err)) return
      error.value = extractApiErrorMessage(
        err,
        fetchOptions?.errorMessage ?? 'Failed to load collection',
      )
      console.error('Failed to load collection:', err)
      if (fetchOptions?.rethrow) throw err
    } finally {
      externalSignal?.removeEventListener('abort', abortFromExternal)
      if (currentController === controller) {
        currentController = null
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

  async function runMutation<T>(run: () => Promise<T>, errorMessage: string): Promise<T> {
    mutationLoading.value = true
    mutationError.value = null
    try {
      return await run()
    } catch (err: unknown) {
      mutationError.value = extractApiErrorMessage(err, errorMessage)
      console.error('Collection mutation failed:', err)
      throw err
    } finally {
      mutationLoading.value = false
    }
  }

  function cancel(): void {
    // Only release loading when this slice owns the active fetch request.
    if (currentController) {
      currentController.abort()
      currentController = null
      isLoading.value = false
    }
    requestSequence += 1
  }

  function reset(): void {
    cancel()
    items.value = []
    total.value = 0
    error.value = null
  }
  return {
    items,
    total,
    isLoading,
    error,
    mutationLoading,
    mutationError,
    runMutation,
    fetch,
    fetchWith,
    updateItems,
    setTotal,
    clearError,
    cancel,
    reset,
  }
}
