<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useProblemsStore } from '@/stores/admin/problems'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { Separator } from '@/components/ui/separator'
import {
  ArrowLeft,
  Edit,
  Eye,
  EyeOff,
  FileText,
  ListChecks,
  Trophy,
  BarChart3,
} from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import DescriptionDisplay from './components/DescriptionDisplay.vue'
import CodeDisplay from './components/CodeDisplay.vue'
import CasesDisplay from './components/CasesDisplay.vue'

const router = useRouter()
const route = useRoute()
const problemsStore = useProblemsStore()

const publishing = ref(false)
const isInitialLoad = ref(true)

const problemId = computed(() => route.params.id as string)
const problem = computed(() => problemsStore.currentProblem)

// Determine current view from route
const currentView = computed(() => {
  const path = route.path
  if (path.endsWith('/code')) return 'code'
  if (path.endsWith('/cases')) return 'cases'
  return 'description'
})

// Page title based on current view
const pageTitle = computed(() => {
  switch (currentView.value) {
    case 'code':
      return 'Code'
    case 'cases':
      return 'Test Cases'
    default:
      return 'Description'
  }
})

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
      toast.success('Problem unpublished')
    } else {
      await problemsStore.publishProblem(problemId.value)
      toast.success('Problem published')
    }
  } catch (error) {
    console.error('Failed to toggle publish:', error)
    toast.error('Failed to update publish status')
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

const acceptanceRate = computed(() => {
  if (!problem.value?.submission_count || !problem.value?.solution_count) return '0.0'
  return ((problem.value.solution_count / problem.value.submission_count) * 100).toFixed(1)
})
</script>

<template>
  <div class="min-h-[calc(100vh-4rem)] bg-background">
    <!-- Header -->
    <header class="sticky top-0 z-10 bg-background/95 backdrop-blur border-b">
      <div class="flex items-center justify-between h-14 px-4 lg:px-6">
        <!-- Left: Back & Title -->
        <div class="flex items-center gap-3 min-w-0 flex-1">
          <Button
            variant="ghost"
            size="sm"
            class="gap-1.5 text-muted-foreground px-2 shrink-0"
            @click="router.push({ name: 'problems' })"
          >
            <ArrowLeft :size="16" />
          </Button>

          <div v-if="problem" class="flex items-center gap-2 min-w-0">
            <h1 class="text-sm font-semibold truncate">{{ problem.title }}</h1>
            <Badge
              v-if="!problem.is_published"
              variant="secondary"
              class="text-[10px] px-1.5 py-0 shrink-0"
            >
              Draft
            </Badge>
            <Badge
              v-if="problem.is_premium"
              variant="outline"
              class="text-[10px] px-1.5 py-0 shrink-0"
            >
              Premium
            </Badge>
            <Badge variant="outline" class="text-[10px] px-1.5 py-0 shrink-0 text-muted-foreground">
              {{ pageTitle }}
            </Badge>
          </div>
          <Skeleton v-else class="h-5 w-32" />
        </div>

        <!-- Right: Actions -->
        <div v-if="problem" class="flex items-center gap-1.5">
          <Button variant="ghost" size="sm" class="gap-1.5 h-8 text-xs" @click="editProblem">
            <Edit :size="14" />
            <span class="hidden sm:inline">Edit</span>
          </Button>
          <Button
            :variant="problem.is_published ? 'outline' : 'default'"
            size="sm"
            class="gap-1.5 h-8"
            :disabled="publishing"
            @click="togglePublish"
          >
            <Eye v-if="!problem.is_published" :size="14" />
            <EyeOff v-else :size="14" />
            <span class="hidden sm:inline">{{
              problem.is_published ? 'Unpublish' : 'Publish'
            }}</span>
          </Button>
        </div>
      </div>
    </header>

    <!-- Main Content -->
    <main class="max-w-6xl mx-auto p-4 lg:p-6">
      <!-- Error State -->
      <div
        v-if="problemsStore.error"
        class="flex flex-col items-center justify-center py-24 text-center"
      >
        <div class="w-12 h-12 rounded-full bg-muted flex items-center justify-center mb-3">
          <FileText :size="24" class="text-muted-foreground" />
        </div>
        <h2 class="text-sm font-semibold mb-1">Error Loading Problem</h2>
        <p class="text-xs text-muted-foreground mb-4">{{ problemsStore.error }}</p>
        <div class="flex gap-2">
          <Button variant="outline" size="sm" @click="router.push({ name: 'problems' })">
            Back
          </Button>
          <Button size="sm" @click="problemsStore.fetchProblem(problemId)"> Retry </Button>
        </div>
      </div>

      <!-- Loading State -->
      <div v-else-if="isInitialLoad || problemsStore.loading" class="space-y-4">
        <Skeleton class="h-7 w-48 mb-6" />
        <div class="grid grid-cols-4 gap-3 mb-6">
          <Skeleton v-for="i in 4" :key="i" class="h-16" />
        </div>
        <Skeleton class="h-64 w-full" />
      </div>

      <!-- Not Found State -->
      <div v-else-if="!problem" class="flex flex-col items-center justify-center py-24 text-center">
        <div class="w-12 h-12 rounded-full bg-muted flex items-center justify-center mb-3">
          <FileText :size="24" class="text-muted-foreground" />
        </div>
        <h2 class="text-sm font-semibold mb-1">Problem Not Found</h2>
        <p class="text-xs text-muted-foreground mb-4">
          The problem doesn't exist or you don't have permission to view it.
        </p>
        <Button variant="outline" size="sm" @click="router.push({ name: 'problems' })">
          Back to Problems
        </Button>
      </div>

      <!-- Problem Content -->
      <template v-else>
        <!-- Content based on current view -->
        <DescriptionDisplay v-if="currentView === 'description'" :problem="problem" />
        <CodeDisplay v-else-if="currentView === 'code'" :languages="problem.languages" />
        <CasesDisplay v-else-if="currentView === 'cases'" :problem="problem" />
      </template>
    </main>
  </div>
</template>
