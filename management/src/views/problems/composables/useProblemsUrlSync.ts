import { watch, type Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDebounceFn } from '@vueuse/core'

export function useProblemsUrlSync(filters: {
  searchQuery: Ref<string>
  difficultyFilter: Ref<string>
  statusFilter: Ref<string>
  publishedFilter: Ref<string>
  sortBy: Ref<string>
  sortOrder: Ref<'asc' | 'desc'>
  tablePagination: Ref<{ pageIndex: number; pageSize: number }>
  loadProblems: () => Promise<void>
}) {
  const route = useRoute()
  const router = useRouter()

  // URL synchronization - debounced to avoid excessive updates
  const debouncedUpdateUrl = useDebounceFn(() => {
    router.push({
      query: {
        ...(filters.searchQuery.value && { search: filters.searchQuery.value }),
        ...(filters.difficultyFilter.value !== 'all' && {
          difficulty: filters.difficultyFilter.value,
        }),
        ...(filters.statusFilter.value !== 'all' && { status: filters.statusFilter.value }),
        ...(filters.publishedFilter.value !== 'all' && {
          published: filters.publishedFilter.value,
        }),
        ...(filters.sortBy.value !== 'default' && { sortBy: filters.sortBy.value }),
        ...(filters.sortOrder.value && { sortOrder: filters.sortOrder.value }),
        page: (filters.tablePagination.value.pageIndex + 1).toString(),
      },
    })
  }, 300)

  // Watch all filter state changes and update URL (watch specific properties instead of deep watch)
  watch(
    [
      () => filters.searchQuery.value,
      () => filters.difficultyFilter.value,
      () => filters.statusFilter.value,
      () => filters.publishedFilter.value,
      () => filters.sortBy.value,
      () => filters.sortOrder.value,
      () => filters.tablePagination.value.pageIndex,
      () => filters.tablePagination.value.pageSize,
    ],
    debouncedUpdateUrl,
  )

  // Handle browser back/forward navigation
  watch(
    () => route.query,
    (newQuery) => {
      filters.searchQuery.value = (newQuery.search as string) || ''
      filters.difficultyFilter.value = (newQuery.difficulty as string) || 'all'
      filters.statusFilter.value = (newQuery.status as string) || 'all'
      filters.publishedFilter.value = (newQuery.published as string) || 'all'
      filters.sortBy.value = (newQuery.sortBy as string) || 'default'
      filters.sortOrder.value = (newQuery.sortOrder as 'asc' | 'desc') || 'desc'

      const page = Number(newQuery.page) || 1
      filters.tablePagination.value.pageIndex = Math.max(0, page - 1)

      filters.loadProblems()
    },
    { deep: true },
  )
}
