import { computed, ref } from 'vue'
import type { Filter } from '@/components/table/DataTableToolbar.vue'
import { ModerationStatus, ReportCategory, type ModeratableEntityType } from '@/api/admin/moderation'

export interface ModerationFilterState {
  status: string
  category?: ReportCategory | 'all'
  entityType?: ModeratableEntityType | 'all'
}

interface ModerationFilterBinding<TFilters extends ModerationFilterState> {
  query: { readonly value: { readonly filters: TFilters } }
  setFilters: (filters: TFilters) => void
}

interface ModerationFilterOptions {
  statusValues?: readonly string[]
  statusNamespace?: string
  includeCategory?: boolean
  includeEntityType?: boolean
}

const categoryOptions = Object.values(ReportCategory)
const entityTypeOptions: ModeratableEntityType[] = [
  'forum_post',
  'forum_comment',
  'solution',
  'solution_comment',
  'problem',
]

export function useModerationFilters<TFilters extends ModerationFilterState>(
  binding?: ModerationFilterBinding<TFilters>,
  options: ModerationFilterOptions = {},
) {
  const statusValues = options.statusValues ?? Object.values(ModerationStatus)
  const statusNamespace = options.statusNamespace ?? 'moderation.status'
  const includeCategory = options.includeCategory ?? true
  const includeEntityType = options.includeEntityType ?? true

  const statusFilter = binding
    ? computed({
        get: () => binding.query.value.filters.status,
        set: (status: string) =>
          binding.setFilters({ ...binding.query.value.filters, status } as TFilters),
      })
    : ref<string>('all')
  const categoryFilter = binding
    ? computed({
        get: () => binding.query.value.filters.category ?? 'all',
        set: (category: ReportCategory | 'all') =>
          binding.setFilters({ ...binding.query.value.filters, category } as TFilters),
      })
    : ref<ReportCategory | 'all'>('all')
  const entityTypeFilter = binding
    ? computed({
        get: () => binding.query.value.filters.entityType ?? 'all',
        set: (entityType: ModeratableEntityType | 'all') =>
          binding.setFilters({ ...binding.query.value.filters, entityType } as TFilters),
      })
    : ref<ModeratableEntityType | 'all'>('all')

  function buildFilters(t: (key: string) => string) {
    return computed<Filter[]>(() => {
      const filters: Filter[] = [
        {
          modelValue: statusFilter.value,
          placeholder: t(`${statusNamespace}.title`),
          options: [
            { value: 'all', label: t(`${statusNamespace}.all`) },
            ...statusValues.map((value) => ({
              value,
              label: t(`${statusNamespace}.${value}`),
            })),
          ],
          width: 'w-[160px]',
        },
      ]
      if (includeCategory) {
        filters.push({
          modelValue: categoryFilter.value,
          placeholder: t('moderation.categories.title'),
          options: [
            { value: 'all', label: t('moderation.categories.all') },
            ...categoryOptions.map((value) => ({
              value,
              label: t(`moderation.categories.${value}`),
            })),
          ],
          width: 'w-[160px]',
        })
      }
      if (includeEntityType) {
        filters.push({
          modelValue: entityTypeFilter.value,
          placeholder: t('moderation.entityTypes.title'),
          options: [
            { value: 'all', label: t('moderation.entityTypes.all') },
            ...entityTypeOptions.map((value) => ({
              value,
              label: t(`moderation.entityTypes.${value}`),
            })),
          ],
          width: 'w-[140px]',
        })
      }
      return filters
    })
  }

  function handleFilterUpdate(index: number, value: string | number) {
    if (index === 0) statusFilter.value = String(value)
    else if (index === 1 && includeCategory) categoryFilter.value = String(value) as ReportCategory
    else if (includeEntityType) {
      entityTypeFilter.value = String(value) as ModeratableEntityType
    }
  }

  return {
    statusFilter,
    categoryFilter,
    entityTypeFilter,
    buildFilters,
    handleFilterUpdate,
  }
}
