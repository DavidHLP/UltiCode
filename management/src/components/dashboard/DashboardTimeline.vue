<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

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

const getIconColor = (action: string) => {
  const colorMap: Record<string, string> = {
    LOGIN: 'var(--accent-primary)',
    CREATE: 'var(--status-success)',
    UPDATE: 'var(--status-warning)',
    DELETE: 'var(--status-error)',
    PUBLISH: 'var(--status-success)',
    UNPUBLISH: 'var(--status-warning)',
    FLAG: 'var(--status-error)',
    UNFLAG: 'var(--status-success)',
    BAN: 'var(--status-error)',
    UNBAN: 'var(--status-success)',
    MODERATE: 'var(--accent-primary)',
    PIN: 'var(--accent-primary)',
    UNPIN: 'var(--silver-400)',
    LOCK: 'var(--status-warning)',
    UNLOCK: 'var(--status-success)',
    RESET_PASSWORD: 'var(--status-warning)',
    UPDATE_USER: 'var(--status-warning)',
    BAN_USER: 'var(--status-error)',
    UNBAN_USER: 'var(--status-success)',
  }
  return colorMap[action] || 'var(--silver-400)'
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
  <Card
    class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] shadow-float h-full"
  >
    <CardHeader class="pb-2 pt-5 px-5">
      <div class="flex items-center justify-between">
        <div>
          <CardTitle class="text-base font-medium tracking-tight">{{
            t('dashboard.timeline.title')
          }}</CardTitle>
          <CardDescription class="text-xs text-[var(--silver-400)] mt-1">
            {{ t('dashboard.timeline.description') }}
          </CardDescription>
        </div>
      </div>
    </CardHeader>
    <CardContent class="pt-2 px-5 pb-5">
      <div
        v-if="displayActivities.length === 0"
        class="text-center py-8 text-[var(--silver-400)] text-sm"
      >
        {{ t('dashboard.recentActivity.noActivity') }}
      </div>
      <div v-else class="relative timeline-line">
        <!-- Timeline items -->
        <div class="space-y-0">
          <div
            v-for="(activity, index) in displayActivities"
            :key="activity.id"
            class="relative flex items-start gap-3 py-3 group cursor-default"
            :class="{ 'pb-0': index === displayActivities.length - 1 }"
          >
            <!-- Timeline node with icon -->
            <div
              class="relative z-10 flex h-6 w-6 shrink-0 items-center justify-center rounded-full border transition-all duration-200"
              :style="{
                borderColor: getIconColor(activity.action),
                backgroundColor: 'transparent',
              }"
            >
              <div
                class="h-2 w-2 rounded-full transition-all duration-200 group-hover:scale-125"
                :style="{ backgroundColor: getIconColor(activity.action) }"
              ></div>
              <!-- Hover glow effect -->
              <div
                class="absolute inset-0 rounded-full opacity-0 group-hover:opacity-100 transition-opacity duration-200"
                :style="{
                  boxShadow: `0 0 8px 2px ${getIconColor(activity.action)}40`,
                }"
              ></div>
            </div>

            <!-- Activity content -->
            <div class="flex-1 min-w-0">
              <div class="flex items-center justify-between gap-2">
                <p class="text-sm leading-none truncate">
                  <span
                    class="text-xs font-medium px-1.5 py-0.5 rounded border border-[var(--silver-200)] dark:border-[var(--silver-300)] mr-1.5"
                    :style="{
                      color: getIconColor(activity.action),
                      borderColor: getIconColor(activity.action) + '40',
                    }"
                  >
                    {{ getActivityLabel(activity.action as string) }}
                  </span>
                  <span class="text-[var(--silver-500)]">{{ activity.user }}</span>
                  <span class="text-[var(--silver-300)] mx-1">→</span>
                  <span class="font-medium text-foreground">{{ activity.target }}</span>
                </p>
              </div>
              <p class="text-xs text-[var(--silver-400)] mt-1 font-data tabular-nums">
                {{ activity.time }}
              </p>
            </div>
          </div>
        </div>
      </div>
    </CardContent>
  </Card>
</template>
