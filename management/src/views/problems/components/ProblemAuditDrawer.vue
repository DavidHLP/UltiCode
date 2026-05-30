<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { IconClock, IconUser, IconInfoCircle } from '@tabler/icons-vue'
import { Button } from '@/components/ui/button'
import { auditApi, type AuditLog } from '@/api/admin/audit'
import {
  getActionIcon,
  getActionIconColor,
  getActionBadgeClass,
} from '@/views/audit/utils'
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

watch([() => props.open, () => props.problemId], async ([isOpen, pId]) => {
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
}, { immediate: true })

function handleOpenChange(value: boolean) {
  emit('update:open', value)
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
    <template #content="{ }">
      <!-- Error state -->
      <div
        v-if="error"
        class="flex items-center justify-between border border-[var(--terminal-red)] bg-[color-mix(in_oklch,_var(--terminal-red)_8%,_transparent)] p-4"
      >
        <span class="font-data text-sm text-[var(--terminal-red)]">&gt; ERROR: {{ error }}</span>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--terminal-red)] text-[var(--terminal-red)]"
          @click="handleOpenChange(false)"
        >
          {{ t('common.close') }}
        </Button>
      </div>

      <!-- Empty state -->
      <div
        v-else-if="!loading && logs.length === 0"
        class="flex flex-col items-center justify-center py-12 text-[var(--silver-500)]"
      >
        <IconInfoCircle class="h-12 w-12 mb-4 opacity-30" />
        <p class="text-sm font-data">&gt; {{ t('audit.problemDrawer.noLogs') }}</p>
      </div>

      <!-- Audit log list -->
      <div v-else class="space-y-0">
        <div
          v-for="(log, index) in logs"
          :key="log.id"
          class="flex items-start gap-4 py-3 border-b border-[var(--silver-200)] dark:border-[var(--silver-300)] last:border-b-0"
          :class="{ 'animate-pulse': loading && index === 0 }"
        >
          <!-- Icon -->
          <div
            class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
          >
            <component
              :is="getActionIcon(log.action)"
              class="h-4 w-4"
              :class="getActionIconColor(log.action)"
            />
          </div>

          <!-- Main content -->
          <div class="flex-1 min-w-0">
            <div class="flex items-center justify-between gap-2 mb-1">
              <span :class="['terminal-badge', getActionBadgeClass(log.action)]">
                {{ log.action }}
              </span>
              <span class="font-data text-xs text-[var(--silver-500)] tabular-nums flex items-center gap-1">
                <IconClock class="h-3 w-3" />
                {{ new Date(log.createdAt).toLocaleString() }}
              </span>
            </div>
            <div class="flex items-center gap-2 text-sm">
              <IconUser class="h-3 w-3 text-[var(--silver-500)]" />
              <span class="text-[var(--silver-400)]">
                {{ log.performer?.username || 'System' }}
              </span>
              <span
                v-if="log.performer?.role"
                class="terminal-badge terminal-badge-info text-xs scale-90 origin-left"
              >
                {{ log.performer.role.replace('_', ' ') }}
              </span>
            </div>
            <div
              v-if="log.oldValues || log.newValues"
              class="mt-2 text-xs text-[var(--silver-500)] font-data"
            >
              <span v-if="log.oldValues" class="text-[var(--terminal-amber)]">
                {{ Object.keys(log.oldValues).length }} field(s) changed
              </span>
            </div>
          </div>
        </div>
      </div>
    </template>
  </BaseDetailDrawer>
</template>