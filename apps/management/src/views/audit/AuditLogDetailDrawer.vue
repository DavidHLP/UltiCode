<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { formatDateTimeByLocale } from '@/i18n/utils'
import { Separator } from '@ulticode/design-system/ui/separator'
import {
  Database as IconDatabase,
  User as IconUser,
  Terminal as IconTerminal,
  Eye as IconEye,
  Clock as IconClock,
} from '@lucide/vue'

import { ScrollArea } from '@ulticode/design-system/ui/scroll-area'
import type { AuditLog } from '@/api/admin/audit'
import { formatJson, getActionIcon, getActionIconColor, getEntityTypeIcon } from './utils'
import SemanticBadge from '@ulticode/badge-config/SemanticBadge.vue'
import { getAuditActionColor } from '@ulticode/badge-config'
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
      <div class="flex items-start gap-4 p-4 border border-border dark:border-border bg-muted">
        <div
          class="flex h-12 w-12 items-center justify-center rounded-full border border-status-info-mark bg-[color-mix(in_oklch,_var(--status-info-mark)_10%,_transparent)]"
        >
          <component
            :is="getActionIcon(entity.action)"
            class="h-6 w-6"
            :class="getActionIconColor(entity.action)"
          />
        </div>
        <div class="flex flex-col gap-1">
          <h3 class="text-lg font-mono font-semibold tracking-tight">
            {{ entity.action }}
          </h3>
          <p class="text-sm text-muted-foreground flex items-center gap-1">
            <IconClock class="h-3.5 w-3.5" />
            <span class="font-mono tabular-nums">{{
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
          <span class="text-sm font-medium text-xs uppercase tracking-wider">
            {{ t('audit.columns.performer') }}
          </span>
          <div class="flex items-center gap-3">
            <div
              class="flex h-8 w-8 items-center justify-center rounded-full border border-border dark:border-border bg-muted"
            >
              <IconUser class="h-4 w-4 text-muted-foreground" />
            </div>
            <div class="flex flex-col">
              <span class="text-sm font-medium font-mono">
                {{ entity.performer?.username || t('audit.drawer.system') }}
              </span>
              <span class="text-xs text-muted-foreground">
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
          <span class="text-sm font-medium text-xs uppercase tracking-wider">
            {{ t('audit.drawer.targetEntity') }}
          </span>
          <div class="flex items-center gap-3">
            <div
              class="flex h-8 w-8 items-center justify-center rounded-full border border-border dark:border-border bg-muted"
            >
              <component
                :is="getEntityTypeIcon(entity.entityType)"
                class="h-4 w-4 text-muted-foreground"
              />
            </div>
            <div class="flex flex-col">
              <span class="text-sm font-medium">
                {{ entity.entityType || t('audit.drawer.notAvailable') }}
              </span>
              <span class="text-xs text-muted-foreground font-mono">
                {{ entity.entityId?.slice(0, 8) || t('audit.drawer.notAvailable') }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- Request Context - Terminal Style -->
      <div
        v-if="entity.ipAddress || entity.userAgent"
        class="border border-border dark:border-border bg-muted p-3 space-y-2"
      >
        <div v-if="entity.ipAddress" class="flex items-center justify-between text-sm">
          <span class="text-muted-foreground flex items-center gap-2">
            <IconTerminal class="h-3.5 w-3.5" /> {{ t('audit.columns.ip') }}
          </span>
          <span class="font-mono text-foreground">{{ entity.ipAddress }}</span>
        </div>
        <div v-if="entity.userAgent" class="flex flex-col gap-1 text-sm">
          <span class="text-muted-foreground flex items-center gap-2">
            <IconEye class="h-3.5 w-3.5" /> {{ t('audit.drawer.userAgent') }}
          </span>
          <span
            class="text-xs text-muted-foreground break-all bg-card p-2 border border-border dark:border-border font-mono"
          >
            {{ entity.userAgent }}
          </span>
        </div>
      </div>

      <Separator />

      <!-- Changes - Terminal Style -->
      <div class="space-y-4">
        <h4 class="text-sm font-medium leading-none flex items-center gap-2 font-mono">
          <IconDatabase class="h-4 w-4 text-status-info-mark" />
          {{ t('audit.drawer.dataChanges') }}
        </h4>

        <div
          v-if="!entity.oldValues && !entity.newValues"
          class="text-sm text-muted-foreground italic pl-6 font-mono"
        >
          &gt; {{ t('audit.drawer.noDataChanges') }}
        </div>

        <div v-else class="grid gap-4">
          <div v-if="entity.oldValues" class="space-y-2">
            <span class="text-sm font-medium text-xs uppercase tracking-wider">
              {{ t('audit.drawer.previousState') }}
            </span>
            <div class="relative rounded-lg border border-border dark:border-border bg-muted">
              <ScrollArea class="h-[200px] w-full">
                <pre class="p-4 text-xs font-mono leading-relaxed text-foreground">{{
                  formatJson(entity.oldValues)
                }}</pre>
              </ScrollArea>
            </div>
          </div>

          <div v-if="entity.newValues" class="space-y-2">
            <span class="text-sm font-medium text-xs uppercase tracking-wider">
              {{ t('audit.drawer.newState') }}
            </span>
            <div class="relative rounded-lg border border-border dark:border-border bg-muted">
              <ScrollArea class="h-[200px] w-full">
                <pre class="p-4 text-xs font-mono leading-relaxed text-foreground">{{
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
