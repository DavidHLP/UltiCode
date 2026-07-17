import { ref, watch, type Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDebounceFn } from '@vueuse/core'
import { Difficulty } from '@/api/admin/problems'
import type { PaginationState } from '@/composables/useDataTable'

const DIFFICULTIES: readonly Difficulty[] = [Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD]

/** Publish-status wire values accepted by the problems query. */
type PublishStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

/**
 * Type guard that narrows a free-form (e.g. URL-derived) string to
 * {@link Difficulty} without an `as` cast, so a malformed or crafted query
 * value cannot be forwarded to the API as a typed lie.
 */
function isDifficulty(value: string): value is Difficulty {
  return DIFFICULTIES.some((d) => d === value)
}

/**
 * Type guard that narrows a free-form string to a publish-status wire value
 * without an `as` cast.
 */
function isProblemStatus(value: string): value is PublishStatus {
  return value === 'DRAFT' || value === 'PUBLISHED' || value === 'ARCHIVED'
}

export interface ProblemFilterState {
  searchQuery: Ref<string>
  difficultyFilter: Ref<string>
  statusFilter: Ref<string>
  publishedFilter: Ref<string>
  sortBy: Ref<string>
  sortOrder: Ref<'asc' | 'desc'>
  tablePagination: Ref<PaginationState>
}

export function useProblemFilters() {
  const route = useRoute()
  const router = useRouter()

  // Initialize filters from URL query params
  const searchQuery = ref((route.query.search as string) || '')
  const difficultyFilter = ref((route.query.difficulty as string) || 'all')
  const statusFilter = ref((route.query.status as string) || 'all')
  const publishedFilter = ref((route.query.published as string) || 'all')

  const initialPage = Number(route.query.page) || 1
  const sortBy = ref((route.query.sortBy as string) || 'default')
  const sortOrder = ref<'asc' | 'desc'>((route.query.sortOrder as 'asc' | 'desc') || 'desc')

  // Placeholder -- actual tablePagination comes from useDataTable
  // This ref is used only for URL sync and route-based page init
  const routePageIndex = ref(Math.max(0, initialPage - 1))

  // URL synchronization - debounced to avoid excessive updates
  const debouncedUpdateUrl = useDebounceFn(() => {
    router.push({
      query: {
        ...(searchQuery.value && { search: searchQuery.value }),
        ...(difficultyFilter.value !== 'all' && { difficulty: difficultyFilter.value }),
        ...(statusFilter.value !== 'all' && { status: statusFilter.value }),
        ...(publishedFilter.value !== 'all' && { published: publishedFilter.value }),
        ...(sortBy.value !== 'default' && { sortBy: sortBy.value }),
        ...(sortOrder.value && { sortOrder: sortOrder.value }),
        page: (routePageIndex.value + 1).toString(),
      },
    })
  }, 300)

  // Sync tablePagination from useDataTable back to routePageIndex for URL sync
  function bindTablePagination(tablePagination: Ref<PaginationState>) {
    watch(
      tablePagination,
      (val) => {
        routePageIndex.value = val.pageIndex
      },
      { deep: true },
    )

    // Return initial page index so caller can set tablePagination
    return Math.max(0, initialPage - 1)
  }

  // Watch all filter state changes and update URL
  watch(
    [
      searchQuery,
      difficultyFilter,
      statusFilter,
      publishedFilter,
      sortBy,
      sortOrder,
      routePageIndex,
    ],
    debouncedUpdateUrl,
    { deep: true },
  )

  // Watch filters for data reload trigger
  const filterChangeTrigger = ref(0)
  watch([difficultyFilter, statusFilter, publishedFilter, sortBy, sortOrder], () => {
    filterChangeTrigger.value++
  })

  // Handle browser back/forward navigation
  function syncFromRoute() {
    searchQuery.value = (route.query.search as string) || ''
    difficultyFilter.value = (route.query.difficulty as string) || 'all'
    statusFilter.value = (route.query.status as string) || 'all'
    publishedFilter.value = (route.query.published as string) || 'all'
    sortBy.value = (route.query.sortBy as string) || 'default'
    sortOrder.value = (route.query.sortOrder as 'asc' | 'desc') || 'desc'
    const page = Number(route.query.page) || 1
    routePageIndex.value = Math.max(0, page - 1)
    filterChangeTrigger.value++
  }

  // Watch route changes (back/forward)
  watch(
    () => route.query,
    () => syncFromRoute(),
    { deep: true },
  )

  // Build transform params for API calls
  function buildFilterParams(tablePagination: PaginationState) {
    return {
      difficulty:
        difficultyFilter.value === 'all' || !isDifficulty(difficultyFilter.value)
          ? undefined
          : difficultyFilter.value,
      publishStatus:
        statusFilter.value === 'all' || !isProblemStatus(statusFilter.value)
          ? undefined
          : statusFilter.value,
      isPublished:
        publishedFilter.value === 'all'
          ? undefined
          : publishedFilter.value === 'published'
            ? true
            : false,
      sortBy: sortBy.value === 'default' ? undefined : sortBy.value,
      sortOrder: sortOrder.value || undefined,
      page: Math.max(1, tablePagination.pageIndex + 1),
      limit: tablePagination.pageSize,
    }
  }

  // Build export filter params
  function buildExportParams() {
    return {
      search: searchQuery.value || undefined,
      difficulty:
        difficultyFilter.value === 'all' || !isDifficulty(difficultyFilter.value)
          ? undefined
          : difficultyFilter.value,
      publishStatus:
        statusFilter.value === 'all' || !isProblemStatus(statusFilter.value)
          ? undefined
          : statusFilter.value,
      isPublished:
        publishedFilter.value === 'all'
          ? undefined
          : publishedFilter.value === 'published'
            ? true
            : false,
    }
  }

  return {
    searchQuery,
    difficultyFilter,
    statusFilter,
    publishedFilter,
    sortBy,
    sortOrder,
    routePageIndex,
    filterChangeTrigger,
    bindTablePagination,
    syncFromRoute,
    buildFilterParams,
    buildExportParams,
  }
}
