import { defineStore } from 'pinia'
import { ref } from 'vue'
import { isAxiosError } from 'axios'
import {
  adminProblemListsApi,
  type ProblemList,
  type ProblemListQuery,
  type CreateProblemListDto,
  type ProblemListDetail,
  type UpdateProblemListProblemsDto,
} from '@/api/admin/problem-lists'

export const useAdminProblemListsStore = defineStore('admin-problem-lists', () => {
  const lists = ref<ProblemList[]>([])
  const currentList = ref<ProblemListDetail | null>(null)
  const total = ref(0)
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  function getErrorMessage(err: unknown, defaultMessage: string): string {
    if (isAxiosError(err) && err.response?.data?.message) {
      return err.response.data.message
    }
    return defaultMessage
  }

  async function fetchLists(query: ProblemListQuery) {
    isLoading.value = true
    error.value = null
    try {
      const pageResult = await adminProblemListsApi.getLists(query)
      lists.value = pageResult.items
      total.value = pageResult.total
    } catch (err) {
      error.value = getErrorMessage(err, 'Failed to fetch problem lists')
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function fetchList(id: string) {
    isLoading.value = true
    error.value = null
    try {
      currentList.value = await adminProblemListsApi.getList(id)
    } catch (err) {
      error.value = getErrorMessage(err, 'Failed to fetch problem list')
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function createList(data: CreateProblemListDto) {
    isLoading.value = true
    error.value = null
    try {
      return await adminProblemListsApi.createList(data)
    } catch (err) {
      error.value = getErrorMessage(err, 'Failed to create problem list')
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function deleteList(id: string) {
    isLoading.value = true
    error.value = null
    try {
      await adminProblemListsApi.deleteList(id)
      lists.value = lists.value.filter((l) => l.id !== id)
    } catch (err) {
      error.value = getErrorMessage(err, 'Failed to delete problem list')
      throw err
    } finally {
      isLoading.value = false
    }
  }

  async function updateListProblems(id: string, data: UpdateProblemListProblemsDto) {
    isLoading.value = true
    error.value = null
    try {
      await adminProblemListsApi.updateListProblems(id, data)
      // Refresh list details
      await fetchList(id)
    } catch (err) {
      error.value = getErrorMessage(err, 'Failed to update list problems')
      throw err
    } finally {
      isLoading.value = false
    }
  }

  return {
    lists,
    currentList,
    total,
    isLoading,
    error,
    fetchLists,
    fetchList,
    createList,
    deleteList,
    updateListProblems,
  }
})
