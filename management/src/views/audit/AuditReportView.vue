<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { IconChartBar, IconUsers, IconDatabase, IconDownload, IconFilter } from '@tabler/icons-vue'
import { auditApi, type AuditStats } from '@/api/admin/audit'

const { t } = useI18n()

const stats = ref<AuditStats | null>(null)
const loading = ref(false)
const startDate = ref('')
const endDate = ref('')
const performerFilter = ref('')

async function loadStats() {
  loading.value = true
  try {
    stats.value = await auditApi.getAuditStats({
      startDate: startDate.value || undefined,
      endDate: endDate.value || undefined,
      performerId: performerFilter.value || undefined,
    })
  } catch (error) {
    console.error('Failed to load audit stats:', error)
  } finally {
    loading.value = false
  }
}

async function exportReport() {
  try {
    await auditApi.exportAuditLogs({
      startDate: startDate.value || undefined,
      endDate: endDate.value || undefined,
      performerId: performerFilter.value || undefined,
      format: 'csv',
    })
  } catch (error) {
    console.error('Failed to export report:', error)
  }
}

const topPerformers = computed(() => {
  if (!stats.value) return []
  return stats.value.actionsByPerformer.slice(0, 5).map((item) => ({
    ...item,
    performer: {
      id: item.performerId,
      username: item.performerId,
      name: item.performerId,
      role: 'USER',
    },
  }))
})

const actionsByEntity = computed(() => {
  if (!stats.value) return []
  return stats.value.actionsByEntity.slice(0, 5)
})

onMounted(() => {
  loadStats()
})
</script>

<template>
  <div class="space-y-6">
    <div>
      <h1 class="text-3xl font-bold tracking-tight">
        {{ t('auditReport.title') }}
      </h1>
      <p class="text-muted-foreground mt-1">
        {{ t('auditReport.description') }}
      </p>
    </div>

    <!-- Filters -->
    <Card>
      <CardHeader>
        <CardTitle class="flex items-center gap-2">
          <IconFilter class="h-5 w-5" />
          {{ t('auditReport.filters') }}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div>
            <Label for="startDate">{{ t('auditReport.startDate') }}</Label>
            <Input id="startDate" v-model="startDate" type="date" class="mt-1" />
          </div>
          <div>
            <Label for="endDate">{{ t('auditReport.endDate') }}</Label>
            <Input id="endDate" v-model="endDate" type="date" class="mt-1" />
          </div>
          <div>
            <Label for="performer">{{ t('auditReport.performer') }}</Label>
            <Input
              id="performer"
              v-model="performerFilter"
              :placeholder="t('auditReport.performerPlaceholder')"
              class="mt-1"
            />
          </div>
        </div>
        <div class="flex gap-2 mt-4">
          <Button @click="loadStats" :disabled="loading">
            <IconFilter class="h-4 w-4 mr-1" />
            {{ t('auditReport.applyFilters') }}
          </Button>
          <Button variant="outline" @click="exportReport">
            <IconDownload class="h-4 w-4 mr-1" />
            {{ t('auditReport.export') }}
          </Button>
        </div>
      </CardContent>
    </Card>

    <!-- Stats Overview -->
    <div v-if="loading" class="flex items-center justify-center py-12">
      <div class="text-muted-foreground">{{ t('common.loading') }}</div>
    </div>

    <div v-else-if="stats" class="grid grid-cols-1 md:grid-cols-3 gap-4">
      <Card>
        <CardHeader class="pb-3">
          <CardDescription>{{ t('auditReport.totalActions') }}</CardDescription>
          <CardTitle class="text-3xl">{{ stats.totalActions }}</CardTitle>
        </CardHeader>
        <CardContent>
          <div class="flex items-center text-sm text-muted-foreground">
            <IconChartBar class="h-4 w-4 mr-1" />
            {{ t('auditReport.allTime') }}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader class="pb-3">
          <CardDescription>{{ t('auditReport.uniqueEntities') }}</CardDescription>
          <CardTitle class="text-3xl">{{ stats.actionsByEntity.length }}</CardTitle>
        </CardHeader>
        <CardContent>
          <div class="flex items-center text-sm text-muted-foreground">
            <IconDatabase class="h-4 w-4 mr-1" />
            {{ t('auditReport.entityTypes') }}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader class="pb-3">
          <CardDescription>{{ t('auditReport.activePerformers') }}</CardDescription>
          <CardTitle class="text-3xl">{{ stats.topPerformers.length }}</CardTitle>
        </CardHeader>
        <CardContent>
          <div class="flex items-center text-sm text-muted-foreground">
            <IconUsers class="h-4 w-4 mr-1" />
            {{ t('auditReport.users') }}
          </div>
        </CardContent>
      </Card>
    </div>

    <!-- Top Performers -->
    <Card v-if="stats && topPerformers.length > 0">
      <CardHeader>
        <CardTitle class="flex items-center gap-2">
          <IconUsers class="h-5 w-5" />
          {{ t('auditReport.topPerformers') }}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div class="space-y-3">
          <div
            v-for="(item, index) in topPerformers"
            :key="item.performer.id"
            class="flex items-center justify-between p-3 bg-muted rounded-lg"
          >
            <div class="flex items-center gap-3">
              <div
                class="flex items-center justify-center w-8 h-8 bg-primary text-primary-foreground rounded-full text-sm font-bold"
              >
                {{ index + 1 }}
              </div>
              <div>
                <p class="font-medium">{{ item.performer.name || item.performer.username }}</p>
                <p class="text-sm text-muted-foreground">{{ item.performer.role }}</p>
              </div>
            </div>
            <div class="text-right">
              <p class="text-2xl font-bold">{{ item.count }}</p>
              <p class="text-sm text-muted-foreground">{{ t('auditReport.actions') }}</p>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>

    <!-- Actions by Entity -->
    <Card v-if="stats && actionsByEntity.length > 0">
      <CardHeader>
        <CardTitle class="flex items-center gap-2">
          <IconDatabase class="h-5 w-5" />
          {{ t('auditReport.actionsByEntity') }}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div class="space-y-3">
          <div
            v-for="item in actionsByEntity"
            :key="item.entityType"
            class="flex items-center justify-between p-3 bg-muted rounded-lg"
          >
            <div class="flex items-center gap-3">
              <div
                class="flex items-center justify-center w-8 h-8 bg-primary text-primary-foreground rounded-lg"
              >
                <IconDatabase class="h-4 w-4" />
              </div>
              <p class="font-medium">{{ item.entityType }}</p>
            </div>
            <div class="text-right">
              <p class="text-2xl font-bold">{{ item.count }}</p>
              <p class="text-sm text-muted-foreground">{{ t('auditReport.actions') }}</p>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
