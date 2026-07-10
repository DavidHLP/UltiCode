<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateTimeByLocale } from '@/i18n/utils'
import {
  IconCode,
  IconFileText,
  IconMessage,
  IconMessages,
  IconExternalLink,
} from '@tabler/icons-vue'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import type { ModeratableEntityType } from '@/api/admin/moderation'

interface Props {
  entityType: ModeratableEntityType
  entityId: string
  title?: string
  content?: string
  author?: {
    username: string
    display_name?: string
  }
  createdAt?: Date | string
}

const props = defineProps<Props>()
const emit = defineEmits<{
  viewEntity: []
}>()

const { t } = useI18n()

const entityIcon = computed(() => {
  const icons: Record<ModeratableEntityType, typeof IconFileText> = {
    forum_post: IconMessages,
    forum_comment: IconMessage,
    solution: IconCode,
    solution_comment: IconMessage,
    problem: IconFileText,
  }
  return icons[props.entityType]
})

const entityTypeColor = computed(() => {
  const colors: Record<ModeratableEntityType, string> = {
    forum_post: 'text-[var(--terminal-cyan)]',
    forum_comment: 'text-[var(--terminal-cyan)]',
    solution: 'text-[var(--terminal-green)]',
    solution_comment: 'text-[var(--terminal-green)]',
    problem: 'text-[var(--terminal-amber)]',
  }
  return colors[props.entityType]
})

const truncatedContent = computed(() => {
  if (!props.content) return null
  if (props.content.length <= 200) return props.content
  return props.content.slice(0, 200) + '...'
})

</script>

<template>
  <Card
    class="border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
  >
    <CardHeader class="pb-3">
      <div class="flex items-center justify-between">
        <CardTitle class="flex items-center gap-2 text-sm font-data uppercase tracking-wider">
          <component :is="entityIcon" :class="['h-4 w-4', entityTypeColor]" />
          <span class="text-[var(--silver-500)]">{{ t('moderation.detail.entityPreview') }}</span>
        </CardTitle>
        <Button
          variant="ghost"
          size="sm"
          class="h-7 font-data text-xs text-[var(--terminal-cyan)] hover:text-[var(--terminal-cyan)] hover:bg-[var(--terminal-cyan)]/10"
          @click="emit('viewEntity')"
        >
          <IconExternalLink class="h-3.5 w-3.5 mr-1" />
          {{ t('moderation.queue.viewEntity') }}
        </Button>
      </div>
    </CardHeader>
    <CardContent class="space-y-3">
      <!-- Entity Type & ID -->
      <div class="flex items-center gap-4 text-xs">
        <div class="flex items-center gap-2">
          <span class="text-[var(--silver-500)]">{{ t('moderation.columns.entityType') }}:</span>
          <span :class="['font-data', entityTypeColor]">
            {{ t(`moderation.entityTypes.${entityType}`) }}
          </span>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-[var(--silver-500)]">ID:</span>
          <span class="font-data text-[var(--foreground)] truncate max-w-[150px]">
            {{ entityId }}
          </span>
        </div>
      </div>

      <!-- Title -->
      <div v-if="title" class="space-y-1">
        <p class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]">
          {{ t('moderation.columns.title') }}
        </p>
        <p class="text-sm font-medium">{{ title }}</p>
      </div>

      <!-- Content Preview -->
      <div v-if="truncatedContent" class="space-y-1">
        <p class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]">
          {{ t('moderation.detail.entityPreview') }}
        </p>
        <p class="text-sm text-[var(--foreground)] whitespace-pre-wrap break-words">
          {{ truncatedContent }}
        </p>
      </div>

      <!-- Author & Date -->
      <div class="flex items-center gap-4 text-xs text-[var(--silver-500)]">
        <div v-if="author" class="flex items-center gap-1">
          <span>{{ t('moderation.reports.reporter') }}:</span>
          <span class="text-[var(--foreground)]">
            {{ author.display_name || author.username }}
          </span>
        </div>
        <div v-if="createdAt" class="flex items-center gap-1">
          <span>{{ t('moderation.columns.createdAt') }}:</span>
          <span class="font-data tabular-nums">{{ formatDateTimeByLocale(createdAt) }}</span>
        </div>
      </div>
    </CardContent>
  </Card>
</template>
