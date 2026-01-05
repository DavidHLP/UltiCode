import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  problemsApi,
  type Problem,
  type ProblemQueryParams,
  type CreateProblemDto,
  type UpdateProblemDto,
  type BulkProblemActionDto,
} from '@/api/admin/problems'

export const useProblemsStore = defineStore('adminProblems', () => {
  const problems = ref<Problem[]>([])
  const total = ref(0)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const currentProblem = ref<Problem | null>(null)

  async function fetchProblems(params: ProblemQueryParams = {}) {
    loading.value = true
    error.value = null
    try {
      const response = await problemsApi.getProblems(params)
      problems.value = response.data
      total.value = response.total
    } catch (err: unknown) {
      error.value =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to fetch problems'
      console.error('Failed to fetch problems:', err)
    } finally {
      loading.value = false
    }
  }

  async function fetchProblem(id: string): Promise<Problem | null> {
    loading.value = true
    error.value = null
    currentProblem.value = null // Clear previous problem
    try {
      console.log('[ProblemsStore] Fetching problem with id:', id)
      const problem = await problemsApi.getProblem(id)
      console.log('[ProblemsStore] Received problem data:', problem)
      currentProblem.value = problem
      return problem
    } catch (err: unknown) {
      const errorMessage =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to fetch problem'
      error.value = errorMessage
      console.error('[ProblemsStore] Failed to fetch problem:', err)
      console.error('[ProblemsStore] Error details:', {
        id,
        error: err,
        responseData: (err as { response?: { data?: unknown } })?.response?.data,
        status: (err as { response?: { status?: number } })?.response?.status,
      })
      return null
    } finally {
      loading.value = false
    }
  }

  async function createProblem(data: CreateProblemDto) {
    loading.value = true
    error.value = null
    try {
      const problem = await problemsApi.createProblem(data)
      return problem
    } catch (err: unknown) {
      error.value =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to create problem'
      console.error('Failed to create problem:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  async function updateProblem(id: string, data: UpdateProblemDto) {
    loading.value = true
    error.value = null
    try {
      const problem = await problemsApi.updateProblem(id, data)
      // Update local list if present
      const index = problems.value.findIndex((p) => p.id === id)
      if (index !== -1) {
        problems.value[index] = problem
      }
      // Also update currentProblem if it matches
      if (currentProblem.value?.id === id) {
        currentProblem.value = problem
      }
      return problem
    } catch (err: unknown) {
      error.value =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to update problem'
      console.error('Failed to update problem:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  async function deleteProblem(id: string) {
    loading.value = true
    error.value = null
    try {
      await problemsApi.deleteProblem(id)
      // Remove from local list
      const index = problems.value.findIndex((p) => p.id === id)
      if (index !== -1) {
        problems.value.splice(index, 1)
        total.value--
      }
      // Clear currentProblem if it matches
      if (currentProblem.value?.id === id) {
        currentProblem.value = null
      }
    } catch (err: unknown) {
      error.value =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to delete problem'
      console.error('Failed to delete problem:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  async function publishProblem(id: string) {
    loading.value = true
    error.value = null
    try {
      const problem = await problemsApi.publishProblem(id)
      // Update local list if present
      const index = problems.value.findIndex((p) => p.id === id)
      if (index !== -1) {
        problems.value[index] = problem
      }
      // Also update currentProblem if it matches
      if (currentProblem.value?.id === id) {
        currentProblem.value = problem
      }
      return problem
    } catch (err: unknown) {
      error.value =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to publish problem'
      console.error('Failed to publish problem:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  async function unpublishProblem(id: string) {
    loading.value = true
    error.value = null
    try {
      const problem = await problemsApi.unpublishProblem(id)
      // Update local list if present
      const index = problems.value.findIndex((p) => p.id === id)
      if (index !== -1) {
        problems.value[index] = problem
      }
      // Also update currentProblem if it matches
      if (currentProblem.value?.id === id) {
        currentProblem.value = problem
      }
      return problem
    } catch (err: unknown) {
      error.value =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to unpublish problem'
      console.error('Failed to unpublish problem:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  async function bulkAction(data: BulkProblemActionDto) {
    loading.value = true
    error.value = null
    try {
      await problemsApi.bulkAction(data)
      // Refresh list after bulk action
      await fetchProblems()
    } catch (err: unknown) {
      error.value =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to perform bulk action'
      console.error('Failed to perform bulk action:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  function clearError() {
    error.value = null
  }

  function clearCurrentProblem() {
    currentProblem.value = null
  }

  function reset() {
    problems.value = []
    total.value = 0
    loading.value = false
    error.value = null
    currentProblem.value = null
  }

  return {
    problems,
    total,
    loading,
    error,
    currentProblem,
    fetchProblems,
    fetchProblem,
    createProblem,
    updateProblem,
    deleteProblem,
    publishProblem,
    unpublishProblem,
    bulkAction,
    clearError,
    clearCurrentProblem,
    reset,
  }
})
