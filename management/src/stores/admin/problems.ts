import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  problemsApi,
  type Problem,
  type ProblemQueryParams,
  type CreateProblemDto,
  type UpdateProblemDto,
  type BulkProblemActionDto,
  type HeaderData,
  type DescriptionData,
  type CodeData,
  type CasesData,
} from '@/api/admin/problems'

export const useProblemsStore = defineStore('adminProblems', () => {
  // ========== List State ==========
  const problems = ref<Problem[]>([])
  const total = ref(0)
  const loading = ref(false)
  const error = ref<string | null>(null)

  // ========== Header State ==========
  const headerData = ref<HeaderData | null>(null)
  const headerLoading = ref(false)
  const headerError = ref<string | null>(null)

  // ========== Description Tab State ==========
  const descriptionData = ref<DescriptionData | null>(null)
  const descriptionLoading = ref(false)
  const descriptionError = ref<string | null>(null)

  // ========== Code Tab State ==========
  const codeData = ref<CodeData | null>(null)
  const codeLoading = ref(false)
  const codeError = ref<string | null>(null)

  // ========== Cases Tab State ==========
  const casesData = ref<CasesData | null>(null)
  const casesLoading = ref(false)
  const casesError = ref<string | null>(null)

  // ========== Abort Controllers ==========
  const abortControllers = ref<Map<string, AbortController>>(new Map())

  // ========== Legacy State (for backward compatibility) ==========
  const currentProblem = ref<Problem | null>(null)
  const loadedProblemId = ref<string | null>(null)

  // ========== AbortController Helpers ==========

  function getAbortController(key: string): AbortController {
    let controller = abortControllers.value.get(key)
    if (controller) {
      controller.abort()
    }
    controller = new AbortController()
    abortControllers.value.set(key, controller)
    return controller
  }

  function abortAllRequests() {
    abortControllers.value.forEach((controller) => controller.abort())
    abortControllers.value.clear()
  }

  function extractErrorMessage(err: unknown): string {
    return (
      (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
      (err as Error)?.message ||
      'An error occurred'
    )
  }

  // ==================== List Operations ====================

  async function fetchProblems(params: ProblemQueryParams = {}) {
    loading.value = true
    error.value = null
    try {
      const response = await problemsApi.getProblems(params)
      problems.value = response.data
      total.value = response.total
    } catch (err: unknown) {
      error.value = extractErrorMessage(err)
      console.error('Failed to fetch problems:', err)
    } finally {
      loading.value = false
    }
  }

  // ========== Tab Fetch Functions ==========

  async function fetchHeader(id: string): Promise<HeaderData | null> {
    const controller = getAbortController('header')
    headerLoading.value = true
    headerError.value = null

    try {
      const data = await problemsApi.getHeader(id, controller.signal)
      headerData.value = data
      return data
    } catch (err: unknown) {
      if ((err as Error).name === 'AbortError') {
        return null
      }
      headerError.value = extractErrorMessage(err)
      console.error('[ProblemsStore] Failed to fetch header:', err)
      return null
    } finally {
      headerLoading.value = false
    }
  }

  async function fetchDescription(id: string): Promise<DescriptionData | null> {
    const controller = getAbortController('description')
    descriptionLoading.value = true
    descriptionError.value = null

    try {
      const data = await problemsApi.getDescription(id, controller.signal)
      descriptionData.value = data
      return data
    } catch (err: unknown) {
      if ((err as Error).name === 'AbortError') {
        return null
      }
      descriptionError.value = extractErrorMessage(err)
      console.error('[ProblemsStore] Failed to fetch description:', err)
      return null
    } finally {
      descriptionLoading.value = false
    }
  }

  async function fetchCode(id: string): Promise<CodeData | null> {
    const controller = getAbortController('code')
    codeLoading.value = true
    codeError.value = null

    try {
      const data = await problemsApi.getCode(id, controller.signal)
      codeData.value = data
      return data
    } catch (err: unknown) {
      if ((err as Error).name === 'AbortError') {
        return null
      }
      codeError.value = extractErrorMessage(err)
      console.error('[ProblemsStore] Failed to fetch code:', err)
      return null
    } finally {
      codeLoading.value = false
    }
  }

  async function fetchCases(id: string): Promise<CasesData | null> {
    const controller = getAbortController('cases')
    casesLoading.value = true
    casesError.value = null

    try {
      const data = await problemsApi.getCases(id, controller.signal)
      casesData.value = data
      return data
    } catch (err: unknown) {
      if ((err as Error).name === 'AbortError') {
        return null
      }
      casesError.value = extractErrorMessage(err)
      console.error('[ProblemsStore] Failed to fetch cases:', err)
      return null
    } finally {
      casesLoading.value = false
    }
  }

  // ==================== Legacy Fetch Problem (for backward compatibility) ====================

  async function fetchProblem(id: string, forceRefresh = false): Promise<Problem | null> {
    // Skip fetching if we already have this problem loaded and not forcing refresh
    if (!forceRefresh && loadedProblemId.value === id && currentProblem.value) {
      console.log('[ProblemsStore] Using cached problem data for id:', id)
      return currentProblem.value
    }

    loading.value = true
    error.value = null

    try {
      console.log('[ProblemsStore] Fetching problem with id:', id)
      const problem = await problemsApi.getProblem(id)
      console.log('[ProblemsStore] Received problem data:', problem)
      currentProblem.value = problem
      loadedProblemId.value = id

      return problem
    } catch (err: unknown) {
      error.value = extractErrorMessage(err)
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

  // ==================== CRUD Operations ====================

  async function createProblem(data: CreateProblemDto) {
    loading.value = true
    error.value = null
    try {
      const problem = await problemsApi.createProblem(data)
      return problem
    } catch (err: unknown) {
      error.value = extractErrorMessage(err)
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
      // Clear tab cache to force refresh on next visit
      clearCurrentProblem()
      return problem
    } catch (err: unknown) {
      error.value = extractErrorMessage(err)
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
      // Remove from local list (immutable)
      const previousLength = problems.value.length
      problems.value = problems.value.filter((p) => p.id !== id)
      if (problems.value.length !== previousLength) {
        total.value = total.value - 1
      }
      // Clear currentProblem and tab data if it matches
      if (currentProblem.value?.id === id) {
        clearCurrentProblem()
      }
    } catch (err: unknown) {
      error.value = extractErrorMessage(err)
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
      error.value = extractErrorMessage(err)
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
      error.value = extractErrorMessage(err)
      console.error('Failed to unpublish problem:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  async function updateProblemWithPublish(
    id: string,
    data: UpdateProblemDto,
    targetPublishedState: boolean,
  ) {
    loading.value = true
    error.value = null
    try {
      let problem = await problemsApi.updateProblem(id, data)

      const currentState = problem.is_published
      if (currentState !== targetPublishedState) {
        problem = targetPublishedState
          ? await problemsApi.publishProblem(id)
          : await problemsApi.unpublishProblem(id)

        const index = problems.value.findIndex((p) => p.id === id)
        if (index !== -1) {
          problems.value[index] = problem
        }
        if (currentProblem.value?.id === id) {
          currentProblem.value = problem
        }
      }

      // Clear tab cache to force refresh on next visit
      clearCurrentProblem()

      return problem
    } catch (err: unknown) {
      error.value = extractErrorMessage(err)
      console.error('Failed to update problem:', err)
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
      await fetchProblems()
    } catch (err: unknown) {
      error.value = extractErrorMessage(err)
      console.error('Failed to perform bulk action:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  // ==================== Utility Functions ====================

  function clearError() {
    error.value = null
  }

  function clearCurrentProblem() {
    // Clear new flat state
    headerData.value = null
    headerError.value = null
    descriptionData.value = null
    descriptionError.value = null
    codeData.value = null
    codeError.value = null
    casesData.value = null
    casesError.value = null

    // Clear legacy state
    currentProblem.value = null
    loadedProblemId.value = null
  }

  function reset() {
    problems.value = []
    total.value = 0
    loading.value = false
    error.value = null

    // Clear new flat state
    headerData.value = null
    headerError.value = null
    descriptionData.value = null
    descriptionError.value = null
    codeData.value = null
    codeError.value = null
    casesData.value = null
    casesError.value = null

    // Clear legacy state
    currentProblem.value = null
    loadedProblemId.value = null

    // Abort all pending requests
    abortAllRequests()
  }

  return {
    // State
    problems,
    total,
    loading,
    error,
    currentProblem,

    // New flat state
    headerData,
    headerLoading,
    headerError,
    descriptionData,
    descriptionLoading,
    descriptionError,
    codeData,
    codeLoading,
    codeError,
    casesData,
    casesLoading,
    casesError,

    // Actions
    fetchProblems,
    fetchProblem,
    fetchHeader,
    fetchDescription,
    fetchCode,
    fetchCases,
    createProblem,
    updateProblem,
    updateProblemWithPublish,
    deleteProblem,
    publishProblem,
    unpublishProblem,
    bulkAction,
    clearError,
    clearCurrentProblem,
    clearTabData: clearCurrentProblem, // Alias for backward compatibility
    abortAllRequests,
    reset,
  }
})
