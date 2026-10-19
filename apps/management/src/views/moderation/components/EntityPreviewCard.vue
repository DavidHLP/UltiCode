<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateTimeByLocale } from '@/i18n/utils'
import {
  Code as IconCode,
  FileText as IconFileText,
  MessageSquare as IconMessage,
  MessagesSquare as IconMessages,
  ExternalLink as IconExternalLink,
} from '@lucide/vue'

import { Button } from '@ulticode/design-system/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@ulticode/design-system/ui/card'
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
    forum_post: 'text-foreground',
    forum_comment: 'text-foreground',
    solution: 'text-foreground',
    solution_comment: 'text-foreground',
    problem: 'text-foreground',
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
  <Card>
    <CardHeader class="pb-3">
      <div class="flex items-center justify-between">
        <CardTitle class="flex items-center gap-2">
          <component :is="entityIcon" :class="['h-4 w-4', entityTypeColor]" />
          <span class="text-muted-foreground">{{ t('moderation.detail.entityPreview') }}</span>
        </CardTitle>
        <Button variant="ghost" size="sm" @click="emit('viewEntity')">
          <IconExternalLink class="h-3.5 w-3.5 mr-1" />
          {{ t('moderation.queue.viewEntity') }}
        </Button>
      </div>
    </CardHeader>
    <CardContent class="space-y-3">
      <!-- Entity Type & ID -->
      <div class="flex items-center gap-4 text-xs">
        <div class="flex items-center gap-2">
          <span class="text-muted-foreground">{{ t('moderation.columns.entityType') }}:</span>
          <span :class="['font-mono', entityTypeColor]">
            {{ t(`moderation.entityTypes.${entityType}`) }}
          </span>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-muted-foreground">ID:</span>
          <span class="font-mono text-foreground truncate max-w-[150px]">
            {{ entityId }}
          </span>
        </div>
      </div>

      <!-- Title -->
      <div v-if="title" class="space-y-1">
        <p class="text-xs font-mono uppercase tracking-wider text-muted-foreground">
          {{ t('moderation.columns.title') }}
        </p>
        <p class="text-sm font-medium">{{ title }}</p>
      </div>

      <!-- Content Preview -->
      <div v-if="truncatedContent" class="space-y-1">
        <p class="text-xs font-mono uppercase tracking-wider text-muted-foreground">
          {{ t('moderation.detail.entityPreview') }}
        </p>
        <p class="text-sm text-foreground whitespace-pre-wrap break-words">
          {{ truncatedContent }}
        </p>
      </div>

      <!-- Author & Date -->
      <div class="flex items-center gap-4 text-xs text-muted-foreground">
        <div v-if="author" class="flex items-center gap-1">
          <span>{{ t('moderation.reports.reporter') }}:</span>
          <span class="text-foreground">
            {{ author.display_name || author.username }}
          </span>
        </div>
        <div v-if="createdAt" class="flex items-center gap-1">
          <span>{{ t('moderation.columns.createdAt') }}:</span>
          <span class="font-mono tabular-nums">{{ formatDateTimeByLocale(createdAt) }}</span>
        </div>
      </div>
    </CardContent>
  </Card>
</template>
