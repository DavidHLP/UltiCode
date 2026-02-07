<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useProblemsStore } from '@/stores/admin/problems'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
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
    <!-- Header -->
    <header class="sticky top-0 z-10 bg-background/95 backdrop-blur border-b">
      <div class="flex items-center justify-between h-14 px-4 lg:px-6">
        <!-- Left: Back & Title -->
        <div class="flex items-center gap-4 min-w-0">
          <Button
            variant="ghost"
            size="icon"
            class="h-8 w-8 text-muted-foreground -ml-2"
            @click="router.push({ name: 'problems' })"
          >
            <ArrowLeft :size="18" />
          </Button>

          <div v-if="problem" class="flex items-center gap-3 min-w-0">
            <h1 class="text-sm font-semibold truncate">{{ problem.title }}</h1>
            <div class="hidden sm:flex items-center gap-2">
              <Badge
                v-if="!problem.is_published"
                variant="secondary"
                class="text-[10px] px-1.5 py-0 h-5"
              >
                {{ t('problems.published.draft') }}
              </Badge>
              <Badge
                v-if="problem.is_premium"
                variant="outline"
                class="text-[10px] px-1.5 py-0 h-5 border-amber-500/20 text-amber-600 bg-amber-500/5"
              >
                {{ t('problems.badges.premium') }}
              </Badge>
            </div>
          </div>
          <Skeleton v-else class="h-5 w-32" />
        </div>

        <!-- Center: Tabs (Desktop) -->
        <div class="absolute left-1/2 -translate-x-1/2 hidden md:block">
          <Tabs :model-value="currentView" @update:model-value="handleTabChange">
            <TabsList class="h-9">
              <TabsTrigger value="description" class="text-xs h-7 px-3">{{
                t('problems.tabs.description')
              }}</TabsTrigger>
              <TabsTrigger value="code" class="text-xs h-7 px-3">{{
                t('problems.tabs.code')
              }}</TabsTrigger>
              <TabsTrigger value="cases" class="text-xs h-7 px-3">{{
                t('problems.tabs.testCases')
              }}</TabsTrigger>
              <TabsTrigger value="audit" class="text-xs h-7 px-3">{{
                t('problems.tabs.audit')
              }}</TabsTrigger>
            </TabsList>
          </Tabs>
        </div>

        <!-- Right: Actions -->
        <div v-if="problem" class="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            class="h-8 gap-1.5"
            @click="versionHistoryOpen = true"
          >
            <History :size="14" />
            <span class="hidden sm:inline">{{ t('problems.versionHistory.title') }}</span>
          </Button>

          <Button
            variant="outline"
            size="sm"
            class="h-8 gap-1.5 hidden sm:flex"
            @click="editProblem"
          >
            <Edit :size="14" />
            <span>{{ t('common.edit') }}</span>
          </Button>

          <Button
            :variant="problem.is_published ? 'outline' : 'default'"
            size="sm"
            class="h-8 gap-1.5"
            :disabled="publishing"
            @click="togglePublish"
          >
            <Eye v-if="!problem.is_published" :size="14" />
            <EyeOff v-else :size="14" />
            <span class="hidden sm:inline">{{
              problem.is_published ? t('problems.actions.unpublish') : t('problems.actions.publish')
            }}</span>
          </Button>
        </div>
      </div>

      <!-- Mobile Tabs (Below Header) -->
      <div class="md:hidden border-t p-1 bg-muted/10">
        <Tabs :model-value="currentView" @update:model-value="handleTabChange" class="w-full">
          <TabsList class="w-full h-9">
            <TabsTrigger value="description" class="flex-1 text-xs h-7">{{
              t('problems.tabs.description')
            }}</TabsTrigger>
            <TabsTrigger value="code" class="flex-1 text-xs h-7">{{
              t('problems.tabs.code')
            }}</TabsTrigger>
            <TabsTrigger value="cases" class="flex-1 text-xs h-7">{{
              t('problems.tabs.testCases')
            }}</TabsTrigger>
            <TabsTrigger value="audit" class="flex-1 text-xs h-7">{{
              t('problems.tabs.audit')
            }}</TabsTrigger>
          </TabsList>
        </Tabs>
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-1 w-full max-w-[1600px] mx-auto p-4 lg:p-6 lg:pt-8">
      <!-- Error State -->
      <div
        v-if="problemsStore.error"
        class="flex flex-col items-center justify-center py-24 text-center"
      >
        <div class="w-12 h-12 rounded-full bg-muted flex items-center justify-center mb-3">
          <FileText :size="24" class="text-muted-foreground" />
        </div>
        <h2 class="text-sm font-semibold mb-1">{{ t('problems.view.errorLoading') }}</h2>
        <p class="text-xs text-muted-foreground mb-4">{{ problemsStore.error }}</p>
        <div class="flex gap-2">
          <Button variant="outline" size="sm" @click="router.push({ name: 'problems' })">
            {{ t('common.back') }}
          </Button>
          <Button size="sm" @click="problemsStore.fetchProblem(problemId)">{{
            t('common.retry')
          }}</Button>
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

      <!-- Not Found State -->
      <div v-else-if="!problem" class="flex flex-col items-center justify-center py-24 text-center">
        <div class="w-12 h-12 rounded-full bg-muted flex items-center justify-center mb-3">
          <FileText :size="24" class="text-muted-foreground" />
        </div>
        <h2 class="text-sm font-semibold mb-1">{{ t('problems.view.notFound') }}</h2>
        <p class="text-xs text-muted-foreground mb-4">
          {{ t('problems.view.notFoundDescription') }}
        </p>
        <Button variant="outline" size="sm" @click="router.push({ name: 'problems' })">
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
