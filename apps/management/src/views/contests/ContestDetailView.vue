<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useContestsStore } from '@/stores/admin/contests'
import { useAuthStore } from '@/stores/auth'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import {
  IconArrowLeft,
  IconPlayerPlay,
  IconPlayerStop,
  IconTrash,
  IconTrophy,
} from '@tabler/icons-vue'
import { toast } from 'vue-sonner'
import {
  SemanticBadge,
  CONTEST_STATUS_COLOR_MAP,
  CONTEST_TYPE_COLOR_MAP,
} from '@/components/ui/terminal'
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
const isLoaded = ref(false)

const canUpdate = computed(() => authStore.hasPermission('UPDATE', 'CONTEST'))
const canDelete = computed(() => authStore.hasPermission('DELETE', 'CONTEST'))

onMounted(async () => {
  if (contestId.value) {
    await loadData()
  }
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
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
      problemId: Number(problem.id),
      score: 100,
    })
    toast.success(t('contests.toast.problemAdded'))
    problemPickerOpen.value = false
  } catch {
    toast.error(t('contests.toast.failedToAddProblem'))
  }
}

async function handleRemoveProblem(problemId: number) {
  if (!confirm(t('contests.confirmation.removeProblem'))) return
  try {
    await contestsStore.removeProblem(contestId.value, problemId)
    toast.success(t('contests.toast.problemRemoved'))
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
    <!-- Terminal Header -->
    <header
      :class="[
        'sticky top-0 z-10 border-b border-[var(--border-subtle)] dark:border-[var(--border-subtle)] bg-[var(--card)]',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <div class="px-4 lg:px-6 py-4">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-4">
            <Button
              variant="terminal"
              size="icon"
              class="h-8 w-8 border-[var(--border-subtle)] hover:border-[var(--primary)] hover:text-[var(--primary)]"
              @click="router.push({ name: 'contests' })"
            >
              <IconArrowLeft class="h-4 w-4" />
            </Button>

            <div v-if="loading && !contest" class="space-y-2">
              <Skeleton class="h-4 w-48" />
              <Skeleton class="h-3 w-32" />
            </div>

            <div v-else-if="contest" class="flex items-center gap-3">
              <div
                class="h-10 w-10 border flex items-center justify-center bg-[var(--surface-highlight)] dark:bg-[var(--foreground-strong)] border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] text-[var(--primary)]"
              >
                <IconTrophy class="h-5 w-5" />
              </div>
              <div>
                <div class="flex items-center gap-2">
                  <h1 class="font-medium text-sm text-[var(--foreground)]">{{ contest.title }}</h1>
                </div>
                <div class="flex items-center gap-2 mt-1">
                  <SemanticBadge
                    :color="CONTEST_TYPE_COLOR_MAP[contest.contestType] ?? 'neutral'"
                    :label="t(`contests.type.${contest.contestType}`, contest.contestType)"
                    size="sm"
                  />
                  <SemanticBadge
                    :color="CONTEST_STATUS_COLOR_MAP[contest.status] ?? 'neutral'"
                    :label="contest.status"
                    size="sm"
                    :dot="contest.status === 'RUNNING'"
                    :pulse="contest.status === 'RUNNING'"
                  />
                </div>
              </div>
            </div>
          </div>

          <div class="flex items-center gap-2">
            <template v-if="contest && canUpdate">
              <Button
                v-if="contest.status === 'UPCOMING'"
                variant="terminal"
                size="sm"
                class="font-data text-xs border-[var(--status-success-mark)] text-foreground-strong hover:bg-[color-mix(in_oklch,_var(--status-success-mark)_10%,_transparent)]"
                @click="handleStart"
              >
                <IconPlayerPlay class="mr-1.5 h-3.5 w-3.5" />
                <span class="uppercase tracking-wider">{{ t('contests.detail.start') }}</span>
              </Button>
              <Button
                v-if="contest.status === 'RUNNING'"
                variant="terminal"
                size="sm"
                class="font-data text-xs border-[var(--status-warning-mark)] text-foreground-strong hover:bg-[color-mix(in_oklch,_var(--status-warning-mark)_10%,_transparent)]"
                @click="handleEnd"
              >
                <IconPlayerStop class="mr-1.5 h-3.5 w-3.5" />
                <span class="uppercase tracking-wider">{{ t('contests.detail.end') }}</span>
              </Button>
            </template>
            <Button
              v-if="canDelete"
              variant="terminal"
              size="sm"
              class="font-data text-xs border-[var(--status-error-mark)] text-foreground-strong hover:bg-[color-mix(in_oklch,_var(--status-error-mark)_10%,_transparent)]"
              @click="handleDelete"
            >
              <IconTrash class="mr-1.5 h-3.5 w-3.5" />
              <span class="uppercase tracking-wider">{{ t('contests.actions.delete') }}</span>
            </Button>
          </div>
        </div>
      </div>
    </header>

    <main class="flex-1 p-4 lg:p-6 max-w-[1200px] mx-auto w-full">
      <div v-if="loading && !contest" class="space-y-6">
        <Skeleton class="h-48 w-full border border-[var(--border-subtle)]" />
        <Skeleton class="h-96 w-full border border-[var(--border-subtle)]" />
      </div>

      <template v-else-if="contest">
        <Tabs :model-value="activeTab" @update:model-value="handleTabChange" class="space-y-6">
          <TabsList
            class="border border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] bg-[var(--surface-sunken)] p-1"
          >
            <TabsTrigger
              value="overview"
              class="font-data text-xs uppercase tracking-wider data-[state=active]:bg-[var(--card)] data-[state=active]:border-[var(--primary)] data-[state=active]:text-[var(--primary)]"
            >
              {{ t('contests.detail.overview') }}
            </TabsTrigger>
            <TabsTrigger
              value="problems"
              class="font-data text-xs uppercase tracking-wider data-[state=active]:bg-[var(--card)] data-[state=active]:border-[var(--primary)] data-[state=active]:text-[var(--primary)]"
            >
              {{ t('contests.detail.problems') }}
            </TabsTrigger>
            <TabsTrigger
              value="participants"
              class="font-data text-xs uppercase tracking-wider data-[state=active]:bg-[var(--card)] data-[state=active]:border-[var(--primary)] data-[state=active]:text-[var(--primary)]"
            >
              {{ t('contests.detail.participants') }}
            </TabsTrigger>
            <TabsTrigger
              value="rankings"
              class="font-data text-xs uppercase tracking-wider data-[state=active]:bg-[var(--card)] data-[state=active]:border-[var(--primary)] data-[state=active]:text-[var(--primary)]"
            >
              {{ t('contests.detail.rankings') }}
            </TabsTrigger>
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

      <div
        v-else
        class="flex flex-col items-center justify-center h-64 border border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] bg-[var(--card)]"
      >
        <span class="terminal-comment mb-4">{{ t('contests.detail.contestNotFound') }}</span>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--border-subtle)]"
          @click="router.push({ name: 'contests' })"
        >
          {{ t('contests.detail.backToList') }}
        </Button>
      </div>

      <ContestProblemPicker
        v-if="contest"
        v-model:open="problemPickerOpen"
        :exclude-ids="contest.problemIds || []"
        @select="handleAddProblem"
      />
    </main>
  </div>
</template>
