<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useProblemsStore } from '@/stores/admin/problems'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import { Skeleton } from '@/components/ui/skeleton'
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
import PreviewTab from './tabs/ProblemPreviewTab.vue'
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
  { value: 'preview', label: 'Preview', icon: Eye, count: null },
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
    <!-- Header -->
    <header class="sticky top-0 z-10 bg-background/95 backdrop-blur border-b">
      <div class="flex items-center gap-3 h-14 px-4 lg:px-6">
        <Button
          variant="ghost"
          size="sm"
          class="gap-1.5 text-muted-foreground px-2"
          @click="router.push({ name: 'problems' })"
        >
          <ArrowLeft :size="16" />
        </Button>

        <!-- Problem Info -->
        <div v-if="problem" class="flex items-center gap-3 min-w-0 flex-1">
          <h1 class="text-sm font-semibold truncate">{{ problem.title }}</h1>
          <span class="text-xs font-mono px-1.5 py-0.5 rounded bg-muted text-muted-foreground">
            {{ problem.slug }}
          </span>
          <Badge :class="['text-[10px] px-1.5 py-0', getDifficultyVariant(problem.difficulty)]">
            {{ problem.difficulty }}
          </Badge>
          <Badge v-if="problem.is_premium" variant="secondary" class="text-[10px] px-1.5 py-0">
            Premium
          </Badge>
        </div>

        <!-- Actions -->
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
        <div class="flex gap-6">
          <Skeleton v-for="i in 4" :key="i" class="h-12 flex-1" />
        </div>
        <Skeleton class="h-9 w-full" />
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
        <!-- Stats Bar -->
        <div class="grid grid-cols-4 gap-3 mb-6">
          <div class="flex items-center gap-2 px-3 py-2 rounded-lg border bg-muted/20">
            <ListChecks :size="14" class="text-muted-foreground" />
            <div class="min-w-0">
              <p class="text-[10px] text-muted-foreground uppercase tracking-wide">Submissions</p>
              <p class="text-sm font-medium tabular-nums">{{ problem.submission_count ?? 0 }}</p>
            </div>
          </div>
          <div class="flex items-center gap-2 px-3 py-2 rounded-lg border bg-muted/20">
            <Trophy :size="14" class="text-muted-foreground" />
            <div class="min-w-0">
              <p class="text-[10px] text-muted-foreground uppercase tracking-wide">Solutions</p>
              <p class="text-sm font-medium tabular-nums">{{ problem.solution_count ?? 0 }}</p>
            </div>
          </div>
          <div class="flex items-center gap-2 px-3 py-2 rounded-lg border bg-muted/20">
            <BarChart3 :size="14" class="text-muted-foreground" />
            <div class="min-w-0">
              <p class="text-[10px] text-muted-foreground uppercase tracking-wide">Acceptance</p>
              <p class="text-sm font-medium tabular-nums">
                {{
                  problem.submission_count && problem.solution_count
                    ? ((problem.solution_count / problem.submission_count) * 100).toFixed(1) + '%'
                    : '-'
                }}
              </p>
            </div>
          </div>
          <div class="flex items-center gap-2 px-3 py-2 rounded-lg border bg-muted/20">
            <Clock :size="14" class="text-muted-foreground" />
            <div class="min-w-0">
              <p class="text-[10px] text-muted-foreground uppercase tracking-wide">Updated</p>
              <p class="text-sm font-medium tabular-nums">
                {{ new Date(problem.updated_at).toLocaleDateString() }}
              </p>
            </div>
          </div>
        </div>

        <!-- Tabs -->
        <Tabs v-model="activeTab" class="w-full">
          <TabsList class="h-9 bg-muted/50 px-1">
            <TabsTrigger
              v-for="tab in tabItems"
              :key="tab.value"
              :value="tab.value"
              class="gap-1.5 h-8 px-3 text-xs data-[state=active]:bg-background"
            >
              <component :is="tab.icon" :size="14" />
              <span>{{ tab.label }}</span>
              <Badge v-if="tab.count" variant="secondary" class="h-4 px-1 text-[10px]">
                {{ tab.count }}
              </Badge>
            </TabsTrigger>
          </TabsList>

          <div class="mt-4">
            <TabsContent value="overview" class="mt-0">
              <OverviewTab :problem="problem" />
            </TabsContent>

            <TabsContent value="preview" class="mt-0">
              <PreviewTab :problem="problem" />
            </TabsContent>

            <TabsContent value="testcases" class="mt-0">
              <TestCasesTab :examples="problem.examples || []" />
            </TabsContent>

            <TabsContent value="submissions" class="mt-0">
              <SubmissionsTab :problem-id="problem.id" />
            </TabsContent>

            <TabsContent value="solutions" class="mt-0">
              <SolutionsTab :problem-id="problem.id" />
            </TabsContent>
          </div>
        </Tabs>
      </template>
    </main>
  </div>
</template>
