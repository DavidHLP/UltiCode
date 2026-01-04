<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useAuthStore } from '@/stores/admin/auth'
import { useDashboardStore } from '@/stores/admin/dashboard'
import { useAuditStore } from '@/stores/admin/audit'
import SectionCards, { type StatItem } from '@/components/dashboard/SectionCards.vue'
import ChartAreaInteractive from '@/components/dashboard/ChartAreaInteractive.vue'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { IconShieldCheck } from '@tabler/icons-vue'
import { useRouter } from 'vue-router'

const authStore = useAuthStore()
const dashboardStore = useDashboardStore()
const auditStore = useAuditStore()
const router = useRouter()

const loading = ref(true)

// Computed stats from API data
const stats = computed<StatItem[]>(() => {
  const data = dashboardStore.stats
  if (!data) return []

  // Calculate flagged content count
  const flaggedCount =
    (data.solutions?.flagged || 0) +
    (data.forum?.flaggedPosts || 0) +
    (data.forum?.flaggedComments || 0)

  return [
    {
      title: 'Total Users',
      value: data.users?.total?.toLocaleString() || '0',
      change: data.users?.activeToday ? `+${data.users.activeToday}` : '+0',
      trend: data.users?.activeToday > 0 ? 'up' : 'neutral',
      description: `${data.users?.activeWeek || 0} active this week`,
    },
    {
      title: 'Total Problems',
      value: data.problems?.total?.toLocaleString() || '0',
      change: `${data.problems?.published || 0} published`,
      trend: 'neutral',
      description: `${data.problems?.unpublished || 0} unpublished`,
    },
    {
      title: 'Active Contests',
      value: data.contests?.running?.toString() || '0',
      change: `${data.contests?.upcoming || 0} upcoming`,
      trend: 'neutral',
      description: `${data.contests?.finished || 0} finished`,
    },
    {
      title: 'Flagged Content',
      value: flaggedCount.toString(),
      change: flaggedCount > 0 ? 'Action needed' : 'All clear',
      trend: flaggedCount > 0 ? 'down' : 'neutral',
      description: 'Pending moderation',
    },
  ]
})

const recentActivity = computed(() => {
  return auditStore.logs.slice(0, 5).map((log) => ({
    id: log.id,
    action: log.action,
    user: log.performer?.username || 'System',
    target: log.user?.username || log.entity_type || 'N/A',
    time: formatRelativeTime(log.created_at),
  }))
})

function formatRelativeTime(date: Date | string): string {
  const d = new Date(date)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return 'Just now'
  if (minutes < 60) return `${minutes} minute${minutes > 1 ? 's' : ''} ago`
  if (hours < 24) return `${hours} hour${hours > 1 ? 's' : ''} ago`
  return `${days} day${days > 1 ? 's' : ''} ago`
}

async function loadData() {
  loading.value = true
  try {
    await Promise.all([dashboardStore.fetchStats(), auditStore.fetchLogs({ limit: 10 })])
  } catch (error) {
    console.error('Failed to load dashboard data:', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => loadData())
</script>

<template>
  <div class="flex flex-col gap-4 py-4 md:gap-8 md:py-8">
    <!-- Header Section -->
    <div class="flex flex-col gap-4 px-4 sm:flex-row sm:items-center sm:justify-between lg:px-6">
      <div class="space-y-1">
        <h1 class="text-3xl font-bold tracking-tight">Dashboard</h1>
        <p class="text-muted-foreground">
          Welcome back, <span class="font-medium text-foreground">{{ authStore.userName }}</span>
        </p>
      </div>

      <div class="flex items-center gap-2">
        <Badge
          variant="outline"
          class="gap-1.5 py-1.5 px-3 text-sm font-medium border-primary/20 bg-primary/5 text-primary"
        >
          <IconShieldCheck class="h-4 w-4" />
          {{ authStore.userRole }}
        </Badge>
      </div>
    </div>

    <div v-if="loading" class="flex items-center justify-center py-12">
      <div class="text-muted-foreground">Loading dashboard...</div>
    </div>

    <div v-else class="flex flex-col gap-4">
      <SectionCards :stats="stats" />

      <div class="grid gap-4 md:grid-cols-2 lg:grid-cols-7 px-4 lg:px-6">
        <ChartAreaInteractive
          class="col-span-4"
          title="User Registration Trend"
          description="Daily user registrations for the past 30 days"
        />

        <Card class="col-span-3">
          <CardHeader>
            <CardTitle>Recent Activity</CardTitle>
            <CardDescription> Latest admin actions across the platform </CardDescription>
          </CardHeader>
          <CardContent>
            <div v-if="recentActivity.length === 0" class="text-center py-4 text-muted-foreground">
              No recent activity
            </div>
            <div v-else class="space-y-4">
              <div
                v-for="activity in recentActivity"
                :key="activity.id"
                class="flex items-start gap-3 pb-3 border-b last:border-0 last:pb-0 cursor-pointer hover:bg-muted/50 p-2 rounded -mx-2"
                @click="router.push({ name: 'audit' })"
              >
                <div class="flex-1 space-y-1">
                  <p class="text-sm font-medium leading-none">
                    {{ activity.action }}
                  </p>
                  <p class="text-sm text-muted-foreground">Target: {{ activity.target }}</p>
                </div>
                <div class="text-sm text-muted-foreground whitespace-nowrap">
                  {{ activity.time }}
                </div>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  </div>
</template>
