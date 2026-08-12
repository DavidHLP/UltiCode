<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateTimeByLocale } from '@/i18n/utils'
import { monitoringApi } from '@/api/admin/monitoring'
import type {
  SystemInfo,
  ResourceUsage,
  DatabaseStats,
  QueueStats,
  RedisStats,
  SystemHealth,
} from '@/api/admin/monitoring'
import { Button } from '@/components/ui/button'
import { SemanticBadge } from '@/components/ui/terminal'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  IconRefresh,
  IconServer,
  IconDatabase,
  IconBrain,
  IconListTree,
  IconHeartbeat,
  IconLoader2,
  IconAlertTriangle,
  IconCheck,
  IconX,
} from '@tabler/icons-vue'

const { t } = useI18n()

// State
const loading = ref(true)
const refreshing = ref(false)
const autoRefresh = ref(true)
const refreshInterval = ref<number | null>(null)

const systemInfo = ref<SystemInfo | null>(null)
const resourceUsage = ref<ResourceUsage | null>(null)
const databaseStats = ref<DatabaseStats | null>(null)
const queueStats = ref<QueueStats[]>([])
const redisStats = ref<RedisStats | null>(null)
const healthStatus = ref<SystemHealth | null>(null)
const isLoaded = ref(false)

// Computed
const overallStatus = computed(() => healthStatus.value?.status ?? 'unknown')

const statusBgColor = computed(() => {
  switch (overallStatus.value) {
    case 'healthy':
      return 'bg-status-success-surface'
    case 'degraded':
      return 'bg-status-warning-surface'
    case 'unhealthy':
      return 'bg-status-error-surface'
    default:
      return 'bg-muted'
  }
})

// Methods
async function loadAllStats() {
  try {
    const stats = await monitoringApi.getAllStats()
    systemInfo.value = stats.system
    resourceUsage.value = stats.resources
    databaseStats.value = stats.database
    queueStats.value = stats.queues
    redisStats.value = stats.redis
    healthStatus.value = stats.health
  } catch (error) {
    console.error('Failed to load monitoring stats:', error)
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

async function refresh() {
  refreshing.value = true
  await loadAllStats()
}

function formatUptime(seconds: number): string {
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)

  const parts: string[] = []
  if (days > 0) parts.push(`${days}d`)
  if (hours > 0) parts.push(`${hours}h`)
  if (minutes > 0) parts.push(`${minutes}m`)

  return parts.join(' ') || '< 1m'
}

function formatBytes(bytes: number): string {
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let unitIndex = 0
  let value = bytes

  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024
    unitIndex++
  }

  return `${value.toFixed(1)} ${units[unitIndex]}`
}

function getMemoryPercent(): number {
  if (!resourceUsage.value) return 0
  return (resourceUsage.value.memory.heapUsed / resourceUsage.value.memory.heapMax) * 100
}

function getHealthIcon(status: string) {
  switch (status) {
    case 'healthy':
      return IconCheck
    case 'degraded':
      return IconAlertTriangle
    default:
      return IconX
  }
}

function getHealthColor(status: string): string {
  switch (status) {
    case 'healthy':
      return 'text-foreground-strong'
    case 'degraded':
      return 'text-foreground-strong'
    default:
      return 'text-foreground-strong'
  }
}

// Lifecycle
onMounted(async () => {
  await loadAllStats()
  isLoaded.value = true

  // Set up auto-refresh every 30 seconds
  if (autoRefresh.value) {
    refreshInterval.value = window.setInterval(() => {
      if (autoRefresh.value) {
        loadAllStats()
      }
    }, 30000)
  }
})

onUnmounted(() => {
  if (refreshInterval.value) {
    clearInterval(refreshInterval.value)
  }
})
</script>

