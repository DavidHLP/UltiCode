<script setup lang="ts">
import type { Component } from 'vue'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  IconLogin,
  IconPlus,
  IconEdit,
  IconTrash,
  IconUpload,
  IconFlag,
  IconBan,
  IconShield,
  IconLock,
  IconLockOpen,
  IconPin,
  IconPinnedOff,
} from '@tabler/icons-vue'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'

export interface TimelineActivity {
  id: string | number
  action: string
  user: string
  target: string
  time: string
}

const props = defineProps<{
  activities: TimelineActivity[]
}>()

const { t } = useI18n()

const getIconForActivity = (action: string) => {
  const actionMap: Record<string, Component> = {
    LOGIN: IconLogin,
    CREATE: IconPlus,
    UPDATE: IconEdit,
    DELETE: IconTrash,
    PUBLISH: IconUpload,
    UNPUBLISH: IconUpload,
    FLAG: IconFlag,
    UNFLAG: IconFlag,
    BAN: IconBan,
    UNBAN: IconBan,
    MODERATE: IconShield,
    PIN: IconPin,
    UNPIN: IconPinnedOff,
    LOCK: IconLock,
    UNLOCK: IconLockOpen,
  }
  return actionMap[action] || IconEdit
}

const getIconColor = (action: string) => {
  const colorMap: Record<string, string> = {
    LOGIN: 'text-blue-500 dark:text-blue-400',
    CREATE: 'text-green-500 dark:text-green-400',
    UPDATE: 'text-orange-500 dark:text-orange-400',
    DELETE: 'text-red-500 dark:text-red-400',
    PUBLISH: 'text-green-500 dark:text-green-400',
    UNPUBLISH: 'text-yellow-500 dark:text-yellow-400',
    FLAG: 'text-red-500 dark:text-red-400',
    UNFLAG: 'text-green-500 dark:text-green-400',
    BAN: 'text-red-600 dark:text-red-400',
    UNBAN: 'text-green-500 dark:text-green-400',
    MODERATE: 'text-purple-500 dark:text-purple-400',
    PIN: 'text-blue-500 dark:text-blue-400',
    UNPIN: 'text-gray-500 dark:text-gray-400',
    LOCK: 'text-yellow-600 dark:text-yellow-400',
    UNLOCK: 'text-green-500 dark:text-green-400',
  }
  return colorMap[action] || 'text-muted-foreground'
}

const getActivityLabel = (action: string): string => {
  const key = `dashboard.timeline.activityTypes.${action}` as const
  return t(key)
}

const displayActivities = computed(() => {
  return props.activities.slice(0, 5)
})
</script>

<template>
  <Card class="border-border/50">
    <CardHeader class="pb-3">
      <div class="flex items-center justify-between">
        <div>
          <CardTitle class="tracking-tight">{{ t('dashboard.timeline.title') }}</CardTitle>
          <CardDescription class="text-xs mt-1">
            {{ t('dashboard.timeline.description') }}
          </CardDescription>
        </div>
      </div>
    </CardHeader>
    <CardContent class="pt-2">
      <div
        v-if="displayActivities.length === 0"
        class="text-center py-8 text-muted-foreground text-sm"
      >
        {{ t('dashboard.recentActivity.noActivity') }}
      </div>
      <div v-else class="relative">
        <!-- Vertical timeline line -->
        <div class="absolute left-[15px] top-2 bottom-2 w-px bg-border/50"></div>

        <!-- Timeline items -->
        <div class="space-y-4">
          <div
            v-for="activity in displayActivities"
            :key="activity.id"
            class="relative flex items-start gap-3 group cursor-pointer"
          >
            <!-- Timeline dot with icon -->
            <div
              class="relative z-10 flex h-8 w-8 shrink-0 items-center justify-center rounded-full border-2 border-border bg-background shadow-sm transition-all group-hover:scale-110 group-hover:shadow-md"
            >
              <component
                :is="getIconForActivity(activity.action)"
                class="h-4 w-4"
                :class="getIconColor(activity.action)"
              />
            </div>

            <!-- Activity content -->
            <div class="flex-1 min-w-0 pb-1">
              <div class="flex items-start justify-between gap-2">
                <div class="space-y-0.5">
                  <p class="text-sm font-medium leading-none truncate">
                    <Badge
                      variant="outline"
                      class="text-xs px-1.5 py-0 h-5 mr-1.5 border-border/50"
                    >
                      {{ getActivityLabel(activity.action as string) }}
                    </Badge>
                    <span class="text-muted-foreground">{{ activity.user }}</span>
                    <span class="text-xs text-muted-foreground mx-1">→</span>
                    <span class="font-medium">{{ activity.target }}</span>
                  </p>
                </div>
                <span class="text-xs text-muted-foreground whitespace-nowrap shrink-0">
                  {{ activity.time }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </CardContent>
  </Card>
</template>
