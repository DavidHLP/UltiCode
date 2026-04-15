import { type Ref, ref } from 'vue'
import {
  reportsApi,
  type Report,
  type QueryReportsParams,
  type ModeratableEntityType,
} from '@/api/admin/moderation'

export function useReportsModule(abortControllers: Ref<Map<string, AbortController>>) {
  const reports = ref<Report[]>([])
  const reportsTotal = ref(0)
  const reportsLoading = ref(false)
  const reportsError = ref<string | null>(null)

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

  async function fetchReports(params: QueryReportsParams = {}) {
    const controller = getAbortController('reports')
    reportsLoading.value = true
    reportsError.value = null
    try {
      const response = await reportsApi.getReports(params, controller.signal)
      if (controller.signal.aborted) return
      reports.value = response.data ?? response.data ?? (Array.isArray(response) ? response : [])
      reportsTotal.value = response.total ?? response.meta?.total ?? 0
    } catch (err: unknown) {
      if ((err as Error).name === 'AbortError') return
      reportsError.value = extractErrorMessage(err)
      console.error('[ModerationStore] Failed to fetch reports:', err)
    } finally {
      if (abortControllers.value.get('reports') === controller) reportsLoading.value = false
    }
  }

  async function fetchReportsByEntity(entityType: ModeratableEntityType, entityId: string) {
    const controller = getAbortController('entityReports')
    try {
      const data = await reportsApi.getReportsByEntity(entityType, entityId, controller.signal)
      if (controller.signal.aborted) return []
      return data
    } catch (err: unknown) {
      if ((err as Error).name === 'AbortError') return []
      console.error('[ModerationStore] Failed to fetch entity reports:', err)
      return []
    }
  }

  return {
    reports,
    reportsTotal,
    reportsLoading,
    reportsError,
    fetchReports,
    fetchReportsByEntity,
  }
}
