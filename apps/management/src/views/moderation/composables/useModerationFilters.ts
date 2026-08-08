import { ref, computed } from 'vue'
import {
  ModerationStatus,
  ReportCategory,
  type ModeratableEntityType,
} from '@/api/admin/moderation'

export function useModerationFilters() {
  const statusFilter = ref<ModerationStatus | 'all'>('all')
  const categoryFilter = ref<ReportCategory | 'all'>('all')
  const entityTypeFilter = ref<ModeratableEntityType | 'all'>('all')

  // Filter configuration for DataTableToolbar
  function buildFilters(t: (key: string) => string) {
    return computed(() => [
      {
        modelValue: statusFilter.value,
        placeholder: t('moderation.status.title'),
        options: [
          { value: 'all', label: t('moderation.status.all') },
          { value: ModerationStatus.PENDING, label: t('moderation.status.PENDING') },
          { value: ModerationStatus.UNDER_REVIEW, label: t('moderation.status.UNDER_REVIEW') },
          { value: ModerationStatus.RESOLVED, label: t('moderation.status.RESOLVED') },
          { value: ModerationStatus.DISMISSED, label: t('moderation.status.DISMISSED') },
          { value: ModerationStatus.APPEAL_PENDING, label: t('moderation.status.APPEAL_PENDING') },
        ],
        width: 'w-[160px]',
      },
      {
        modelValue: categoryFilter.value,
        placeholder: t('moderation.categories.title'),
        options: [
          { value: 'all', label: t('moderation.categories.all') },
          { value: ReportCategory.SPAM, label: t('moderation.categories.SPAM') },
          { value: ReportCategory.HARASSMENT, label: t('moderation.categories.HARASSMENT') },
          { value: ReportCategory.HATE_SPEECH, label: t('moderation.categories.HATE_SPEECH') },
          { value: ReportCategory.VIOLENCE, label: t('moderation.categories.VIOLENCE') },
          {
            value: ReportCategory.SEXUAL_CONTENT,
            label: t('moderation.categories.SEXUAL_CONTENT'),
          },
          {
            value: ReportCategory.MISINFORMATION,
            label: t('moderation.categories.MISINFORMATION'),
          },
          { value: ReportCategory.WRONG_ANSWER, label: t('moderation.categories.WRONG_ANSWER') },
          { value: ReportCategory.COPYRIGHT, label: t('moderation.categories.COPYRIGHT') },
          { value: ReportCategory.OTHER, label: t('moderation.categories.OTHER') },
        ],
        width: 'w-[160px]',
      },
      {
        modelValue: entityTypeFilter.value,
        placeholder: t('moderation.entityTypes.title'),
        options: [
          { value: 'all', label: t('moderation.entityTypes.all') },
          { value: 'forum_post', label: t('moderation.entityTypes.forum_post') },
          { value: 'forum_comment', label: t('moderation.entityTypes.forum_comment') },
          { value: 'solution', label: t('moderation.entityTypes.solution') },
          { value: 'solution_comment', label: t('moderation.entityTypes.solution_comment') },
          { value: 'problem', label: t('moderation.entityTypes.problem') },
        ],
        width: 'w-[140px]',
      },
    ])
  }

  function handleFilterUpdate(index: number, value: string | number) {
    if (index === 0) {
      statusFilter.value = value as ModerationStatus | 'all'
    } else if (index === 1) {
      categoryFilter.value = value as ReportCategory | 'all'
    } else if (index === 2) {
      entityTypeFilter.value = value as ModeratableEntityType | 'all'
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
