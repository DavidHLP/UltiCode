<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useContestsStore } from '@/stores/admin/contests'
import { useAuthStore } from '@/stores/auth'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import {
  IconArrowLeft,
  IconPlayerPlay,
  IconPlayerStop,
  IconTrash,
} from '@tabler/icons-vue'
import { toast } from 'vue-sonner'
import ContestProblemPicker from './components/ContestProblemPicker.vue'
import ContestOverviewTab from './components/ContestOverviewTab.vue'
import ContestProblemsTab from './components/ContestProblemsTab.vue'
import ContestParticipantsTab from './components/ContestParticipantsTab.vue'
import ContestRankingsTab from './components/ContestRankingsTab.vue'

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
          <TabsContent value="overview">
            <ContestOverviewTab :contest="contest" />
          </TabsContent>

          <!-- Problems Tab -->
          <TabsContent value="problems">
            <ContestProblemsTab
              :contest="contest"
              :can-update="canUpdate"
              @add-problem="problemPickerOpen = true"
              @remove-problem="handleRemoveProblem"
            />
          </TabsContent>

          <!-- Participants Tab -->
          <TabsContent value="participants">
            <ContestParticipantsTab :contest="contest" />
          </TabsContent>

          <!-- Rankings Tab -->
          <TabsContent value="rankings">
            <ContestRankingsTab :rankings="rankings" />
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
