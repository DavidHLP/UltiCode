<script setup lang="ts">
import { solutionPermissionRules } from '@/composables/domainPermissionMaps'
import { usePermissionMap } from '@/composables/usePermissionMap'
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useSolutionsStore } from '@/stores/admin/solutions'
import { Button } from '@ulticode/design-system/ui/button'
import { Skeleton } from '@ulticode/design-system/ui/skeleton'
import { ArrowLeft, Flag, Eye, Trash, FileText, User } from '@lucide/vue'
import { toast } from 'vue-sonner'
import DescriptionDisplay from './components/DescriptionDisplay.vue'
import CodeDisplay from './components/CodeDisplay.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'

const router = useRouter()
const route = useRoute()
const solutionsStore = useSolutionsStore()
const can = { solution: usePermissionMap(solutionPermissionRules) }
const { t } = useI18n()

const isInitialLoad = ref(true)
const deleteDialogOpen = ref(false)
const flagDialogOpen = ref(false)

const solutionId = computed(() => route.params.id as string)
const solution = computed(() => solutionsStore.currentSolution)

const canUpdateSolution = can.solution.moderate
const canDeleteSolution = can.solution.delete

// Determine current view from route
const currentView = computed(() => {
  const path = route.path
  if (path.endsWith('/code')) return 'code'
  return 'description'
})

const tabs = computed(() => [
  { value: 'description', label: t('solutions.tabs.description') },
  { value: 'code', label: t('solutions.tabs.code') },
])

function handleTabChange(value: string) {
  router.push({ name: 'solution-detail', params: { id: solutionId.value, tab: value } })
}

onMounted(async () => {
  if (solutionId.value) {
    await solutionsStore.fetchSolution(solutionId.value)
    isInitialLoad.value = false
  }
})

async function unflagSolution() {
  if (!solution.value) return
  try {
    await solutionsStore.unflagSolution(solutionId.value)
    toast.success(t('solutions.toast.unflaggedSuccessfully'))
    await solutionsStore.fetchSolution(solutionId.value)
  } catch {
    toast.error(t('solutions.toast.failedToUnflag'))
  }
}

function handleDeleteSuccess() {
  router.push({ name: 'solutions' })
}

function handleFlagSuccess() {
  solutionsStore.fetchSolution(solutionId.value)
}

async function handleDeleteSolution(id: string | number) {
  await solutionsStore.deleteSolution(String(id))
}

async function handleFlagSolution(id: string | number, reason?: string) {
  await solutionsStore.flagSolution(String(id), { reason: reason || '' })
}

function back() {
  router.push({ name: 'solutions' })
}
</script>

