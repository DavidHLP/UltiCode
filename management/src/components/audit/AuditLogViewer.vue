<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateTimeByLocale } from '@/i18n/utils'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Input } from '@/components/ui/input'
import { ScrollArea } from '@/components/ui/scroll-area'
import { IconSearch, IconDownload, IconChevronDown, IconChevronUp } from '@tabler/icons-vue'
import { auditApi, type AuditLog } from '@/api/admin/audit'
import { formatJson } from '@/views/audit/utils'
import { SemanticBadge, getAuditActionColor } from '@/components/ui/terminal'

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
    })
    auditLogs.value = response.items || []
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

function formatAction(action: string): string {
  const key = `audit.actionTypes.${action}`
  const translated = t(key)
  if (translated === key) {
    return action
      .replace(/_/g, ' ')
      .toLowerCase()
      .replace(/\b\w/g, (l) => l.toUpperCase())
  }
  return translated
}

function getChangesSummary(log: AuditLog): { count: number; label: string } {
  let count = 0
  if (log.oldValues && typeof log.oldValues === 'object') {
    count += Object.keys(log.oldValues).length
  }
  if (log.newValues && typeof log.newValues === 'object') {
    count += Object.keys(log.newValues).length
  }
  return { count, label: `${count} change${count !== 1 ? 's' : ''}` }
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
  // Initial load is handled by watch(immediate: true)
})
</script>

