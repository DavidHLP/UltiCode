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
import { createCollectionSlice } from '@/stores/createCollectionSlice'

export const useAdminProblemListsStore = defineStore('admin-problem-lists', () => {
  const collection = createCollectionSlice<ProblemList, ProblemListQuery>({
    load: async (query = {}) => {
      const pageResult = await adminProblemListsApi.getLists(query)
      return { items: pageResult.items, total: pageResult.total }
    },
  })
  const lists = collection.items
  const currentList = ref<ProblemListDetail | null>(null)
  const total = collection.total
  const isLoading = collection.isLoading
  const error = collection.error
  const fetchLists = collection.fetch

  function getErrorMessage(err: unknown, defaultMessage: string): string {
    if (isAxiosError(err) && err.response?.data?.message) {
      return err.response.data.message
    }
    return defaultMessage
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
    items: collection.items,
    fetch: collection.fetch,
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
