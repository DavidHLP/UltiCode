import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  submissionsApi,
  type SubmissionListItem,
  type SubmissionStatistics,
  type StatusOption,
  type LanguageOption,
  type SubmissionQueryParams,
} from '@/api/admin/submissions'

export const useSubmissionsStore = defineStore('admin-submissions', () => {
  // State
  const submissions = ref<SubmissionListItem[]>([])
  const total = ref(0)
  const totalPages = ref(0)
  const loading = ref(false)
  const error = ref<string | null>(null)

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
  async function fetchSubmissions(params: SubmissionQueryParams) {
    loading.value = true
    error.value = null
    try {
      const response = await submissionsApi.getList(params)
      submissions.value = response.items
      total.value = response.total
      totalPages.value = response.totalPages
    } catch (err) {
      console.error('Failed to load submissions:', err)
      error.value = 'Failed to load submissions'
    } finally {
      loading.value = false
    }
  }

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
    return submissionsApi.rejudge(id, notifyUser)
  }

  async function batchRejudge(submissionIds: string[], notifyUsers: boolean = false) {
    return submissionsApi.batchRejudge(submissionIds, notifyUsers)
  }

  async function getSubmissionDetail(id: string) {
    return submissionsApi.getById(id)
  }

  function clearError() {
    error.value = null
  }

  function reset() {
    submissions.value = []
    total.value = 0
    totalPages.value = 0
    loading.value = false
    error.value = null
    statistics.value = null
    statsLoading.value = false
    statuses.value = []
    languages.value = []
  }

  return {
    // State
    submissions,
    total,
    totalPages,
    loading,
    error,
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
