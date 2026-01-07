import { defineStore } from 'pinia';
import { ref } from 'vue';
import {
  adminProblemListsApi,
  type ProblemList,
  type ProblemListQuery,
  type CreateProblemListDto,
  type UpdateProblemListDto,
  type ProblemListDetail,
  type UpdateProblemListProblemsDto,
} from '@/api/admin/problem-lists';

export const useAdminProblemListsStore = defineStore('admin-problem-lists', () => {
  const lists = ref<ProblemList[]>([]);
  const currentList = ref<ProblemListDetail | null>(null);
  const total = ref(0);
  const isLoading = ref(false);
  const error = ref<string | null>(null);

  async function fetchLists(query: ProblemListQuery) {
    isLoading.value = true;
    error.value = null;
    try {
      const response = await adminProblemListsApi.getLists(query);
      lists.value = response.data;
      total.value = response.total;
    } catch (err: any) {
      error.value = err.response?.data?.message || 'Failed to fetch problem lists';
      throw err;
    } finally {
      isLoading.value = false;
    }
  }

  async function fetchList(id: string) {
    isLoading.value = true;
    error.value = null;
    try {
      currentList.value = await adminProblemListsApi.getList(id);
    } catch (err: any) {
      error.value = err.response?.data?.message || 'Failed to fetch problem list';
      throw err;
    } finally {
      isLoading.value = false;
    }
  }

  async function createList(data: CreateProblemListDto) {
    isLoading.value = true;
    error.value = null;
    try {
      return await adminProblemListsApi.createList(data);
    } catch (err: any) {
      error.value = err.response?.data?.message || 'Failed to create problem list';
      throw err;
    } finally {
      isLoading.value = false;
    }
  }

  async function updateList(id: string, data: UpdateProblemListDto) {
    isLoading.value = true;
    error.value = null;
    try {
      const updatedList = await adminProblemListsApi.updateList(id, data);
      if (currentList.value && currentList.value.id === id) {
        currentList.value = { ...currentList.value, ...updatedList };
      }
      return updatedList;
    } catch (err: any) {
      error.value = err.response?.data?.message || 'Failed to update problem list';
      throw err;
    } finally {
      isLoading.value = false;
    }
  }

  async function deleteList(id: string) {
    isLoading.value = true;
    error.value = null;
    try {
      await adminProblemListsApi.deleteList(id);
      lists.value = lists.value.filter((l) => l.id !== id);
    } catch (err: any) {
      error.value = err.response?.data?.message || 'Failed to delete problem list';
      throw err;
    } finally {
      isLoading.value = false;
    }
  }

  async function updateListProblems(id: string, data: UpdateProblemListProblemsDto) {
    isLoading.value = true;
    error.value = null;
    try {
      await adminProblemListsApi.updateListProblems(id, data);
      // Refresh list details
      await fetchList(id);
    } catch (err: any) {
      error.value = err.response?.data?.message || 'Failed to update list problems';
      throw err;
    } finally {
      isLoading.value = false;
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
    updateList,
    deleteList,
    updateListProblems,
  };
});
