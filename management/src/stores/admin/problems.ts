import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  problemsApi,
  type Problem,
  type ProblemQueryParams,
  type CreateProblemDto,
  type UpdateProblemDto,
  type BulkProblemActionDto,
  type ProblemExample,
  type ProblemLanguage,
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

  // ==================== List Operations ====================

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

  // ==================== Tab-specific Data Fetching ====================

  /**
   * Clear all cached data when switching to a different problem
   */
  function clearTabData() {
    tabData.value = {
      description: null,
      code: null,
      cases: null,
    }
  }

  /**
   * Fetch description tab data (always fetch fresh data from backend)
   */
  async function fetchDescriptionData(id: string): Promise<DescriptionData | null> {
    tabLoading.value.description = true
    error.value = null

    try {
      console.log('[ProblemsStore] Fetching description data for id:', id)
      const problem = await problemsApi.getProblem(id)
      console.log('[ProblemsStore] Received problem data:', problem)

      const descriptionData: DescriptionData = {
        id: problem.id,
        title: problem.title,
        slug: problem.slug,
        difficulty: problem.difficulty,
        status: problem.status,
        is_premium: problem.is_premium,
        is_published: problem.is_published,
        created_at: problem.created_at,
        updated_at: problem.updated_at,
        published_at: problem.published_at,
        detail: problem.detail,
        tags: problem.tags,
        examples: problem.examples,
      }

      // Update current tab data
      tabData.value.description = descriptionData
      currentProblem.value = problem
      loadedProblemId.value = id

      return descriptionData
    } catch (err: unknown) {
      const errorMessage =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to fetch problem description'
      error.value = errorMessage
      console.error('[ProblemsStore] Failed to fetch description data:', err)
      return null
    } finally {
      tabLoading.value.description = false
    }
  }

  /**
   * Fetch code tab data (always fetch fresh data from backend)
   */
  async function fetchCodeData(id: string): Promise<CodeData | null> {
    tabLoading.value.code = true
    error.value = null

    try {
      console.log('[ProblemsStore] Fetching code data for id:', id)
      const problem = await problemsApi.getProblem(id)

      const codeData: CodeData = {
        id: problem.id,
        languages: problem.languages,
      }

      // Update current tab data
      tabData.value.code = codeData

      return codeData
    } catch (err: unknown) {
      const errorMessage =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to fetch problem code data'
      error.value = errorMessage
      console.error('[ProblemsStore] Failed to fetch code data:', err)
      return null
    } finally {
      tabLoading.value.code = false
    }
  }

  /**
   * Fetch cases tab data (always fetch fresh data from backend)
   */
  async function fetchCasesData(id: string): Promise<CasesData | null> {
    tabLoading.value.cases = true
    error.value = null

    try {
      console.log('[ProblemsStore] Fetching cases data for id:', id)
      const problem = await problemsApi.getProblem(id)

      const casesData: CasesData = {
        id: problem.id,
        examples: problem.examples,
        detail: problem.detail
          ? {
              constraints_json: problem.detail.constraints_json,
              hints: problem.detail.hints,
            }
          : undefined,
        tags: problem.tags,
      }

      // Update current tab data
      tabData.value.cases = casesData

      return casesData
    } catch (err: unknown) {
      const errorMessage =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Failed to fetch problem cases data'
      error.value = errorMessage
      console.error('[ProblemsStore] Failed to fetch cases data:', err)
      return null
    } finally {
      tabLoading.value.cases = false
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

      // Also populate tab caches
      tabData.value.description = {
        id: problem.id,
        title: problem.title,
        slug: problem.slug,
        difficulty: problem.difficulty,
        status: problem.status,
        is_premium: problem.is_premium,
        is_published: problem.is_published,
        created_at: problem.created_at,
        updated_at: problem.updated_at,
        published_at: problem.published_at,
        detail: problem.detail,
        tags: problem.tags,
        examples: problem.examples,
      }
      tabData.value.code = {
        id: problem.id,
        languages: problem.languages,
      }
      tabData.value.cases = {
        id: problem.id,
        examples: problem.examples,
        detail: problem.detail
          ? {
              constraints_json: problem.detail.constraints_json,
              hints: problem.detail.hints,
            }
          : undefined,
        tags: problem.tags,
      }

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

  // ==================== CRUD Operations ====================

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
      // Clear tab cache to force refresh on next visit
      clearTabData()
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
      // Clear currentProblem and tab data if it matches
      if (currentProblem.value?.id === id) {
        currentProblem.value = null
        loadedProblemId.value = null
        clearTabData()
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
      clearTabData()

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

  async function bulkAction(data: BulkProblemActionDto) {
    loading.value = true
    error.value = null
    try {
      await problemsApi.bulkAction(data)
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

  // ==================== Utility Functions ====================

  function clearError() {
    error.value = null
  }

  function clearCurrentProblem() {
    currentProblem.value = null
    loadedProblemId.value = null
    clearTabData()
  }

  function reset() {
    problems.value = []
    total.value = 0
    loading.value = false
    error.value = null
    currentProblem.value = null
    loadedProblemId.value = null
    clearTabData()
  }

  return {
    // State
    problems,
    total,
    loading,
    error,
    currentProblem,
    tabLoading,
    tabData,

    // Actions
    fetchProblems,
    fetchProblem,
    fetchDescriptionData,
    fetchCodeData,
    fetchCasesData,
    createProblem,
    updateProblem,
    updateProblemWithPublish,
    deleteProblem,
    publishProblem,
    unpublishProblem,
    bulkAction,
    clearError,
    clearCurrentProblem,
    clearTabData,
    reset,
  }
})
