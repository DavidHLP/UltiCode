<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { CalendarIcon, RotateCcw } from 'lucide-vue-next'
import { useAuditStore } from '@/stores/admin/audit'
import { normalizeDateParams } from '@/api/admin/audit'
import {
  AUDIT_ENTITY_TYPES,
  AUDIT_ACTIONS_BY_ENTITY,
  actionToI18nKey,
  entityTypeToI18nKey,
} from './utils'

const { t } = useI18n()
const auditStore = useAuditStore()

const startDate = ref('')
const endDate = ref('')
const performerFilter = ref('')
const userIdFilter = ref('')
const entityTypeFilter = ref<string>('')
const actionFilter = ref<string>('')
const searchFilter = ref('')

const entityTypeOptions = computed(() =>
  AUDIT_ENTITY_TYPES.map((type) => ({
    value: type,
    label: t(entityTypeToI18nKey(type)),
  })),
)

const actionOptions = computed(() => {
  if (!entityTypeFilter.value) return []
  const actions = AUDIT_ACTIONS_BY_ENTITY[entityTypeFilter.value] || []
  return actions.map((action) => ({
    value: action,
    label: t(actionToI18nKey(action)),
  }))
})

watch(entityTypeFilter, () => {
  actionFilter.value = ''
})

const stats = computed(() => auditStore.stats)

async function loadStats() {
  const params = normalizeDateParams({
    startDate: startDate.value || undefined,
    endDate: endDate.value || undefined,
    performerId: performerFilter.value || undefined,
    userId: userIdFilter.value || undefined,
    entityType: entityTypeFilter.value || undefined,
    action: actionFilter.value || undefined,
    search: searchFilter.value || undefined,
  })
  await auditStore.fetchStats(params)
}

function resetFilters() {
  startDate.value = ''
  endDate.value = ''
  performerFilter.value = ''
  userIdFilter.value = ''
  entityTypeFilter.value = ''
  actionFilter.value = ''
  searchFilter.value = ''
}

onMounted(() => {
  loadStats()
})
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-bold">{{ t('auditReport.title') }}</h1>
    </div>

    <!-- Filters -->
    <Card>
      <CardHeader>
        <CardTitle>{{ t('auditReport.filters') }}</CardTitle>
      </CardHeader>
      <CardContent>
        <div class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
          <div class="space-y-2">
            <Label>{{ t('auditReport.startDate') }}</Label>
            <Input v-model="startDate" type="date" />
          </div>
          <div class="space-y-2">
            <Label>{{ t('auditReport.endDate') }}</Label>
            <Input v-model="endDate" type="date" />
          </div>
          <div class="space-y-2">
            <Label>{{ t('auditReport.performerId') }}</Label>
            <Input v-model="performerFilter" :placeholder="t('auditReport.performerPlaceholder')" />
          </div>
          <div class="space-y-2">
            <Label>{{ t('auditReport.userId') }}</Label>
            <Input v-model="userIdFilter" :placeholder="t('auditReport.userIdPlaceholder')" />
          </div>
          <div class="space-y-2">
            <Label>{{ t('audit.filters.entityType') }}</Label>
            <Select v-model="entityTypeFilter">
              <SelectTrigger>
                <SelectValue :placeholder="t('audit.filters.allEntityTypes')" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__all__">{{ t('audit.filters.allEntityTypes') }}</SelectItem>
                <SelectItem v-for="opt in entityTypeOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div class="space-y-2">
            <Label>{{ t('audit.filters.action') }}</Label>
            <Select v-model="actionFilter" :disabled="!entityTypeFilter">
              <SelectTrigger>
                <SelectValue
                  :placeholder="
                    entityTypeFilter
                      ? t('audit.filters.allActions')
                      : t('auditReport.selectEntityTypeFirst')
                  "
                />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__all__">{{ t('audit.filters.allActions') }}</SelectItem>
                <SelectItem v-for="opt in actionOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div class="space-y-2">
            <Label>{{ t('audit.filters.search') }}</Label>
            <Input v-model="searchFilter" :placeholder="t('auditReport.searchPlaceholder')" />
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

    <!-- Total Actions -->
    <Card>
      <CardHeader>
        <CardTitle>{{ t('auditReport.totalActions') }}</CardTitle>
      </CardHeader>
      <CardContent>
        <div class="text-4xl font-bold">{{ stats?.totalActions ?? 0 }}</div>
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
          <div v-if="stats?.actionsByEntity?.length" class="space-y-2">
            <div
              v-for="item in stats.actionsByEntity"
              :key="item.entityType"
              class="flex items-center justify-between"
            >
              <span class="text-sm">{{ t(entityTypeToI18nKey(item.entityType)) }}</span>
              <Badge variant="secondary">{{ item.count }}</Badge>
            </div>
          </div>
          <div v-else class="text-muted-foreground text-sm">{{ t('auditReport.noData') }}</div>
        </CardContent>
      </Card>

      <!-- Actions by Type -->
      <Card>
        <CardHeader>
          <CardTitle>{{ t('auditReport.actionsByType') }}</CardTitle>
        </CardHeader>
        <CardContent>
          <div v-if="stats?.actionsByType?.length" class="space-y-2">
            <div
              v-for="item in stats.actionsByType"
              :key="item.actionType"
              class="flex items-center justify-between"
            >
              <span class="text-sm">{{ t(`audit.actionTypeGroups.${item.actionType}`) }}</span>
              <Badge variant="secondary">{{ item.count }}</Badge>
            </div>
          </div>
          <div v-else class="text-muted-foreground text-sm">{{ t('auditReport.noData') }}</div>
        </CardContent>
      </Card>

      <!-- Top Performers -->
      <Card>
        <CardHeader>
          <CardTitle>{{ t('auditReport.topPerformers') }}</CardTitle>
        </CardHeader>
        <CardContent>
          <div v-if="stats?.topPerformers?.length" class="space-y-2">
            <div
              v-for="item in stats.topPerformers"
              :key="item.performerId"
              class="flex items-center justify-between"
            >
              <span class="text-sm">{{ item.name || item.username }}</span>
              <Badge variant="secondary">{{ item.count }}</Badge>
            </div>
          </div>
          <div v-else class="text-muted-foreground text-sm">{{ t('auditReport.noData') }}</div>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
