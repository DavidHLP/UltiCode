<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useProblemsStore } from '@/stores/admin/problems'
import { useProblemTabData } from './composables/useProblemTabData'
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
const versionHistoryOpen = ref(false)

const problemId = computed(() => route.params.id as string)

const {
  currentTab,
  headerData,
  descriptionData,
  codeData,
  casesData,
  isLoading,
  error: currentError,
  isReady,
  loadTabData,
} = useProblemTabData(problemId)

function handleTabChange(value: string | number) {
  const tab = value as string
  if (tab === currentTab.value) return
  router.push({ name: 'problem-detail', params: { id: problemId.value, tab } })
}

async function togglePublish() {
  if (!headerData.value) return
  publishing.value = true
  try {
    if (headerData.value.isPublished) {
      await problemsStore.unpublishProblem(problemId.value)
      toast.success(t('problems.toast.unpublishSuccess'))
    } else {
      await problemsStore.publishProblem(problemId.value)
      toast.success(t('problems.toast.publishSuccess'))
    }
    await problemsStore.fetchHeader(problemId.value, true)
  } catch (error) {
    console.error('Failed to toggle publish:', error)
    toast.error(t('problems.toast.publishFailed'))
  } finally {
    publishing.value = false
  }
}

function editProblem() {
  const editTabs: Record<string, string> = {
    code: 'code',
    cases: 'cases',
    description: 'description',
    audit: 'description',
  }
  router.push({
    name: 'problem-edit',
    params: { id: problemId.value, tab: editTabs[currentTab.value] },
  })
}

async function handleVersionRestored() {
  toast.success(t('problems.versionHistory.restoreSuccess'))
  await loadTabData()
}

function retryFetch() {
  loadTabData()
}
</script>

