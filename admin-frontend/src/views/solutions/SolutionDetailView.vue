<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useSolutionsStore } from '@/stores/admin/solutions'
import { useAuthStore } from '@/stores/auth'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ArrowLeft, Flag, Eye, FileText, Trash, User } from 'lucide-vue-next'
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

function handleTabChange(value: string | number) {
  const view = value as string
  const routeName = `solution-view-${view}`
  router.push({ name: routeName, params: { id: solutionId.value } })
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
            @click="router.push({ name: 'solutions' })"
          >
            <ArrowLeft :size="18" />
          </Button>

          <div v-if="solution" class="flex items-center gap-3 min-w-0">
            <h1 class="text-sm font-semibold truncate">{{ solution.title }}</h1>
            <div class="hidden sm:flex items-center gap-2">
              <Badge
                v-if="solution.is_flagged"
                variant="destructive"
                class="text-[10px] px-1.5 py-0 h-5"
              >
                {{ t('solutions.status.flagged') }}
              </Badge>
              <Badge
                v-if="!solution.is_published"
                variant="secondary"
                class="text-[10px] px-1.5 py-0 h-5"
              >
                {{ t('solutions.status.unpublished') }}
              </Badge>
              <Badge variant="outline" class="text-[10px] px-1.5 py-0 h-5 flex gap-1">
                <User :size="10" />
                {{ solution.author.username }}
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
                t('solutions.tabs.description')
              }}</TabsTrigger>
              <TabsTrigger value="code" class="text-xs h-7 px-3">{{
                t('solutions.tabs.code')
              }}</TabsTrigger>
            </TabsList>
          </Tabs>
        </div>

        <!-- Right: Actions -->
        <div v-if="solution" class="flex items-center gap-2">
          <template v-if="canUpdateSolution">
            <Button
              v-if="solution.is_flagged"
              variant="outline"
              size="sm"
              class="h-8 gap-1.5 hidden sm:flex text-emerald-600 hover:text-emerald-700"
              @click="unflagSolution"
            >
              <Eye :size="14" />
              <span>{{ t('solutions.actions.unflag') }}</span>
            </Button>
            <Button
              v-else
              variant="outline"
              size="sm"
              class="h-8 gap-1.5 hidden sm:flex text-amber-600 hover:text-amber-700"
              @click="flagDialogOpen = true"
            >
              <Flag :size="14" />
              <span>{{ t('solutions.actions.flag') }}</span>
            </Button>
          </template>

          <Button
            v-if="canDeleteSolution"
            variant="ghost"
            size="icon"
            class="h-8 w-8 text-destructive hover:text-destructive hover:bg-destructive/10"
            @click="deleteDialogOpen = true"
          >
            <Trash :size="16" />
          </Button>
        </div>
      </div>

      <!-- Mobile Tabs (Below Header) -->
      <div class="md:hidden border-t p-1 bg-muted/10">
        <Tabs :model-value="currentView" @update:model-value="handleTabChange" class="w-full">
          <TabsList class="w-full h-9">
            <TabsTrigger value="description" class="flex-1 text-xs h-7">{{
              t('solutions.tabs.description')
            }}</TabsTrigger>
            <TabsTrigger value="code" class="flex-1 text-xs h-7">{{
              t('solutions.tabs.code')
            }}</TabsTrigger>
          </TabsList>
        </Tabs>
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-1 w-full max-w-[1600px] mx-auto p-4 lg:p-6 lg:pt-8">
      <!-- Error State -->
      <div
        v-if="solutionsStore.error"
        class="flex flex-col items-center justify-center py-24 text-center"
      >
        <div class="w-12 h-12 rounded-full bg-muted flex items-center justify-center mb-3">
          <FileText :size="24" class="text-muted-foreground" />
        </div>
        <h2 class="text-sm font-semibold mb-1">{{ t('solutions.error.loadingSolution') }}</h2>
        <p class="text-xs text-muted-foreground mb-4">{{ solutionsStore.error }}</p>
        <div class="flex gap-2">
          <Button variant="outline" size="sm" @click="router.push({ name: 'solutions' })">
            {{ t('solutions.error.back') }}
          </Button>
          <Button size="sm" @click="solutionsStore.fetchSolution(solutionId)">
            {{ t('solutions.error.retry') }}
          </Button>
        </div>
      </div>

      <!-- Loading State -->
      <div v-else-if="isInitialLoad || solutionsStore.loading" class="space-y-6">
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
      <div
        v-else-if="!solution"
        class="flex flex-col items-center justify-center py-24 text-center"
      >
        <div class="w-12 h-12 rounded-full bg-muted flex items-center justify-center mb-3">
          <FileText :size="24" class="text-muted-foreground" />
        </div>
        <h2 class="text-sm font-semibold mb-1">{{ t('solutions.error.solutionNotFound') }}</h2>
        <p class="text-xs text-muted-foreground mb-4">
          {{ t('solutions.error.notFoundDescription') }}
        </p>
        <Button variant="outline" size="sm" @click="router.push({ name: 'solutions' })">
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
