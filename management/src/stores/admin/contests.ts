import { defineStore } from 'pinia'
import { ref } from 'vue'
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
export const useContestsStore = defineStore('adminContests', () => {
  const contests = ref<Contest[]>([])
  const total = ref(0)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const currentContest = ref<Contest | null>(null)
  const currentRankings = ref<ContestRanking[]>([])

  async function fetchContests(params: ContestQueryParams = {}) {
    loading.value = true
    error.value = null
    try {
      const response = await contestsApi.getContests(params)
      contests.value = response.items
      total.value = response.total
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to fetch contests')
      console.error('Failed to fetch contests:', err)
    } finally {
      loading.value = false
    }
  }

  async function fetchContest(id: string): Promise<Contest | null> {
    loading.value = true
    error.value = null
    currentContest.value = null // Clear previous
    try {
      const contest = await contestsApi.getContest(id)
      currentContest.value = contest
      return contest
    } catch (err: unknown) {
      const errorMessage =
        extractApiErrorMessage(err, 'Failed to fetch contest')
      error.value = errorMessage
      console.error('Failed to fetch contest:', err)
      return null
    } finally {
      loading.value = false
    }
  }

  async function createContest(data: CreateContestDto) {
    loading.value = true
    error.value = null
    try {
      const contest = await contestsApi.createContest(data)
      return contest
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to create contest')
      console.error('Failed to create contest:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  async function updateContest(id: string, data: UpdateContestDto) {
    loading.value = true
    error.value = null
    try {
      const contest = await contestsApi.updateContest(id, data)
      // Update local list if present
      const index = contests.value.findIndex((c) => c.id === id)
      if (index !== -1) {
        contests.value[index] = contest
      }
      // Also update currentContest if it matches
      if (currentContest.value?.id === id) {
        currentContest.value = { ...currentContest.value, ...contest }
      }
      return contest
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to update contest')
      console.error('Failed to update contest:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  async function deleteContest(id: string) {
    loading.value = true
    error.value = null
    try {
      await contestsApi.deleteContest(id)
      // Remove from local list
      const index = contests.value.findIndex((c) => c.id === id)
      if (index !== -1) {
        contests.value.splice(index, 1)
        total.value--
      }
      // Clear currentContest if it matches
      if (currentContest.value?.id === id) {
        currentContest.value = null
      }
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to delete contest')
      console.error('Failed to delete contest:', err)
      throw err
    } finally {
      loading.value = false
    }
  }

  async function startContest(id: string) {
    loading.value = true
    error.value = null
    try {
      const contest = await contestsApi.startContest(id)
      const index = contests.value.findIndex((c) => c.id === id)
      if (index !== -1) contests.value[index] = contest
      if (currentContest.value?.id === id) currentContest.value = contest
      return contest
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to start contest')
      throw err
    } finally {
      loading.value = false
    }
  }

  async function endContest(id: string) {
    loading.value = true
    error.value = null
    try {
      const contest = await contestsApi.endContest(id)
      const index = contests.value.findIndex((c) => c.id === id)
      if (index !== -1) contests.value[index] = contest
      if (currentContest.value?.id === id) currentContest.value = contest
      return contest
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to end contest')
      throw err
    } finally {
      loading.value = false
    }
  }

  async function addProblem(id: string, data: AddContestProblemDto) {
    // Note: This endpoint returns the ContestProblem, but we often want to refresh the whole contest
    loading.value = true
    try {
      await contestsApi.addProblem(id, data)
      await fetchContest(id) // Refresh to get updated problems list
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to add problem')
      throw err
    } finally {
      loading.value = false
    }
  }

  async function removeProblem(id: string, problemId: number) {
    loading.value = true
    try {
      await contestsApi.removeProblem(id, problemId)
      if (currentContest.value?.problemIds) {
        currentContest.value = {
          ...currentContest.value,
          problemIds: currentContest.value.problemIds.filter((pid) => pid !== problemId),
        }
      }
    } catch (err: unknown) {
      error.value = extractApiErrorMessage(err, 'Failed to remove problem')
      throw err
    } finally {
      loading.value = false
    }
  }

  async function fetchRankings(id: string, page = 1, limit = 50) {
    loading.value = true
    try {
      const response = await contestsApi.getRankings(id, page, limit)
      currentRankings.value = response.items
      return response.items
    } catch (err: unknown) {
      console.error('Failed to fetch rankings', err)
    } finally {
      loading.value = false
    }
  }

  function clearError() {
    error.value = null
  }

  function clearCurrentContest() {
    currentContest.value = null
    currentRankings.value = []
  }

  return {
    contests,
    total,
    loading,
    error,
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
