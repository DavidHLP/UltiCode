import { defineStore } from 'pinia'
import { ref, computed, readonly, type DeepReadonly } from 'vue'
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
import { createCollectionSlice } from '@/stores/createCollectionSlice'

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
  const collection = createCollectionSlice<Problem, ProblemQueryParams>({
    load: async (params = {}) => {
      const pageResult = await problemsApi.getProblems(params)
      return { items: pageResult.items, total: pageResult.total }
    },
  })
  const problems: DeepReadonly<typeof collection.items> = readonly(collection.items)
  const total: DeepReadonly<typeof collection.total> = readonly(collection.total)
  const loading: DeepReadonly<typeof collection.isLoading> = readonly(collection.isLoading)
  const error: DeepReadonly<typeof collection.error> = readonly(collection.error)
  const fetchProblems = collection.fetch
  const operationLoading = ref(false)
  const operationError = ref<string | null>(null)

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
    operationLoading.value = true
    operationError.value = null
    try {
      const problem = await problemsApi.createProblem(data)
      return problem
    } catch (err: unknown) {
      operationError.value = extractErrorMessage(err)
      console.error('Failed to create problem:', err)
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function updateProblem(id: string, data: ProblemUpdateInput) {
    operationLoading.value = true
    operationError.value = null
    try {
      const problem = await problemsApi.updateProblem(id, data)
      collection.updateItems((current) =>
        current.map((problemItem) => (problemItem.id === id ? problem : problemItem)),
      )
      invalidateTabCache(id)
      return problem
    } catch (err: unknown) {
      operationError.value = extractErrorMessage(err)
      console.error('Failed to update problem:', err)
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function deleteProblem(id: string) {
    operationLoading.value = true
    operationError.value = null
    try {
      await problemsApi.deleteProblem(id)
      const removed = problems.value.some((problemItem) => problemItem.id === id)
      collection.updateItems((current) => current.filter((problemItem) => problemItem.id !== id))
      if (removed) collection.setTotal(Math.max(0, total.value - 1))
      if (getTabState<HeaderData>('header').loadedId === id) {
        clearCurrentProblem()
      }
    } catch (err: unknown) {
      operationError.value = extractErrorMessage(err)
      console.error('Failed to delete problem:', err)
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function publishProblem(id: string) {
    operationLoading.value = true
    operationError.value = null
    try {
      const problem = await problemsApi.publishProblem(id)
      collection.updateItems((current) =>
        current.map((problemItem) => (problemItem.id === id ? problem : problemItem)),
      )
      invalidateTabCache(id)
      return problem
    } catch (err: unknown) {
      operationError.value = extractErrorMessage(err)
      console.error('Failed to publish problem:', err)
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function unpublishProblem(id: string) {
    operationLoading.value = true
    operationError.value = null
    try {
      const problem = await problemsApi.unpublishProblem(id)
      collection.updateItems((current) =>
        current.map((problemItem) => (problemItem.id === id ? problem : problemItem)),
      )
      invalidateTabCache(id)
      return problem
    } catch (err: unknown) {
      operationError.value = extractErrorMessage(err)
      console.error('Failed to unpublish problem:', err)
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function updateProblemWithPublish(
    id: string,
    data: ProblemUpdateInput,
    targetPublishedState: boolean,
  ) {
    operationLoading.value = true
    operationError.value = null
    try {
      let problem = await problemsApi.updateProblem(id, data)

      const currentState = problem.isPublished
      if (currentState !== targetPublishedState) {
        problem = targetPublishedState
          ? await problemsApi.publishProblem(id)
          : await problemsApi.unpublishProblem(id)

        collection.updateItems((current) =>
          current.map((problemItem) => (problemItem.id === id ? problem : problemItem)),
        )
      }

      invalidateTabCache(id)

      return problem
    } catch (err: unknown) {
      operationError.value = extractErrorMessage(err)
      console.error('Failed to update problem:', err)
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function bulkAction(data: BulkProblemActionDto) {
    operationLoading.value = true
    operationError.value = null
    try {
      await problemsApi.bulkAction(data)
      await fetchProblems()
    } catch (err: unknown) {
      operationError.value = extractErrorMessage(err)
      console.error('Failed to perform bulk action:', err)
      throw err
    } finally {
      operationLoading.value = false
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
    collection.clearError()
    operationError.value = null
  }

  function clearCurrentProblem() {
    tabStates.value.clear()
    abortAllRequests()
  }

  function reset() {
    collection.reset()
    operationLoading.value = false
    operationError.value = null

    clearCurrentProblem()
  }

  return {
    items: problems,
    isLoading: loading,
    fetch: collection.fetch,
    problems,
    total,
    loading,
    error,
    operationLoading,
    operationError,
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
