<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { IconArrowRight, IconArrowUp, IconArrowDown, IconX, IconCheck } from '@tabler/icons-vue'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import type { ProblemVersion } from '@/api/admin/problems'

interface Props {
  oldVersion: ProblemVersion | null
  newVersion: ProblemVersion | null
}

const props = defineProps<Props>()

const { t } = useI18n()

const changes = computed(() => {
  if (!props.oldVersion || !props.newVersion) return []

  const oldValues = props.oldVersion.oldValues || {}
  const newValues = props.newVersion.newValues || {}

  const fields: Array<{
    field: string
    label: string
    oldValue: unknown
    newValue: unknown
    type: 'text' | 'boolean' | 'array' | 'object'
  }> = []

  // Define fields to compare
  const fieldDefinitions = [
    { key: 'title', label: t('problems.fields.title'), type: 'text' as const },
    { key: 'description', label: t('problems.fields.description'), type: 'text' as const },
    { key: 'difficulty', label: t('problems.fields.difficulty'), type: 'text' as const },
    { key: 'status', label: t('problems.fields.status'), type: 'text' as const },
    { key: 'is_premium', label: t('problems.fields.premium'), type: 'boolean' as const },
    { key: 'is_published', label: t('problems.fields.published'), type: 'boolean' as const },
    { key: 'tags', label: t('problems.fields.tags'), type: 'array' as const },
    { key: 'languages', label: t('problems.fields.languages'), type: 'array' as const },
    { key: 'time_limit', label: t('problems.fields.timeLimit'), type: 'text' as const },
    { key: 'memory_limit', label: t('problems.fields.memoryLimit'), type: 'text' as const },
  ]

  for (const def of fieldDefinitions) {
    const oldValue = oldValues[def.key]
    const newValue = newValues[def.key]

    if (JSON.stringify(oldValue) !== JSON.stringify(newValue)) {
      fields.push({
        field: def.key,
        label: def.label,
        oldValue,
        newValue,
        type: def.type,
      })
    }
  }

  return fields
})

function formatValue(value: unknown, type: string): string {
  if (value === null || value === undefined) {
    return '—'
  }

  switch (type) {
    case 'boolean':
      return value ? t('common.yes') : t('common.no')
    case 'array':
      if (Array.isArray(value)) {
        if (value.length === 0) return '—'
        return value
          .map((v) => (typeof v === 'object' ? v.label || v.name || JSON.stringify(v) : v))
          .join(', ')
      }
      return String(value)
    case 'object':
      if (typeof value === 'object') {
        return JSON.stringify(value, null, 2)
      }
      return String(value)
    default:
      return String(value)
  }
}

function getChangeIcon(oldValue: unknown, newValue: unknown) {
  if (oldValue === null || oldValue === undefined) {
    return IconArrowUp
  }
  if (newValue === null || newValue === undefined) {
    return IconArrowDown
  }
  return IconArrowRight
}

function getChangeType(oldValue: unknown, newValue: unknown): 'added' | 'removed' | 'changed' {
  if (oldValue === null || oldValue === undefined) return 'added'
  if (newValue === null || newValue === undefined) return 'removed'
  return 'changed'
}

function getChangeBadgeVariant(type: 'added' | 'removed' | 'changed') {
  switch (type) {
    case 'added':
      return 'default'
    case 'removed':
      return 'destructive'
    case 'changed':
      return 'secondary'
  }
}

function getChangeLabel(type: 'added' | 'removed' | 'changed') {
  switch (type) {
    case 'added':
      return t('problems.versionHistory.changeType.added')
    case 'removed':
      return t('problems.versionHistory.changeType.removed')
    case 'changed':
      return t('problems.versionHistory.changeType.changed')
  }
}
</script>

<template>
  <div class="space-y-4">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <h3 class="text-sm font-semibold">{{ t('problems.versionHistory.comparisonTitle') }}</h3>
      <Badge variant="outline" class="text-xs">
        {{ changes.length }} {{ t('problems.versionHistory.changesCount') }}
      </Badge>
    </div>

    <!-- Empty State -->
    <div
      v-if="changes.length === 0"
      class="flex flex-col items-center justify-center py-8 text-center"
    >
      <IconCheck class="h-8 w-8 text-muted-foreground mb-2" />
      <p class="text-sm text-muted-foreground">
        {{ t('problems.versionHistory.noChanges') }}
      </p>
    </div>

    <!-- Changes List -->
    <div v-else class="space-y-3">
      <div
        v-for="change in changes"
        :key="change.field"
        class="rounded-lg border bg-card p-4 space-y-3"
      >
        <!-- Field Header -->
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <component
              :is="getChangeIcon(change.oldValue, change.newValue)"
              class="h-4 w-4 text-muted-foreground"
            />
            <span class="text-sm font-medium">{{ change.label }}</span>
          </div>
          <Badge
            :variant="getChangeBadgeVariant(getChangeType(change.oldValue, change.newValue))"
            class="text-xs"
          >
            {{ getChangeLabel(getChangeType(change.oldValue, change.newValue)) }}
          </Badge>
        </div>

        <!-- Values -->
        <div class="grid grid-cols-2 gap-4">
          <!-- Old Value -->
          <div class="space-y-1">
            <div class="flex items-center gap-1.5 text-xs text-muted-foreground">
              <IconX class="h-3 w-3" />
              <span>{{ t('problems.versionHistory.oldValue') }}</span>
            </div>
            <div
              class="rounded-md bg-muted/50 p-2.5 min-h-[60px] text-xs"
              :class="{
                'line-through text-muted-foreground':
                  change.oldValue !== null && change.oldValue !== undefined,
              }"
            >
              <pre v-if="change.type === 'object'" class="whitespace-pre-wrap font-mono">{{
                formatValue(change.oldValue, change.type)
              }}</pre>
              <span v-else class="break-words">{{
                formatValue(change.oldValue, change.type)
              }}</span>
            </div>
          </div>

          <!-- New Value -->
          <div class="space-y-1">
            <div class="flex items-center gap-1.5 text-xs text-muted-foreground">
              <IconCheck class="h-3 w-3" />
              <span>{{ t('problems.versionHistory.newValue') }}</span>
            </div>
            <div
              class="rounded-md bg-muted/50 p-2.5 min-h-[60px] text-xs"
              :class="{
                'border-2 border-primary/20':
                  change.newValue !== null && change.newValue !== undefined,
              }"
            >
              <pre v-if="change.type === 'object'" class="whitespace-pre-wrap font-mono">{{
                formatValue(change.newValue, change.type)
              }}</pre>
              <span v-else class="break-words">{{
                formatValue(change.newValue, change.type)
              }}</span>
            </div>
          </div>
        </div>

        <!-- Separator -->
        <Separator v-if="changes.indexOf(change) !== changes.length - 1" />
      </div>
    </div>
  </div>
</template>
