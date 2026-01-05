<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useProblemsStore } from '@/stores/admin/problems'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import { Skeleton } from '@/components/ui/skeleton'
import { Separator } from '@/components/ui/separator'
import {
  ArrowLeft,
  Edit,
  Eye,
  EyeOff,
  FileText,
  Code,
  ListChecks,
  Lightbulb,
  Trophy,
  Clock,
  BarChart3,
} from 'lucide-vue-next'
import { toast } from 'vue-sonner'
import OverviewTab from './tabs/OverviewTab.vue'
import TestCasesTab from './tabs/TestCasesTab.vue'
import SubmissionsTab from './tabs/SubmissionsTab.vue'
import SolutionsTab from './tabs/SolutionsTab.vue'

const router = useRouter()
const route = useRoute()
const problemsStore = useProblemsStore()

const activeTab = ref('overview')
const publishing = ref(false)
const isInitialLoad = ref(true)

const problemId = computed(() => route.params.id as string)
const problem = computed(() => problemsStore.currentProblem)

// Fetch problem data on mount and when id changes
onMounted(async () => {
  if (problemId.value) {
    await problemsStore.fetchProblem(problemId.value)
    isInitialLoad.value = false
  }
})

watch(problemId, async (newId) => {
  if (newId) {
    isInitialLoad.value = true
    await problemsStore.fetchProblem(newId)
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
  router.push({ name: 'problem-edit', params: { id: problemId.value } })
}

function getDifficultyVariant(difficulty: string) {
  switch (difficulty) {
    case 'EASY':
      return 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border-emerald-500/20'
    case 'MEDIUM':
      return 'bg-amber-500/10 text-amber-700 dark:text-amber-400 border-amber-500/20'
    case 'HARD':
      return 'bg-rose-500/10 text-rose-700 dark:text-rose-400 border-rose-500/20'
    default:
      return 'bg-muted text-muted-foreground'
  }
}

const tabItems = computed(() => [
  { value: 'overview', label: 'Overview', icon: FileText, count: null },
  { value: 'testcases', label: 'Examples', icon: Code, count: problem.value?.examples?.length },
  {
    value: 'submissions',
    label: 'Submissions',
    icon: ListChecks,
    count: problem.value?.submission_count,
  },
  { value: 'solutions', label: 'Solutions', icon: Lightbulb, count: problem.value?.solution_count },
])
</script>

<template>
  <div class="min-h-[calc(100vh-4rem)] bg-background">
    <!-- Header with breadcrumbs and actions -->
    <header
      class="sticky top-0 z-10 bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60 border-b"
    >
      <div class="flex h-14 items-center gap-2 px-4 lg:px-6">
        <Button
          variant="ghost"
          size="sm"
          class="gap-1.5 -ml-2 text-muted-foreground hover:text-foreground"
          @click="router.push({ name: 'problems' })"
        >
          <ArrowLeft :size="16" />
          <span class="hidden sm:inline">Back</span>
        </Button>

        <Separator orientation="vertical" class="h-4" />

        <!-- Problem Info -->
        <div v-if="problem" class="flex items-center gap-3 min-w-0 flex-1">
          <div class="flex items-center gap-2 min-w-0">
            <span
              class="text-xs font-mono px-1.5 py-0.5 rounded bg-muted text-muted-foreground truncate"
            >
              {{ problem.slug }}
            </span>
            <h1 class="text-sm font-semibold truncate">{{ problem.title }}</h1>
          </div>
          <Badge
            :class="['text-[10px] px-1.5 py-0 border', getDifficultyVariant(problem.difficulty)]"
          >
            {{ problem.difficulty }}
          </Badge>
          <Badge
            v-if="problem.is_premium"
            variant="secondary"
            class="text-[10px] px-1.5 py-0 bg-amber-500/10 text-amber-700 dark:text-amber-400 border-amber-500/20"
          >
            Premium
          </Badge>
        </div>

        <div class="flex items-center gap-2">
          <Button
            v-if="problem"
            variant="ghost"
            size="sm"
            :class="[
              'gap-1.5 text-xs',
              problem.is_published
                ? 'text-emerald-600 dark:text-emerald-400'
                : 'text-muted-foreground',
            ]"
          >
            <component :is="problem.is_published ? Eye : EyeOff" :size="14" />
            <span class="hidden md:inline">{{ problem.is_published ? 'Published' : 'Draft' }}</span>
          </Button>
          <Button
            v-if="problem"
            variant="outline"
            size="sm"
            class="gap-1.5 h-8"
            @click="editProblem"
          >
            <Edit :size="14" />
            <span class="hidden sm:inline">Edit</span>
          </Button>
          <Button
            v-if="problem"
            :variant="problem.is_published ? 'secondary' : 'default'"
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
    <main class="p-4 lg:p-6">
      <!-- Error State -->
      <div
        v-if="problemsStore.error"
        class="flex flex-col items-center justify-center py-20 text-center max-w-md mx-auto"
      >
        <div class="w-16 h-16 rounded-full bg-destructive/10 flex items-center justify-center mb-4">
          <FileText :size="32" class="text-destructive" />
        </div>
        <h2 class="text-lg font-semibold mb-2">Error Loading Problem</h2>
        <p class="text-sm text-muted-foreground mb-4">{{ problemsStore.error }}</p>
        <div class="flex gap-2">
          <Button variant="outline" size="sm" @click="router.push({ name: 'problems' })">
            <ArrowLeft :size="16" class="mr-2" />
            Back to Problems
          </Button>
          <Button variant="default" size="sm" @click="problemsStore.fetchProblem(problemId)">
            Retry
          </Button>
        </div>
      </div>

      <!-- Loading State -->
      <div v-else-if="isInitialLoad || problemsStore.loading" class="space-y-6 max-w-6xl mx-auto">
        <!-- Skeleton header -->
        <div class="flex items-start justify-between gap-4">
          <div class="space-y-3 flex-1">
            <Skeleton class="h-8 w-48" />
            <Skeleton class="h-4 w-32" />
          </div>
          <div class="flex gap-2">
            <Skeleton class="h-9 w-20" />
            <Skeleton class="h-9 w-24" />
          </div>
        </div>
        <!-- Skeleton tabs -->
        <div class="space-y-4">
          <div class="flex gap-2">
            <Skeleton v-for="i in 4" :key="i" class="h-9 w-20" />
          </div>
          <Skeleton class="h-64 w-full" />
        </div>
      </div>

      <!-- Not Found State -->
      <div
        v-else-if="!problem"
        class="flex flex-col items-center justify-center py-20 text-center max-w-md mx-auto"
      >
        <div class="w-16 h-16 rounded-full bg-muted flex items-center justify-center mb-4">
          <FileText :size="32" class="text-muted-foreground" />
        </div>
        <h2 class="text-lg font-semibold mb-2">Problem Not Found</h2>
        <p class="text-sm text-muted-foreground mb-6">
          The problem you're looking for doesn't exist or you don't have permission to view it.
        </p>
        <Button variant="outline" @click="router.push({ name: 'problems' })">
          <ArrowLeft :size="16" class="mr-2" />
          Back to Problems
        </Button>
      </div>

      <!-- Problem Content -->
      <div v-else class="max-w-6xl mx-auto space-y-6">
        <!-- Quick Stats Bar -->
        <div class="grid grid-cols-2 sm:grid-cols-4 gap-3">
          <div class="flex items-center gap-2 p-3 rounded-lg bg-muted/30 border">
            <ListChecks :size="16" class="text-muted-foreground" />
            <div class="min-w-0">
              <p class="text-[10px] uppercase tracking-wider text-muted-foreground">Submissions</p>
              <p class="text-sm font-semibold tabular-nums">{{ problem.submission_count ?? 0 }}</p>
            </div>
          </div>
          <div class="flex items-center gap-2 p-3 rounded-lg bg-muted/30 border">
            <Trophy :size="16" class="text-muted-foreground" />
            <div class="min-w-0">
              <p class="text-[10px] uppercase tracking-wider text-muted-foreground">Solutions</p>
              <p class="text-sm font-semibold tabular-nums">{{ problem.solution_count ?? 0 }}</p>
            </div>
          </div>
          <div class="flex items-center gap-2 p-3 rounded-lg bg-muted/30 border">
            <BarChart3 :size="16" class="text-muted-foreground" />
            <div class="min-w-0">
              <p class="text-[10px] uppercase tracking-wider text-muted-foreground">Acceptance</p>
              <p class="text-sm font-semibold tabular-nums">
                {{
                  problem.submission_count && problem.solution_count
                    ? ((problem.solution_count / problem.submission_count) * 100).toFixed(1) + '%'
                    : 'N/A'
                }}
              </p>
            </div>
          </div>
          <div class="flex items-center gap-2 p-3 rounded-lg bg-muted/30 border">
            <Clock :size="16" class="text-muted-foreground" />
            <div class="min-w-0">
              <p class="text-[10px] uppercase tracking-wider text-muted-foreground">Updated</p>
              <p class="text-sm font-semibold tabular-nums">
                {{ new Date(problem.updated_at).toLocaleDateString() }}
              </p>
            </div>
          </div>
        </div>

        <!-- Tabs Navigation -->
        <Tabs v-model="activeTab" class="w-full">
          <div class="flex items-center justify-between border-b rounded-t-lg bg-muted/20 px-1">
            <TabsList class="bg-transparent h-auto p-0 gap-1 border-none shadow-none">
              <TabsTrigger
                v-for="tab in tabItems"
                :key="tab.value"
                :value="tab.value"
                class="gap-1.5 rounded-md px-3 py-2 data-[state=active]:bg-background data-[state=active]:shadow-sm data-[state=active]:border data-[state=active]:border-border/50"
              >
                <component :is="tab.icon" :size="15" />
                <span class="hidden sm:inline">{{ tab.label }}</span>
                <Badge
                  v-if="tab.count"
                  variant="secondary"
                  class="h-5 px-1 text-[10px] font-medium"
                >
                  {{ tab.count }}
                </Badge>
              </TabsTrigger>
            </TabsList>
          </div>

          <div class="bg-background rounded-b-lg border-x border-b p-4 lg:p-6">
            <TabsContent
              value="overview"
              class="mt-0 focus-visible:outline-none focus-visible:ring-0"
            >
              <OverviewTab :problem="problem" />
            </TabsContent>

            <TabsContent
              value="testcases"
              class="mt-0 focus-visible:outline-none focus-visible:ring-0"
            >
              <TestCasesTab :examples="problem.examples || []" />
            </TabsContent>

            <TabsContent
              value="submissions"
              class="mt-0 focus-visible:outline-none focus-visible:ring-0"
            >
              <SubmissionsTab :problem-id="problem.id" />
            </TabsContent>

            <TabsContent
              value="solutions"
              class="mt-0 focus-visible:outline-none focus-visible:ring-0"
            >
              <SolutionsTab :problem-id="problem.id" />
            </TabsContent>
          </div>
        </Tabs>
      </div>
    </main>
  </div>
</template>
