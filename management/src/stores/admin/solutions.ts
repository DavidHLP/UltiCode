import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  solutionsApi,
  type Solution,
  type SolutionListItem,
  type SolutionQueryParams,
  type FlagSolutionDto,
  type BulkSolutionActionDto,
} from '@/api/admin/solutions'
import { extractApiErrorMessage } from '@/utils/error'
export const useSolutionsStore = defineStore('adminSolutions', () => {
  const solutions = ref<SolutionListItem[]>([])
  const total = ref(0)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const currentSolution = ref<Solution | null>(null)

  // Computed stats for terminal ticker
  const totalCount = computed(() => total.value)
  const flaggedCount = computed(() => solutions.value.filter((s) => s.isFlagged).length)
  const publishedCount = computed(() => solutions.value.filter((s) => s.isPublished).length)

  async function fetchSolutions(params: SolutionQueryParams = {}) {
    loading.value = true
    error.value = null
    try {
      const response = await solutionsApi.getSolutions(params)
      solutions.value = response.items
      total.value = response.total
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to fetch solutions')
      console.error('Failed to fetch solutions:', err)
    } finally {
      loading.value = false
    }
  }

  async function fetchFlaggedSolutions(params: SolutionQueryParams = {}) {
    loading.value = true
    error.value = null
    try {
      const response = await solutionsApi.getFlaggedSolutions(params)
      solutions.value = response.items
      total.value = response.total
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to fetch flagged solutions')
      console.error('Failed to fetch flagged solutions:', err)
    } finally {
      loading.value = false
    }
  }

  async function fetchSolution(id: string): Promise<Solution | null> {
    loading.value = true
    error.value = null
    currentSolution.value = null // Clear previous solution
    try {
      const solution = await solutionsApi.getSolution(id)
      currentSolution.value = solution
      return solution
    } catch (err: unknown) {
      const errorMessage =
        extractApiErrorMessage(err, 'Failed to fetch solution')
      error.value = errorMessage
      console.error('Failed to fetch solution:', err)
      return null
    } finally {
      loading.value = false
    }
  }

  async function flagSolution(id: string, data: FlagSolutionDto) {
    loading.value = true
    error.value = null
    try {
      const solution = await solutionsApi.flagSolution(id, data)
      // Update list item flags if present
      const index = solutions.value.findIndex((s) => s.id === id)
      if (index !== -1) {
        solutions.value[index] = { ...solutions.value[index], isFlagged: true }
      }
      // Also update currentSolution if it matches
      if (currentSolution.value?.id === id) {
        currentSolution.value = solution
      }
      return solution
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to flag solution')
      console.error('Failed to flag solution:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  async function unflagSolution(id: string) {
    loading.value = true
    error.value = null
    try {
      const solution = await solutionsApi.unflagSolution(id)
      // Update list item flags if present
      const index = solutions.value.findIndex((s) => s.id === id)
      if (index !== -1) {
        solutions.value[index] = { ...solutions.value[index], isFlagged: false }
      }
      // Also update currentSolution if it matches
      if (currentSolution.value?.id === id) {
        currentSolution.value = solution
      }
      return solution
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to unflag solution')
      console.error('Failed to unflag solution:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  async function deleteSolution(id: string) {
    loading.value = true
    error.value = null
    try {
      await solutionsApi.deleteSolution(id)
      // Remove from local list (immutable update)
      solutions.value = solutions.value.filter((s) => s.id !== id)
      total.value--
      // Clear currentSolution if it matches
      if (currentSolution.value?.id === id) {
        currentSolution.value = null
      }
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to delete solution')
      console.error('Failed to delete solution:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  async function bulkAction(data: BulkSolutionActionDto) {
    loading.value = true
    error.value = null
    try {
      await solutionsApi.bulkAction(data)
      // Refresh list after bulk action
      await fetchSolutions()
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to perform bulk action')
      console.error('Failed to perform bulk action:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  function clearError() {
    error.value = null
  }

  function clearCurrentSolution() {
    currentSolution.value = null
  }

  function reset() {
    solutions.value = []
    total.value = 0
    loading.value = false
    error.value = null
    currentSolution.value = null
  }

  return {
    solutions,
    total,
    loading,
    error,
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
