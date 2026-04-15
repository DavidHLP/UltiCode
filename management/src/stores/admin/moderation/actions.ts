import { ref, computed } from 'vue'
import type {
  ModerationStatus,
  ReportCategory,
  ModeratableEntityType,
} from '@/api/admin/moderation'

export function useActionsModule(queueLoading: { value: boolean }) {
  const filters = ref<{
    status?: ModerationStatus
    category?: ReportCategory
    entityType?: ModeratableEntityType
    assignedToId?: string
  }>({})

  const pagination = ref({
    page: 1,
    limit: 20,
  })

  const hasActiveFilters = computed(() => {
    return Boolean(
      filters.value.status ||
      filters.value.category ||
      filters.value.entityType ||
      filters.value.assignedToId,
    )
  })

  function setFilters(newFilters: Partial<typeof filters.value>) {
    filters.value = { ...filters.value, ...newFilters }
    pagination.value.page = 1
  }

  function clearFilters() {
    filters.value = {}
    pagination.value.page = 1
  }

  function setPage(page: number) {
    pagination.value.page = page
  }

  function setLimit(limit: number) {
    pagination.value.limit = limit
    pagination.value.page = 1
  }

  return {
    filters,
    pagination,
    hasActiveFilters,
    setFilters,
    clearFilters,
    setPage,
    setLimit,
  }
}
