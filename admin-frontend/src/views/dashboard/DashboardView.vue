<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/admin/auth'
import SectionCards from '@/template/dashboard/SectionCards.vue'
import ChartAreaInteractive from '@/template/dashboard/ChartAreaInteractive.vue'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'

const authStore = useAuthStore()
const loading = ref(true)

// Mock data for demonstration
const stats = ref([
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
  <div class="flex flex-col gap-4">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold tracking-tight">Dashboard</h1>
        <p class="text-muted-foreground">Welcome back, {{ authStore.userName }}</p>
      </div>
      <Badge variant="outline" class="text-lg px-3 py-1">
        {{ authStore.userRole }}
      </Badge>
    </div>

    <SectionCards :stats="stats" />

    <div class="grid gap-4 md:grid-cols-2 lg:grid-cols-7">
      <Card class="col-span-4">
        <CardHeader>
          <CardTitle>User Registration Trend</CardTitle>
          <CardDescription> Daily user registrations for the past 30 days </CardDescription>
        </CardHeader>
        <CardContent class="pl-2">
          <ChartAreaInteractive />
        </CardContent>
      </Card>

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
