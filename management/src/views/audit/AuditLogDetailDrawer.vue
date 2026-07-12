<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { formatDateTimeByLocale } from '@/i18n/utils'
import { Separator } from '@/components/ui/separator'
import { IconDatabase, IconUser, IconTerminal, IconEye, IconClock } from '@tabler/icons-vue'
import { ScrollArea } from '@/components/ui/scroll-area'
import type { AuditLog } from '@/api/admin/audit'
import { formatJson, getActionIcon, getActionIconColor, getEntityTypeIcon } from './utils'
import { SemanticBadge, getAuditActionColor } from '@/components/ui/terminal'
import BaseDetailDrawer from '@/components/shared/BaseDetailDrawer.vue'

const { t } = useI18n()

defineProps<{
  open: boolean
  log: AuditLog | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()
</script>

<template>
  <BaseDetailDrawer
    :open="open"
    @update:open="emit('update:open', $event)"
    :loading="false"
    :entity="log"
    :title="t('audit.columns.details')"
    :description="t('audit.drawer.description')"
    :not-found-text="t('audit.drawer.notFound')"
  >
    <template #content="{ entity }">
      <!-- Header Info - Terminal Style -->
      <div
        class="flex items-start gap-4 p-4 border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
      >
        <div
          class="flex h-12 w-12 items-center justify-center rounded-full border border-[var(--terminal-cyan)] bg-[color-mix(in_oklch,_var(--terminal-cyan)_10%,_transparent)]"
        >
          <component
            :is="getActionIcon(entity.action)"
            class="h-6 w-6"
            :class="getActionIconColor(entity.action)"
          />
        </div>
        <div class="flex flex-col gap-1">
          <h3 class="text-lg font-data font-semibold tracking-tight">
            {{ entity.action }}
          </h3>
          <p class="text-sm text-[var(--silver-500)] flex items-center gap-1">
            <IconClock class="h-3.5 w-3.5" />
            <span class="font-data tabular-nums">{{
              formatDateTimeByLocale(entity.createdAt)
            }}</span>
          </p>
          <div class="flex flex-wrap gap-2 mt-1">
            <SemanticBadge :color="getAuditActionColor(entity.action)" :label="entity.action" />
            <SemanticBadge color="info" :label="`ID: ${entity.id.slice(0, 8)}`" />
          </div>
        </div>
      </div>

      <Separator />

      <!-- Context Grid - Terminal Style -->
      <div class="grid grid-cols-2 gap-6">
        <div class="space-y-4">
          <span class="terminal-label text-xs uppercase tracking-wider">
            {{ t('audit.columns.performer') }}
          </span>
          <div class="flex items-center gap-3">
            <div
              class="flex h-8 w-8 items-center justify-center rounded-full border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
            >
              <IconUser class="h-4 w-4 text-[var(--silver-500)]" />
            </div>
            <div class="flex flex-col">
              <span class="text-sm font-medium font-data">
                {{ entity.performer?.username || t('audit.drawer.system') }}
              </span>
              <span class="text-xs text-[var(--silver-500)]">
                {{
                  entity.performer?.role
                    ? t(`users.filters.role.${entity.performer.role}`, entity.performer.role)
                    : 'SYSTEM'
                }}
              </span>
            </div>
          </div>
        </div>

        <div class="space-y-4">
          <span class="terminal-label text-xs uppercase tracking-wider">
            {{ t('audit.drawer.targetEntity') }}
          </span>
          <div class="flex items-center gap-3">
            <div
              class="flex h-8 w-8 items-center justify-center rounded-full border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
            >
              <component
                :is="getEntityTypeIcon(entity.entityType)"
                class="h-4 w-4 text-[var(--silver-500)]"
              />
            </div>
            <div class="flex flex-col">
              <span class="text-sm font-medium">
                {{ entity.entityType || t('audit.drawer.notAvailable') }}
              </span>
              <span class="text-xs text-[var(--silver-500)] font-data">
                {{ entity.entityId?.slice(0, 8) || t('audit.drawer.notAvailable') }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- Request Context - Terminal Style -->
      <div
        v-if="entity.ipAddress || entity.userAgent"
        class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)] p-3 space-y-2"
      >
        <div v-if="entity.ipAddress" class="flex items-center justify-between text-sm">
          <span class="text-[var(--silver-500)] flex items-center gap-2">
            <IconTerminal class="h-3.5 w-3.5" /> {{ t('audit.columns.ip') }}
          </span>
          <span class="font-data text-[var(--terminal-cyan)]">{{ entity.ipAddress }}</span>
        </div>
        <div v-if="entity.userAgent" class="flex flex-col gap-1 text-sm">
          <span class="text-[var(--silver-500)] flex items-center gap-2">
            <IconEye class="h-3.5 w-3.5" /> {{ t('audit.drawer.userAgent') }}
          </span>
          <span
            class="text-xs text-[var(--silver-400)] break-all bg-[var(--card)] p-2 border border-[var(--silver-200)] dark:border-[var(--silver-300)] font-data"
          >
            {{ entity.userAgent }}
          </span>
        </div>
      </div>

      <Separator />

      <!-- Changes - Terminal Style -->
      <div class="space-y-4">
        <h4 class="text-sm font-medium leading-none flex items-center gap-2 font-data">
          <IconDatabase class="h-4 w-4 text-[var(--terminal-cyan)]" />
          {{ t('audit.drawer.dataChanges') }}
        </h4>

        <div
          v-if="!entity.oldValues && !entity.newValues"
          class="text-sm text-[var(--silver-500)] italic pl-6 font-data"
        >
          &gt; {{ t('audit.drawer.noDataChanges') }}
        </div>

        <div v-else class="grid gap-4">
          <div v-if="entity.oldValues" class="space-y-2">
            <span class="terminal-label text-xs uppercase tracking-wider">
              {{ t('audit.drawer.previousState') }}
            </span>
            <div
              class="relative rounded-none border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
            >
              <ScrollArea class="h-[200px] w-full rounded-none">
                <pre class="p-4 text-xs font-data leading-relaxed text-[var(--terminal-cyan)]">{{
                  formatJson(entity.oldValues)
                }}</pre>
              </ScrollArea>
            </div>
          </div>

          <div v-if="entity.newValues" class="space-y-2">
            <span class="terminal-label text-xs uppercase tracking-wider">
              {{ t('audit.drawer.newState') }}
            </span>
            <div
              class="relative rounded-none border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
            >
              <ScrollArea class="h-[200px] w-full rounded-none">
                <pre class="p-4 text-xs font-data leading-relaxed text-[var(--terminal-green)]">{{
                  formatJson(entity.newValues)
                }}</pre>
              </ScrollArea>
            </div>
          </div>
        </div>
      </div>
    </template>
  </BaseDetailDrawer>
</template>
