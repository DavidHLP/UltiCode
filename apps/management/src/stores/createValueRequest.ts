import { ref } from 'vue'
import { extractApiErrorMessage } from '@/utils/error'

interface CreateValueRequestOptions {
  errorMessage: string
  rethrow?: boolean
  onError?: (err: unknown) => void
}

/** Newest-wins request state and cancellation policy for single-value reads. */
export function createValueRequest(options: CreateValueRequestOptions) {
  const loading = ref(false)
  const error = ref<string | null>(null)
  let requestSequence = 0
  let currentController: AbortController | null = null

  async function run<T>(load: (signal: AbortSignal) => Promise<T>): Promise<T | null> {
    currentController?.abort()
    const controller = new AbortController()
    currentController = controller
    const request = ++requestSequence
    loading.value = true
    error.value = null

    try {
      const value = await load(controller.signal)
      return request === requestSequence && !controller.signal.aborted ? value : null
    } catch (err: unknown) {
      if (request !== requestSequence || controller.signal.aborted) return null
      error.value = extractApiErrorMessage(err, options.errorMessage)
      options.onError?.(err)
      if (options.rethrow) throw err
      return null
    } finally {
      if (currentController === controller) {
        currentController = null
        loading.value = false
      }
    }
  }

  function cancel(): void {
    requestSequence += 1
    currentController?.abort()
    currentController = null
    loading.value = false
  }

  return { loading, error, run, cancel }
}
