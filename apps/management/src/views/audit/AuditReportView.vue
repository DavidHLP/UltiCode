<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Card, CardContent, CardHeader, CardTitle } from '@ulticode/design-system/ui/card'
import { Input } from '@ulticode/design-system/ui/input'
import { Label } from '@ulticode/design-system/ui/label'
import { Badge } from '@ulticode/design-system/ui/badge'
import { Button } from '@ulticode/design-system/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  SelectGroup,
} from '@ulticode/design-system/ui/select'
import { CalendarIcon, RotateCcw } from '@lucide/vue'
import { useAuditStore } from '@/stores/admin/audit'
import { toAuditStatsQueryParams } from './useAuditReadWorkspace'
import {
  AUDIT_ENTITY_TYPES,
  AUDIT_ACTIONS_BY_ENTITY,
  actionToI18nKey,
  actionTypeGroupToI18nKey,
  entityTypeToI18nKey,
} from './utils'

const { t } = useI18n()
const auditStore = useAuditStore()

const startDate = ref('')
const endDate = ref('')
const performerFilter = ref('')
const userIdFilter = ref('')
const entityTypeFilter = ref<string>('all')
const actionFilter = ref<string>('all')
const searchFilter = ref('')
const isLoaded = ref(false)

const entityTypeOptions = computed(() =>
  AUDIT_ENTITY_TYPES.map((type) => ({
    value: type,
    label: t(entityTypeToI18nKey(type)),
  })),
)

const actionOptions = computed(() => {
  if (!entityTypeFilter.value || entityTypeFilter.value === 'all') return []
  const actions = AUDIT_ACTIONS_BY_ENTITY[entityTypeFilter.value] || []
  return actions.map((action) => ({
    value: action,
    label: t(actionToI18nKey(action)),
  }))
})

watch(entityTypeFilter, () => {
  actionFilter.value = 'all'
})

const stats = computed(() => auditStore.stats)
const statsError = computed(() => auditStore.statsError)
const statsUnavailable = computed(() => auditStore.statsLoading || Boolean(statsError.value))

async function loadStats() {
  const params = toAuditStatsQueryParams({
    search: searchFilter.value,
    filters: {
      action: actionFilter.value,
      entityType: entityTypeFilter.value,
      startDate: startDate.value,
      endDate: endDate.value,
      performerId: performerFilter.value,
      userId: userIdFilter.value,
    },
  })
  try {
    await auditStore.fetchStats(params)
  } catch {
    // The store records the user-facing error; the report renders it.
  }
}

function resetFilters() {
  startDate.value = ''
  endDate.value = ''
  performerFilter.value = ''
  userIdFilter.value = ''
  entityTypeFilter.value = 'all'
  actionFilter.value = 'all'
  searchFilter.value = ''
}

onMounted(async () => {
  await loadStats()
  // Revealed once the first attempt settles: a failed load must still show the error.
  isLoaded.value = true
})
</script>

