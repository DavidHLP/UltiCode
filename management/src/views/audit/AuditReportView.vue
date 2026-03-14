<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  IconChartBar,
  IconUsers,
  IconDatabase,
  IconDownload,
  IconFilter,
  IconLoader2,
} from '@tabler/icons-vue'
import { auditApi, type AuditStats } from '@/api/admin/audit'

const { t } = useI18n()

const stats = ref<AuditStats | null>(null)
const loading = ref(false)
const startDate = ref('')
const endDate = ref('')
const performerFilter = ref('')

// Animation state for staggered reveal
const isLoaded = ref(false)

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
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
})
</script>

<template>
  <div class="relative flex flex-col gap-0 overflow-auto">
    <!-- Terminal Header -->
    <div
      :class="[
        'border-b border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)]',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <div class="flex items-center gap-4">
          <div class="flex items-center gap-2">
            <span class="terminal-prompt text-base">audit-report</span>
            <span class="terminal-cursor" />
          </div>
          <h1 class="text-xl font-medium tracking-tight text-[var(--foreground)]">
            {{ t('auditReport.title') }}
          </h1>
        </div>
      </div>

      <!-- Description Ticker -->
      <div
        class="px-4 lg:px-6 py-2.5 flex items-center gap-6 border-t border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-2 text-[var(--silver-400)]">
          <IconChartBar class="h-4 w-4" />
          <span class="text-xs font-data uppercase tracking-wider">{{
            t('auditReport.description')
          }}</span>
        </div>
      </div>
    </div>

    <!-- Main Content Area -->
    <div class="flex-1 py-4 space-y-6">
      <!-- Filters - Terminal Style -->
      <div
        class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)]"
      >
        <div
          class="px-4 py-3 border-b border-[var(--silver-200)] dark:border-[var(--silver-300)] flex items-center gap-2"
        >
          <IconFilter class="h-4 w-4 text-[var(--terminal-cyan)]" />
          <span class="font-data text-sm uppercase tracking-wider">
            {{ t('auditReport.filters') }}
          </span>
        </div>
        <div class="p-4">
          <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div class="space-y-2">
              <Label for="startDate" class="terminal-label text-xs uppercase tracking-wider">{{
                t('auditReport.startDate')
              }}</Label>
              <Input
                id="startDate"
                v-model="startDate"
                type="date"
                class="terminal-input font-data text-sm"
              />
            </div>
            <div class="space-y-2">
              <Label for="endDate" class="terminal-label text-xs uppercase tracking-wider">{{
                t('auditReport.endDate')
              }}</Label>
              <Input
                id="endDate"
                v-model="endDate"
                type="date"
                class="terminal-input font-data text-sm"
              />
            </div>
            <div class="space-y-2">
              <Label for="performer" class="terminal-label text-xs uppercase tracking-wider">{{
                t('auditReport.performer')
              }}</Label>
              <Input
                id="performer"
                v-model="performerFilter"
                :placeholder="t('auditReport.performerPlaceholder')"
                class="terminal-input font-data text-sm"
              />
            </div>
          </div>
          <div class="flex gap-2 mt-4">
            <Button
              variant="terminal"
              size="sm"
              class="font-data text-xs border-[var(--terminal-cyan)] text-[var(--terminal-cyan)] hover:bg-[oklch(0.65_0.15_200/0.1)]"
              @click="loadStats"
              :disabled="loading"
            >
              <IconFilter class="h-4 w-4 mr-1.5" />
              <span class="uppercase tracking-wider">{{ t('auditReport.applyFilters') }}</span>
            </Button>
            <Button
              variant="terminal"
              size="sm"
              class="font-data text-xs border-[var(--silver-300)] hover:border-[var(--terminal-green)] hover:text-[var(--terminal-green)]"
              @click="exportReport"
            >
              <IconDownload class="h-4 w-4 mr-1.5" />
              <span class="uppercase tracking-wider">{{ t('auditReport.export') }}</span>
            </Button>
          </div>
        </div>
      </div>

      <!-- Loading State -->
      <div v-if="loading" class="flex items-center justify-center py-12">
        <div class="flex items-center gap-3 text-[var(--silver-500)]">
          <IconLoader2 class="h-5 w-5 animate-spin" />
          <span class="font-data text-sm">{{ t('common.loading') }}</span>
        </div>
      </div>

      <!-- Stats Overview - Terminal Style -->
      <div v-else-if="stats" class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <!-- Total Actions -->
        <div
          class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)] p-4"
        >
          <div class="terminal-label text-xs uppercase tracking-wider text-[var(--silver-500)]">
            {{ t('auditReport.totalActions') }}
          </div>
          <div class="font-data text-3xl text-[var(--terminal-cyan)] tabular-nums mt-2">
            {{ stats.totalActions }}
          </div>
          <div class="text-xs text-[var(--silver-400)] mt-2 flex items-center gap-1">
            <IconChartBar class="h-3 w-3" />
            <span class="font-data">{{ t('auditReport.allTime') }}</span>
          </div>
        </div>

        <!-- Unique Entities -->
        <div
          class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)] p-4"
        >
          <div class="terminal-label text-xs uppercase tracking-wider text-[var(--silver-500)]">
            {{ t('auditReport.uniqueEntities') }}
          </div>
          <div class="font-data text-3xl text-[var(--terminal-green)] tabular-nums mt-2">
            {{ stats.actionsByEntity.length }}
          </div>
          <div class="text-xs text-[var(--silver-400)] mt-2 flex items-center gap-1">
            <IconDatabase class="h-3 w-3" />
            <span class="font-data">{{ t('auditReport.entityTypes') }}</span>
          </div>
        </div>

        <!-- Active Performers -->
        <div
          class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)] p-4"
        >
          <div class="terminal-label text-xs uppercase tracking-wider text-[var(--silver-500)]">
            {{ t('auditReport.activePerformers') }}
          </div>
          <div class="font-data text-3xl text-[var(--terminal-amber)] tabular-nums mt-2">
            {{ stats.topPerformers.length }}
          </div>
          <div class="text-xs text-[var(--silver-400)] mt-2 flex items-center gap-1">
            <IconUsers class="h-3 w-3" />
            <span class="font-data">{{ t('auditReport.users') }}</span>
          </div>
        </div>
      </div>

      <!-- Top Performers - Terminal Style -->
      <div
        v-if="stats && topPerformers.length > 0"
        class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)]"
      >
        <div
          class="px-4 py-3 border-b border-[var(--silver-200)] dark:border-[var(--silver-300)] flex items-center gap-2"
        >
          <IconUsers class="h-4 w-4 text-[var(--terminal-cyan)]" />
          <span class="font-data text-sm uppercase tracking-wider">
            {{ t('auditReport.topPerformers') }}
          </span>
        </div>
        <div class="p-4 space-y-3">
          <div
            v-for="(item, index) in topPerformers"
            :key="item.performer.id"
            class="flex items-center justify-between p-3 border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
          >
            <div class="flex items-center gap-3">
              <div
                class="flex items-center justify-center w-8 h-8 border border-[var(--terminal-cyan)] text-[var(--terminal-cyan)] font-data font-bold text-sm"
              >
                {{ index + 1 }}
              </div>
              <div>
                <p class="font-medium font-data">
                  {{ item.performer.name || item.performer.username }}
                </p>
                <p class="text-xs text-[var(--silver-500)]">{{ item.performer.role }}</p>
              </div>
            </div>
            <div class="text-right">
              <p class="font-data text-2xl text-[var(--terminal-cyan)] tabular-nums">
                {{ item.count }}
              </p>
              <p class="text-xs text-[var(--silver-500)]">{{ t('auditReport.actions') }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Actions by Entity - Terminal Style -->
      <div
        v-if="stats && actionsByEntity.length > 0"
        class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)]"
      >
        <div
          class="px-4 py-3 border-b border-[var(--silver-200)] dark:border-[var(--silver-300)] flex items-center gap-2"
        >
          <IconDatabase class="h-4 w-4 text-[var(--terminal-green)]" />
          <span class="font-data text-sm uppercase tracking-wider">
            {{ t('auditReport.actionsByEntity') }}
          </span>
        </div>
        <div class="p-4 space-y-3">
          <div
            v-for="item in actionsByEntity"
            :key="item.entityType"
            class="flex items-center justify-between p-3 border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
          >
            <div class="flex items-center gap-3">
              <div
                class="flex items-center justify-center w-8 h-8 border border-[var(--terminal-green)] text-[var(--terminal-green)]"
              >
                <IconDatabase class="h-4 w-4" />
              </div>
              <p class="font-medium font-data">{{ item.entityType }}</p>
            </div>
            <div class="text-right">
              <p class="font-data text-2xl text-[var(--terminal-green)] tabular-nums">
                {{ item.count }}
              </p>
              <p class="text-xs text-[var(--silver-500)]">{{ t('auditReport.actions') }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
