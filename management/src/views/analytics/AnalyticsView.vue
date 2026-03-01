<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  IconUsers,
  IconFileText,
  IconTrophy,
  IconCreditCard,
  IconServer,
  IconRefresh,
  IconTrendingUp,
  IconTrendingDown,
} from '@tabler/icons-vue'
import {
  analyticsApi,
  type UserActivityReport,
  type ProblemCompletionReport,
  type ContestParticipationReport,
  type RevenueReport,
  type PerformanceReport,
} from '@/api/admin/analytics'

const { t } = useI18n()

type ReportTab =
  | 'user_activity'
  | 'problem_completion'
  | 'contest_participation'
  | 'revenue'
  | 'performance'

const activeTab = ref<ReportTab>('user_activity')
const loading = ref(false)
const days = ref(30)

const userActivityReport = ref<UserActivityReport | null>(null)
const problemCompletionReport = ref<ProblemCompletionReport | null>(null)
const contestParticipationReport = ref<ContestParticipationReport | null>(null)
const revenueReport = ref<RevenueReport | null>(null)
const performanceReport = ref<PerformanceReport | null>(null)

const tabConfig = [
  { value: 'user_activity' as ReportTab, label: t('analytics.tabs.userActivity'), icon: IconUsers },
  {
    value: 'problem_completion' as ReportTab,
    label: t('analytics.tabs.problemCompletion'),
    icon: IconFileText,
  },
  {
    value: 'contest_participation' as ReportTab,
    label: t('analytics.tabs.contestParticipation'),
    icon: IconTrophy,
  },
  { value: 'revenue' as ReportTab, label: t('analytics.tabs.revenue'), icon: IconCreditCard },
  { value: 'performance' as ReportTab, label: t('analytics.tabs.performance'), icon: IconServer },
]

async function loadReport() {
  loading.value = true
  try {
    switch (activeTab.value) {
      case 'user_activity':
        userActivityReport.value = await analyticsApi.getUserActivity({ days: days.value })
        break
      case 'problem_completion':
        problemCompletionReport.value = await analyticsApi.getProblemCompletion({
          days: days.value,
        })
        break
      case 'contest_participation':
        contestParticipationReport.value = await analyticsApi.getContestParticipation({
          days: days.value,
        })
        break
      case 'revenue':
        revenueReport.value = await analyticsApi.getRevenue({ days: days.value })
        break
      case 'performance':
        performanceReport.value = await analyticsApi.getPerformance()
        break
    }
  } catch (error) {
    console.error('Failed to load report:', error)
    toast.error(t('analytics.loadError'))
  } finally {
    loading.value = false
  }
}

function formatNumber(num: number): string {
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return num.toFixed(0)
}

function formatPercent(num: number): string {
  return num.toFixed(1) + '%'
}

function formatCurrency(num: number): string {
  return '$' + num.toFixed(2)
}

function formatUptime(seconds: number): string {
  const d = Math.floor(seconds / 86400)
  const h = Math.floor((seconds % 86400) / 3600)
  return `${d}d ${h}h`
}

watch([activeTab, days], () => {
  loadReport()
})

