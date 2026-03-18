<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Input } from '@/components/ui/input'
import {
  IconClock,
  IconUser,
  IconDatabase,
  IconSearch,
  IconDownload,
  IconChevronDown,
  IconChevronUp,
} from '@tabler/icons-vue'
import { auditApi, type AuditLog } from '@/api/admin/audit'

const { t } = useI18n()

interface Props {
  entityType?: string
  entityId?: string
  limit?: number
  showFilters?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  limit: 20,
  showFilters: true,
})

const auditLogs = ref<AuditLog[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(props.limit)
const total = ref(0)
const totalPages = ref(0)

const searchQuery = ref('')
const actionFilter = ref('')
const performerFilter = ref('')
const sortBy = ref('created_at')
const sortOrder = ref<'asc' | 'desc'>('desc')

const expandedLogs = ref<Set<string>>(new Set())

async function loadAuditLogs() {
  if (!props.entityType || !props.entityId) {
    return
  }
  loading.value = true
  try {
    const response = await auditApi.getAuditLogs({
      entityType: props.entityType,
      entityId: props.entityId,
      search: searchQuery.value || undefined,
      action: actionFilter.value && actionFilter.value !== 'all' ? actionFilter.value : undefined,
      performerId: performerFilter.value || undefined,
      page: currentPage.value,
      limit: pageSize.value,
      sortBy: sortBy.value,
      sortOrder: sortOrder.value,
    })
    auditLogs.value = response.logs
    total.value = response.total
    totalPages.value = response.totalPages
  } catch (error) {
    console.error('Failed to load audit logs:', error)
  } finally {
    loading.value = false
  }
}

function toggleExpand(logId: string) {
  if (expandedLogs.value.has(logId)) {
    expandedLogs.value.delete(logId)
  } else {
    expandedLogs.value.add(logId)
  }
}

function handlePreviousPage() {
  currentPage.value--
  loadAuditLogs()
}

function handleNextPage() {
  currentPage.value++
  loadAuditLogs()
}

function formatDate(date: Date | string): string {
  return new Date(date).toLocaleString()
}

function formatAction(action: string): string {
  return action
    .replace(/_/g, ' ')
    .toLowerCase()
    .replace(/\b\w/g, (l) => l.toUpperCase())
}

function formatValues(values: unknown): string {
  if (values === null || values === undefined) {
    return 'N/A'
  }
  if (typeof values === 'object') {
    return JSON.stringify(values, null, 2)
  }
  return String(values)
}

function getActionBadgeVariant(
  action: string,
): 'default' | 'destructive' | 'outline' | 'secondary' {
  const upperAction = action.toUpperCase()
  if (
    upperAction.includes('DELETE') ||
    upperAction.includes('BAN') ||
    upperAction.includes('FLAG')
  ) {
    return 'destructive'
  }
  if (upperAction.includes('CREATE') || upperAction.includes('PUBLISH')) {
    return 'default'
  }
  if (upperAction.includes('UPDATE') || upperAction.includes('MODERATE')) {
    return 'secondary'
  }
  return 'outline'
}

const filteredLogs = computed(() => {
  return auditLogs.value
})

// Watch for entityId changes to trigger data fetch
watch(
  () => props.entityId,
  (newEntityId) => {
    if (newEntityId && props.entityType) {
      currentPage.value = 1
      loadAuditLogs()
    }
  },
  { immediate: true },
)

onMounted(() => {
  // Initial load is handled by the watch with immediate: true
  // This is kept for backwards compatibility if watch doesn't trigger
  if (props.entityType && props.entityId) {
    loadAuditLogs()
  }
})
</script>

<template>
  <div class="space-y-4">
    <div v-if="showFilters" class="flex items-center gap-2 flex-wrap">
      <div class="relative flex-1 min-w-[200px] max-w-sm">
        <IconSearch class="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
        <Input
          v-model="searchQuery"
          :placeholder="t('auditLogs.searchPlaceholder')"
          class="pl-8"
          @keyup.enter="loadAuditLogs"
        />
      </div>
      <Select v-model="actionFilter" @update:model-value="loadAuditLogs">
        <SelectTrigger class="w-[180px]">
          <SelectValue :placeholder="t('auditLogs.filterAction')" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="all">{{ t('auditLogs.allActions') }}</SelectItem>
          <SelectItem value="CREATE">{{ t('auditLogs.actions.create') }}</SelectItem>
          <SelectItem value="UPDATE">{{ t('auditLogs.actions.update') }}</SelectItem>
          <SelectItem value="DELETE">{{ t('auditLogs.actions.delete') }}</SelectItem>
          <SelectItem value="PUBLISH">{{ t('auditLogs.actions.publish') }}</SelectItem>
          <SelectItem value="MODERATE">{{ t('auditLogs.actions.moderate') }}</SelectItem>
        </SelectContent>
      </Select>
      <Button variant="outline" size="sm" @click="loadAuditLogs">
        <IconSearch class="h-4 w-4 mr-1" />
        {{ t('common.search') }}
      </Button>
      <Button
        variant="outline"
        size="sm"
        @click="
          auditApi.exportAuditLogs({
            entityType,
            entityId,
            search: searchQuery || undefined,
            action: actionFilter || undefined,
            format: 'csv',
          })
        "
      >
        <IconDownload class="h-4 w-4 mr-1" />
        {{ t('auditLogs.export') }}
      </Button>
    </div>

    <div v-if="loading" class="flex items-center justify-center py-12">
      <div class="text-muted-foreground">{{ t('common.loading') }}</div>
    </div>

    <div
      v-else-if="filteredLogs.length === 0"
      class="flex flex-col items-center justify-center py-12 text-center"
    >
      <IconDatabase class="h-12 w-12 text-muted-foreground mb-4" />
      <h3 class="text-lg font-semibold mb-2">{{ t('auditLogs.noLogs') }}</h3>
      <p class="text-muted-foreground max-w-md">
        {{ t('auditLogs.noLogsDescription') }}
      </p>
    </div>

    <div v-else class="space-y-3">
      <Card v-for="log in filteredLogs" :key="log.id" class="hover:shadow-md transition-shadow">
        <CardHeader class="pb-3">
          <div class="flex items-start justify-between">
            <div class="flex-1">
              <div class="flex items-center gap-2 mb-2">
                <Badge :variant="getActionBadgeVariant(log.action)">
                  {{ formatAction(log.action) }}
                </Badge>
                <Badge v-if="log.entity_type" variant="outline" class="text-xs">
                  {{ log.entity_type }}
                </Badge>
              </div>
              <CardTitle class="text-base">{{
                log.entity_id || t('auditLogs.systemAction')
              }}</CardTitle>
            </div>
            <div class="flex items-center gap-2">
              <span class="text-sm text-muted-foreground">
                <IconClock class="inline h-3 w-3 mr-1" />
                {{ formatDate(log.created_at) }}
              </span>
              <Button variant="ghost" size="sm" @click="toggleExpand(log.id)">
                <component
                  :is="expandedLogs.has(log.id) ? IconChevronUp : IconChevronDown"
                  class="h-4 w-4"
                />
              </Button>
            </div>
          </div>
        </CardHeader>
        <CardContent v-if="expandedLogs.has(log.id)" class="pt-0">
          <div class="space-y-3">
            <div v-if="log.performer" class="flex items-center gap-2 text-sm">
              <IconUser class="h-4 w-4 text-muted-foreground" />
              <span class="font-medium">{{ log.performer.name || log.performer.username }}</span>
              <Badge variant="outline" class="text-xs">{{ log.performer.role }}</Badge>
            </div>

            <div v-if="log.old_values" class="bg-muted rounded-lg p-3">
              <p class="text-sm font-medium mb-1">{{ t('auditLogs.oldValues') }}</p>
              <pre class="text-xs text-muted-foreground whitespace-pre-wrap">{{
                formatValues(log.old_values)
              }}</pre>
            </div>

            <div v-if="log.new_values" class="bg-muted rounded-lg p-3">
              <p class="text-sm font-medium mb-1">{{ t('auditLogs.newValues') }}</p>
              <pre class="text-xs text-muted-foreground whitespace-pre-wrap">{{
                formatValues(log.new_values)
              }}</pre>
            </div>

            <div
              v-if="log.ip_address || log.user_agent"
              class="flex flex-wrap gap-4 text-xs text-muted-foreground"
            >
              <span v-if="log.ip_address"
                >{{ t('auditLogs.ipAddress') }}: {{ log.ip_address }}</span
              >
              <span v-if="log.user_agent"
                >{{ t('auditLogs.userAgent') }}: {{ log.user_agent }}</span
              >
            </div>
          </div>
        </CardContent>
      </Card>
    </div>

    <div v-if="totalPages > 1" class="flex items-center justify-center gap-2">
      <Button variant="outline" :disabled="currentPage === 1" @click="handlePreviousPage">
        {{ t('common.previous') }}
      </Button>
      <span class="text-sm text-muted-foreground">
        {{ t('common.page') }} {{ currentPage }} / {{ totalPages }}
      </span>
      <Button variant="outline" :disabled="currentPage === totalPages" @click="handleNextPage">
        {{ t('common.next') }}
      </Button>
    </div>
  </div>
</template>
