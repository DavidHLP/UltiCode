import { defineStore } from 'pinia'
import { ref, computed, readonly, type DeepReadonly } from 'vue'
import {
  submissionsApi,
  type SubmissionListItem,
  type SubmissionStatistics,
  type StatusOption,
  type LanguageOption,
  type SubmissionQueryParams,
} from '@/api/admin/submissions'
import { createCollectionSlice } from '@/stores/createCollectionSlice'

export const useSubmissionsStore = defineStore('admin-submissions', () => {
  // State
  const collection = createCollectionSlice<SubmissionListItem, SubmissionQueryParams>({
    load: async (params = {}, signal) => {
      const response = await submissionsApi.getList(params, signal)
      return { items: response.items, total: response.total }
    },
  })
  const submissions = collection.items
  const readonlySubmissions: DeepReadonly<typeof submissions> = readonly(submissions)
  const total: DeepReadonly<typeof collection.total> = readonly(collection.total)
  const loading: DeepReadonly<typeof collection.isLoading> = readonly(collection.isLoading)
  const error: DeepReadonly<typeof collection.error> = readonly(collection.error)
  const fetchSubmissions = collection.fetch
  const operationLoading = ref(false)
  const operationError = ref<string | null>(null)

  // Statistics
  const statistics = ref<SubmissionStatistics | null>(null)
  const statsLoading = ref(false)

  // Filter options
  const statuses = ref<StatusOption[]>([])
  const languages = ref<LanguageOption[]>([])

  // Computed
  const stats = computed(() => {
    const statsData = statistics.value
    if (!statsData) return { total: 0, pending: 0, topLanguage: '-', acceptedRate: '0' }
    const acceptedCount = statsData.byStatus.find((s) => s.status === 'ACCEPTED')?.count || 0
    const acceptedRate =
      statsData.total > 0 ? ((acceptedCount / statsData.total) * 100).toFixed(1) : '0'
    return {
      total: statsData.total,
      pending: statsData.pending,
      topLanguage: statsData.byLanguage[0]?.language || '-',
      acceptedRate,
    }
  })

  // Actions
  async function fetchStatistics() {
    statsLoading.value = true
    try {
      statistics.value = await submissionsApi.getStatistics()
    } catch (err) {
      console.error('Failed to load statistics:', err)
    } finally {
      statsLoading.value = false
    }
  }

  async function fetchFilters() {
    try {
      const [statusesRes, languagesRes] = await Promise.all([
        submissionsApi.getStatuses(),
        submissionsApi.getLanguages(),
      ])
      statuses.value = statusesRes
      languages.value = languagesRes
    } catch (err) {
      console.error('Failed to load filters:', err)
    }
  }

  async function rejudgeSubmission(id: string, notifyUser: boolean = false) {
    operationLoading.value = true
    operationError.value = null
    try {
      return await submissionsApi.rejudge(id, notifyUser)
    } catch (err) {
      operationError.value = err instanceof Error ? err.message : 'Failed to rejudge submission'
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function batchRejudge(submissionIds: string[], notifyUsers: boolean = false) {
    operationLoading.value = true
    operationError.value = null
    try {
      return await submissionsApi.batchRejudge(submissionIds, notifyUsers)
    } catch (err) {
      operationError.value = err instanceof Error ? err.message : 'Failed to rejudge submissions'
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function getSubmissionDetail(id: string) {
    return submissionsApi.getById(id)
  }

  function clearError() {
    collection.clearError()
    operationError.value = null
  }

  function reset() {
    collection.reset()
    statistics.value = null
    statsLoading.value = false
    operationLoading.value = false
    operationError.value = null
    statuses.value = []
    languages.value = []
  }

  return {
    // State
    items: readonlySubmissions,
    isLoading: loading,
    fetch: collection.fetch,
    cancel: collection.cancel,
    submissions: readonlySubmissions,
    total,
    loading,
    error,
    operationLoading,
    operationError,
    statistics,
    statsLoading,
    statuses,
    languages,
    // Computed
    stats,
    // Actions
    fetchSubmissions,
    fetchStatistics,
    fetchFilters,
    rejudgeSubmission,
    batchRejudge,
    getSubmissionDetail,
    clearError,
    reset,
  }
})