<template>
  <div class="space-y-3">
    <!-- Compact filters -->
    <div v-if="showFilters" class="flex items-center gap-2 flex-wrap">
      <div class="relative flex-1 min-w-[200px] max-w-sm">
        <IconSearch class="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
        <Input
          v-model="searchQuery"
          :placeholder="t('audit.searchPlaceholder')"
          class="pl-8"
          @keyup.enter="loadAuditLogs"
        />
      </div>
      <Select v-model="actionFilter" @update:model-value="loadAuditLogs">
        <SelectTrigger class="w-[180px]">
          <SelectValue :placeholder="t('audit.filterAction')" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="all">{{ t('audit.allActions') }}</SelectItem>
          <SelectItem value="CREATE_USER">{{ t('audit.actionTypes.CREATE_USER') }}</SelectItem>
          <SelectItem value="UPDATE_USER">{{ t('audit.actionTypes.UPDATE_USER') }}</SelectItem>
          <SelectItem value="DELETE_USER">{{ t('audit.actionTypes.DELETE_USER') }}</SelectItem>
          <SelectItem value="BAN_USER">{{ t('audit.actionTypes.BAN_USER') }}</SelectItem>
          <SelectItem value="UNBAN_USER">{{ t('audit.actionTypes.UNBAN_USER') }}</SelectItem>
          <SelectItem value="GRANT_PERMISSION">{{
            t('audit.actionTypes.GRANT_PERMISSION')
          }}</SelectItem>
          <SelectItem value="REVOKE_PERMISSION">{{
            t('audit.actionTypes.REVOKE_PERMISSION')
          }}</SelectItem>
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
        {{ t('audit.export') }}
      </Button>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="flex items-center justify-center py-8">
      <div class="text-muted-foreground text-sm">{{ t('common.loading') }}</div>
    </div>

    <!-- Empty state -->
    <div
      v-else-if="!filteredLogs?.length"
      class="flex flex-col items-center justify-center py-12 text-center"
    >
      <div class="text-muted-foreground mb-2">{{ t('audit.noLogs') }}</div>
      <p class="text-muted-foreground text-sm">{{ t('audit.noLogsDescription') }}</p>
    </div>

    <!-- Compact cards -->
    <div v-else class="space-y-2">
      <Card
        v-for="log in filteredLogs"
        :key="log.id"
        class="hover:shadow-sm transition-shadow border-[var(--silver-200)] dark:border-[var(--silver-300)]"
      >
        <!-- Compact header: action + entity + time + performer in one line -->
        <CardHeader class="py-2 px-3">
          <div class="flex items-center justify-between gap-2">
            <!-- Left: badges -->
            <div class="flex items-center gap-2 min-w-0 flex-1">
              <SemanticBadge
                :color="getAuditActionColor(log.action)"
                :label="formatAction(log.action)"
                size="sm"
              />
              <Badge v-if="log.entityType" variant="outline" class="text-xs px-1.5 py-0">
                {{ t(`audit.entityTypes.${log.entityType}`, log.entityType) }}
              </Badge>
              <span class="text-xs text-[var(--silver-500)] font-data tabular-nums truncate">
                {{ formatDateTimeByLocale(log.createdAt) }}
              </span>
              <span class="text-[var(--silver-400)] text-xs">·</span>
              <span
                v-if="log.performer"
                class="text-xs text-[var(--silver-600)] dark:text-[var(--silver-400)] truncate"
              >
                {{ log.performer.username }}
              </span>
              <span v-else class="text-xs text-[var(--silver-500)]">System</span>
            </div>

            <!-- Right: expand toggle + change count -->
            <div class="flex items-center gap-2 flex-shrink-0">
              <span
                v-if="!expandedLogs.has(log.id) && getChangesSummary(log).count > 0"
                class="text-xs text-[var(--silver-400)]"
              >
                {{ getChangesSummary(log).label }}
              </span>
              <Button variant="ghost" size="sm" class="h-6 w-6 p-0" @click="toggleExpand(log.id)">
                <component
                  :is="expandedLogs.has(log.id) ? IconChevronUp : IconChevronDown"
                  class="h-3 w-3"
                />
              </Button>
            </div>
          </div>

          <!-- ID row when expanded -->
          <div v-if="expandedLogs.has(log.id)" class="flex items-center gap-2 mt-1">
            <span class="text-xs text-[var(--silver-400)] font-data">
              ID: {{ log.id.slice(0, 8) }}
            </span>
          </div>
        </CardHeader>

        <!-- Expandable details -->
        <CardContent v-if="expandedLogs.has(log.id)" class="pt-0 px-3 pb-3">
          <div class="space-y-2">
            <!-- Performer info -->
            <div v-if="log.performer" class="flex items-center gap-2 text-xs">
              <Badge variant="secondary" class="text-xs">
                {{ t(`users.filters.role.${log.performer.role}`, log.performer.role) }}
              </Badge>
              <span class="font-medium text-[var(--silver-700)] dark:text-[var(--silver-300)]">{{
                log.performer.name || log.performer.username
              }}</span>
            </div>

            <!-- Changes section -->
            <div v-if="log.oldValues || log.newValues" class="space-y-2">
              <div v-if="log.oldValues" class="space-y-1">
                <span class="text-xs font-medium text-[var(--silver-500)] uppercase tracking-wider">
                  {{ t('audit.oldValues') }}
                </span>
                <div
                  class="rounded-none border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
                >
                  <ScrollArea class="h-[120px] w-full rounded-none">
                    <pre
                      class="p-2 text-xs font-data leading-relaxed text-[var(--terminal-cyan)]"
                      >{{ formatJson(log.oldValues) }}</pre
                    >
                  </ScrollArea>
                </div>
              </div>

              <div v-if="log.newValues" class="space-y-1">
                <span class="text-xs font-medium text-[var(--silver-500)] uppercase tracking-wider">
                  {{ t('audit.newValues') }}
                </span>
                <div
                  class="rounded-none border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
                >
                  <ScrollArea class="h-[120px] w-full rounded-none">
                    <pre
                      class="p-2 text-xs font-data leading-relaxed text-[var(--terminal-green)]"
                      >{{ formatJson(log.newValues) }}</pre
                    >
                  </ScrollArea>
                </div>
              </div>
            </div>

            <!-- Metadata: IP and userAgent -->
            <div
              v-if="log.ipAddress || log.userAgent"
              class="flex flex-wrap gap-x-4 gap-y-1 text-xs text-[var(--silver-400)] pt-1"
            >
              <span v-if="log.ipAddress" class="font-data">
                {{ t('audit.ipAddress') }}: {{ log.ipAddress }}
              </span>
              <span v-if="log.userAgent" class="truncate max-w-[300px]" :title="log.userAgent">
                {{ t('audit.userAgent') }}: {{ log.userAgent.slice(0, 60)
                }}{{ log.userAgent.length > 60 ? '...' : '' }}
              </span>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>

    <!-- Pagination -->
    <div v-if="totalPages > 1" class="flex items-center justify-center gap-2">
      <Button variant="outline" size="sm" :disabled="currentPage === 1" @click="handlePreviousPage">
        {{ t('common.previous') }}
      </Button>
      <span class="text-sm text-muted-foreground">
        {{ t('common.page') }} {{ currentPage }} / {{ totalPages }}
      </span>
      <Button
        variant="outline"
        size="sm"
        :disabled="currentPage === totalPages"
        @click="handleNextPage"
      >
        {{ t('common.next') }}
      </Button>
    </div>
  </div>
</template>
