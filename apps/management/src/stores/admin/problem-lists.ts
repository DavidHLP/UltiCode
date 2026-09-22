import { defineStore } from 'pinia'
import { ref, readonly, type DeepReadonly } from 'vue'
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
    load: async (query = {}, signal) => {
      const pageResult = await adminProblemListsApi.getLists(query, signal)
      return { items: pageResult.items, total: pageResult.total }
    },
  })
  const lists: DeepReadonly<typeof collection.items> = readonly(collection.items)
  const currentList = ref<ProblemListDetail | null>(null)
  const total: DeepReadonly<typeof collection.total> = readonly(collection.total)
  const isLoading: DeepReadonly<typeof collection.isLoading> = readonly(collection.isLoading)
  const error: DeepReadonly<typeof collection.error> = readonly(collection.error)
  const fetchLists = collection.fetch
  const operationLoading = ref(false)
  const operationError = ref<string | null>(null)

  function getErrorMessage(err: unknown, defaultMessage: string): string {
    if (isAxiosError(err) && err.response?.data?.message) {
      return err.response.data.message
    }
    return defaultMessage
  }

  async function fetchList(id: string) {
    operationLoading.value = true
    operationError.value = null
    try {
      currentList.value = await adminProblemListsApi.getList(id)
    } catch (err) {
      operationError.value = getErrorMessage(err, 'Failed to fetch problem list')
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function createList(data: CreateProblemListDto) {
    operationLoading.value = true
    operationError.value = null
    try {
      return await adminProblemListsApi.createList(data)
    } catch (err) {
      operationError.value = getErrorMessage(err, 'Failed to create problem list')
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function deleteList(id: string) {
    operationLoading.value = true
    operationError.value = null
    try {
      await adminProblemListsApi.deleteList(id)
      const removed = lists.value.some((list) => list.id === id)
      collection.updateItems((current) => current.filter((list) => list.id !== id))
      if (removed) collection.setTotal(Math.max(0, total.value - 1))
    } catch (err) {
      operationError.value = getErrorMessage(err, 'Failed to delete problem list')
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  async function updateListProblems(id: string, data: UpdateProblemListProblemsDto) {
    operationLoading.value = true
    operationError.value = null
    try {
      await adminProblemListsApi.updateListProblems(id, data)
      // Refresh list details
      await fetchList(id)
    } catch (err) {
      operationError.value = getErrorMessage(err, 'Failed to update list problems')
      throw err
    } finally {
      operationLoading.value = false
    }
  }

  return {
    items: lists,
    fetch: collection.fetch,
    cancel: collection.cancel,
    lists,
    currentList,
    total,
    isLoading,
    error,
    operationLoading,
    operationError,
    fetchLists,
    fetchList,
    createList,
    deleteList,
    updateListProblems,
  }
})
