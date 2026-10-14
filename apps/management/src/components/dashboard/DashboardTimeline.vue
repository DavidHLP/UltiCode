<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@ulticode/design-system/ui/card'

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

const { t, te } = useI18n()

const formatEnumValue = (value: string): string => value.replaceAll('_', ' ')

const getIconColor = (action: string) => {
  const colorMap: Record<string, string> = {
    LOGIN: 'var(--primary)',
    CREATE: 'var(--status-success-mark)',
    UPDATE: 'var(--status-warning-mark)',
    DELETE: 'var(--status-error-mark)',
    PUBLISH: 'var(--status-success-mark)',
    UNPUBLISH: 'var(--status-warning-mark)',
    FLAG: 'var(--status-error-mark)',
    UNFLAG: 'var(--status-success-mark)',
    BAN: 'var(--status-error-mark)',
    UNBAN: 'var(--status-success-mark)',
    MODERATE: 'var(--primary)',
    PIN: 'var(--primary)',
    UNPIN: 'var(--muted-foreground)',
    LOCK: 'var(--status-warning-mark)',
    UNLOCK: 'var(--status-success-mark)',
    RESET_PASSWORD: 'var(--status-warning-mark)',
    UPDATE_USER: 'var(--status-warning-mark)',
    BAN_USER: 'var(--status-error-mark)',
    UNBAN_USER: 'var(--status-success-mark)',
    CREATE_FORUM_POST: 'var(--status-success-mark)',
    UPDATE_FORUM_POST: 'var(--status-warning-mark)',
    DELETE_FORUM_POST: 'var(--status-error-mark)',
    PIN_POST: 'var(--primary)',
    UNPIN_POST: 'var(--muted-foreground)',
    LOCK_POST: 'var(--status-warning-mark)',
    UNLOCK_POST: 'var(--status-success-mark)',
    FLAG_POST: 'var(--status-error-mark)',
    UNFLAG_POST: 'var(--status-success-mark)',
    GRANT_PERMISSION: 'var(--primary)',
    REVOKE_PERMISSION: 'var(--primary)',
    CREATE_USER: 'var(--status-success-mark)',
    DELETE_USER: 'var(--status-error-mark)',
    CREATE_PROBLEM: 'var(--status-success-mark)',
    UPDATE_PROBLEM: 'var(--status-warning-mark)',
    DELETE_PROBLEM: 'var(--status-error-mark)',
    CREATE_CONTEST: 'var(--status-success-mark)',
    UPDATE_CONTEST: 'var(--status-warning-mark)',
    DELETE_CONTEST: 'var(--status-error-mark)',
    CREATE_CONTEST_ANNOUNCEMENT: 'var(--status-success-mark)',
    UPDATE_CONTEST_ANNOUNCEMENT: 'var(--status-warning-mark)',
    DELETE_CONTEST_ANNOUNCEMENT: 'var(--status-error-mark)',
    CREATE_SOLUTION: 'var(--status-success-mark)',
    UPDATE_SOLUTION: 'var(--status-warning-mark)',
    DELETE_SOLUTION: 'var(--status-error-mark)',
    FLAG_SOLUTION: 'var(--status-error-mark)',
    UNFLAG_SOLUTION: 'var(--status-success-mark)',
    BULK_SOLUTION_ACTION: 'var(--status-warning-mark)',
    FLAG_COMMENT: 'var(--status-error-mark)',
    UNFLAG_COMMENT: 'var(--status-success-mark)',
    DELETE_COMMENT: 'var(--status-error-mark)',
    CREATE_TAG: 'var(--status-success-mark)',
    UPDATE_TAG: 'var(--status-warning-mark)',
    DELETE_TAG: 'var(--status-error-mark)',
    UPDATE_SETTINGS: 'var(--status-warning-mark)',
    UPDATE_PROBLEM_LIST: 'var(--status-warning-mark)',
    DELETE_PROBLEM_LIST: 'var(--status-error-mark)',
    CREATE_NOTIFICATION: 'var(--status-success-mark)',
    UPDATE_NOTIFICATION: 'var(--status-warning-mark)',
    DELETE_NOTIFICATION: 'var(--status-error-mark)',
    REQUEUE_SUBMISSION: 'var(--status-warning-mark)',
    DELETE_SUBMISSION: 'var(--status-error-mark)',
    MODERATE_CONTENT: 'var(--primary)',
  }
  return colorMap[action] || 'var(--muted-foreground)'
}
const colorMix = (color: string, percentage: number) =>
  `color-mix(in srgb, ${color} ${percentage}%, transparent)`

