import { defineStore } from 'pinia'
import { ref, readonly, type DeepReadonly } from 'vue'
import {
  contestsApi,
  type Contest,
  type ContestQueryParams,
  type CreateContestDto,
  type UpdateContestDto,
  type AddContestProblemDto,
  type ContestRanking,
} from '@/api/admin/contests'
import { extractApiErrorMessage } from '@/utils/error'
import { createCollectionSlice } from '@/stores/createCollectionSlice'
export const useContestsStore = defineStore('adminContests', () => {
  const collection = createCollectionSlice<Contest, ContestQueryParams>({
    load: async (params = {}, signal) => {
      const response = await contestsApi.getContests(params, signal)
      return { items: response.items, total: response.total }
    },
  })
  const contests: DeepReadonly<typeof collection.items> = readonly(collection.items)
  const total: DeepReadonly<typeof collection.total> = readonly(collection.total)
  const loading: DeepReadonly<typeof collection.isLoading> = readonly(collection.isLoading)
  const error: DeepReadonly<typeof collection.error> = readonly(collection.error)
  const fetchContests = collection.fetch
  const operationLoading = ref(false)
  const operationError = ref<string | null>(null)
  const currentContest = ref<Contest | null>(null)
  const currentRankings = ref<ContestRanking[]>([])

  async function fetchContest(id: string): Promise<Contest | null> {
    operationLoading.value = true
    operationError.value = null
    currentContest.value = null // Clear previous
    try {
      const contest = await contestsApi.getContest(id)
      currentContest.value = contest
      return contest
    } catch (err: unknown) {
      const errorMessage = extractApiErrorMessage(err, 'Failed to fetch contest')
      operationError.value = errorMessage
      console.error('Failed to fetch contest:', err)
      return null
    } finally {
      operationLoading.value = false
    }
  }

  async function createContest(data: CreateContestDto) {
    operationLoading.value = true
    operationError.value = null
    try {
      const contest = await contestsApi.createContest(data)
      return contest
    } catch (err: unknown) {
      operationError.value = extractApiErrorMessage(err, 'Failed to create contest')
      console.error('Failed to create contest:', err)
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function updateContest(id: string, data: UpdateContestDto) {
    operationLoading.value = true
    operationError.value = null
    try {
      const contest = await contestsApi.updateContest(id, data)
      // Update local list if present
      collection.updateItems((current) =>
        current.map((contestItem) => (contestItem.id === id ? contest : contestItem)),
      )
      // Also update currentContest if it matches
      if (currentContest.value?.id === id) {
        currentContest.value = { ...currentContest.value, ...contest }
      }
      return contest
    } catch (err: unknown) {
      operationError.value = extractApiErrorMessage(err, 'Failed to update contest')
      console.error('Failed to update contest:', err)
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function deleteContest(id: string) {
    operationLoading.value = true
    operationError.value = null
    try {
      await contestsApi.deleteContest(id)
      // Remove from local list
      const removed = contests.value.some((contestItem) => contestItem.id === id)
      collection.updateItems((current) => current.filter((contestItem) => contestItem.id !== id))
      if (removed) collection.setTotal(Math.max(0, total.value - 1))
      // Clear currentContest if it matches
      if (currentContest.value?.id === id) {
        currentContest.value = null
      }
    } catch (err: unknown) {
      operationError.value = extractApiErrorMessage(err, 'Failed to delete contest')
      console.error('Failed to delete contest:', err)
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function startContest(id: string) {
    operationLoading.value = true
    operationError.value = null
    try {
      const contest = await contestsApi.startContest(id)
      collection.updateItems((current) =>
        current.map((contestItem) => (contestItem.id === id ? contest : contestItem)),
      )
      if (currentContest.value?.id === id) currentContest.value = contest
      return contest
    } catch (err: unknown) {
      operationError.value = extractApiErrorMessage(err, 'Failed to start contest')
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function endContest(id: string) {
    operationLoading.value = true
    operationError.value = null
    try {
      const contest = await contestsApi.endContest(id)
      collection.updateItems((current) =>
        current.map((contestItem) => (contestItem.id === id ? contest : contestItem)),
      )
      if (currentContest.value?.id === id) currentContest.value = contest
      return contest
    } catch (err: unknown) {
      operationError.value = extractApiErrorMessage(err, 'Failed to end contest')
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function addProblem(id: string, data: AddContestProblemDto) {
    // Note: This endpoint returns the ContestProblem, but we often want to refresh the whole contest
    operationLoading.value = true
    operationError.value = null
    try {
      await contestsApi.addProblem(id, data)
      await fetchContest(id) // Refresh to get updated problems list
    } catch (err: unknown) {
      operationError.value = extractApiErrorMessage(err, 'Failed to add problem')
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function removeProblem(id: string, problemId: number) {
    operationLoading.value = true
    operationError.value = null
    try {
      await contestsApi.removeProblem(id, problemId)
      if (currentContest.value?.problemIds) {
        currentContest.value = {
          ...currentContest.value,
          problemIds: currentContest.value.problemIds.filter((pid) => pid !== problemId),
        }
      }
    } catch (err: unknown) {
      operationError.value = extractApiErrorMessage(err, 'Failed to remove problem')
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function fetchRankings(id: string, page = 1, limit = 50) {
    operationLoading.value = true
    operationError.value = null
    try {
      const response = await contestsApi.getRankings(id, page, limit)
      currentRankings.value = response.items
      return response.items
    } catch (err: unknown) {
      operationError.value = extractApiErrorMessage(err, 'Failed to fetch contest rankings')
      console.error('Failed to fetch rankings', err)
    } finally {
      operationLoading.value = false
    }
  }

  function clearError() {
    collection.clearError()
    operationError.value = null
  }

  function clearCurrentContest() {
    currentContest.value = null
    currentRankings.value = []
  }

  return {
    items: contests,
    isLoading: loading,
    fetch: collection.fetch,
    cancel: collection.cancel,
    contests,
    total,
    loading,
    error,
    operationLoading,
    operationError,
    currentContest,
    currentRankings,
    fetchContests,
    fetchContest,
    createContest,
    updateContest,
    deleteContest,
    startContest,
    endContest,
    addProblem,
    removeProblem,
    fetchRankings,
    clearError,
    clearCurrentContest,
  }
})
