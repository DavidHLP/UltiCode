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

const { t, te } = useI18n()

const formatEnumValue = (value: string): string => value.replaceAll('_', ' ')

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
    CREATE_FORUM_POST: 'var(--status-success)',
    UPDATE_FORUM_POST: 'var(--status-warning)',
    DELETE_FORUM_POST: 'var(--status-error)',
    PIN_POST: 'var(--accent-primary)',
    UNPIN_POST: 'var(--silver-400)',
    LOCK_POST: 'var(--status-warning)',
    UNLOCK_POST: 'var(--status-success)',
    FLAG_POST: 'var(--status-error)',
    UNFLAG_POST: 'var(--status-success)',
    GRANT_PERMISSION: 'var(--accent-primary)',
    REVOKE_PERMISSION: 'var(--accent-primary)',
    CREATE_USER: 'var(--status-success)',
    DELETE_USER: 'var(--status-error)',
    CREATE_PROBLEM: 'var(--status-success)',
    UPDATE_PROBLEM: 'var(--status-warning)',
    DELETE_PROBLEM: 'var(--status-error)',
    CREATE_CONTEST: 'var(--status-success)',
    UPDATE_CONTEST: 'var(--status-warning)',
    DELETE_CONTEST: 'var(--status-error)',
    CREATE_CONTEST_ANNOUNCEMENT: 'var(--status-success)',
    UPDATE_CONTEST_ANNOUNCEMENT: 'var(--status-warning)',
    DELETE_CONTEST_ANNOUNCEMENT: 'var(--status-error)',
    CREATE_SOLUTION: 'var(--status-success)',
    UPDATE_SOLUTION: 'var(--status-warning)',
    DELETE_SOLUTION: 'var(--status-error)',
    FLAG_SOLUTION: 'var(--status-error)',
    UNFLAG_SOLUTION: 'var(--status-success)',
    BULK_SOLUTION_ACTION: 'var(--status-warning)',
    FLAG_COMMENT: 'var(--status-error)',
    UNFLAG_COMMENT: 'var(--status-success)',
    DELETE_COMMENT: 'var(--status-error)',
    CREATE_TAG: 'var(--status-success)',
    UPDATE_TAG: 'var(--status-warning)',
    DELETE_TAG: 'var(--status-error)',
    UPDATE_SETTINGS: 'var(--status-warning)',
    UPDATE_PROBLEM_LIST: 'var(--status-warning)',
    DELETE_PROBLEM_LIST: 'var(--status-error)',
    CREATE_NOTIFICATION: 'var(--status-success)',
    UPDATE_NOTIFICATION: 'var(--status-warning)',
    DELETE_NOTIFICATION: 'var(--status-error)',
    REQUEUE_SUBMISSION: 'var(--status-warning)',
    DELETE_SUBMISSION: 'var(--status-error)',
    MODERATE_CONTENT: 'var(--accent-primary)',
  }
  return colorMap[action] || 'var(--silver-400)'
}

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
  <Card
    class="border-2 border-[var(--silver-200)] dark:border-[var(--silver-300)] shadow-float h-full rounded-none"
  >
    <CardHeader
      class="pb-3 pt-3 px-5 bg-[var(--silver-50)] dark:bg-[var(--silver-100)]/20 border-b border-[var(--silver-200)] dark:border-[var(--silver-300)]"
    >
      <div class="flex items-center justify-between">
        <div>
          <CardTitle
            class="text-base font-bold font-mono uppercase tracking-wide text-foreground"
            >{{ t('dashboard.timeline.title') }}</CardTitle
          >
          <CardDescription class="text-xs text-[var(--silver-400)] mt-1">
            {{ t('dashboard.timeline.description') }}
          </CardDescription>
        </div>
      </div>
    </CardHeader>
    <CardContent class="pt-4 px-5 pb-5">
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
            class="relative flex items-start gap-4 py-3 group cursor-default"
            :class="{ 'pb-0': index === displayActivities.length - 1 }"
          >
            <!-- Timeline node with icon (Square design) -->
            <div
              class="relative z-10 flex h-6 w-6 shrink-0 items-center justify-center rounded-none border transition-all duration-200"
              :style="{
                borderColor: getIconColor(activity.action),
                backgroundColor: 'var(--card)',
              }"
            >
              <div
                class="h-2 w-2 rounded-none transition-all duration-200 group-hover:scale-125"
                :style="{ backgroundColor: getIconColor(activity.action) }"
              ></div>
              <!-- Hover glow effect (Square) -->
              <div
                class="absolute inset-0 rounded-none opacity-0 group-hover:opacity-100 transition-opacity duration-200"
                :style="{
                  boxShadow: `0 0 8px 1.5px ${getIconColor(activity.action)}40`,
                }"
              ></div>
            </div>

            <!-- Structured Activity Content -->
            <div class="flex flex-col gap-1.5 flex-1 min-w-0">
              <div class="flex items-center justify-between gap-2">
                <!-- Action Badge & Performer -->
                <div class="flex items-center gap-2 flex-wrap">
                  <span
                    class="text-2xs font-mono font-bold px-1.5 py-0.5 rounded-none border"
                    :style="{
                      color: getIconColor(activity.action),
                      borderColor: getIconColor(activity.action) + '50',
                      backgroundColor: getIconColor(activity.action) + '12',
                    }"
                  >
                    {{ getActivityLabel(activity.action) }}
                  </span>
                  <span class="text-xs font-semibold text-foreground">
                    {{ activity.user }}
                  </span>
                </div>
                <!-- Time (Top-Right aligned) -->
                <span class="text-2xs font-mono text-[var(--silver-400)] tabular-nums shrink-0">
                  {{ activity.time }}
                </span>
              </div>

              <!-- Target & Context Description -->
              <div class="text-xs text-[var(--silver-500)] flex items-center gap-1.5">
                <span class="text-[var(--silver-400)] text-2xs uppercase font-mono tracking-wide"
                  >{{ t('audit.columns.target') }}:</span
                >
                <span
                  class="font-mono bg-[var(--silver-100)] dark:bg-[var(--silver-200)]/20 px-1.5 py-0.5 text-[var(--silver-700)] dark:text-[var(--silver-300)] text-xxs truncate max-w-[170px] border border-[var(--silver-200)] dark:border-[var(--silver-300)]/60 rounded-none"
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
