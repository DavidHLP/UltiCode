<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import { IconDatabase, IconUser, IconTerminal, IconEye, IconClock } from '@tabler/icons-vue'
import { ScrollArea } from '@/components/ui/scroll-area'
import type { AuditLog } from '@/api/admin/audit'
import {
  formatJson,
  getActionBadgeVariant,
  getActionIcon,
  getActionIconColor,
  getEntityTypeIcon,
} from './utils'
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
    description="Detailed record of the system event."
    not-found-text="Select a log entry to view details"
  >
    <template #content="{ entity }">
      <!-- Header Info -->
      <div class="flex items-start gap-4">
        <div class="flex h-12 w-12 items-center justify-center rounded-full bg-muted shadow-sm">
          <component
            :is="getActionIcon(entity.action)"
            class="h-6 w-6"
            :class="getActionIconColor(entity.action)"
          />
        </div>
        <div class="flex flex-col gap-1">
          <h3 class="text-lg font-semibold leading-none tracking-tight">
            {{ entity.action }}
          </h3>
          <p class="text-sm text-muted-foreground flex items-center gap-1">
            <IconClock class="h-3.5 w-3.5" />
            {{ new Date(entity.created_at).toLocaleString() }}
          </p>
          <div class="flex flex-wrap gap-2 mt-1">
            <Badge :variant="getActionBadgeVariant(entity.action)">
              {{ entity.action }}
            </Badge>
            <Badge variant="outline" class="font-mono"> ID: {{ entity.id.slice(0, 8) }} </Badge>
          </div>
        </div>
      </div>

      <Separator />

      <!-- Context Grid -->
      <div class="grid grid-cols-2 gap-6">
        <div class="space-y-4">
          <h4 class="text-xs font-medium text-muted-foreground uppercase tracking-wider">
            {{ t('audit.columns.performer') }}
          </h4>
          <div class="flex items-center gap-3">
            <div class="flex h-8 w-8 items-center justify-center rounded-full bg-muted">
              <IconUser class="h-4 w-4 text-muted-foreground" />
            </div>
            <div class="flex flex-col">
              <span class="text-sm font-medium">
                {{ entity.performer?.username || 'System' }}
              </span>
              <span class="text-xs text-muted-foreground">
                {{ entity.performer?.role || 'SYSTEM' }}
              </span>
            </div>
          </div>
        </div>

        <div class="space-y-4">
          <h4 class="text-xs font-medium text-muted-foreground uppercase tracking-wider">
            Target Entity
          </h4>
          <div class="flex items-center gap-3">
            <div class="flex h-8 w-8 items-center justify-center rounded-full bg-muted">
              <component
                :is="getEntityTypeIcon(entity.entity_type)"
                class="h-4 w-4 text-muted-foreground"
              />
            </div>
            <div class="flex flex-col">
              <span class="text-sm font-medium">
                {{ entity.entity_type || 'N/A' }}
              </span>
              <span class="text-xs text-muted-foreground font-mono">
                {{ entity.entity_id?.slice(0, 8) || 'N/A' }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <div
        v-if="entity.ip_address || entity.user_agent"
        class="rounded-lg bg-muted/50 p-3 space-y-2 border"
      >
        <div v-if="entity.ip_address" class="flex items-center justify-between text-sm">
          <span class="text-muted-foreground flex items-center gap-2">
            <IconTerminal class="h-3.5 w-3.5" /> {{ t('audit.columns.ip') }}
          </span>
          <span class="font-mono">{{ entity.ip_address }}</span>
        </div>
        <div v-if="entity.user_agent" class="flex flex-col gap-1 text-sm">
          <span class="text-muted-foreground flex items-center gap-2">
            <IconEye class="h-3.5 w-3.5" /> User Agent
          </span>
          <span class="text-xs text-muted-foreground break-all bg-background p-2 rounded border">
            {{ entity.user_agent }}
          </span>
        </div>
      </div>

      <Separator />

      <!-- Changes -->
      <div class="space-y-4">
        <h4 class="text-sm font-medium leading-none flex items-center gap-2">
          <IconDatabase class="h-4 w-4" />
          Data Changes
        </h4>

        <div
          v-if="!entity.old_values && !entity.new_values"
          class="text-sm text-muted-foreground italic pl-6"
        >
          No data changes recorded.
        </div>

        <div v-else class="grid gap-4">
          <div v-if="entity.old_values" class="space-y-2">
            <div class="flex items-center justify-between">
              <span class="text-xs font-medium text-muted-foreground uppercase tracking-wider">
                Previous State
              </span>
            </div>
            <div class="relative rounded-md border bg-muted/30">
              <ScrollArea class="h-[200px] w-full rounded-md">
                <pre class="p-4 text-xs font-mono leading-relaxed">{{
                  formatJson(entity.old_values)
                }}</pre>
              </ScrollArea>
            </div>
          </div>

          <div v-if="entity.new_values" class="space-y-2">
            <div class="flex items-center justify-between">
              <span class="text-xs font-medium text-muted-foreground uppercase tracking-wider">
                New State
              </span>
            </div>
            <div class="relative rounded-md border bg-muted/30">
              <ScrollArea class="h-[200px] w-full rounded-md">
                <pre class="p-4 text-xs font-mono leading-relaxed">{{
                  formatJson(entity.new_values)
                }}</pre>
              </ScrollArea>
            </div>
          </div>
        </div>
      </div>
    </template>
  </BaseDetailDrawer>
</template>
