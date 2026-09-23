import { ref } from 'vue'

export interface ValueRequestOptions {
  errorMessage: string
  getErrorMessage?: (error: unknown, fallback: string) => string
  onError?: (error: unknown) => void
  rethrow?: boolean
}

/** Own newest-wins cancellation and state for one single-resource request. */
export function createValueRequest(options: ValueRequestOptions) {
  const loading = ref(false)
  const error = ref<string | null>(null)
  let sequence = 0
  let controller: AbortController | null = null

  async function run<T>(
    load: (signal: AbortSignal) => Promise<T>,
    externalSignal?: AbortSignal,
    onCurrent?: (value: T) => void,
  ): Promise<T | null> {
    controller?.abort()
    const current = new AbortController()
    controller = current
    const request = ++sequence
    const abortFromExternal = () => current.abort()
    externalSignal?.addEventListener('abort', abortFromExternal, { once: true })
    if (externalSignal?.aborted) current.abort()
    loading.value = true
    error.value = null

    try {
      const value = await load(current.signal)
      if (request !== sequence || current.signal.aborted) return null
      onCurrent?.(value)
      return value
    } catch (cause) {
      if (request !== sequence || current.signal.aborted) return null
      error.value =
        options.getErrorMessage?.(cause, options.errorMessage) ??
        (cause instanceof Error ? cause.message : options.errorMessage)
      options.onError?.(cause)
      if (options.rethrow) throw cause
      return null
    } finally {
      externalSignal?.removeEventListener('abort', abortFromExternal)
      if (controller === current) {
        controller = null
        loading.value = false
      }
    }
  }

  function cancel(): void {
    sequence += 1
    controller?.abort()
    controller = null
    loading.value = false
  }

  return { loading, error, run, cancel, isActive: () => controller !== null }
}
