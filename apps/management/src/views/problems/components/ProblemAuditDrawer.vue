<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateTimeByLocale } from '@/i18n/utils'
import {
  Clock as IconClock,
  User as IconUser,
  Info as IconInfoCircle,
  ArrowRight as IconArrowRight,
  ChevronDown as IconChevronDown,
  ChevronRight as IconChevronRight,
} from '@lucide/vue'

import { Button } from '@ulticode/design-system/ui/button'
import { auditApi, type AuditLog } from '@/api/admin/audit'
import { getActionIcon, getActionIconColor } from '@/views/audit/utils'
import SemanticBadge from '@ulticode/badge-config/SemanticBadge.vue'
import { getAuditActionColor, USER_ROLE_COLOR_MAP } from '@ulticode/badge-config'
import BaseDetailDrawer from '@/components/shared/BaseDetailDrawer.vue'

const { t } = useI18n()

const props = defineProps<{
  open: boolean
  problemId: string | number | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const logs = ref<AuditLog[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const expandedLogs = ref<Set<string>>(new Set())

watch(
  [() => props.open, () => props.problemId],
  async ([isOpen, pId]) => {
    if (isOpen && pId) {
      loading.value = true
      error.value = null
      try {
        const result = await auditApi.getProblemAuditLogs(pId)
        logs.value = result.items
      } catch (e) {
        error.value = e instanceof Error ? e.message : 'Failed to load audit logs'
        logs.value = []
      } finally {
        loading.value = false
      }
    } else if (!isOpen) {
      logs.value = []
      error.value = null
    }
  },
  { immediate: true },
)

function handleOpenChange(value: boolean) {
  emit('update:open', value)
}

function toggleExpanded(logId: string) {
  if (expandedLogs.value.has(logId)) {
    expandedLogs.value.delete(logId)
  } else {
    expandedLogs.value.add(logId)
  }
  // Force reactivity update
  expandedLogs.value = new Set(expandedLogs.value)
}

function isExpanded(logId: string): boolean {
  return expandedLogs.value.has(logId)
}

interface ChangeEntry {
  field: string
  oldVal: string
  newVal: string
}

function getChanges(
  oldValues: Record<string, unknown> | null,
  newValues: Record<string, unknown> | null,
): ChangeEntry[] {
  const changes: ChangeEntry[] = []
  const allKeys = new Set([...Object.keys(oldValues || {}), ...Object.keys(newValues || {})])

  for (const key of allKeys) {
    const oldVal = oldValues?.[key]
    const newVal = newValues?.[key]
    if (JSON.stringify(oldVal) !== JSON.stringify(newVal)) {
      changes.push({
        field: key,
        oldVal: formatValue(oldVal),
        newVal: formatValue(newVal),
      })
    }
  }
  return changes
}

function formatValue(val: unknown): string {
  if (val === null || val === undefined) return '—'
  if (typeof val === 'object') return JSON.stringify(val)
  return String(val)
}

function formatJson(val: unknown): string {
  if (val === null || val === undefined) return '—'
  let parsed: unknown = val
  if (typeof val === 'string') {
    try {
      parsed = JSON.parse(val)
    } catch {
      return val
    }
  }
  return JSON.stringify(parsed, null, 2)
}
</script>

<template>
  <BaseDetailDrawer
    :open="open"
    @update:open="handleOpenChange"
    :loading="loading"
    :entity="null"
    :title="t('audit.problemDrawer.title')"
    :description="t('audit.problemDrawer.problemId', { id: problemId })"
    :not-found-text="t('audit.problemDrawer.noLogs')"
  >
    <template #content="{}">
      <!-- Error state -->
      <div
        v-if="error"
        class="flex items-center justify-between border border-status-error-mark bg-[color-mix(in_oklch,_var(--status-error-mark)_8%,_transparent)] p-4"
      >
        <span class="font-mono text-sm text-foreground">&gt; ERROR: {{ error }}</span>
        <Button variant="outline" size="sm" @click="handleOpenChange(false)">
          {{ t('common.close') }}
        </Button>
      </div>

      <!-- Empty state -->
      <div
        v-else-if="!loading && logs.length === 0"
        class="flex flex-col items-center justify-center py-12 text-muted-foreground"
      >
        <IconInfoCircle class="h-12 w-12 mb-4 opacity-30" />
        <p class="text-sm font-mono">&gt; {{ t('audit.problemDrawer.noLogs') }}</p>
      </div>

      <!-- Audit log list -->
      <div v-else class="space-y-0">
        <div
          v-for="(log, index) in logs"
          :key="log.id"
          class="flex flex-col py-4 border-b border-border dark:border-border last:border-b-0"
          :class="{ 'animate-pulse': loading && index === 0 }"
        >
          <!-- Header row -->
          <div class="flex items-start gap-3">
            <!-- Icon -->
            <div
              class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-border dark:border-border bg-muted"
            >
              <component
                :is="getActionIcon(log.action)"
                class="h-4 w-4"
                :class="getActionIconColor(log.action)"
              />
            </div>

            <!-- Main content -->
            <div class="flex-1 min-w-0">
              <div class="flex items-center justify-between gap-2 mb-1.5">
                <div class="flex items-center gap-2">
                  <SemanticBadge
                    :color="getAuditActionColor(log.action)"
                    :label="
                      t(`audit.actionTypes.${log.action}`, log.action) ||
                      log.action.replace('_', ' ')
                    "
                  />
                  <span
                    v-if="log.oldValues || log.newValues"
                    class="text-xs text-muted-foreground font-mono"
                  >
                    {{ getChanges(log.oldValues, log.newValues).length }} change(s)
                  </span>
                </div>
                <span
                  class="font-mono text-xs text-muted-foreground tabular-nums flex items-center gap-1 shrink-0"
                >
                  <IconClock class="h-3 w-3" />
                  {{ formatDateTimeByLocale(log.createdAt) }}
                </span>
              </div>

              <div class="flex items-center gap-2 text-sm">
                <IconUser class="h-3 w-3 text-muted-foreground shrink-0" />
                <span class="text-muted-foreground">
                  {{ log.performer?.username || 'System' }}
                </span>
                <SemanticBadge
                  v-if="log.performer?.role"
                  :color="USER_ROLE_COLOR_MAP[log.performer.role] ?? 'neutral'"
                  :label="t(`users.filters.role.${log.performer.role}`, log.performer.role)"
                  size="sm"
                />
              </div>

              <!-- IP & UA small -->
              <div class="flex items-center gap-4 mt-1 text-xs text-foreground font-mono">
                <span v-if="log.ipAddress" class="truncate max-w-[120px]">{{ log.ipAddress }}</span>
                <span v-if="log.userAgent" class="truncate max-w-[200px] hidden sm:inline">{{
                  log.userAgent.split(' ')[0]
                }}</span>
              </div>

              <!-- Changes toggle -->
              <div v-if="log.oldValues || log.newValues" class="mt-2">
                <Button
                  variant="outline"
                  @click="toggleExpanded(log.id)"
                  class="flex items-center gap-1"
                >
                  <component
                    :is="isExpanded(log.id) ? IconChevronDown : IconChevronRight"
                    class="h-3 w-3"
                  />
                  {{ isExpanded(log.id) ? 'Hide' : 'Show' }} changes
                </Button>
              </div>
            </div>
          </div>

          <!-- Expanded diff view -->
          <div
            v-if="isExpanded(log.id) && (log.oldValues || log.newValues)"
            class="mt-3 ml-0 lg:ml-12"
          >
            <div
              class="border border-border dark:border-border rounded-lg overflow-hidden text-xs font-mono"
            >
              <!-- Change rows -->
              <template v-if="getChanges(log.oldValues, log.newValues).length > 0">
                <div
                  v-for="change in getChanges(log.oldValues, log.newValues)"
                  :key="change.field"
                  class="flex items-stretch border-b border-border dark:border-border last:border-b-0"
                >
                  <!-- Field name -->
                  <div
                    class="w-32 shrink-0 bg-[var(--surface-raised)] px-3 py-2 text-muted-foreground font-medium border-r border-border dark:border-border"
                  >
                    {{ change.field }}
                  </div>
                  <!-- Old value -->
                  <div class="flex-1 px-3 py-2 min-w-0">
                    <div class="text-foreground opacity-70">
                      <span class="opacity-50 text-xs mr-1">OLD</span>
                      <span class="truncate block">{{ change.oldVal }}</span>
                    </div>
                  </div>
                  <!-- Arrow -->
                  <div class="flex items-center px-2 text-muted-foreground shrink-0">
                    <IconArrowRight class="h-3 w-3" />
                  </div>
                  <!-- New value -->
                  <div class="flex-1 px-3 py-2 min-w-0">
                    <div class="text-foreground">
                      <span class="opacity-50 text-xs mr-1">NEW</span>
                      <span class="truncate block">{{ change.newVal }}</span>
                    </div>
                  </div>
                </div>
              </template>

              <!-- JSON dump for complex/missing fields -->
              <template v-else>
                <div class="p-3 space-y-3">
                  <div v-if="log.oldValues">
                    <div class="text-muted-foreground mb-1 text-xs uppercase tracking-wider">
                      Old Values
                    </div>
                    <pre
                      class="text-foreground opacity-80 whitespace-pre-wrap break-all bg-muted p-2 rounded-lg border border-border dark:border-border text-xs"
                      >{{ formatJson(log.oldValues) }}</pre>
                  </div>
                  <div v-if="log.newValues">
                    <div class="text-muted-foreground mb-1 text-xs uppercase tracking-wider">
                      New Values
                    </div>
                    <pre
                      class="text-foreground whitespace-pre-wrap break-all bg-muted p-2 rounded-lg border border-border dark:border-border text-xs"
                      >{{ formatJson(log.newValues) }}</pre>
                  </div>
                </div>
              </template>
            </div>
          </div>
        </div>
      </div>
    </template>
  </BaseDetailDrawer>
</template>