<template>
  <div class="relative flex flex-col gap-4 w-full min-w-0">
    <!-- Page header -->
    <div
      :class="[
        'border border-border dark:border-border bg-card',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <h1 class="text-xl font-medium tracking-tight text-foreground">
          {{ t('auditReport.title') }}
        </h1>
      </div>
    </div>

    <!-- Main Content Area -->
    <div
      :class="[
        'mt-6 space-y-6 transition-all duration-500 delay-100',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2',
      ]"
    >
      <!-- Filters -->
      <Card>
        <CardHeader>
          <CardTitle>{{ t('auditReport.filters') }}</CardTitle>
        </CardHeader>
        <CardContent>
          <div class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
            <div class="space-y-2">
              <Label for="audit-report-start-date">{{ t('auditReport.startDate') }}</Label>
              <Input id="audit-report-start-date" v-model="startDate" type="date" />
            </div>
            <div class="space-y-2">
              <Label for="audit-report-end-date">{{ t('auditReport.endDate') }}</Label>
              <Input id="audit-report-end-date" v-model="endDate" type="date" />
            </div>
            <div class="space-y-2">
              <Label for="audit-report-performer">{{ t('auditReport.performer') }}</Label>
              <Input
                id="audit-report-performer"
                v-model="performerFilter"
                :placeholder="t('auditReport.performerPlaceholder')"
              />
            </div>
            <div class="space-y-2">
              <Label for="audit-report-user">{{ t('auditReport.userId') }}</Label>
              <Input
                id="audit-report-user"
                v-model="userIdFilter"
                :placeholder="t('auditReport.userIdPlaceholder')"
              />
            </div>
            <div class="space-y-2">
              <Label for="audit-report-entity">{{ t('audit.filters.entityType') }}</Label>
              <Select v-model="entityTypeFilter">
                <SelectTrigger
                  id="audit-report-entity"
                  class="min-h-8 w-full whitespace-normal text-left data-[size=default]:h-auto *:data-[slot=select-value]:line-clamp-none *:data-[slot=select-value]:[overflow-wrap:anywhere]"
                >
                  <SelectValue>
                    {{
                      entityTypeFilter === 'all'
                        ? t('audit.filters.allEntityTypes')
                        : t(entityTypeToI18nKey(entityTypeFilter))
                    }}
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup
                    ><SelectItem value="all">{{ t('audit.filters.allEntityTypes') }}</SelectItem>
                    <SelectItem
                      v-for="opt in entityTypeOptions"
                      :key="opt.value"
                      :value="opt.value"
                    >
                      {{ opt.label }}
                    </SelectItem></SelectGroup
                  >
                </SelectContent>
              </Select>
            </div>
            <div class="space-y-2">
              <Label for="audit-report-action">{{ t('audit.filters.action') }}</Label>
              <Select v-model="actionFilter" :disabled="entityTypeFilter === 'all'">
                <SelectTrigger
                  id="audit-report-action"
                  class="min-h-8 w-full whitespace-normal text-left data-[size=default]:h-auto *:data-[slot=select-value]:line-clamp-none *:data-[slot=select-value]:[overflow-wrap:anywhere]"
                >
                  <SelectValue>
                    {{
                      entityTypeFilter === 'all'
                        ? t('auditReport.selectEntityTypeFirst')
                        : actionFilter === 'all'
                          ? t('audit.filters.allActions')
                          : t(actionToI18nKey(actionFilter))
                    }}
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup
                    ><SelectItem value="all">{{ t('audit.filters.allActions') }}</SelectItem>
                    <SelectItem v-for="opt in actionOptions" :key="opt.value" :value="opt.value">
                      {{ opt.label }}
                    </SelectItem></SelectGroup
                  >
                </SelectContent>
              </Select>
            </div>
            <div class="space-y-2">
              <Label for="audit-report-search">{{ t('audit.filters.search') }}</Label>
              <Input
                id="audit-report-search"
                v-model="searchFilter"
                :placeholder="t('auditReport.searchPlaceholder')"
              />
            </div>
            <div class="flex items-end gap-2">
              <Button @click="loadStats">
                <CalendarIcon class="mr-2 h-4 w-4" />
                {{ t('auditReport.apply') }}
              </Button>
              <Button variant="outline" @click="resetFilters">
                <RotateCcw class="mr-2 h-4 w-4" />
                {{ t('auditReport.reset') }}
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      <!-- Stats failure keeps the report readable; Apply is the retry path. -->
      <div
        v-if="statsError"
        role="alert"
        class="flex items-center gap-3 border border-status-error-mark bg-[color-mix(in_oklch,_var(--status-error-mark)_8%,_transparent)] p-4"
      >
        <span class="font-mono text-sm text-foreground">&gt; ERROR:</span>
        <span class="text-sm text-foreground">{{ statsError }}</span>
      </div>

      <!-- Total Actions -->
      <Card>
        <CardHeader>
          <CardTitle>{{ t('auditReport.totalActions') }}</CardTitle>
        </CardHeader>
        <CardContent>
          <div class="text-4xl font-bold">
            {{ statsUnavailable ? '—' : (stats?.totalActions ?? 0) }}
          </div>
        </CardContent>
      </Card>

      <!-- Stats Cards Grid -->
      <div class="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
        <!-- Actions by Entity -->
        <Card>
          <CardHeader>
            <CardTitle>{{ t('auditReport.actionsByEntity') }}</CardTitle>
          </CardHeader>
          <CardContent>
            <div v-if="!statsUnavailable && stats?.actionsByEntity?.length" class="space-y-2">
              <div
                v-for="item in stats.actionsByEntity"
                :key="item.entityType"
                class="flex items-center justify-between"
              >
                <span class="text-sm">{{ t(entityTypeToI18nKey(item.entityType)) }}</span>
                <Badge variant="secondary">{{ item.count }}</Badge>
              </div>
            </div>
            <div v-else class="text-muted-foreground text-sm">
              {{ statsUnavailable ? '—' : t('auditReport.noData') }}
            </div>
          </CardContent>
        </Card>

        <!-- Actions by Type -->
        <Card>
          <CardHeader>
            <CardTitle>{{ t('auditReport.actionsByType') }}</CardTitle>
          </CardHeader>
          <CardContent>
            <div v-if="!statsUnavailable && stats?.actionsByType?.length" class="space-y-2">
              <div
                v-for="item in stats.actionsByType"
                :key="item.actionType"
                class="flex items-center justify-between"
              >
                <span class="text-sm">{{ t(actionTypeGroupToI18nKey(item.actionType)) }}</span>
                <Badge variant="secondary">{{ item.count }}</Badge>
              </div>
            </div>
            <div v-else class="text-muted-foreground text-sm">
              {{ statsUnavailable ? '—' : t('auditReport.noData') }}
            </div>
          </CardContent>
        </Card>

        <!-- Top Performers -->
        <Card>
          <CardHeader>
            <CardTitle>{{ t('auditReport.topPerformers') }}</CardTitle>
          </CardHeader>
          <CardContent>
            <div v-if="!statsUnavailable && stats?.topPerformers?.length" class="space-y-2">
              <div
                v-for="item in stats.topPerformers"
                :key="item.performerId"
                class="flex items-center justify-between"
              >
                <span class="text-sm">{{ item.name || item.username }}</span>
                <Badge variant="secondary">{{ item.count }}</Badge>
              </div>
            </div>
            <div v-else class="text-muted-foreground text-sm">
              {{ statsUnavailable ? '—' : t('auditReport.noData') }}
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  </div>
</template>
