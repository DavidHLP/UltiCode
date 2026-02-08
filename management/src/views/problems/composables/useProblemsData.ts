import { computed, onMounted, watch } from 'vue'
import type { Ref } from 'vue'
import { useRoute } from 'vue-router'
import { useProblemsStore } from '@/stores/admin/problems'
import { useDataTable } from '@/composables/useDataTable'
import type { Problem, Difficulty } from '@/api/admin/problems'

export function useProblemsData(filters: {
  searchQuery: Ref<string>
  difficultyFilter: Ref<string>
  statusFilter: Ref<string>
  publishedFilter: Ref<string>
  sortBy: Ref<string>
  sortOrder: Ref<'asc' | 'desc'>
}) {
  const route = useRoute()
  const problemsStore = useProblemsStore()

  // Convert page from query (1-based) to pageIndex (0-based)
  const initialPage = Number(route.query.page) || 1

  const {
    searchQuery: internalSearchQuery,
    tablePagination,
    loading,
    data,
    total,
    error,
    loadEntities: loadProblems,
  } = useDataTable<
    Problem,
    {
      sortBy: string
      sortOrder: 'asc' | 'desc'
    },
    Parameters<typeof problemsStore.fetchProblems>[0]
  >({
    store: {
      data: computed(() => problemsStore.problems),
      total: computed(() => problemsStore.total),
      isLoading: computed(() => problemsStore.loading),
      error: computed(() => problemsStore.error),
      fetch: (params) => problemsStore.fetchProblems(params),
    },
    filters: {
      sortBy: filters.sortBy.value,
      sortOrder: filters.sortOrder.value,
    },
    transformParams: ({ search, filters: filterParams, page, limit }) => ({
      search,
      difficulty:
        filters.difficultyFilter.value === 'all'
          ? undefined
          : (filters.difficultyFilter.value as Difficulty),
      status:
        filters.statusFilter.value === 'all'
          ? undefined
          : (filters.statusFilter.value as Problem['status']),
      is_published:
        filters.publishedFilter.value === 'all'
          ? undefined
          : filters.publishedFilter.value === 'published'
            ? true
            : false,
      sortBy: filterParams.sortBy === 'default' ? undefined : filterParams.sortBy,
      sortOrder: filterParams.sortOrder || undefined,
      page,
      limit,
    }),
    autoLoad: false,
  })

  // Initialize pageIndex from URL
  tablePagination.value.pageIndex = Math.max(0, initialPage - 1)

  // Sync external searchQuery with internal one and trigger reload
  watch(
    () => filters.searchQuery.value,
    (newValue) => {
      internalSearchQuery.value = newValue
    },
    { immediate: true },
  )

  // Load on mount
  onMounted(() => loadProblems())

  // Watch filters for data reload
  watch(
    [
      filters.difficultyFilter,
      filters.statusFilter,
      filters.publishedFilter,
      filters.sortBy,
      filters.sortOrder,
    ],
    () => {
      loadProblems()
    },
  )

  // Watch pagination for data reload (watch specific properties instead of deep watch)
  watch([() => tablePagination.value.pageIndex, () => tablePagination.value.pageSize], () => {
    loadProblems()
  })

  return {
    tablePagination,
    loading,
    data,
    total,
    error,
    loadProblems,
  }
}
