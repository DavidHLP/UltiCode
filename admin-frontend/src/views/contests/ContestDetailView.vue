<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useContestsStore } from '@/stores/admin/contests'
import { useAuthStore } from '@/stores/admin/auth'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Separator } from '@/components/ui/separator'
import {
  IconArrowLeft,
  IconCalendar,
  IconClock,
  IconUsers,
  IconTrophy,
  IconPlayerPlay,
  IconPlayerStop,
  IconTrash,
  IconPlus,
} from '@tabler/icons-vue'
import { toast } from 'vue-sonner'
import ContestProblemPicker from './components/ContestProblemPicker.vue'

const route = useRoute()
const router = useRouter()
const contestsStore = useContestsStore()
const authStore = useAuthStore()
const { t } = useI18n()

const contestId = computed(() => route.params.id as string)
const contest = computed(() => contestsStore.currentContest)
const rankings = computed(() => contestsStore.currentRankings)

const loading = ref(true)
const problemPickerOpen = ref(false)
const activeTab = ref('overview')

const canUpdate = computed(() => authStore.hasPermission('UPDATE', 'CONTEST'))
const canDelete = computed(() => authStore.hasPermission('DELETE', 'CONTEST'))

onMounted(async () => {
  if (contestId.value) {
    await loadData()
  }
})