<template>
  <div class="min-h-[calc(100vh-4rem)] bg-background flex flex-col">
    <!-- Terminal Header -->
    <header
      :class="[
        'sticky top-0 z-10 bg-[var(--card)] border-b border-[var(--border-subtle)] dark:border-[var(--border-subtle)]',
        'transition-all duration-500',
        isReady ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <div class="flex items-center justify-between h-14 px-4 lg:px-6">
        <!-- Left: Back & Title -->
        <div class="flex items-center gap-4 min-w-0">
          <Button
            variant="terminal"
            size="icon"
            class="h-8 w-8 border-[var(--border-subtle)]"
            @click="router.push({ name: 'problems' })"
          >
            <ArrowLeft :size="18" />
          </Button>

          <div v-if="headerData" class="flex items-center gap-3 min-w-0">
            <div class="flex items-center gap-2"></div>
            <h1 class="text-sm font-medium text-[var(--foreground)] truncate">
              {{ headerData.title }}
            </h1>
            <div class="hidden sm:flex items-center gap-2">
              <span
                v-if="!headerData.isPublished"
                class="font-data text-2xs uppercase px-2 py-0.5 border rounded-none bg-[color-mix(in_oklch,_var(--status-warning-mark)_15%,_transparent)] border-[color-mix(in_oklch,_var(--status-warning-mark)_40%,_transparent)] text-foreground-strong"
              >
                {{ t('problems.published.draft') }}
              </span>
              <span
                v-if="headerData.isPremium"
                class="font-data text-2xs uppercase px-2 py-0.5 border rounded-none bg-[color-mix(in_oklch,_var(--status-warning-mark)_15%,_transparent)] border-[color-mix(in_oklch,_var(--status-warning-mark)_40%,_transparent)] text-foreground-strong"
              >
                {{ t('problems.badges.premium') }}
              </span>
            </div>
          </div>
          <Skeleton v-else class="h-5 w-32" />
        </div>

        <!-- Center: Tabs (Desktop) -->
        <div class="absolute left-1/2 -translate-x-1/2 hidden md:block">
          <Tabs :model-value="currentTab" @update:model-value="handleTabChange">
            <TabsList class="h-9 bg-transparent">
              <TabsTrigger
                value="description"
                class="h-7 px-3 font-data text-2xs uppercase tracking-wide data-[state=active]:border-[var(--primary)] data-[state=active]:text-[var(--primary)] border-b-2 border-transparent rounded-none data-[state=active]:bg-transparent"
              >
                {{ t('problems.tabs.description') }}
              </TabsTrigger>
              <TabsTrigger
                value="code"
                class="h-7 px-3 font-data text-2xs uppercase tracking-wide data-[state=active]:border-[var(--primary)] data-[state=active]:text-[var(--primary)] border-b-2 border-transparent rounded-none data-[state=active]:bg-transparent"
              >
                {{ t('problems.tabs.code') }}
              </TabsTrigger>
              <TabsTrigger
                value="cases"
                class="h-7 px-3 font-data text-2xs uppercase tracking-wide data-[state=active]:border-[var(--primary)] data-[state=active]:text-[var(--primary)] border-b-2 border-transparent rounded-none data-[state=active]:bg-transparent"
              >
                {{ t('problems.tabs.testCases') }}
              </TabsTrigger>
              <TabsTrigger
                value="audit"
                class="h-7 px-3 font-data text-2xs uppercase tracking-wide data-[state=active]:border-[var(--primary)] data-[state=active]:text-[var(--primary)] border-b-2 border-transparent rounded-none data-[state=active]:bg-transparent"
              >
                {{ t('problems.tabs.audit') }}
              </TabsTrigger>
            </TabsList>
          </Tabs>
        </div>

        <!-- Right: Actions -->
        <div v-if="headerData" class="flex items-center gap-2">
          <Button
            variant="terminal"
            size="sm"
            class="h-8 font-data text-2xs border-[var(--border-subtle)]"
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
            class="h-8 font-data text-2xs border-[var(--border-subtle)] hidden sm:flex"
            @click="editProblem"
          >
            <Edit :size="14" class="mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('common.edit') }}</span>
          </Button>

          <Button
            :variant="headerData.isPublished ? 'terminal' : 'terminal_primary'"
            size="sm"
            class="h-8 font-data text-2xs"
            :disabled="publishing"
            @click="togglePublish"
          >
            <Eye v-if="!headerData.isPublished" :size="14" class="mr-1.5" />
            <EyeOff v-else :size="14" class="mr-1.5" />
            <span class="hidden sm:inline uppercase tracking-wider">{{
              headerData.isPublished
                ? t('problems.actions.unpublish')
                : t('problems.actions.publish')
            }}</span>
          </Button>
        </div>
      </div>

      <!-- Mobile Tabs (Below Header) -->
      <div
        class="md:hidden border-t border-[var(--border-subtle)] dark:border-[var(--border-subtle)] bg-[var(--surface-sunken)]"
      >
        <Tabs :model-value="currentTab" @update:model-value="handleTabChange" class="w-full">
          <TabsList class="w-full h-9 bg-transparent">
            <TabsTrigger
              value="description"
              class="flex-1 h-7 font-data text-2xs uppercase tracking-wide data-[state=active]:border-[var(--primary)] data-[state=active]:text-[var(--primary)] border-b-2 border-transparent rounded-none data-[state=active]:bg-transparent"
            >
              {{ t('problems.tabs.description') }}
            </TabsTrigger>
            <TabsTrigger
              value="code"
              class="flex-1 h-7 font-data text-2xs uppercase tracking-wide data-[state=active]:border-[var(--primary)] data-[state=active]:text-[var(--primary)] border-b-2 border-transparent rounded-none data-[state=active]:bg-transparent"
            >
              {{ t('problems.tabs.code') }}
            </TabsTrigger>
            <TabsTrigger
              value="cases"
              class="flex-1 h-7 font-data text-2xs uppercase tracking-wide data-[state=active]:border-[var(--primary)] data-[state=active]:text-[var(--primary)] border-b-2 border-transparent rounded-none data-[state=active]:bg-transparent"
            >
              {{ t('problems.tabs.testCases') }}
            </TabsTrigger>
            <TabsTrigger
              value="audit"
              class="flex-1 h-7 font-data text-2xs uppercase tracking-wide data-[state=active]:border-[var(--primary)] data-[state=active]:text-[var(--primary)] border-b-2 border-transparent rounded-none data-[state=active]:bg-transparent"
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
        class="flex items-center justify-between border border-[var(--status-error-mark)] bg-[color-mix(in_oklch,_var(--status-error-mark)_8%,_transparent)] dark:bg-[color-mix(in_oklch,_var(--status-error-mark)_15%,_transparent)] p-4 mb-6"
      >
        <div class="flex items-center gap-3">
          <span class="font-data text-sm text-[var(--foreground-strong)]">&gt; ERROR:</span>
          <span class="text-sm text-[var(--foreground)]">{{ currentError }}</span>
        </div>
        <div class="flex gap-2">
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--border-subtle)]"
            @click="router.push({ name: 'problems' })"
          >
            {{ t('common.back') }}
          </Button>
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--status-error-mark)] text-foreground-strong"
            @click="retryFetch"
          >
            {{ t('common.retry') }}
          </Button>
        </div>
      </div>

      <!-- Loading State -->
      <div v-else-if="isLoading" class="space-y-6">
        <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
          <div class="lg:col-span-8 space-y-4">
            <Skeleton class="h-12 w-3/4 rounded-none" />
            <Skeleton class="h-64 w-full rounded-none" />
          </div>
          <div class="lg:col-span-4 space-y-4">
            <Skeleton class="h-32 w-full rounded-none" />
            <Skeleton class="h-32 w-full rounded-none" />
          </div>
        </div>
      </div>

      <!-- Not Found State - Terminal Style -->
      <div
        v-else-if="!headerData"
        class="flex flex-col items-center justify-center py-24 text-center"
      >
        <div
          class="w-12 h-12 rounded-full bg-[var(--surface-sunken)] border border-[var(--border-subtle)] dark:border-[var(--border-subtle)] flex items-center justify-center mb-3"
        >
          <FileText :size="24" class="text-[var(--foreground-muted)]" />
        </div>
        <h2 class="text-sm font-medium mb-1 font-data">{{ t('problems.view.notFound') }}</h2>
        <p class="text-xs text-[var(--foreground-muted)] mb-4 font-data">
          // {{ t('problems.view.notFoundDescription') }}
        </p>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--border-subtle)]"
          @click="router.push({ name: 'problems' })"
        >
          {{ t('problems.view.backToProblems') }}
        </Button>
      </div>

      <!-- Problem Content -->
      <template v-else>
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
              currentTab === 'description'
                ? DescriptionDisplay
                : currentTab === 'code'
                  ? CodeDisplay
                  : currentTab === 'cases'
                    ? CasesDisplay
                    : AuditLogViewer
            "
            :key="currentTab"
            :problem="
              currentTab === 'description'
                ? descriptionData
                : currentTab === 'cases'
                  ? casesData
                  : undefined
            "
            :languages="currentTab === 'code' ? codeData?.languages : undefined"
            :entity-type="currentTab === 'audit' ? 'PROBLEM' : undefined"
            :entity-id="currentTab === 'audit' ? problemId : undefined"
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