<template>
  <div class="relative flex flex-col gap-4 w-full min-w-0">
    <!-- Terminal Header -->
    <div
      :class="[
        'border border-[var(--border-subtle)] dark:border-[var(--border-subtle)] bg-[var(--card)]',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <div class="space-y-1">
          <h1 class="text-xl font-medium tracking-tight text-[var(--foreground)]">
            {{ t('system.monitoring.title') }}
          </h1>
          <p class="text-xs text-[var(--foreground-muted)]">{{ t('system.monitoring.description') }}</p>
        </div>
        <div class="flex items-center gap-2">
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--border-subtle)] hover:border-[var(--primary)] hover:text-[var(--primary)] transition-colors"
            :disabled="refreshing"
            @click="refresh"
          >
            <IconLoader2 v-if="refreshing" class="h-3.5 w-3.5 mr-1.5 animate-spin" />
            <IconRefresh v-else class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('common.refresh') }}</span>
          </Button>
        </div>
      </div>
    </div>

    <!-- Main Content Area -->
    <div
      :class="[
        'mt-6 space-y-6 transition-all duration-500 delay-100',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2',
      ]"
    >
      <!-- Loading state -->
      <div v-if="loading" class="flex items-center justify-center py-12">
        <IconLoader2 class="h-8 w-8 animate-spin text-muted-foreground" />
      </div>

      <!-- Main content -->
      <template v-else>
        <!-- Overall Health Status -->
        <Card :class="['border-2', statusBgColor]">
          <CardHeader>
            <CardTitle class="flex items-center gap-2">
              <IconHeartbeat class="h-5 w-5" />
              {{ t('system.monitoring.healthStatus') }}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div class="flex items-center gap-4 mb-4">
              <SemanticBadge
                :color="overallStatus === 'healthy' ? 'success' : 'error'"
                :label="t(`system.monitoring.status.${overallStatus}`, overallStatus)"
                class="text-lg px-4 py-2"
              />
              <span class="text-sm text-muted-foreground">
                {{ t('system.monitoring.lastChecked') }}:
                {{ healthStatus ? formatDateTimeByLocale(healthStatus.timestamp) : '-' }}
              </span>
            </div>

            <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
              <div
                v-for="check in healthStatus?.checks"
                :key="check.service"
                class="flex items-center gap-2 p-3 rounded-none bg-muted/50"
              >
                <component
                  :is="getHealthIcon(check.status)"
                  :class="['h-5 w-5', getHealthColor(check.status)]"
                />
                <div>
                  <p class="font-medium capitalize">{{ check.service }}</p>
                  <p class="text-xs text-muted-foreground">{{ check.message }}</p>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>

        <!-- Stats Grid -->
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          <!-- System Info -->
          <Card>
            <CardHeader class="pb-2">
              <CardTitle class="text-sm font-medium flex items-center gap-2">
                <IconServer class="h-4 w-4" />
                {{ t('system.monitoring.systemInfo') }}
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div class="space-y-2 text-sm">
                <div class="flex justify-between">
                  <span class="text-muted-foreground">{{
                    t('system.monitoring.javaVersion')
                  }}</span>
                  <span class="font-mono">{{ systemInfo?.javaVersion }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-muted-foreground">{{ t('system.monitoring.platform') }}</span>
                  <span>{{ systemInfo?.platform }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-muted-foreground">{{ t('system.monitoring.uptime') }}</span>
                  <span>{{ systemInfo ? formatUptime(systemInfo.uptime) : '-' }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-muted-foreground">{{
                    t('system.monitoring.environment')
                  }}</span>
                  <span>{{ systemInfo?.env }}</span>
                </div>
              </div>
            </CardContent>
          </Card>

          <!-- Memory Usage -->
          <Card>
            <CardHeader class="pb-2">
              <CardTitle class="text-sm font-medium flex items-center gap-2">
                <IconBrain class="h-4 w-4" />
                {{ t('system.monitoring.memoryUsage') }}
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div class="space-y-3">
                <div>
                  <div class="flex justify-between text-sm mb-1">
                    <span>{{ t('system.monitoring.heapUsed') }}</span>
                    <span>{{ getMemoryPercent().toFixed(1) }}%</span>
                  </div>
                  <div class="h-2 bg-muted rounded-full overflow-hidden">
                    <div
                      class="h-full bg-primary transition-all"
                      :style="{ width: `${getMemoryPercent()}%` }"
                    />
                  </div>
                </div>
                <div class="grid grid-cols-2 gap-2 text-xs">
                  <div>
                    <span class="text-muted-foreground">{{ t('system.monitoring.heapMax') }}:</span>
                    <span class="ml-1 font-mono">
                      {{ resourceUsage ? formatBytes(resourceUsage.memory.heapMax) : '-' }}
                    </span>
                  </div>
                  <div>
                    <span class="text-muted-foreground"
                      >{{ t('system.monitoring.nonHeapUsed') }}:</span
                    >
                    <span class="ml-1 font-mono">
                      {{ resourceUsage ? formatBytes(resourceUsage.memory.nonHeapUsed) : '-' }}
                    </span>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>

          <!-- Database -->
          <Card>
            <CardHeader class="pb-2">
              <CardTitle class="text-sm font-medium flex items-center gap-2">
                <IconDatabase class="h-4 w-4" />
                {{ t('system.monitoring.database') }}
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div class="space-y-2 text-sm">
                <div class="flex justify-between">
                  <span class="text-muted-foreground">{{
                    t('system.monitoring.activeConnections')
                  }}</span>
                  <span>{{ databaseStats?.activeConnections ?? 0 }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-muted-foreground">{{
                    t('system.monitoring.maxConnections')
                  }}</span>
                  <span>{{ databaseStats?.maxConnections ?? 0 }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-muted-foreground">{{ t('system.monitoring.queryCount') }}</span>
                  <span>{{ databaseStats?.queryCount ?? 0 }}</span>
                </div>
                <div class="flex justify-between">
                  <span class="text-muted-foreground">{{
                    t('system.monitoring.slowQueries')
                  }}</span>
                  <span :class="{ 'text-foreground-strong': (databaseStats?.slowQueries ?? 0) > 0 }">
                    {{ databaseStats?.slowQueries ?? 0 }}
                  </span>
                </div>
              </div>
            </CardContent>
          </Card>

          <!-- Redis -->
          <Card>
            <CardHeader class="pb-2">
              <CardTitle class="text-sm font-medium flex items-center gap-2">
                <IconServer class="h-4 w-4" />
                Redis
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div class="space-y-2 text-sm">
                <div class="flex justify-between items-center">
                  <span class="text-muted-foreground">{{
                    t('system.monitoring.status.status')
                  }}</span>
                  <SemanticBadge
                    :color="redisStats?.connected ? 'success' : 'error'"
                    :label="
                      redisStats?.connected
                        ? t('system.monitoring.connected')
                        : t('system.monitoring.disconnected')
                    "
                  />
                </div>
                <div v-if="redisStats?.version" class="flex justify-between">
                  <span class="text-muted-foreground">{{ t('system.monitoring.version') }}</span>
                  <span>{{ redisStats.version }}</span>
                </div>
                <div v-if="redisStats?.usedMemory" class="flex justify-between">
                  <span class="text-muted-foreground">{{ t('system.monitoring.usedMemory') }}</span>
                  <span>{{ formatBytes(redisStats.usedMemory) }}</span>
                </div>
              </div>
            </CardContent>
          </Card>

          <!-- Queues -->
          <Card class="md:col-span-2">
            <CardHeader class="pb-2">
              <CardTitle class="text-sm font-medium flex items-center gap-2">
                <IconListTree class="h-4 w-4" />
                {{ t('system.monitoring.queues') }}
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div v-if="queueStats.length === 0" class="text-sm text-muted-foreground">
                {{ t('system.monitoring.noQueues') }}
              </div>
              <div v-else class="space-y-4">
                <div v-for="queue in queueStats" :key="queue.name" class="space-y-2">
                  <div class="flex items-center justify-between">
                    <span class="font-medium">{{ queue.name }}</span>
                  </div>
                  <div class="grid grid-cols-5 gap-2 text-xs">
                    <div class="text-center p-2 bg-muted rounded-none">
                      <div class="text-lg font-bold text-foreground-strong">{{ queue.waiting }}</div>
                      <div class="text-muted-foreground">{{ t('system.monitoring.waiting') }}</div>
                    </div>
                    <div class="text-center p-2 bg-muted rounded-none">
                      <div class="text-lg font-bold text-foreground-strong">{{ queue.active }}</div>
                      <div class="text-muted-foreground">{{ t('system.monitoring.active') }}</div>
                    </div>
                    <div class="text-center p-2 bg-muted rounded-none">
                      <div class="text-lg font-bold text-foreground-strong">{{ queue.completed }}</div>
                      <div class="text-muted-foreground">
                        {{ t('system.monitoring.completed') }}
                      </div>
                    </div>
                    <div class="text-center p-2 bg-muted rounded-none">
                      <div class="text-lg font-bold text-foreground-strong">{{ queue.failed }}</div>
                      <div class="text-muted-foreground">{{ t('system.monitoring.failed') }}</div>
                    </div>
                    <div class="text-center p-2 bg-muted rounded-none">
                      <div class="text-lg font-bold text-foreground-strong">{{ queue.delayed }}</div>
                      <div class="text-muted-foreground">{{ t('system.monitoring.delayed') }}</div>
                    </div>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      </template>
    </div>
  </div>
</template>