<template>
  <div class="min-h-[calc(100vh-4rem)] bg-background flex flex-col">
    <!-- Page header -->
    <header class="sticky top-0 z-10 bg-card/95 backdrop-blur border-b border-border">
      <div class="flex flex-wrap items-center justify-between gap-3 px-4 py-3 lg:px-6">
        <div class="flex min-w-0 items-center gap-4">
          <Button
            variant="ghost"
            size="icon"
            class="w-8 shrink-0"
            :aria-label="t('common.back')"
            @click="back"
          >
            <ArrowLeft :size="18" />
          </Button>
          <h1 v-if="solution" class="min-w-0 break-words text-sm font-semibold text-foreground">
            {{ solution.title }}
          </h1>
          <Skeleton v-else class="h-5 w-32" />
        </div>

        <!-- Actions -->
        <div v-if="solution" class="flex flex-wrap items-center gap-2">
          <template v-if="canUpdateSolution">
            <Button v-if="solution.isFlagged" variant="outline" size="sm" @click="unflagSolution">
              <Eye :size="14" class="mr-1.5" />
              <span class="uppercase tracking-wider">{{ t('solutions.actions.unflag') }}</span>
            </Button>
            <Button v-else variant="outline" size="sm" @click="flagDialogOpen = true">
              <Flag :size="14" class="mr-1.5" />
              <span class="uppercase tracking-wider">{{ t('solutions.actions.flag') }}</span>
            </Button>
          </template>

          <Button
            v-if="canDeleteSolution"
            variant="outline"
            size="sm"
            @click="deleteDialogOpen = true"
          >
            <Trash :size="14" class="mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('solutions.actions.delete') }}</span>
          </Button>
        </div>
      </div>

      <!-- Status Ticker -->
      <div v-if="solution" class="px-4 lg:px-6 py-2.5 border-t border-border bg-muted">
        <div class="flex flex-wrap items-center gap-x-6 gap-y-2 whitespace-nowrap text-xs">
          <div class="flex items-center gap-2">
            <span class="text-sm font-medium">{{ t('solutions.columns.status') }}:</span>
            <span v-if="solution.isFlagged" class="font-mono text-foreground uppercase">
              {{ t('solutions.status.flagged') }}
            </span>
            <span v-else-if="solution.isPublished" class="font-mono text-foreground uppercase">
              {{ t('solutions.status.published') }}
            </span>
            <span v-else class="font-mono text-muted-foreground uppercase">{{ t('solutions.status.unpublished') }}</span>
          </div>
          <div class="flex min-w-0 max-w-full items-center gap-2 whitespace-normal">
            <span class="text-sm font-medium">{{ t('solutions.detail.author') }}:</span>
            <div class="flex min-w-0 items-center gap-1">
              <User :size="10" class="shrink-0 text-status-info-mark" />
              <span class="min-w-0 break-all font-mono text-foreground">{{ solution.author.username }}</span>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <span class="text-sm font-medium">{{ t('solutions.detail.views') }}:</span>
            <span class="font-mono text-muted-foreground tabular-nums">{{
              solution.views.toLocaleString()
            }}</span>
          </div>
        </div>
      </div>

      <!-- Terminal Tabs Navigation -->
      <div class="border-b border-border bg-card">
        <div class="px-4 lg:px-6 flex gap-1">
          <Button
            variant="outline"
            v-for="tab in tabs"
            :key="tab.value"
            :class="[
              'px-4 py-3 font-mono text-xs uppercase tracking-label border-b-2 transition-colors cursor-pointer',
              currentView === tab.value
                ? 'border-primary text-foreground'
                : 'border-transparent text-muted-foreground hover:text-foreground dark:hover:text-border',
            ]"
            @click="handleTabChange(tab.value)"
          >
            <span v-if="currentView === tab.value" class="text-primary">//</span>
            {{ tab.label }}
          </Button>
        </div>
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-1 w-full max-w-5xl mx-auto p-4 lg:p-6 lg:pt-8">
      <!-- Error State -->
      <div
        v-if="solutionsStore.operationError"
        class="flex flex-col items-center justify-center py-24 text-center"
      >
        <div
          class="w-12 h-12 rounded-full border-2 border-status-error-mark flex items-center justify-center mb-3"
        >
          <FileText :size="24" class="text-status-error-mark" />
        </div>
        <h2 class="text-sm font-semibold mb-1 text-foreground">
          {{ t('solutions.error.loadingSolution') }}
        </h2>
        <p class="text-xs font-mono text-muted-foreground mb-4">
          {{ solutionsStore.operationError }}
        </p>
        <div class="flex gap-2">
          <Button variant="outline" size="sm" @click="back">
            {{ t('solutions.error.back') }}
          </Button>
          <Button variant="outline" size="sm" @click="solutionsStore.fetchSolution(solutionId)">
            {{ t('solutions.error.retry') }}
          </Button>
        </div>
      </div>

      <!-- Loading State -->
      <div v-else-if="isInitialLoad || solutionsStore.operationLoading" class="space-y-6">
        <div class="space-y-4">
          <Skeleton class="h-12 w-1/3" />
          <Skeleton class="h-64 w-full" />
        </div>
      </div>

      <!-- Not Found State -->
      <div
        v-else-if="!solution"
        class="flex flex-col items-center justify-center py-24 text-center"
      >
        <div
          class="w-12 h-12 rounded-full border-2 border-status-warning-mark flex items-center justify-center mb-3"
        >
          <FileText :size="24" class="text-status-warning-mark" />
        </div>
        <h2 class="text-sm font-semibold mb-1 text-foreground">
          {{ t('solutions.error.solutionNotFound') }}
        </h2>
        <p class="text-xs font-mono text-muted-foreground mb-4">
          {{ t('solutions.error.notFoundDescription') }}
        </p>
        <Button variant="outline" size="sm" @click="back">
          {{ t('solutions.error.backToSolutions') }}
        </Button>
      </div>

      <!-- Solution Content -->
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
            :is="currentView === 'description' ? DescriptionDisplay : CodeDisplay"
            :key="currentView"
            :solution="solution"
          />
        </transition>
      </template>
    </main>

    <EntityActionDialog
      v-model:open="deleteDialogOpen"
      :entity-id="solutionId"
      :entity-title="solution?.title || null"
      action="delete"
      :title="t('solutions.delete.title')"
      :description="t('solutions.delete.description')"
      :confirm-label="t('solutions.delete.confirm')"
      :cancel-label="t('solutions.delete.cancel')"
      :success-label="t('solutions.toast.deletedSuccessfully')"
      :error-label="t('solutions.toast.failedToDelete')"
      :on-action="handleDeleteSolution"
      @success="handleDeleteSuccess"
    />

    <EntityActionDialog
      v-model:open="flagDialogOpen"
      :entity-id="solutionId"
      action="flag"
      :title="t('solutions.flag.title')"
      :description="t('solutions.flag.description')"
      :confirm-label="t('solutions.flag.confirm')"
      :cancel-label="t('solutions.flag.cancel')"
      :success-label="t('solutions.toast.flaggedSuccessfully')"
      :error-label="t('solutions.toast.failedToFlag')"
      :reason-label="t('solutions.flag.reasonLabel')"
      :reason-placeholder="t('solutions.flag.reasonPlaceholder')"
      :reason-required-label="t('solutions.toast.reasonRequired')"
      :on-action="handleFlagSolution"
      @success="handleFlagSuccess"
    />
  </div>
</template>
