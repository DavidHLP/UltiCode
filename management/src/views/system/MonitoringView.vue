<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
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
import { Badge } from '@/components/ui/badge'
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

// Computed
const overallStatus = computed(() => healthStatus.value?.status ?? 'unknown')

const statusBgColor = computed(() => {
  switch (overallStatus.value) {
    case 'healthy':
      return 'bg-green-500/10'
    case 'degraded':
      return 'bg-yellow-500/10'
    case 'unhealthy':
      return 'bg-red-500/10'
    default:
      return 'bg-gray-500/10'
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
  return (resourceUsage.value.memory.heapUsed / resourceUsage.value.memory.heapTotal) * 100
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
      return 'text-green-500'
    case 'degraded':
      return 'text-yellow-500'
    default:
      return 'text-red-500'
  }
}

// Lifecycle
onMounted(() => {
  loadAllStats()

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
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold tracking-tight">{{ t('system.monitoring.title') }}</h1>
        <p class="text-muted-foreground">{{ t('system.monitoring.description') }}</p>
      </div>
      <div class="flex items-center gap-2">
        <Button variant="outline" :disabled="refreshing" @click="refresh">
          <IconLoader2 v-if="refreshing" class="h-4 w-4 mr-1 animate-spin" />
          <IconRefresh v-else class="h-4 w-4 mr-1" />
          {{ t('common.refresh') }}
        </Button>
      </div>
    </div>

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
            <Badge
              :variant="overallStatus === 'healthy' ? 'default' : 'destructive'"
              class="text-lg px-4 py-2"
            >
              {{ t(`system.monitoring.status.${overallStatus}`) }}
            </Badge>
            <span class="text-sm text-muted-foreground">
              {{ t('system.monitoring.lastChecked') }}:
              {{ healthStatus ? new Date(healthStatus.timestamp).toLocaleString() : '-' }}
            </span>
          </div>

          <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div
              v-for="check in healthStatus?.checks"
              :key="check.service"
              class="flex items-center gap-2 p-3 rounded-lg bg-muted/50"
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
                <span class="text-muted-foreground">{{ t('system.monitoring.nodeVersion') }}</span>
                <span class="font-mono">{{ systemInfo?.nodeVersion }}</span>
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
                <span class="text-muted-foreground">{{ t('system.monitoring.environment') }}</span>
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
                  <span class="text-muted-foreground">{{ t('system.monitoring.heapTotal') }}:</span>
                  <span class="ml-1 font-mono">
                    {{ resourceUsage ? formatBytes(resourceUsage.memory.heapTotal) : '-' }}
                  </span>
                </div>
                <div>
                  <span class="text-muted-foreground">{{ t('system.monitoring.rss') }}:</span>
                  <span class="ml-1 font-mono">
                    {{ resourceUsage ? formatBytes(resourceUsage.memory.rss) : '-' }}
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
                <span class="text-muted-foreground">{{ t('system.monitoring.activeConnections') }}</span>
                <span>{{ databaseStats?.activeConnections ?? 0 }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">{{ t('system.monitoring.maxConnections') }}</span>
                <span>{{ databaseStats?.maxConnections ?? 0 }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">{{ t('system.monitoring.queryCount') }}</span>
                <span>{{ databaseStats?.queryCount ?? 0 }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">{{ t('system.monitoring.slowQueries') }}</span>
                <span :class="{ 'text-red-500': (databaseStats?.slowQueries ?? 0) > 0 }">
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
                <span class="text-muted-foreground">{{ t('system.monitoring.status.status') }}</span>
                <Badge :variant="redisStats?.connected ? 'default' : 'destructive'">
                  {{
                    redisStats?.connected ? t('system.monitoring.connected') : t('system.monitoring.disconnected')
                  }}
                </Badge>
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
                  <Badge v-if="queue.paused" variant="secondary">
                    {{ t('system.monitoring.paused') }}
                  </Badge>
                </div>
                <div class="grid grid-cols-5 gap-2 text-xs">
                  <div class="text-center p-2 bg-muted rounded">
                    <div class="text-lg font-bold text-blue-500">{{ queue.waiting }}</div>
                    <div class="text-muted-foreground">{{ t('system.monitoring.waiting') }}</div>
                  </div>
                  <div class="text-center p-2 bg-muted rounded">
                    <div class="text-lg font-bold text-yellow-500">{{ queue.active }}</div>
                    <div class="text-muted-foreground">{{ t('system.monitoring.active') }}</div>
                  </div>
                  <div class="text-center p-2 bg-muted rounded">
                    <div class="text-lg font-bold text-green-500">{{ queue.completed }}</div>
                    <div class="text-muted-foreground">{{ t('system.monitoring.completed') }}</div>
                  </div>
                  <div class="text-center p-2 bg-muted rounded">
                    <div class="text-lg font-bold text-red-500">{{ queue.failed }}</div>
                    <div class="text-muted-foreground">{{ t('system.monitoring.failed') }}</div>
                  </div>
                  <div class="text-center p-2 bg-muted rounded">
                    <div class="text-lg font-bold text-purple-500">{{ queue.delayed }}</div>
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
</template>
