import { computed, ref } from 'vue'
import { toast } from 'vue-sonner'
import { useI18n } from 'vue-i18n'
import type { ApiError } from '@/utils/request'

export interface ErrorHandlerOptions {
  showToast?: boolean
  logError?: boolean
}

export function useErrorHandler() {
  const { t } = useI18n()
  const error = ref<ApiError | null>(null)
  const errorMessage = computed(() => {
    if (!error.value) return null
    return error.value.message || t('common.error.unknown')
  })

  function handleError(err: unknown, options: ErrorHandlerOptions = {}) {
    const { showToast = true, logError = true } = options

    // Normalize error
    if (err instanceof Error) {
      error.value = {
        message: err.message,
        code: 500, // ApiError code is a number
        name: 'ApiError',
      }
    } else if (typeof err === 'object' && err !== null) {
      error.value = err as ApiError
    } else {
      error.value = {
        message: String(err),
        code: 500, // ApiError code is a number
        name: 'ApiError',
      }
    }

    // Log error
    if (logError) {
      console.error('Error handled:', error.value)
    }

    // Show toast
    if (showToast && error.value && error.value.message) {
      toast.error(error.value.message)
    }

    return error.value
  }

  function clearError() {
    error.value = null
  }

  async function handleAsync<T>(
    asyncFn: () => Promise<T>,
    options?: ErrorHandlerOptions,
  ): Promise<T | null> {
    try {
      return await asyncFn()
    } catch (err) {
      handleError(err, options)
      return null
    }
  }

  return {
    error,
    errorMessage,
    handleError,
    clearError,
    handleAsync,
  }
}
