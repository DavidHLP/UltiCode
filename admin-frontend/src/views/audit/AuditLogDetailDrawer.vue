<script setup lang="ts">
import {
  Drawer,
  DrawerContent,
  DrawerDescription,
  DrawerHeader,
  DrawerTitle,
} from '@/components/ui/drawer'
import { Badge } from '@/components/ui/badge'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Separator } from '@/components/ui/separator'
import { IconDatabase, IconUser, IconTerminal, IconEye, IconClock } from '@tabler/icons-vue'
import type { AuditLog } from '@/api/admin/audit'
import {
  formatJson,
  getActionBadgeVariant,
  getActionIcon,
  getActionIconColor,
  getEntityTypeIcon,
} from './utils'

defineProps<{
  open: boolean
  log: AuditLog | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()
</script>

<template>
  <Drawer :open="open" @update:open="emit('update:open', $event)" direction="right">
    <DrawerContent class="h-full w-[400px] sm:w-[540px]">
      <DrawerHeader class="border-b px-6 py-4">
        <div class="flex items-center justify-between">
          <div>
            <DrawerTitle>Audit Log Details</DrawerTitle>
            <DrawerDescription>Detailed record of the system event.</DrawerDescription>
          </div>
        </div>
      </DrawerHeader>

      <ScrollArea v-if="log" class="flex-1">
        <div class="flex flex-col gap-6 p-6">
          <!-- Header Info -->
          <div class="flex items-start gap-4">
            <div class="flex h-12 w-12 items-center justify-center rounded-full bg-muted shadow-sm">
              <component
                :is="getActionIcon(log.action)"
                class="h-6 w-6"
                :class="getActionIconColor(log.action)"
              />
            </div>
            <div class="flex flex-col gap-1">
              <h3 class="text-lg font-semibold leading-none tracking-tight">
                {{ log.action }}
              </h3>
              <p class="text-sm text-muted-foreground flex items-center gap-1">
                <IconClock class="h-3.5 w-3.5" />
                {{ new Date(log.created_at).toLocaleString() }}
              </p>
              <div class="flex flex-wrap gap-2 mt-1">
                <Badge :variant="getActionBadgeVariant(log.action)">
                  {{ log.action }}
                </Badge>
                <Badge variant="outline" class="font-mono"> ID: {{ log.id.slice(0, 8) }} </Badge>
              </div>
            </div>
          </div>

          <Separator />

          <!-- Context Grid -->
          <div class="grid grid-cols-2 gap-6">
            <div class="space-y-4">
              <h4 class="text-xs font-medium text-muted-foreground uppercase tracking-wider">
                Performer
              </h4>
              <div class="flex items-center gap-3">
                <div class="flex h-8 w-8 items-center justify-center rounded-full bg-muted">
                  <IconUser class="h-4 w-4 text-muted-foreground" />
                </div>
                <div class="flex flex-col">
                  <span class="text-sm font-medium">
                    {{ log.performer?.username || 'System' }}
                  </span>
                  <span class="text-xs text-muted-foreground">
                    {{ log.performer?.role || 'SYSTEM' }}
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
                    :is="getEntityTypeIcon(log.entity_type)"
                    class="h-4 w-4 text-muted-foreground"
                  />
                </div>
                <div class="flex flex-col">
                  <span class="text-sm font-medium">
                    {{ log.entity_type || 'N/A' }}
                  </span>
                  <span class="text-xs text-muted-foreground font-mono">
                    {{ log.entity_id?.slice(0, 8) || 'N/A' }}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <div
            v-if="log.ip_address || log.user_agent"
            class="rounded-lg bg-muted/50 p-3 space-y-2 border"
          >
            <div v-if="log.ip_address" class="flex items-center justify-between text-sm">
              <span class="text-muted-foreground flex items-center gap-2">
                <IconTerminal class="h-3.5 w-3.5" /> IP Address
              </span>
              <span class="font-mono">{{ log.ip_address }}</span>
            </div>
            <div v-if="log.user_agent" class="flex flex-col gap-1 text-sm">
              <span class="text-muted-foreground flex items-center gap-2">
                <IconEye class="h-3.5 w-3.5" /> User Agent
              </span>
              <span
                class="text-xs text-muted-foreground break-all bg-background p-2 rounded border"
              >
                {{ log.user_agent }}
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
              v-if="!log.old_values && !log.new_values"
              class="text-sm text-muted-foreground italic pl-6"
            >
              No data changes recorded.
            </div>

            <div v-else class="grid gap-4">
              <div v-if="log.old_values" class="space-y-2">
                <div class="flex items-center justify-between">
                  <span class="text-xs font-medium text-muted-foreground uppercase tracking-wider">
                    Previous State
                  </span>
                </div>
                <div class="relative rounded-md border bg-muted/30">
                  <ScrollArea class="h-[200px] w-full rounded-md">
                    <pre class="p-4 text-xs font-mono leading-relaxed">{{
                      formatJson(log.old_values)
                    }}</pre>
                  </ScrollArea>
                </div>
              </div>

              <div v-if="log.new_values" class="space-y-2">
                <div class="flex items-center justify-between">
                  <span class="text-xs font-medium text-muted-foreground uppercase tracking-wider">
                    New State
                  </span>
                </div>
                <div class="relative rounded-md border bg-muted/30">
                  <ScrollArea class="h-[200px] w-full rounded-md">
                    <pre class="p-4 text-xs font-mono leading-relaxed">{{
                      formatJson(log.new_values)
                    }}</pre>
                  </ScrollArea>
                </div>
              </div>
            </div>
          </div>
        </div>
      </ScrollArea>

      <div v-else class="flex h-full items-center justify-center p-8">
        <p class="text-muted-foreground">Select a log entry to view details</p>
      </div>
    </DrawerContent>
  </Drawer>
</template>
