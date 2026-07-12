<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useSolutionsStore } from '@/stores/admin/solutions'
import { useAuthStore } from '@/stores/auth'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ArrowLeft, Flag, Eye, Trash, FileText, User } from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import DescriptionDisplay from './components/DescriptionDisplay.vue'
import CodeDisplay from './components/CodeDisplay.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'

const router = useRouter()
const route = useRoute()
const solutionsStore = useSolutionsStore()
const authStore = useAuthStore()
const { t } = useI18n()

const isInitialLoad = ref(true)
const deleteDialogOpen = ref(false)
const flagDialogOpen = ref(false)

const solutionId = computed(() => route.params.id as string)
const solution = computed(() => solutionsStore.currentSolution)

const canUpdateSolution = computed(() => authStore.hasPermission('MODERATE', 'SOLUTION'))
const canDeleteSolution = computed(() => authStore.hasPermission('DELETE', 'SOLUTION'))

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
    <!-- Terminal Header -->
    <header
      class="sticky top-0 z-10 bg-[var(--card)]/95 backdrop-blur border-b border-[var(--silver-200)]"
    >
      <div class="flex items-center justify-between h-14 px-4 lg:px-6">
        <div class="flex items-center gap-4">
          <Button
            variant="ghost"
            size="icon"
            class="h-8 w-8 text-[var(--silver-400)] -ml-2 hover:bg-[var(--silver-100)] dark:hover:bg-[var(--silver-800)]"
            @click="back"
          >
            <ArrowLeft :size="18" />
          </Button>
          <h1 v-if="solution" class="text-sm font-semibold text-[var(--foreground)]">
            {{ solution.title }}
          </h1>
          <Skeleton v-else class="h-5 w-32" />
        </div>

        <!-- Actions -->
        <div v-if="solution" class="flex items-center gap-2">
          <template v-if="canUpdateSolution">
            <Button
              v-if="solution.isFlagged"
              variant="terminal"
              size="sm"
              class="h-8 font-data text-xs border-[var(--terminal-green)] text-[var(--terminal-green)] hover:bg-[color-mix(in_oklch,_var(--terminal-green)_10%,_transparent)]"
              @click="unflagSolution"
            >
              <Eye :size="14" class="mr-1.5" />
              <span class="uppercase tracking-wider">{{ t('solutions.actions.unflag') }}</span>
            </Button>
            <Button
              v-else
              variant="terminal"
              size="sm"
              class="h-8 font-data text-xs border-[var(--terminal-amber)] text-[var(--terminal-amber)] hover:bg-[color-mix(in_oklch,_var(--terminal-amber)_10%,_transparent)]"
              @click="flagDialogOpen = true"
            >
              <Flag :size="14" class="mr-1.5" />
              <span class="uppercase tracking-wider">{{ t('solutions.actions.flag') }}</span>
            </Button>
          </template>

          <Button
            v-if="canDeleteSolution"
            variant="terminal"
            size="sm"
            class="h-8 font-data text-xs border-[var(--terminal-red)] text-[var(--terminal-red)] hover:bg-[color-mix(in_oklch,_var(--terminal-red)_10%,_transparent)]"
            @click="deleteDialogOpen = true"
          >
            <Trash :size="14" class="mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('solutions.actions.delete') }}</span>
          </Button>
        </div>
      </div>

      <!-- Status Ticker -->
      <div
        v-if="solution"
        class="px-4 lg:px-6 py-2.5 border-t border-[var(--silver-200)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-6 text-xs">
          <div class="flex items-center gap-2">
            <span class="terminal-label">status:</span>
            <span
              v-if="solution.isFlagged"
              class="font-data text-[var(--terminal-amber)] uppercase"
            >
              flagged
            </span>
            <span
              v-else-if="solution.isPublished"
              class="font-data text-[var(--terminal-green)] uppercase"
            >
              published
            </span>
            <span v-else class="font-data text-[var(--silver-400)] uppercase"> unpublished </span>
          </div>
          <div class="flex items-center gap-2">
            <span class="terminal-label">author:</span>
            <div class="flex items-center gap-1">
              <User :size="10" class="text-[var(--terminal-cyan)]" />
              <span class="font-data text-[var(--terminal-cyan)]">{{
                solution.author.username
              }}</span>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <span class="terminal-label">views:</span>
            <span class="font-data text-[var(--silver-400)] tabular-nums">{{
              solution.views.toLocaleString()
            }}</span>
          </div>
        </div>
      </div>

      <!-- Terminal Tabs Navigation -->
      <div class="border-b border-[var(--silver-200)] bg-[var(--card)]">
        <div class="px-4 lg:px-6 flex gap-1">
          <button
            v-for="tab in tabs"
            :key="tab.value"
            :class="[
              'px-4 py-3 font-data text-xs uppercase tracking-label border-b-2 transition-colors cursor-pointer',
              currentView === tab.value
                ? 'border-[var(--accent-electric)] text-[var(--foreground)]'
                : 'border-transparent text-[var(--silver-400)] hover:text-[var(--silver-600)] dark:hover:text-[var(--silver-300)]',
            ]"
            @click="handleTabChange(tab.value)"
          >
            <span v-if="currentView === tab.value" class="text-[var(--accent-electric)]">//</span>
            {{ tab.label }}
          </button>
        </div>
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-1 w-full max-w-5xl mx-auto p-4 lg:p-6 lg:pt-8">
      <!-- Error State -->
      <div
        v-if="solutionsStore.error"
        class="flex flex-col items-center justify-center py-24 text-center"
      >
        <div
          class="w-12 h-12 rounded-full border-2 border-[var(--terminal-red)] flex items-center justify-center mb-3"
        >
          <FileText :size="24" class="text-[var(--terminal-red)]" />
        </div>
        <h2 class="text-sm font-semibold mb-1 text-[var(--foreground)]">
          {{ t('solutions.error.loadingSolution') }}
        </h2>
        <p class="text-xs font-data text-[var(--silver-400)] mb-4">{{ solutionsStore.error }}</p>
        <div class="flex gap-2">
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--silver-300)]"
            @click="back"
          >
            {{ t('solutions.error.back') }}
          </Button>
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--accent-electric)] text-[var(--accent-electric)]"
            @click="solutionsStore.fetchSolution(solutionId)"
          >
            {{ t('solutions.error.retry') }}
          </Button>
        </div>
      </div>

      <!-- Loading State -->
      <div v-else-if="isInitialLoad || solutionsStore.loading" class="space-y-6">
        <div class="space-y-4">
          <Skeleton class="h-12 w-1/3 rounded-none" />
          <Skeleton class="h-64 w-full rounded-none" />
        </div>
      </div>

      <!-- Not Found State -->
      <div
        v-else-if="!solution"
        class="flex flex-col items-center justify-center py-24 text-center"
      >
        <div
          class="w-12 h-12 rounded-full border-2 border-[var(--terminal-amber)] flex items-center justify-center mb-3"
        >
          <FileText :size="24" class="text-[var(--terminal-amber)]" />
        </div>
        <h2 class="text-sm font-semibold mb-1 text-[var(--foreground)]">
          {{ t('solutions.error.solutionNotFound') }}
        </h2>
        <p class="text-xs font-data text-[var(--silver-400)] mb-4">
          {{ t('solutions.error.notFoundDescription') }}
        </p>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--silver-300)]"
          @click="back"
        >
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
