import { defineStore } from 'pinia'
import { ref, computed, readonly, type DeepReadonly } from 'vue'
import {
  solutionsApi,
  type Solution,
  type SolutionListItem,
  type SolutionQueryParams,
  type FlagSolutionDto,
  type BulkSolutionActionDto,
} from '@/api/admin/solutions'
import { extractApiErrorMessage } from '@/utils/error'
import { createCollectionSlice } from '@/stores/createCollectionSlice'
export const useSolutionsStore = defineStore('adminSolutions', () => {
  const collection = createCollectionSlice<SolutionListItem, SolutionQueryParams>({
    load: async (params = {}, signal) => {
      const response = await solutionsApi.getSolutions(params, signal)
      return { items: response.items, total: response.total }
    },
  })
  const solutions: DeepReadonly<typeof collection.items> = readonly(collection.items)
  const total: DeepReadonly<typeof collection.total> = readonly(collection.total)
  const loading: DeepReadonly<typeof collection.isLoading> = readonly(collection.isLoading)
  const error: DeepReadonly<typeof collection.error> = readonly(collection.error)
  const fetchSolutions = collection.fetch
  const operationLoading = ref(false)
  const operationError = ref<string | null>(null)
  const currentSolution = ref<Solution | null>(null)

  // Computed stats for terminal ticker
  const totalCount = computed(() => total.value)
  const flaggedCount = computed(() => solutions.value.filter((s) => s.isFlagged).length)
  const publishedCount = computed(() => solutions.value.filter((s) => s.isPublished).length)

  async function fetchFlaggedSolutions(params: SolutionQueryParams = {}) {
    return collection.fetchWith(
      async (query = {}, signal) => {
        const response = await solutionsApi.getFlaggedSolutions(query, signal)
        return { items: response.items, total: response.total }
      },
      params,
      { errorMessage: 'Failed to fetch flagged solutions' },
    )
  }
  async function fetchSolution(id: string): Promise<Solution | null> {
    operationLoading.value = true
    operationError.value = null
    currentSolution.value = null // Clear previous solution
    try {
      const solution = await solutionsApi.getSolution(id)
      currentSolution.value = solution
      return solution
    } catch (err: unknown) {
      const errorMessage = extractApiErrorMessage(err, 'Failed to fetch solution')
      operationError.value = errorMessage
      console.error('Failed to fetch solution:', err)
      return null
    } finally {
      operationLoading.value = false
    }
  }

  async function flagSolution(id: string, data: FlagSolutionDto) {
    operationLoading.value = true
    operationError.value = null
    try {
      const solution = await solutionsApi.flagSolution(id, data)
      // Update list item flags if present
      collection.updateItems((current) =>
        current.map((solutionItem) =>
          solutionItem.id === id ? { ...solutionItem, isFlagged: true } : solutionItem,
        ),
      )
      // Also update currentSolution if it matches
      if (currentSolution.value?.id === id) {
        currentSolution.value = solution
      }
      return solution
    } catch (err: unknown) {
      operationError.value = extractApiErrorMessage(err, 'Failed to flag solution')
      console.error('Failed to flag solution:', err)
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function unflagSolution(id: string) {
    operationLoading.value = true
    operationError.value = null
    try {
      const solution = await solutionsApi.unflagSolution(id)
      // Update list item flags if present
      collection.updateItems((current) =>
        current.map((solutionItem) =>
          solutionItem.id === id ? { ...solutionItem, isFlagged: false } : solutionItem,
        ),
      )
      // Also update currentSolution if it matches
      if (currentSolution.value?.id === id) {
        currentSolution.value = solution
      }
      return solution
    } catch (err: unknown) {
      operationError.value = extractApiErrorMessage(err, 'Failed to unflag solution')
      console.error('Failed to unflag solution:', err)
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function deleteSolution(id: string) {
    operationLoading.value = true
    operationError.value = null
    try {
      await solutionsApi.deleteSolution(id)
      // Remove from local list (immutable update)
      const removed = solutions.value.some((solutionItem) => solutionItem.id === id)
      collection.updateItems((current) => current.filter((solutionItem) => solutionItem.id !== id))
      if (removed) {
        collection.setTotal(Math.max(0, total.value - 1))
      }
      // Clear currentSolution if it matches
      if (currentSolution.value?.id === id) {
        currentSolution.value = null
      }
    } catch (err: unknown) {
      operationError.value = extractApiErrorMessage(err, 'Failed to delete solution')
      console.error('Failed to delete solution:', err)
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function bulkAction(data: BulkSolutionActionDto) {
    operationLoading.value = true
    operationError.value = null
    try {
      await solutionsApi.bulkAction(data)
      // Refresh list after bulk action
      await fetchSolutions()
    } catch (err: unknown) {
      operationError.value = extractApiErrorMessage(err, 'Failed to perform bulk action')
      console.error('Failed to perform bulk action:', err)
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  function clearError() {
    collection.clearError()
    operationError.value = null
  }

  function clearCurrentSolution() {
    currentSolution.value = null
  }

  function reset() {
    collection.reset()
    operationLoading.value = false
    operationError.value = null
    currentSolution.value = null
  }

  return {
    items: solutions,
    isLoading: loading,
    fetch: collection.fetch,
    solutions,
    total,
    loading,
    error,
    operationLoading,
    operationError,
    currentSolution,
    // Computed stats
    totalCount,
    flaggedCount,
    publishedCount,
    // Actions
    fetchSolutions,
    fetchFlaggedSolutions,
    fetchSolution,
    flagSolution,
    unflagSolution,
    deleteSolution,
    bulkAction,
    clearError,
    clearCurrentSolution,
    reset,
  }
})
