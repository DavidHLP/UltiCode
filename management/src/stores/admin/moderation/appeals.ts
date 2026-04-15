import { type Ref, ref } from 'vue'
import {
  appealsApi,
  type Appeal,
  type QueryAppealsParams,
  type CreateAppealDto,
  type ReviewAppealDto,
} from '@/api/admin/moderation'

export function useAppealsModule(
  abortControllers: Ref<Map<string, AbortController>>,
  actionLoading: { value: boolean },
) {
  const appeals = ref<Appeal[]>([])
  const appealsTotal = ref(0)
  const appealsLoading = ref(false)
  const appealsError = ref<string | null>(null)
  const currentAppeal = ref<Appeal | null>(null)
  const currentAppealLoading = ref(false)
  const currentAppealError = ref<string | null>(null)

  function getAbortController(key: string): AbortController {
    const controller = abortControllers.value.get(key)
    if (controller) controller.abort()
    const newController = new AbortController()
    abortControllers.value.set(key, newController)
    return newController
  }

  function extractErrorMessage(err: unknown): string {
    const errorObj = err as { response?: { data?: { message?: string } }; message?: string }
    return errorObj?.response?.data?.message || errorObj?.message || 'An error occurred'
  }

  async function fetchAppeals(params: QueryAppealsParams = {}) {
    const controller = getAbortController('appeals')
    appealsLoading.value = true
    appealsError.value = null
    try {
      const response = await appealsApi.getAppeals(params, controller.signal)
      if (controller.signal.aborted) return
      appeals.value = response.data ?? response.data ?? (Array.isArray(response) ? response : [])
      appealsTotal.value = response.total ?? response.meta?.total ?? 0
    } catch (err: unknown) {
      if ((err as Error).name === 'AbortError') return
      appealsError.value = extractErrorMessage(err)
      console.error('[ModerationStore] Failed to fetch appeals:', err)
    } finally {
      if (abortControllers.value.get('appeals') === controller) appealsLoading.value = false
    }
  }

  async function fetchAppeal(id: string, forceRefresh = false) {
    if (!forceRefresh && currentAppeal.value?.id === id) return currentAppeal.value
    const controller = getAbortController('appeal')
    currentAppealLoading.value = true
    currentAppealError.value = null
    try {
      const appeal = await appealsApi.getAppeal(id, controller.signal)
      if (controller.signal.aborted) return null
      currentAppeal.value = appeal
      return appeal
    } catch (err: unknown) {
      if ((err as Error).name === 'AbortError') return null
      currentAppealError.value = extractErrorMessage(err)
      console.error('[ModerationStore] Failed to fetch appeal:', err)
      return null
    } finally {
      if (abortControllers.value.get('appeal') === controller) currentAppealLoading.value = false
    }
  }

  async function reviewAppeal(id: string, data: ReviewAppealDto) {
    actionLoading.value = true
    try {
      const appeal = await appealsApi.reviewAppeal(id, data)
      const index = appeals.value.findIndex((a) => a.id === id)
      if (index !== -1) appeals.value[index] = appeal
      if (currentAppeal.value?.id === id) currentAppeal.value = appeal
      return appeal
    } catch (err: unknown) {
      console.error('[ModerationStore] Failed to review appeal:', err)
      throw err
    } finally {
      actionLoading.value = false
    }
  }

  async function createAppeal(data: CreateAppealDto) {
    actionLoading.value = true
    try {
      const appeal = await appealsApi.createAppeal(data)
      return appeal
    } catch (err: unknown) {
      console.error('[ModerationStore] Failed to create appeal:', err)
      throw err
    } finally {
      actionLoading.value = false
    }
  }

  return {
    appeals,
    appealsTotal,
    appealsLoading,
    appealsError,
    currentAppeal,
    currentAppealLoading,
    currentAppealError,
    fetchAppeals,
    fetchAppeal,
    reviewAppeal,
    createAppeal,
  }
}
