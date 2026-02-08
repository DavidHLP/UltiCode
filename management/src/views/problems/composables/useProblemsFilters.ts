import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import type { Filter } from '@/components/table/DataTableToolbar.vue'

export function useProblemsFilters() {
  const route = useRoute()

  // Initialize filters from URL query params
  const searchQuery = ref((route.query.search as string) || '')
  const difficultyFilter = ref((route.query.difficulty as string) || 'all')
  const statusFilter = ref((route.query.status as string) || 'all')
  const publishedFilter = ref((route.query.published as string) || 'all')
  const sortBy = ref((route.query.sortBy as string) || 'default')
  const sortOrder = ref<'asc' | 'desc'>((route.query.sortOrder as 'asc' | 'desc') || 'desc')

  const toolbarFilters = computed<Filter[]>(() => [
    {
      modelValue: difficultyFilter.value,
      placeholder: 'All Difficulty',
      width: 'w-[140px]',
      options: [
        { value: 'all', label: 'All Difficulty' },
        { value: 'EASY', label: 'Easy' },
        { value: 'MEDIUM', label: 'Medium' },
        { value: 'HARD', label: 'Hard' },
      ],
    },
    {
      modelValue: statusFilter.value,
      placeholder: 'All Status',
      width: 'w-[140px]',
      options: [
        { value: 'all', label: 'All Status' },
        { value: 'DRAFT', label: 'Draft' },
        { value: 'PUBLISHED', label: 'Published' },
        { value: 'ARCHIVED', label: 'Archived' },
      ],
    },
    {
      modelValue: publishedFilter.value,
      placeholder: 'All Published',
      width: 'w-[160px]',
      options: [
        { value: 'all', label: 'All Published' },
        { value: 'published', label: 'Published' },
        { value: 'unpublished', label: 'Unpublished' },
      ],
    },
  ])

  return {
    searchQuery,
    difficultyFilter,
    statusFilter,
    publishedFilter,
    sortBy,
    sortOrder,
    toolbarFilters,
  }
}
