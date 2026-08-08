import { ref, type Ref } from 'vue'
import { useDebounceFn } from '@vueuse/core'

export interface UseAutoSaveOptions {
  debounceMs?: number
  blurTriggers?: boolean
}

export interface UseAutoSaveReturn<T> {
  saveStatus: Ref<'idle' | 'saving' | 'saved' | 'error'>
  lastSavedAt: Ref<Date | null>
  error: Ref<Error | null>
  save: (data: T) => Promise<void>
  cancel: () => void
}

export function useAutoSave<T>(
  saveFn: (data: T, signal: AbortSignal) => Promise<void>,
  options: UseAutoSaveOptions = {},
): UseAutoSaveReturn<T> {
  const { debounceMs = 1000, blurTriggers = true } = options

  const saveStatus = ref<'idle' | 'saving' | 'saved' | 'error'>('idle')
  const lastSavedAt = ref<Date | null>(null)
  const error = ref<Error | null>(null)

  let currentController: AbortController | null = null

  const performSave = async (data: T) => {
    if (currentController) {
      currentController.abort()
      currentController = null
    }

    currentController = new AbortController()
    saveStatus.value = 'saving'
    error.value = null

    try {
      await saveFn(data, currentController.signal)
      saveStatus.value = 'saved'
      lastSavedAt.value = new Date()
    } catch (err) {
      if (err instanceof Error && err.name === 'AbortError') {
        if (saveStatus.value === 'saving') {
          saveStatus.value = 'idle'
        }
        return
      }
      saveStatus.value = 'error'
      error.value = err instanceof Error ? err : new Error(String(err))
    } finally {
      if (currentController?.signal.aborted === false) {
        currentController = null
      }
    }
  }

  const debouncedSave = useDebounceFn(performSave, debounceMs)

  const save = async (data: T) => {
    if (blurTriggers) {
      await debouncedSave(data)
    } else {
      await performSave(data)
    }
  }

  const cancel = () => {
    if (currentController) {
      currentController.abort()
      currentController = null
    }
    saveStatus.value = 'idle'
  }

  return {
    saveStatus,
    lastSavedAt,
    error,
    save,
    cancel,
  }
}