onMounted(() => {
  loadReport()
})
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold tracking-tight">{{ t('analytics.title') }}</h1>
        <p class="text-muted-foreground mt-1">{{ t('analytics.description') }}</p>
      </div>
      <div class="flex items-center gap-4">
        <Select v-model="days">
          <SelectTrigger class="w-[150px]">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="7">{{ t('analytics.periods.7days') }}</SelectItem>
            <SelectItem value="30">{{ t('analytics.periods.30days') }}</SelectItem>
            <SelectItem value="90">{{ t('analytics.periods.90days') }}</SelectItem>
            <SelectItem value="365">{{ t('analytics.periods.1year') }}</SelectItem>
          </SelectContent>
        </Select>
        <Button variant="outline" size="sm" @click="loadReport" :disabled="loading">
          <IconRefresh class="h-4 w-4 mr-1" :class="{ 'animate-spin': loading }" />
          {{ t('common.refresh') }}
        </Button>
      </div>
    </div>

    <Tabs v-model="activeTab">
      <TabsList class="grid w-full grid-cols-5">
        <TabsTrigger v-for="tab in tabConfig" :key="tab.value" :value="tab.value">
          <component :is="tab.icon" class="h-4 w-4 mr-2" />
          {{ tab.label }}
        </TabsTrigger>
      </TabsList>

      <!-- User Activity -->
      <TabsContent value="user_activity" class="space-y-4">
        <div v-if="loading" class="flex items-center justify-center py-12">
          <div class="text-muted-foreground">{{ t('common.loading') }}</div>
        </div>
        <template v-else-if="userActivityReport">
          <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
            <Card>
              <CardHeader class="pb-2">
                <CardDescription>{{
                  t('analytics.userActivity.dailyActiveUsers')
                }}</CardDescription>
                <CardTitle class="text-2xl">
                  {{ formatNumber(userActivityReport.activeUsersDaily.slice(-1)[0]?.count || 0) }}
                </CardTitle>
              </CardHeader>
            </Card>
            <Card>
              <CardHeader class="pb-2">
                <CardDescription>{{ t('analytics.userActivity.retention1d') }}</CardDescription>
                <CardTitle class="text-2xl flex items-center gap-2">
                  {{ formatPercent(userActivityReport.userRetention.day1) }}
                  <IconTrendingUp
                    v-if="userActivityReport.userRetention.day1 > 50"
                    class="h-5 w-5 text-green-500"
                  />
                </CardTitle>
              </CardHeader>
            </Card>
            <Card>
              <CardHeader class="pb-2">
                <CardDescription>{{ t('analytics.userActivity.retention7d') }}</CardDescription>
                <CardTitle class="text-2xl">{{
                  formatPercent(userActivityReport.userRetention.day7)
                }}</CardTitle>
              </CardHeader>
            </Card>
            <Card>
              <CardHeader class="pb-2">
                <CardDescription>{{ t('analytics.userActivity.retention30d') }}</CardDescription>
                <CardTitle class="text-2xl">{{
                  formatPercent(userActivityReport.userRetention.day30)
                }}</CardTitle>
              </CardHeader>
            </Card>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <Card>
              <CardHeader>
                <CardTitle>{{ t('analytics.userActivity.activeUsersTrend') }}</CardTitle>
              </CardHeader>
              <CardContent>
                <div class="space-y-2">
                  <div
                    v-for="(item, idx) in userActivityReport.activeUsersDaily.slice(-7)"
                    :key="idx"
                    class="flex items-center justify-between"
                  >
                    <span class="text-sm text-muted-foreground">{{ item.date }}</span>
                    <div class="flex items-center gap-2">
                      <div class="w-32 h-2 bg-muted rounded overflow-hidden">
                        <div
                          class="h-full bg-primary"
                          :style="{ width: Math.min(100, item.count / 10) + '%' }"
                        />
                      </div>
                      <span class="text-sm font-medium w-12 text-right">{{ item.count }}</span>
                    </div>
                  </div>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>{{ t('analytics.userActivity.peakHours') }}</CardTitle>
              </CardHeader>
              <CardContent>
                <div class="grid grid-cols-6 gap-1">
                  <div
                    v-for="(item, idx) in userActivityReport.peakActiveHours"
                    :key="idx"
                    class="flex flex-col items-center p-1 rounded"
                    :class="{ 'bg-primary/10': item.count > 0 }"
                  >
                    <span class="text-xs text-muted-foreground">{{ item.hour }}:00</span>
                    <span class="text-xs font-medium">{{ item.count }}</span>
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>{{ t('analytics.userActivity.topUsers') }}</CardTitle>
            </CardHeader>
            <CardContent>
              <div class="space-y-2">
                <div
                  v-for="(user, idx) in userActivityReport.topActiveUsers"
                  :key="user.userId"
                  class="flex items-center justify-between p-2 rounded hover:bg-muted/50"
                >
                  <div class="flex items-center gap-3">
                    <Badge variant="outline">{{ idx + 1 }}</Badge>
                    <span class="font-medium">{{ user.username }}</span>
                  </div>
                  <div class="flex items-center gap-4 text-sm text-muted-foreground">
                    <span>{{ user.loginCount }} {{ t('analytics.userActivity.logins') }}</span>
                    <span>{{ new Date(user.lastActive).toLocaleDateString() }}</span>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
        </template>
      </TabsContent>

      <!-- Problem Completion -->
      <TabsContent value="problem_completion" class="space-y-4">
        <div v-if="loading" class="flex items-center justify-center py-12">
          <div class="text-muted-foreground">{{ t('common.loading') }}</div>
        </div>
        <template v-else-if="problemCompletionReport">
          <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
            <Card>
              <CardHeader class="pb-2">
                <CardDescription>{{
                  t('analytics.problemCompletion.totalAttempts')
                }}</CardDescription>
                <CardTitle class="text-2xl">{{
                  formatNumber(problemCompletionReport.totalAttempts)
                }}</CardTitle>
              </CardHeader>
            </Card>
            <Card>
              <CardHeader class="pb-2">
                <CardDescription>{{
                  t('analytics.problemCompletion.successfulAttempts')
                }}</CardDescription>
                <CardTitle class="text-2xl">{{
                  formatNumber(problemCompletionReport.successfulAttempts)
                }}</CardTitle>
              </CardHeader>
            </Card>
            <Card>
              <CardHeader class="pb-2">
                <CardDescription>{{
                  t('analytics.problemCompletion.completionRate')
                }}</CardDescription>
                <CardTitle class="text-2xl flex items-center gap-2">
                  {{ formatPercent(problemCompletionReport.overallCompletionRate) }}
                  <IconTrendingUp
                    v-if="problemCompletionReport.overallCompletionRate > 30"
                    class="h-5 w-5 text-green-500"
                  />
                  <IconTrendingDown v-else class="h-5 w-5 text-red-500" />
                </CardTitle>
              </CardHeader>
            </Card>
            <Card>
              <CardHeader class="pb-2">
                <CardDescription>{{
                  t('analytics.problemCompletion.trendingProblems')
                }}</CardDescription>
                <CardTitle class="text-2xl">{{
                  problemCompletionReport.trendingProblems.length
                }}</CardTitle>
              </CardHeader>
            </Card>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <Card>
              <CardHeader>
                <CardTitle>{{ t('analytics.problemCompletion.byDifficulty') }}</CardTitle>
              </CardHeader>
              <CardContent>
                <div class="space-y-3">
                  <div
                    v-for="item in problemCompletionReport.byDifficulty"
                    :key="item.difficulty"
                    class="space-y-1"
                  >
                    <div class="flex items-center justify-between">
                      <span class="text-sm font-medium">{{ item.difficulty }}</span>
                      <span class="text-sm text-muted-foreground">{{
                        formatPercent(item.rate)
                      }}</span>
                    </div>
                    <div class="w-full h-2 bg-muted rounded overflow-hidden">
                      <div
                        class="h-full"
                        :class="{
                          'bg-green-500': item.difficulty === 'EASY',
                          'bg-yellow-500': item.difficulty === 'MEDIUM',
                          'bg-red-500': item.difficulty === 'HARD',
                        }"
                        :style="{ width: item.rate + '%' }"
                      />
                    </div>
                  </div>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>{{ t('analytics.problemCompletion.hardestProblems') }}</CardTitle>
              </CardHeader>
              <CardContent>
                <div class="space-y-2">
                  <div
                    v-for="problem in problemCompletionReport.hardestProblems"
                    :key="problem.problemId"
                    class="flex items-center justify-between p-2 rounded hover:bg-muted/50"
                  >
                    <div>
                      <p class="font-medium text-sm">{{ problem.title }}</p>
                      <p class="text-xs text-muted-foreground">{{ problem.difficulty }}</p>
                    </div>
                    <Badge variant="destructive">{{ formatPercent(problem.completionRate) }}</Badge>
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>{{ t('analytics.problemCompletion.topTags') }}</CardTitle>
            </CardHeader>
            <CardContent>
              <div class="flex flex-wrap gap-2">
                <Badge
                  v-for="tag in problemCompletionReport.byTag.slice(0, 15)"
                  :key="tag.tagId"
                  variant="outline"
                  class="py-1"
                >
                  {{ tag.label }} ({{ formatPercent(tag.rate) }})
                </Badge>
              </div>
            </CardContent>
          </Card>
        </template>
      </TabsContent>

      <!-- Contest Participation -->
      <TabsContent value="contest_participation" class="space-y-4">
        <div v-if="loading" class="flex items-center justify-center py-12">
          <div class="text-muted-foreground">{{ t('common.loading') }}</div>
        </div>
        <template v-else-if="contestParticipationReport">
          <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
            <Card>
              <CardHeader class="pb-2">
                <CardDescription>{{
                  t('analytics.contestParticipation.totalContests')
                }}</CardDescription>
                <CardTitle class="text-2xl">{{
                  contestParticipationReport.totalContests
                }}</CardTitle>
              </CardHeader>
            </Card>
            <Card>
              <CardHeader class="pb-2">
                <CardDescription>{{
                  t('analytics.contestParticipation.totalParticipants')
                }}</CardDescription>
                <CardTitle class="text-2xl">{{
                  formatNumber(contestParticipationReport.totalParticipants)
                }}</CardTitle>
              </CardHeader>
            </Card>
            <Card>
              <CardHeader class="pb-2">
                <CardDescription>{{
                  t('analytics.contestParticipation.avgParticipants')
                }}</CardDescription>
                <CardTitle class="text-2xl">{{
                  contestParticipationReport.averageParticipantsPerContest.toFixed(1)
                }}</CardTitle>
              </CardHeader>
            </Card>
            <Card>
              <CardHeader class="pb-2">
                <CardDescription>{{
                  t('analytics.contestParticipation.virtualParticipation')
                }}</CardDescription>
                <CardTitle class="text-2xl">{{
                  contestParticipationReport.virtualParticipation.total
                }}</CardTitle>
              </CardHeader>
            </Card>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <Card>
              <CardHeader>
                <CardTitle>{{ t('analytics.contestParticipation.byType') }}</CardTitle>
              </CardHeader>
              <CardContent>
                <div class="space-y-3">
                  <div
                    v-for="item in contestParticipationReport.byType"
                    :key="item.type"
                    class="flex items-center justify-between"
                  >
                    <div class="flex items-center gap-2">
                      <Badge variant="outline">{{ item.type }}</Badge>
                      <span class="text-sm text-muted-foreground">{{ item.count }} contests</span>
                    </div>
                    <span class="font-medium">{{ item.avgParticipants.toFixed(1) }} avg</span>
                  </div>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>{{ t('analytics.contestParticipation.topContests') }}</CardTitle>
              </CardHeader>
              <CardContent>
                <div class="space-y-2">
                  <div
                    v-for="contest in contestParticipationReport.topContests"
                    :key="contest.contestId"
                    class="flex items-center justify-between p-2 rounded hover:bg-muted/50"
                  >
                    <span class="font-medium text-sm truncate max-w-[200px]">{{
                      contest.title
                    }}</span>
                    <Badge
                      >{{ contest.participants }} {{ t('analytics.contestParticipants') }}</Badge
                    >
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>
        </template>
      </TabsContent>

      <!-- Revenue -->
      <TabsContent value="revenue" class="space-y-4">
        <div v-if="loading" class="flex items-center justify-center py-12">
          <div class="text-muted-foreground">{{ t('common.loading') }}</div>
        </div>
        <template v-else-if="revenueReport">
          <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
            <Card>
              <CardHeader class="pb-2">
                <CardDescription>{{ t('analytics.revenue.mrr') }}</CardDescription>
                <CardTitle class="text-2xl">{{ formatCurrency(revenueReport.mrr) }}</CardTitle>
              </CardHeader>
            </Card>
            <Card>
              <CardHeader class="pb-2">
                <CardDescription>{{ t('analytics.revenue.arr') }}</CardDescription>
                <CardTitle class="text-2xl">{{ formatCurrency(revenueReport.arr) }}</CardTitle>
              </CardHeader>
            </Card>
            <Card>
              <CardHeader class="pb-2">
                <CardDescription>{{ t('analytics.revenue.subscribers') }}</CardDescription>
                <CardTitle class="text-2xl">{{ revenueReport.subscriberCount }}</CardTitle>
              </CardHeader>
            </Card>
            <Card>
              <CardHeader class="pb-2">
                <CardDescription>{{ t('analytics.revenue.conversionRate') }}</CardDescription>
                <CardTitle class="text-2xl flex items-center gap-2">
                  {{ formatPercent(revenueReport.conversionRate) }}
                  <IconTrendingUp
                    v-if="revenueReport.conversionRate > 5"
                    class="h-5 w-5 text-green-500"
                  />
                </CardTitle>
              </CardHeader>
            </Card>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <Card>
              <CardHeader>
                <CardTitle>{{ t('analytics.revenue.byPlan') }}</CardTitle>
              </CardHeader>
              <CardContent>
                <div class="space-y-3">
                  <div
                    v-for="item in revenueReport.byPlan"
                    :key="item.plan"
                    class="flex items-center justify-between"
                  >
                    <div>
                      <p class="font-medium">{{ item.plan }}</p>
                      <p class="text-sm text-muted-foreground">
                        {{ item.subscribers }} subscribers
                      </p>
                    </div>
                    <span class="text-lg font-semibold">{{ formatCurrency(item.revenue) }}/mo</span>
                  </div>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>{{ t('analytics.revenue.metrics') }}</CardTitle>
              </CardHeader>
              <CardContent>
                <div class="space-y-4">
                  <div class="flex items-center justify-between">
                    <span class="text-sm text-muted-foreground">{{
                      t('analytics.revenue.arpu')
                    }}</span>
                    <span class="font-medium">{{ formatCurrency(revenueReport.arpu) }}</span>
                  </div>
                  <div class="flex items-center justify-between">
                    <span class="text-sm text-muted-foreground">{{
                      t('analytics.revenue.churnRate')
                    }}</span>
                    <span
                      class="font-medium"
                      :class="revenueReport.churnRate > 5 ? 'text-red-500' : 'text-green-500'"
                    >
                      {{ formatPercent(revenueReport.churnRate) }}
                    </span>
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>
        </template>
      </TabsContent>

      <!-- Performance -->
      <TabsContent value="performance" class="space-y-4">
        <div v-if="loading" class="flex items-center justify-center py-12">
          <div class="text-muted-foreground">{{ t('common.loading') }}</div>
        </div>
        <template v-else-if="performanceReport">
          <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
            <Card>
              <CardHeader class="pb-2">
                <CardDescription>{{ t('analytics.performance.uptime') }}</CardDescription>
                <CardTitle class="text-2xl flex items-center gap-2">
                  {{ formatUptime(performanceReport.systemUptime) }}
                  <IconTrendingUp
                    v-if="performanceReport.systemUptime > 86400 * 7"
                    class="h-5 w-5 text-green-500"
                  />
                </CardTitle>
              </CardHeader>
            </Card>
            <Card>
              <CardHeader class="pb-2">
                <CardDescription>{{ t('analytics.performance.throughput') }}</CardDescription>
                <CardTitle class="text-2xl"
                  >{{ performanceReport.throughput
                  }}<span class="text-sm font-normal">/24h</span></CardTitle
                >
              </CardHeader>
            </Card>
            <Card>
              <CardHeader class="pb-2">
                <CardDescription>{{ t('analytics.performance.errorRate') }}</CardDescription>
                <CardTitle
                  class="text-2xl"
                  :class="performanceReport.errorRate > 1 ? 'text-red-500' : 'text-green-500'"
                >
                  {{ formatPercent(performanceReport.errorRate) }}
                </CardTitle>
              </CardHeader>
            </Card>
            <Card>
              <CardHeader class="pb-2">
                <CardDescription>{{ t('analytics.performance.memoryUsage') }}</CardDescription>
                <CardTitle class="text-2xl">{{
                  formatPercent(performanceReport.resourceUsage.memory)
                }}</CardTitle>
              </CardHeader>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>{{ t('analytics.performance.resourceUsage') }}</CardTitle>
            </CardHeader>
            <CardContent>
              <div class="grid grid-cols-3 gap-6">
                <div class="space-y-2">
                  <div class="flex items-center justify-between">
                    <span class="text-sm text-muted-foreground">CPU</span>
                    <span class="font-medium">{{
                      formatPercent(performanceReport.resourceUsage.cpu)
                    }}</span>
                  </div>
                  <div class="w-full h-2 bg-muted rounded overflow-hidden">
                    <div
                      class="h-full bg-blue-500"
                      :style="{ width: performanceReport.resourceUsage.cpu + '%' }"
                    />
                  </div>
                </div>
                <div class="space-y-2">
                  <div class="flex items-center justify-between">
                    <span class="text-sm text-muted-foreground">Memory</span>
                    <span class="font-medium">{{
                      formatPercent(performanceReport.resourceUsage.memory)
                    }}</span>
                  </div>
                  <div class="w-full h-2 bg-muted rounded overflow-hidden">
                    <div
                      class="h-full bg-green-500"
                      :style="{ width: performanceReport.resourceUsage.memory + '%' }"
                    />
                  </div>
                </div>
                <div class="space-y-2">
                  <div class="flex items-center justify-between">
                    <span class="text-sm text-muted-foreground">Disk</span>
                    <span class="font-medium">{{
                      formatPercent(performanceReport.resourceUsage.disk)
                    }}</span>
                  </div>
                  <div class="w-full h-2 bg-muted rounded overflow-hidden">
                    <div
                      class="h-full bg-yellow-500"
                      :style="{ width: performanceReport.resourceUsage.disk + '%' }"
                    />
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
        </template>
      </TabsContent>
    </Tabs>
  </div>
</template>
