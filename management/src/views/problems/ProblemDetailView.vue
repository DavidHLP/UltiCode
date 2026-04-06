<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
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

// Use headerData for basic problem info (title, badges, etc)
const headerInfo = computed(() => problemsStore.headerData)

// Tab-specific data
const descriptionData = computed(() => problemsStore.descriptionData)
const codeData = computed(() => problemsStore.codeData)
const casesData = computed(() => problemsStore.casesData)

// Loading state for current tab
const isLoading = computed(() => {
  switch (currentView.value) {
    case 'description':
      return problemsStore.descriptionLoading || problemsStore.headerLoading
    case 'code':
      return problemsStore.codeLoading || problemsStore.headerLoading
    case 'cases':
      return problemsStore.casesLoading || problemsStore.headerLoading
    case 'audit':
      return problemsStore.headerLoading
    default:
      return false
  }
})

// Error state for current tab
const currentError = computed(() => {
  switch (currentView.value) {
    case 'description':
      return problemsStore.descriptionError || problemsStore.headerError
    case 'code':
      return problemsStore.codeError || problemsStore.headerError
    case 'cases':
      return problemsStore.casesError || problemsStore.headerError
    default:
      return problemsStore.headerError
  }
})

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
  router.push({ name: 'problem-detail', params: { id: problemId.value, tab: view } })
}

// Fetch data for current view
async function fetchCurrentViewData() {
  if (!problemId.value) return

  // Always fetch header data first (for title, badges)
  await problemsStore.fetchHeader(problemId.value)

  // Then fetch tab-specific data
  switch (currentView.value) {
    case 'description':
      await problemsStore.fetchDescription(problemId.value)
      break
    case 'code':
      await problemsStore.fetchCode(problemId.value)
      break
    case 'cases':
      await problemsStore.fetchCases(problemId.value)
      break
    case 'audit':
      // No additional data needed for audit view
      break
  }

  isInitialLoad.value = false
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
}

// Watch for view changes to fetch appropriate data
watch(currentView, () => {
  isLoaded.value = false
  fetchCurrentViewData()
})

onMounted(() => {
  fetchCurrentViewData()
})

async function togglePublish() {
  if (!headerInfo.value) return
  publishing.value = true
  try {
    if (headerInfo.value.isPublished) {
      await problemsStore.unpublishProblem(problemId.value)
      toast.success(t('problems.toast.unpublishSuccess'))
    } else {
      await problemsStore.publishProblem(problemId.value)
      toast.success(t('problems.toast.publishSuccess'))
    }
    // Refresh header data after toggling publish state
    await problemsStore.fetchHeader(problemId.value, true)
  } catch (error) {
    console.error('Failed to toggle publish:', error)
    toast.error(t('problems.toast.publishFailed'))
  } finally {
    publishing.value = false
  }
}

function editProblem() {
  // Map current view to valid edit tab (audit has no edit view, default to description)
  const editTabs: Record<string, string> = {
    code: 'code',
    cases: 'cases',
    description: 'description',
    audit: 'description',
  }
  router.push({
    name: 'problem-edit',
    params: { id: problemId.value, tab: editTabs[currentView.value] },
  })
}

async function handleVersionRestored() {
  toast.success(t('problems.versionHistory.restoreSuccess'))
  // Refresh current view data after restore
  await fetchCurrentViewData()
}

function retryFetch() {
  fetchCurrentViewData()
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

          <div v-if="headerInfo" class="flex items-center gap-3 min-w-0">
            <div class="flex items-center gap-2">
              <span class="terminal-prompt text-sm">problem</span>
              <span class="terminal-cursor" />
            </div>
            <h1 class="text-sm font-medium text-[var(--foreground)] truncate">
              {{ headerInfo.title }}
            </h1>
            <div class="hidden sm:flex items-center gap-2">
              <span
                v-if="!headerInfo.isPublished"
                class="font-data text-[10px] uppercase px-2 py-0.5 border rounded-sm bg-[color-mix(in_oklch,_var(--terminal-amber)_15%,_transparent)] border-[color-mix(in_oklch,_var(--terminal-amber)_40%,_transparent)] text-[var(--terminal-amber)]"
              >
                {{ t('problems.published.draft') }}
              </span>
              <span
                v-if="headerInfo.isPremium"
                class="font-data text-[10px] uppercase px-2 py-0.5 border rounded-sm bg-[color-mix(in_oklch,_var(--terminal-amber)_15%,_transparent)] border-[color-mix(in_oklch,_var(--terminal-amber)_40%,_transparent)] text-[var(--terminal-amber)]"
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
        <div v-if="headerInfo" class="flex items-center gap-2">
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
            :variant="headerInfo.isPublished ? 'terminal' : 'terminal_primary'"
            size="sm"
            class="h-8 font-data text-[10px]"
            :disabled="publishing"
            @click="togglePublish"
          >
            <Eye v-if="!headerInfo.isPublished" :size="14" class="mr-1.5" />
            <EyeOff v-else :size="14" class="mr-1.5" />
            <span class="hidden sm:inline uppercase tracking-wider">{{
              headerInfo.isPublished
                ? t('problems.actions.unpublish')
                : t('problems.actions.publish')
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
        v-if="currentError"
        class="flex items-center justify-between border border-[var(--terminal-red)] bg-[color-mix(in_oklch,_var(--terminal-red)_8%,_transparent)] dark:bg-[color-mix(in_oklch,_var(--terminal-red)_15%,_transparent)] p-4 mb-6"
      >
        <div class="flex items-center gap-3">
          <span class="font-data text-sm text-[var(--terminal-red)]">&gt; ERROR:</span>
          <span class="text-sm text-[var(--foreground)]">{{ currentError }}</span>
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
            @click="retryFetch"
          >
            {{ t('common.retry') }}
          </Button>
        </div>
      </div>

      <!-- Loading State -->
      <div v-else-if="isInitialLoad || isLoading" class="space-y-6">
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
      <div
        v-else-if="!headerInfo"
        class="flex flex-col items-center justify-center py-24 text-center"
      >
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
            :problem="
              currentView === 'description'
                ? descriptionData
                : currentView === 'cases'
                  ? casesData
                  : undefined
            "
            :languages="currentView === 'code' ? codeData?.languages : undefined"
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
