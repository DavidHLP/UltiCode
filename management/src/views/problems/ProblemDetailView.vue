<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useProblemsStore } from '@/stores/admin/problems'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ArrowLeft, Edit, Eye, EyeOff, FileText, History } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import DescriptionDisplay from './components/DescriptionDisplay.vue'
import CodeDisplay from './components/CodeDisplay.vue'
import CasesDisplay from './components/CasesDisplay.vue'
import VersionHistoryTimeline from '@/components/problems/VersionHistoryTimeline.vue'
import AuditLogViewer from '@/components/audit/AuditLogViewer.vue'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const problemsStore = useProblemsStore()

const publishing = ref(false)
const isInitialLoad = ref(true)
const versionHistoryOpen = ref(false)

// Animation state for staggered reveal
const isLoaded = ref(false)

const problemId = computed(() => route.params.id as string)
const problem = computed(() => problemsStore.currentProblem)

// Determine current view from route
const currentView = computed(() => {
  const path = route.path
  if (path.endsWith('/code')) return 'code'
  if (path.endsWith('/cases')) return 'cases'
  if (path.endsWith('/audit')) return 'audit'
  return 'description'
})

function handleTabChange(value: string | number) {
  const view = value as string
  const routeName = `problem-view-${view}`
  router.push({ name: routeName, params: { id: problemId.value } })
}

onMounted(async () => {
  if (problemId.value) {
    await problemsStore.fetchProblem(problemId.value)
    isInitialLoad.value = false
    setTimeout(() => {
      isLoaded.value = true
    }, 100)
  }
})

async function togglePublish() {
  if (!problem.value) return
  publishing.value = true
  try {
    if (problem.value.is_published) {
      await problemsStore.unpublishProblem(problemId.value)
      toast.success(t('problems.toast.unpublishSuccess'))
    } else {
      await problemsStore.publishProblem(problemId.value)
      toast.success(t('problems.toast.publishSuccess'))
    }
  } catch (error) {
    console.error('Failed to toggle publish:', error)
    toast.error(t('problems.toast.publishFailed'))
  } finally {
    publishing.value = false
  }
}

function editProblem() {
  // Navigate to the edit view corresponding to current view
  const editRoutes: Record<string, string> = {
    code: 'problem-edit-code',
    cases: 'problem-edit-cases',
    description: 'problem-edit-description',
  }
  router.push({ name: editRoutes[currentView.value], params: { id: problemId.value } })
}

async function handleVersionRestored() {
  toast.success(t('problems.versionHistory.restoreSuccess'))
  await problemsStore.fetchProblem(problemId.value)
}
</script>

