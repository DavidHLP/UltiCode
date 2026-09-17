import { watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Difficulty } from '@/api/admin/problems'
import type { RemoteTableQuery, RemoteTableRouteAdapter } from '@/composables/useRemoteTable'

const DIFFICULTIES: readonly Difficulty[] = [Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD]
type PublishStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

export interface ProblemTableFilters {
  difficulty: string
  status: string
  published: string
  sortBy: string
  sortOrder: 'asc' | 'desc'
}

function isDifficulty(value: string): value is Difficulty {
  return DIFFICULTIES.some((difficulty) => difficulty === value)
}

function isProblemStatus(value: string): value is PublishStatus {
  return value === 'DRAFT' || value === 'PUBLISHED' || value === 'ARCHIVED'
}

function queryString(value: unknown): string | undefined {
  if (typeof value === 'string') return value
  if (Array.isArray(value) && typeof value[0] === 'string') return value[0]
  return undefined
}

function routeKey(query: RemoteTableQuery<ProblemTableFilters>): string {
  return JSON.stringify({
    search: query.search,
    filters: query.filters,
    pageIndex: query.pagination.pageIndex,
  })
}

export function useProblemFilters() {
  const route = useRoute()
  const router = useRouter()
  let lastWrittenRouteKey: string | undefined

  function readRoute(): RemoteTableQuery<ProblemTableFilters> {
    const search = queryString(route.query.search) ?? ''
    const difficulty = queryString(route.query.difficulty) ?? 'all'
    const status = queryString(route.query.status) ?? 'all'
    const published = queryString(route.query.published) ?? 'all'
    const sortBy = queryString(route.query.sortBy) ?? 'default'
    const rawSortOrder = queryString(route.query.sortOrder)
    const sortOrder: 'asc' | 'desc' = rawSortOrder === 'asc' ? 'asc' : 'desc'
    const rawPage = Number(queryString(route.query.page))
    const pageIndex = Number.isInteger(rawPage) && rawPage > 0 ? rawPage - 1 : 0

    return {
      search,
      filters: { difficulty, status, published, sortBy, sortOrder },
      pagination: { pageIndex, pageSize: 10 },
    }
  }

  const routeAdapter: RemoteTableRouteAdapter<ProblemTableFilters> = {
    read: readRoute,
    writeDebounceMs: 300,
    write: (query) => {
      const { filters } = query
      lastWrittenRouteKey = routeKey(query)
      router.push({
        query: {
          ...(query.search && { search: query.search }),
          ...(filters.difficulty !== 'all' && { difficulty: filters.difficulty }),
          ...(filters.status !== 'all' && { status: filters.status }),
          ...(filters.published !== 'all' && { published: filters.published }),
          ...(filters.sortBy !== 'default' && { sortBy: filters.sortBy }),
          ...(filters.sortOrder !== 'desc' && { sortOrder: filters.sortOrder }),
          page: (query.pagination.pageIndex + 1).toString(),
        },
      })
    },
    subscribe: (onChange) =>
      watch(
        () => route.query,
        () => {
          const nextQuery = readRoute()
          if (routeKey(nextQuery) === lastWrittenRouteKey) {
            lastWrittenRouteKey = undefined
            return
          }
          lastWrittenRouteKey = undefined
          onChange(nextQuery)
        },
        { deep: true },
      ),
  }

  function buildFilterParams(filters: ProblemTableFilters, page: number, limit: number) {
    return {
      difficulty:
        filters.difficulty === 'all' || !isDifficulty(filters.difficulty)
          ? undefined
          : filters.difficulty,
      publishStatus:
        filters.status === 'all' || !isProblemStatus(filters.status) ? undefined : filters.status,
      isPublished:
        filters.published === 'all' ? undefined : filters.published === 'published',
      sortBy: filters.sortBy === 'default' ? undefined : filters.sortBy,
      sortOrder: filters.sortOrder || undefined,
      page: Math.max(1, page),
      limit,
    }
  }

  function buildExportParams(query: RemoteTableQuery<ProblemTableFilters>) {
    const params = buildFilterParams(query.filters, 1, 10)
    return {
      search: query.search || undefined,
      difficulty: params.difficulty,
      publishStatus: params.publishStatus,
      isPublished: params.isPublished,
    }
  }

  return {
    initialQuery: readRoute(),
    routeAdapter,
    buildFilterParams,
    buildExportParams,
  }
}