const getActivityLabel = (action: string): string => {
  const key = `dashboard.timeline.activityTypes.${action}` as const
  return te(key) ? t(key) : formatEnumValue(action)
}

const getTargetLabel = (target: string): string => {
  const key = `audit.entityTypes.${target}` as const
  return te(key) ? t(key) : formatEnumValue(target)
}

const displayActivities = computed(() => {
  return props.activities.slice(0, 5)
})
</script>

<template>
  <Card class="h-full gap-0 py-0 flex flex-col">
    <CardHeader class="pb-3 pt-3 px-5">
      <div class="flex items-center justify-between">
        <div>
          <CardTitle>{{ t('dashboard.timeline.title') }}</CardTitle>
          <CardDescription class="mt-1">
            {{ t('dashboard.timeline.description') }}
          </CardDescription>
        </div>
      </div>
    </CardHeader>
    <CardContent class="pt-4 px-5 pb-5">
      <div
        v-if="displayActivities.length === 0"
        class="text-center py-8 text-muted-foreground text-sm"
      >
        {{ t('dashboard.recentActivity.noActivity') }}
      </div>
      <div v-else class="relative timeline-line">
        <!-- Timeline items -->
        <div class="space-y-0">
          <div
            v-for="(activity, index) in displayActivities"
            :key="activity.id"
            class="relative flex items-start gap-4 py-3 group cursor-default"
            :class="{ 'pb-0': index === displayActivities.length - 1 }"
          >
            <!-- Timeline node with icon (Square design) -->
            <div
              class="relative z-10 flex h-6 w-6 shrink-0 items-center justify-center rounded-lg border transition-all duration-200"
              :style="{
                borderColor: getIconColor(activity.action),
                backgroundColor: 'var(--card)',
              }"
            >
              <div
                class="h-2 w-2 rounded-lg transition-all duration-200 group-hover:scale-125"
                :style="{ backgroundColor: getIconColor(activity.action) }"
              ></div>
              <!-- Hover glow effect (Square) -->
              <div
                class="absolute inset-0 rounded-lg opacity-0 group-hover:opacity-100 transition-opacity duration-200"
                :style="{
                  boxShadow: `0 0 8px 1.5px ${colorMix(getIconColor(activity.action), 25)}`,
                }"
              ></div>
            </div>

            <!-- Structured Activity Content -->
            <div class="flex flex-col gap-1.5 flex-1 min-w-0">
              <div class="flex items-center justify-between gap-2">
                <!-- Action Badge & Performer -->
                <div class="flex items-center gap-2 flex-wrap">
                  <span
                    class="text-xs font-mono font-bold px-1.5 py-0.5 rounded-lg border"
                    :style="{
                      color: getIconColor(activity.action),
                      borderColor: colorMix(getIconColor(activity.action), 31),
                      backgroundColor: colorMix(getIconColor(activity.action), 7),
                    }"
                  >
                    {{ getActivityLabel(activity.action) }}
                  </span>
                  <span class="text-xs font-semibold text-foreground">
                    {{ activity.user }}
                  </span>
                </div>
                <!-- Time (Top-Right aligned) -->
                <span class="text-xs font-mono text-muted-foreground tabular-nums shrink-0">
                  {{ activity.time }}
                </span>
              </div>

              <!-- Target & Context Description -->
              <div class="text-xs text-muted-foreground flex items-center gap-1.5">
                <span class="text-muted-foreground text-xs uppercase font-mono tracking-wide"
                  >{{ t('audit.columns.target') }}:</span
                >
                <span
                  class="font-mono bg-accent dark:bg-border/20 px-1.5 py-0.5 text-foreground dark:text-border text-xs truncate max-w-[170px] border border-border dark:border-border/60 rounded-lg"
                >
                  {{ getTargetLabel(activity.target) }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </CardContent>
  </Card>
</template>