async function loadData() {
  loading.value = true
  try {
    await contestsStore.fetchContest(contestId.value)
    if (activeTab.value === 'rankings') {
      await contestsStore.fetchRankings(contestId.value)
    }
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

async function handleStart() {
  if (!confirm(t('contests.confirmation.startNow'))) return
  try {
    await contestsStore.startContest(contestId.value)
    toast.success(t('contests.toast.startedSuccessfully'))
  } catch {
    toast.error(t('contests.toast.failedToStart'))
  }
}

async function handleEnd() {
  if (!confirm(t('contests.confirmation.endNow'))) return
  try {
    await contestsStore.endContest(contestId.value)
    toast.success(t('contests.toast.endedSuccessfully'))
  } catch {
    toast.error(t('contests.toast.failedToEnd'))
  }
}

async function handleDelete() {
  if (!confirm(t('contests.confirmation.deleteThis'))) return
  try {
    await contestsStore.deleteContest(contestId.value)
    toast.success(t('contests.toast.deletedSuccessfully'))
    router.push({ name: 'contests' })
  } catch {
    toast.error(t('contests.toast.failedToDelete'))
  }
}

async function handleAddProblem(problem: { id: string }) {
  try {
    await contestsStore.addProblem(contestId.value, {
      problem_id: problem.id,
      score: 100, // Default, maybe add dialog to set score later
    })
    toast.success(t('contests.toast.problemAdded'))
    problemPickerOpen.value = false
  } catch {
    toast.error(t('contests.toast.failedToAddProblem'))
  }
}

async function handleRemoveProblem(problemId: string) {
  if (!confirm(t('contests.confirmation.removeProblem'))) return
  try {
    // ProblemId here refers to the problem entity ID, not the join table ID in some contexts,
    // but API expects problem_id (BigInt in backend).
    // Wait, backend delete expects :problemId which is the BigInt of problem.
    await contestsStore.removeProblem(contestId.value, problemId)
    toast.success(t('contests.toast.problemRemoved'))
    // Optimistic update or refetch done by store
  } catch {
    toast.error(t('contests.toast.failedToRemoveProblem'))
  }
}

function handleTabChange(value: string | number) {
  activeTab.value = String(value)
  if (activeTab.value === 'rankings') {
    contestsStore.fetchRankings(contestId.value)
  }
}
</script>

<template>
  <div class="flex flex-col min-h-[calc(100vh-4rem)]">
    <!-- Header -->
    <header class="sticky top-0 z-10 bg-background/95 backdrop-blur border-b">
      <div class="flex items-center justify-between h-14 px-4 lg:px-6">
        <div class="flex items-center gap-4">
          <Button variant="ghost" size="icon" @click="router.push({ name: 'contests' })">
            <IconArrowLeft class="h-4 w-4" />
          </Button>
          <div v-if="loading && !contest" class="space-y-1">
            <Skeleton class="h-4 w-32" />
          </div>
          <div v-else-if="contest" class="flex items-center gap-3">
            <h1 class="font-semibold text-sm">{{ contest.title }}</h1>
            <Badge variant="outline" class="uppercase text-[10px]">
              {{ contest.contest_type }}
            </Badge>
            <Badge
              :variant="
                contest.status === 'RUNNING'
                  ? 'default'
                  : contest.status === 'FINISHED'
                    ? 'secondary'
                    : 'outline'
              "
              class="capitalize text-[10px]"
            >
              {{ t(`contests.status.${contest.status.toLowerCase()}`) }}
            </Badge>
          </div>
        </div>

        <div class="flex items-center gap-2">
          <template v-if="contest && canUpdate">
            <Button
              v-if="contest.status === 'UPCOMING'"
              size="sm"
              variant="outline"
              @click="handleStart"
            >
              <IconPlayerPlay class="mr-2 h-3.5 w-3.5" />
              {{ t('contests.detail.start') }}
            </Button>
            <Button
              v-if="contest.status === 'RUNNING'"
              size="sm"
              variant="destructive"
              @click="handleEnd"
            >
              <IconPlayerStop class="mr-2 h-3.5 w-3.5" />
              {{ t('contests.detail.end') }}
            </Button>
          </template>
          <Button
            v-if="canDelete"
            variant="ghost"
            size="icon"
            class="text-destructive"
            @click="handleDelete"
          >
            <IconTrash class="h-4 w-4" />
          </Button>
        </div>
      </div>
    </header>

    <main class="flex-1 p-4 lg:p-6 max-w-[1200px] mx-auto w-full">
      <div v-if="loading && !contest" class="space-y-6">
        <Skeleton class="h-48 w-full rounded-xl" />
        <Skeleton class="h-96 w-full rounded-xl" />
      </div>

      <template v-else-if="contest">
        <Tabs :model-value="activeTab" @update:model-value="handleTabChange" class="space-y-6">
          <TabsList>
            <TabsTrigger value="overview">{{ t('contests.detail.overview') }}</TabsTrigger>
            <TabsTrigger value="problems">{{ t('contests.detail.problems') }}</TabsTrigger>
            <TabsTrigger value="participants">{{ t('contests.detail.participants') }}</TabsTrigger>
            <TabsTrigger value="rankings">{{ t('contests.detail.rankings') }}</TabsTrigger>
          </TabsList>

          <!-- Overview Tab -->
          <TabsContent value="overview" class="space-y-6">
            <div class="grid gap-6 md:grid-cols-2">
              <Card>
                <CardHeader>
                  <CardTitle class="text-lg">{{ t('contests.detail.details') }}</CardTitle>
                </CardHeader>
                <CardContent class="space-y-4">
                  <div class="space-y-1">
                    <span class="text-sm font-medium text-muted-foreground">{{
                      t('contests.detail.description')
                    }}</span>
                    <p class="text-sm whitespace-pre-wrap">
                      {{ contest.description || t('contests.detail.noDescription') }}
                    </p>
                  </div>
                  <Separator />
                  <div class="grid grid-cols-2 gap-4 text-sm">
                    <div>
                      <span class="text-muted-foreground">{{ t('contests.detail.slug') }}</span>
                      <p class="font-mono">{{ contest.slug }}</p>
                    </div>
                    <div>
                      <span class="text-muted-foreground">{{
                        t('contests.detail.visibility')
                      }}</span>
                      <p>
                        {{
                          contest.is_visible
                            ? t('contests.detail.published')
                            : t('contests.detail.hidden')
                        }}
                      </p>
                    </div>
                  </div>
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle class="text-lg">{{ t('contests.detail.statsAndSchedule') }}</CardTitle>
                </CardHeader>
                <CardContent class="space-y-4">
                  <div class="flex items-center justify-between">
                    <div class="flex items-center gap-2">
                      <IconCalendar class="h-4 w-4 text-muted-foreground" />
                      <span class="text-sm">{{ t('contests.detail.startTime') }}</span>
                    </div>
                    <span class="text-sm font-medium">
                      {{ new Date(contest.start_time).toLocaleString() }}
                    </span>
                  </div>
                  <div class="flex items-center justify-between">
                    <div class="flex items-center gap-2">
                      <IconClock class="h-4 w-4 text-muted-foreground" />
                      <span class="text-sm">{{ t('contests.detail.duration') }}</span>
                    </div>
                    <span class="text-sm font-medium"
                      >{{ contest.duration_minutes }} {{ t('common.minutes') }}</span
                    >
                  </div>
                  <Separator />
                  <div class="grid grid-cols-2 gap-4 pt-2">
                    <div class="flex flex-col items-center p-3 bg-muted/30 rounded-lg">
                      <IconTrophy class="h-5 w-5 text-yellow-500 mb-1" />
                      <span class="text-2xl font-bold">{{ contest.problems?.length || 0 }}</span>
                      <span class="text-xs text-muted-foreground">{{
                        t('contests.detail.problems')
                      }}</span>
                    </div>
                    <div class="flex flex-col items-center p-3 bg-muted/30 rounded-lg">
                      <IconUsers class="h-5 w-5 text-blue-500 mb-1" />
                      <span class="text-2xl font-bold">{{ contest.participant_count || 0 }}</span>
                      <span class="text-xs text-muted-foreground">{{
                        t('contests.detail.participants')
                      }}</span>
                    </div>
                  </div>
                </CardContent>
              </Card>
            </div>
          </TabsContent>

          <!-- Problems Tab -->
          <TabsContent value="problems" class="space-y-4">
            <div class="flex justify-between items-center">
              <h3 class="text-lg font-medium">{{ t('contests.detail.contestProblems') }}</h3>
              <Button v-if="canUpdate" size="sm" @click="problemPickerOpen = true">
                <IconPlus class="mr-2 h-4 w-4" />
                {{ t('contests.detail.addProblem') }}
              </Button>
            </div>
            <div class="border rounded-md">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead class="w-[50px]">{{ t('contests.detail.idx') }}</TableHead>
                    <TableHead>{{ t('contests.detail.problem') }}</TableHead>
                    <TableHead>{{ t('contests.detail.difficulty') }}</TableHead>
                    <TableHead>{{ t('contests.detail.score') }}</TableHead>
                    <TableHead class="w-[50px]"></TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  <TableRow v-for="cp in contest.problems" :key="cp.id">
                    <TableCell class="font-medium">{{ cp.problem_index }}</TableCell>
                    <TableCell>
                      <div class="flex flex-col">
                        <span class="font-medium">{{ cp.problem.title }}</span>
                        <span class="text-xs text-muted-foreground">{{ cp.problem.slug }}</span>
                      </div>
                    </TableCell>
                    <TableCell>
                      <Badge variant="outline" class="capitalize">
                        {{ cp.problem.difficulty.toLowerCase() }}
                      </Badge>
                    </TableCell>
                    <TableCell>{{ cp.score }}</TableCell>
                    <TableCell>
                      <Button
                        v-if="canUpdate"
                        variant="ghost"
                        size="icon"
                        class="h-8 w-8 text-destructive"
                        @click="handleRemoveProblem(cp.problem_id)"
                      >
                        <IconTrash class="h-4 w-4" />
                      </Button>
                    </TableCell>
                  </TableRow>
                  <TableRow v-if="!contest.problems?.length">
                    <TableCell colspan="5" class="h-24 text-center text-muted-foreground">
                      {{ t('contests.detail.noProblemsAdded') }}
                    </TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            </div>
          </TabsContent>

          <!-- Participants Tab -->
          <TabsContent value="participants">
            <div class="border rounded-md">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{{ t('contests.detail.user') }}</TableHead>
                    <TableHead>{{ t('contests.detail.joinedAt') }}</TableHead>
                    <!-- Add more fields if available in participant relation -->
                  </TableRow>
                </TableHeader>
                <TableBody>
                  <TableRow v-for="p in contest.participants" :key="p.id">
                    <TableCell class="font-medium">
                      <div class="flex items-center gap-2">
                        <span>{{ p.user.username }}</span>
                        <span v-if="p.user.name" class="text-muted-foreground">
                          ({{ p.user.name }})
                        </span>
                      </div>
                    </TableCell>
                    <TableCell class="text-muted-foreground"> - </TableCell>
                  </TableRow>
                  <TableRow v-if="!contest.participants?.length">
                    <TableCell colspan="2" class="h-24 text-center text-muted-foreground">
                      {{ t('contests.detail.noParticipantsYet') }}
                    </TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            </div>
          </TabsContent>

          <!-- Rankings Tab -->
          <TabsContent value="rankings">
            <div class="border rounded-md">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead class="w-[60px]">{{ t('contests.detail.rank') }}</TableHead>
                    <TableHead>{{ t('contests.detail.user') }}</TableHead>
                    <TableHead class="text-right">{{ t('contests.detail.score') }}</TableHead>
                    <TableHead class="text-right">{{ t('contests.detail.penalty') }}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  <TableRow v-for="(r, i) in rankings" :key="r.id">
                    <TableCell class="font-medium">#{{ i + 1 }}</TableCell>
                    <TableCell>
                      <div class="flex items-center gap-2">
                        <span>{{ r.user.username }}</span>
                      </div>
                    </TableCell>
                    <TableCell class="text-right font-medium">{{ r.total_score }}</TableCell>
                    <TableCell class="text-right text-muted-foreground">
                      {{ r.total_penalty }}
                    </TableCell>
                  </TableRow>
                  <TableRow v-if="!rankings.length">
                    <TableCell colspan="4" class="h-24 text-center text-muted-foreground">
                      {{ t('contests.detail.noRankingsYet') }}
                    </TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            </div>
          </TabsContent>
        </Tabs>
      </template>

      <div v-else class="flex flex-col items-center justify-center h-64 text-muted-foreground">
        <p>{{ t('contests.detail.contestNotFound') }}</p>
        <Button variant="link" @click="router.push({ name: 'contests' })">{{
          t('contests.detail.backToList')
        }}</Button>
      </div>

      <ContestProblemPicker
        v-if="contest"
        v-model:open="problemPickerOpen"
        :exclude-ids="contest.problems?.map((p) => p.problem_id) || []"
        @select="handleAddProblem"
      />
    </main>
  </div>
</template>
