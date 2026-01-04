<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/admin/auth'
import SectionCards, { type StatItem } from '@/components/dashboard/SectionCards.vue'
import ChartAreaInteractive from '@/components/dashboard/ChartAreaInteractive.vue'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { ShieldCheck } from 'lucide-vue-next'

const authStore = useAuthStore()
const loading = ref(true)

// Mock data for demonstration
const stats = ref<StatItem[]>([
  {
    title: 'Total Users',
    value: '2,350',
    change: '+12.5%',
    trend: 'up',
    description: '+20.1% from last month',
  },
  {
    title: 'Total Problems',
    value: '150',
    change: '+5.2%',
    trend: 'up',
    description: '+8 new problems this week',
  },
  {
    title: 'Active Contests',
    value: '8',
    change: '+0',
    trend: 'neutral',
    description: '3 upcoming contests',
  },
  {
    title: 'Flagged Content',
    value: '12',
    change: '-15%',
    trend: 'down',
    description: 'Pending moderation',
  },
])

const recentActivity = ref([
  {
    id: 1,
    action: 'User Created',
    user: 'admin',
    target: 'testuser',
    time: '2 minutes ago',
  },
  {
    id: 2,
    action: 'Problem Published',
    user: 'admin',
    target: 'Two Sum',
    time: '15 minutes ago',
  },
  {
    id: 3,
    action: 'User Banned',
    user: 'moderator',
    target: 'spammer',
    time: '1 hour ago',
  },
  {
    id: 4,
    action: 'Contest Created',
    user: 'admin',
    target: 'Weekly Contest #42',
    time: '3 hours ago',
  },
])

onMounted(() => {
  loading.value = false
})
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
          <ShieldCheck class="h-4 w-4" />
          {{ authStore.userRole }}
        </Badge>
      </div>
    </div>

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
          <div class="space-y-4">
            <div
              v-for="activity in recentActivity"
              :key="activity.id"
              class="flex items-start gap-3 pb-3 border-b last:border-0 last:pb-0"
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
</template>
