import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  problemsApi,
  type Problem,
  type ProblemQueryParams,
  type ProblemCreateInput,
  type ProblemUpdateInput,
  type BulkProblemActionDto,
  type HeaderData,
  type DescriptionData,
  type CodeData,
  type CasesData,
} from '@/api/admin/problems'
import { extractApiErrorMessage } from '@/utils/error'
import { tagsApi, TagType } from '@/api/admin/tags'
import { ApiError } from '@/utils/request'

export interface ProblemTag {
  id: string
  label: string
}

export interface TabState<T> {
  data: T | null
  loading: boolean
  error: string | null
  loadedId: string | null
  loadedAt: number | null
}

function createTabState<T>(): TabState<T> {
  return {
    data: null,
    loading: false,
    error: null,
    loadedId: null,
    loadedAt: null,
  }
}

export type ProblemEditTab = 'header' | 'description' | 'code' | 'cases'

const CACHE_TTL_MS = 30_000

export const useProblemsStore = defineStore('adminProblems', () => {
  const problems = ref<Problem[]>([])
  const total = ref(0)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const tabStates = ref<Map<string, TabState<unknown>>>(new Map())

  function getTabState<T>(tabKey: string): TabState<T> {
    if (!tabStates.value.has(tabKey)) {
      tabStates.value.set(tabKey, createTabState<T>() as TabState<unknown>)
    }
    return tabStates.value.get(tabKey) as TabState<T>
  }

  const headerData = computed(() => getTabState<HeaderData>('header').data)
  const headerLoading = computed(() => getTabState<HeaderData>('header').loading)
  const headerError = computed(() => getTabState<HeaderData>('header').error)

  const descriptionData = computed(() => getTabState<DescriptionData>('description').data)
  const descriptionLoading = computed(() => getTabState<DescriptionData>('description').loading)
  const descriptionError = computed(() => getTabState<DescriptionData>('description').error)

  const codeData = computed(() => getTabState<CodeData>('code').data)
  const codeLoading = computed(() => getTabState<CodeData>('code').loading)
  const codeError = computed(() => getTabState<CodeData>('code').error)

  const casesData = computed(() => getTabState<CasesData>('cases').data)
  const casesLoading = computed(() => getTabState<CasesData>('cases').loading)
  const casesError = computed(() => getTabState<CasesData>('cases').error)

  const allTags = ref<ProblemTag[]>([])
  const tagsLoading = ref(false)

  const abortControllers = ref<Map<string, AbortController>>(new Map())

  function getAbortController(key: string): AbortController {
    const controller = abortControllers.value.get(key)
    if (controller) {
      controller.abort()
    }
    const newController = new AbortController()
    abortControllers.value.set(key, newController)
    return newController
  }

  function abortAllRequests() {
    abortControllers.value.forEach((controller) => controller.abort())
    abortControllers.value.clear()
  }

  function extractErrorMessage(err: unknown): string {
    return extractApiErrorMessage(err, 'An error occurred')
  }

  async function fetchTab<T>(
    tabKey: string,
    id: string,
    fetchFn: (id: string, signal: AbortSignal) => Promise<T>,
    forceRefresh = false,
  ): Promise<T | null> {
    const state = getTabState<T>(tabKey)

    const now = Date.now()
    const isStale = !state.loadedAt || now - state.loadedAt > CACHE_TTL_MS
    if (!forceRefresh && state.loadedId === id && state.data && !isStale) {
      state.loading = false
      return state.data
    }

    const controller = getAbortController(tabKey)
    state.loading = true
    state.error = null

    try {
      const data = await fetchFn(id, controller.signal)
      if (controller.signal.aborted) return null

      state.data = data
      state.loadedId = id
      state.loadedAt = now
      return data
    } catch (err: unknown) {
      if ((err as Error).name === 'AbortError') {
        return null
      }
      state.error = extractErrorMessage(err)
      console.error(`[ProblemsStore] Failed to fetch ${tabKey}:`, err)
      return null
    } finally {
      if (abortControllers.value.get(tabKey) === controller) {
        state.loading = false
      }
    }
  }

  // Monotonic sequence for the list fetch so a stale response can never
  // overwrite a fresher one when two fetchProblems calls overlap (the view and
  // useDataTable can both trigger reloads; only the newest result is applied).
  let lastFetchSeq = 0

  async function fetchProblems(params: ProblemQueryParams = {}) {
    const seq = ++lastFetchSeq
    loading.value = true
    error.value = null
    try {
      const pageResult = await problemsApi.getProblems(params)
      if (seq !== lastFetchSeq) return
      problems.value = pageResult.items
      total.value = pageResult.total
    } catch (err: unknown) {
      if (seq !== lastFetchSeq) return
      // Ignore cancellation errors from debounced requests - not a real error
      if (err instanceof ApiError && err.code === -1 && err.message === 'Request canceled') {
        return
      }
      error.value = extractErrorMessage(err)
      console.error('Failed to fetch problems:', err)
    } finally {
      if (seq === lastFetchSeq) {
        loading.value = false
      }
    }
  }

  async function fetchHeader(id: string, forceRefresh = false): Promise<HeaderData | null> {
    return fetchTab(
      'header',
      id,
      (problemId, signal) => problemsApi.getHeader(problemId, signal),
      forceRefresh,
    )
  }

  async function fetchDescription(
    id: string,
    forceRefresh = false,
  ): Promise<DescriptionData | null> {
    return fetchTab(
      'description',
      id,
      (problemId, signal) => problemsApi.getDescription(problemId, signal),
      forceRefresh,
    )
  }

  async function fetchCode(id: string, forceRefresh = false): Promise<CodeData | null> {
    return fetchTab(
      'code',
      id,
      (problemId, signal) => problemsApi.getCode(problemId, signal),
      forceRefresh,
    )
  }

  async function fetchCases(id: string, forceRefresh = false): Promise<CasesData | null> {
    return fetchTab(
      'cases',
      id,
      (problemId, signal) => problemsApi.getCases(problemId, signal),
      forceRefresh,
    )
  }

  async function fetchAllTags(): Promise<ProblemTag[]> {
    tagsLoading.value = true
    try {
      const tags = await tagsApi.getAllTags(TagType.PROBLEM)
      allTags.value = tags
      return tags
    } catch (err) {
      console.error('[ProblemsStore] Failed to fetch all tags:', err)
      return []
    } finally {
      tagsLoading.value = false
    }
  }

  async function createProblem(data: ProblemCreateInput) {
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

  async function updateProblem(id: string, data: ProblemUpdateInput) {
    loading.value = true
    error.value = null
    try {
      const problem = await problemsApi.updateProblem(id, data)
      const index = problems.value.findIndex((p) => p.id === id)
      if (index !== -1) {
        problems.value[index] = problem
      }
      invalidateTabCache(id)
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
      const previousLength = problems.value.length
      problems.value = problems.value.filter((p) => p.id !== id)
      if (problems.value.length !== previousLength) {
        total.value = total.value - 1
      }
      if (getTabState<HeaderData>('header').loadedId === id) {
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
      const index = problems.value.findIndex((p) => p.id === id)
      if (index !== -1) {
        problems.value[index] = problem
      }
      invalidateTabCache(id)
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
      const index = problems.value.findIndex((p) => p.id === id)
      if (index !== -1) {
        problems.value[index] = problem
      }
      invalidateTabCache(id)
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
    data: ProblemUpdateInput,
    targetPublishedState: boolean,
  ) {
    loading.value = true
    error.value = null
    try {
      let problem = await problemsApi.updateProblem(id, data)

      const currentState = problem.isPublished
      if (currentState !== targetPublishedState) {
        problem = targetPublishedState
          ? await problemsApi.publishProblem(id)
          : await problemsApi.unpublishProblem(id)

        const index = problems.value.findIndex((p) => p.id === id)
        if (index !== -1) {
          problems.value[index] = problem
        }
      }

      invalidateTabCache(id)

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

  function invalidateTabCache(problemId: string) {
    tabStates.value.forEach((state) => {
      if (state.loadedId === problemId) {
        state.loadedId = null
        state.loadedAt = null
      }
    })
  }

  function getRawTabState<T>(tabKey: string): TabState<T> {
    return getTabState<T>(tabKey)
  }

  function clearError() {
    error.value = null
  }

  function clearCurrentProblem() {
    tabStates.value.clear()
    abortAllRequests()
  }

  function reset() {
    problems.value = []
    total.value = 0
    loading.value = false
    error.value = null

    clearCurrentProblem()
  }

  return {
    problems,
    total,
    loading,
    error,
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
    allTags,
    tagsLoading,
    fetchProblems,
    fetchHeader,
    fetchDescription,
    fetchAllTags,
    fetchCode,
    fetchCases,
    createProblem,
    updateProblem,
    updateProblemWithPublish,
    deleteProblem,
    publishProblem,
    unpublishProblem,
    bulkAction,
    invalidateTabCache,
    getRawTabState,
    clearError,
    clearCurrentProblem,
    clearTabData: clearCurrentProblem,
    abortAllRequests,
    reset,
  }
})