<template>
  <div class="min-h-[calc(100vh-4rem)] bg-background flex flex-col">
    <!-- Terminal Header -->
    <header
      :class="[
        'sticky top-0 z-10 bg-[var(--card)] border-b border-[var(--silver-200)] dark:border-[var(--silver-300)]',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <div class="flex items-center justify-between h-14 px-4 lg:px-6">
        <!-- Left: Back & Title -->
        <div class="flex items-center gap-4 min-w-0">
          <Button
            variant="terminal"
            size="icon"
            class="h-8 w-8 border-[var(--silver-300)]"
            @click="router.push({ name: 'problems' })"
          >
            <ArrowLeft :size="18" />
          </Button>

          <div v-if="problem" class="flex items-center gap-3 min-w-0">
            <div class="flex items-center gap-2">
              <span class="terminal-prompt text-sm">problem</span>
              <span class="terminal-cursor" />
            </div>
            <h1 class="text-sm font-medium text-[var(--foreground)] truncate">
              {{ problem.title }}
            </h1>
            <div class="hidden sm:flex items-center gap-2">
              <span
                v-if="!problem.is_published"
                class="font-data text-[10px] uppercase px-2 py-0.5 border rounded-sm bg-[oklch(0.75_0.15_85/0.15)] border-[oklch(0.75_0.15_85/0.4)] text-[var(--terminal-amber)]"
              >
                {{ t('problems.published.draft') }}
              </span>
              <span
                v-if="problem.is_premium"
                class="font-data text-[10px] uppercase px-2 py-0.5 border rounded-sm bg-[oklch(0.75_0.15_85/0.15)] border-[oklch(0.75_0.15_85/0.4)] text-[var(--terminal-amber)]"
              >
                {{ t('problems.badges.premium') }}
              </span>
            </div>
          </div>
          <Skeleton v-else class="h-5 w-32" />
        </div>

        <!-- Center: Tabs (Desktop) -->
        <div class="absolute left-1/2 -translate-x-1/2 hidden md:block">
          <Tabs :model-value="currentView" @update:model-value="handleTabChange">
            <TabsList class="h-9 bg-transparent">
              <TabsTrigger
                value="description"
                class="h-7 px-3 font-data text-[10px] uppercase tracking-[0.1em] data-[state=active]:border-[var(--accent-electric)] data-[state=active]:text-[var(--accent-electric)] border-b-2 border-transparent rounded-none data-[state=active]:bg-transparent"
              >
                {{ t('problems.tabs.description') }}
              </TabsTrigger>
              <TabsTrigger
                value="code"
                class="h-7 px-3 font-data text-[10px] uppercase tracking-[0.1em] data-[state=active]:border-[var(--accent-electric)] data-[state=active]:text-[var(--accent-electric)] border-b-2 border-transparent rounded-none data-[state=active]:bg-transparent"
              >
                {{ t('problems.tabs.code') }}
              </TabsTrigger>
              <TabsTrigger
                value="cases"
                class="h-7 px-3 font-data text-[10px] uppercase tracking-[0.1em] data-[state=active]:border-[var(--accent-electric)] data-[state=active]:text-[var(--accent-electric)] border-b-2 border-transparent rounded-none data-[state=active]:bg-transparent"
              >
                {{ t('problems.tabs.testCases') }}
              </TabsTrigger>
              <TabsTrigger
                value="audit"
                class="h-7 px-3 font-data text-[10px] uppercase tracking-[0.1em] data-[state=active]:border-[var(--accent-electric)] data-[state=active]:text-[var(--accent-electric)] border-b-2 border-transparent rounded-none data-[state=active]:bg-transparent"
              >
                {{ t('problems.tabs.audit') }}
              </TabsTrigger>
            </TabsList>
          </Tabs>
        </div>

        <!-- Right: Actions -->
        <div v-if="problem" class="flex items-center gap-2">
          <Button
            variant="terminal"
            size="sm"
            class="h-8 font-data text-[10px] border-[var(--silver-300)]"
            @click="versionHistoryOpen = true"
          >
            <History :size="14" class="mr-1.5" />
            <span class="hidden sm:inline uppercase tracking-wider">{{
              t('problems.versionHistory.title')
            }}</span>
          </Button>

          <Button
            variant="terminal"
            size="sm"
            class="h-8 font-data text-[10px] border-[var(--silver-300)] hidden sm:flex"
            @click="editProblem"
          >
            <Edit :size="14" class="mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('common.edit') }}</span>
          </Button>

          <Button
            :variant="problem.is_published ? 'terminal' : 'terminal_primary'"
            size="sm"
            class="h-8 font-data text-[10px]"
            :disabled="publishing"
            @click="togglePublish"
          >
            <Eye v-if="!problem.is_published" :size="14" class="mr-1.5" />
            <EyeOff v-else :size="14" class="mr-1.5" />
            <span class="hidden sm:inline uppercase tracking-wider">{{
              problem.is_published ? t('problems.actions.unpublish') : t('problems.actions.publish')
            }}</span>
          </Button>
        </div>
      </div>

      <!-- Mobile Tabs (Below Header) -->
      <div
        class="md:hidden border-t border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
      >
        <Tabs :model-value="currentView" @update:model-value="handleTabChange" class="w-full">
          <TabsList class="w-full h-9 bg-transparent">
            <TabsTrigger
              value="description"
              class="flex-1 h-7 font-data text-[10px] uppercase tracking-[0.1em] data-[state=active]:border-[var(--accent-electric)] data-[state=active]:text-[var(--accent-electric)] border-b-2 border-transparent rounded-none data-[state=active]:bg-transparent"
            >
              {{ t('problems.tabs.description') }}
            </TabsTrigger>
            <TabsTrigger
              value="code"
              class="flex-1 h-7 font-data text-[10px] uppercase tracking-[0.1em] data-[state=active]:border-[var(--accent-electric)] data-[state=active]:text-[var(--accent-electric)] border-b-2 border-transparent rounded-none data-[state=active]:bg-transparent"
            >
              {{ t('problems.tabs.code') }}
            </TabsTrigger>
            <TabsTrigger
              value="cases"
              class="flex-1 h-7 font-data text-[10px] uppercase tracking-[0.1em] data-[state=active]:border-[var(--accent-electric)] data-[state=active]:text-[var(--accent-electric)] border-b-2 border-transparent rounded-none data-[state=active]:bg-transparent"
            >
              {{ t('problems.tabs.testCases') }}
            </TabsTrigger>
            <TabsTrigger
              value="audit"
              class="flex-1 h-7 font-data text-[10px] uppercase tracking-[0.1em] data-[state=active]:border-[var(--accent-electric)] data-[state=active]:text-[var(--accent-electric)] border-b-2 border-transparent rounded-none data-[state=active]:bg-transparent"
            >
              {{ t('problems.tabs.audit') }}
            </TabsTrigger>
          </TabsList>
        </Tabs>
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-1 w-full max-w-[1600px] mx-auto p-4 lg:p-6 lg:pt-8">
      <!-- Error State - Terminal Style -->
      <div
        v-if="problemsStore.error"
        class="flex items-center justify-between border border-[var(--terminal-red)] bg-[oklch(0.6_0.2_25/0.08)] dark:bg-[oklch(0.6_0.2_25/0.15)] p-4 mb-6"
      >
        <div class="flex items-center gap-3">
          <span class="font-data text-sm text-[var(--terminal-red)]">&gt; ERROR:</span>
          <span class="text-sm text-[var(--foreground)]">{{ problemsStore.error }}</span>
        </div>
        <div class="flex gap-2">
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--silver-300)]"
            @click="router.push({ name: 'problems' })"
          >
            {{ t('common.back') }}
          </Button>
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--terminal-red)] text-[var(--terminal-red)]"
            @click="problemsStore.fetchProblem(problemId)"
          >
            {{ t('common.retry') }}
          </Button>
        </div>
      </div>

      <!-- Loading State -->
      <div v-else-if="isInitialLoad || problemsStore.loading" class="space-y-6">
        <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
          <div class="lg:col-span-8 space-y-4">
            <Skeleton class="h-12 w-3/4 rounded-lg" />
            <Skeleton class="h-64 w-full rounded-xl" />
          </div>
          <div class="lg:col-span-4 space-y-4">
            <Skeleton class="h-32 w-full rounded-xl" />
            <Skeleton class="h-32 w-full rounded-xl" />
          </div>
        </div>
      </div>

      <!-- Not Found State - Terminal Style -->
      <div v-else-if="!problem" class="flex flex-col items-center justify-center py-24 text-center">
        <div
          class="w-12 h-12 rounded-full bg-[var(--surface-sunken)] border border-[var(--silver-200)] dark:border-[var(--silver-300)] flex items-center justify-center mb-3"
        >
          <FileText :size="24" class="text-[var(--silver-400)]" />
        </div>
        <h2 class="text-sm font-medium mb-1 font-data">{{ t('problems.view.notFound') }}</h2>
        <p class="text-xs text-[var(--silver-500)] mb-4 font-data">
          // {{ t('problems.view.notFoundDescription') }}
        </p>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--silver-300)]"
          @click="router.push({ name: 'problems' })"
        >
          {{ t('problems.view.backToProblems') }}
        </Button>
      </div>

      <!-- Problem Content -->
      <template v-else>
        <!-- Content based on current view -->
        <transition
          mode="out-in"
          enter-active-class="transition-opacity duration-200 ease-in-out"
          enter-from-class="opacity-0"
          enter-to-class="opacity-100"
          leave-active-class="transition-opacity duration-150 ease-in-out"
          leave-from-class="opacity-100"
          leave-to-class="opacity-0"
        >
          <component
            :is="
              currentView === 'description'
                ? DescriptionDisplay
                : currentView === 'code'
                  ? CodeDisplay
                  : currentView === 'cases'
                    ? CasesDisplay
                    : AuditLogViewer
            "
            :key="currentView"
            :problem="currentView !== 'audit' ? problem : undefined"
            :languages="currentView !== 'audit' ? problem.languages : undefined"
            :entity-type="currentView === 'audit' ? 'PROBLEM' : undefined"
            :entity-id="currentView === 'audit' ? problemId : undefined"
          />
        </transition>
      </template>
    </main>

    <!-- Version History Dialog -->
    <VersionHistoryTimeline
      v-model:open="versionHistoryOpen"
      :problem-id="problemId"
      @restored="handleVersionRestored"
    />
  </div>
</template>
