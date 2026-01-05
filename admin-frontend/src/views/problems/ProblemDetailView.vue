<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useProblemsStore } from '@/stores/admin/problems'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs'
import { ArrowLeft, Edit, Eye, EyeOff } from 'lucide-vue-next'
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

const problemId = computed(() => route.params.id as string)
const problem = computed(() => problemsStore.currentProblem)

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
    // Reload to get updated data
    await problemsStore.fetchProblem(problemId.value)
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

function getDifficultyBadgeVariant(difficulty: string) {
  switch (difficulty) {
    case 'EASY':
      return 'default'
    case 'MEDIUM':
      return 'secondary'
    case 'HARD':
      return 'destructive'
    default:
      return 'outline'
  }
}
</script>

<template>
  <div class="flex flex-col gap-6">
    <!-- Header -->
    <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
      <div class="flex items-start gap-4">
        <Button
          variant="ghost"
          size="icon"
          class="-ml-2 mt-1"
          @click="router.push({ name: 'problems' })"
        >
          <ArrowLeft :size="20" />
        </Button>
        <div v-if="problem" class="space-y-1">
          <div class="flex flex-wrap items-center gap-3">
            <h1 class="text-2xl font-bold tracking-tight">{{ problem.title }}</h1>
            <Badge :variant="getDifficultyBadgeVariant(problem.difficulty)">
              {{ problem.difficulty }}
            </Badge>
            <Badge
              v-if="problem.is_premium"
              variant="secondary"
              class="bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-100 hover:bg-amber-100 dark:hover:bg-amber-900 border-amber-200 dark:border-amber-800"
              >Premium</Badge
            >
          </div>
          <div class="flex items-center gap-3 text-muted-foreground">
            <code class="text-sm bg-muted px-1.5 py-0.5 rounded">{{ problem.slug }}</code>
            <div class="flex items-center gap-1.5 text-sm">
              <component :is="problem.is_published ? Eye : EyeOff" :size="14" />
              <span>{{ problem.is_published ? 'Published' : 'Draft' }}</span>
            </div>
          </div>
        </div>
      </div>

      <div v-if="problem" class="flex items-center gap-2 pl-12 lg:pl-0">
        <Button variant="outline" size="sm" @click="editProblem">
          <Edit :size="14" class="mr-2" />
          Edit Problem
        </Button>
        <Button
          :variant="problem.is_published ? 'secondary' : 'default'"
          size="sm"
          :disabled="publishing"
          @click="togglePublish"
        >
          <Eye v-if="!problem.is_published" :size="14" class="mr-2" />
          <EyeOff v-else :size="14" class="mr-2" />
          {{ problem.is_published ? 'Unpublish' : 'Publish' }}
        </Button>
      </div>
    </div>

    <!-- Loading State -->
    <div v-if="problemsStore.loading" class="flex flex-col items-center justify-center py-12">
      <div
        class="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent"
      ></div>
      <p class="mt-4 text-muted-foreground animate-pulse">Loading problem details...</p>
    </div>

    <!-- Tabs Content -->
    <div v-else-if="problem" class="w-full">
      <Tabs v-model="activeTab" class="w-full">
        <div class="border-b pb-0">
          <TabsList class="w-full justify-start bg-transparent p-0 h-auto gap-2 -mb-px">
            <TabsTrigger
              value="overview"
              class="rounded-none border-b-2 border-transparent px-4 py-2 data-[state=active]:border-primary data-[state=active]:bg-transparent data-[state=active]:shadow-none"
            >
              Overview
            </TabsTrigger>
            <TabsTrigger
              value="testcases"
              class="rounded-none border-b-2 border-transparent px-4 py-2 data-[state=active]:border-primary data-[state=active]:bg-transparent data-[state=active]:shadow-none"
            >
              Test Cases
              <Badge
                v-if="problem.examples?.length"
                variant="secondary"
                class="ml-2 h-5 px-1.5 text-xs"
              >
                {{ problem.examples.length }}
              </Badge>
            </TabsTrigger>
            <TabsTrigger
              value="submissions"
              class="rounded-none border-b-2 border-transparent px-4 py-2 data-[state=active]:border-primary data-[state=active]:bg-transparent data-[state=active]:shadow-none"
            >
              Submissions
              <Badge
                v-if="problem.submission_count"
                variant="secondary"
                class="ml-2 h-5 px-1.5 text-xs"
              >
                {{ problem.submission_count }}
              </Badge>
            </TabsTrigger>
            <TabsTrigger
              value="solutions"
              class="rounded-none border-b-2 border-transparent px-4 py-2 data-[state=active]:border-primary data-[state=active]:bg-transparent data-[state=active]:shadow-none"
            >
              Solutions
              <Badge
                v-if="problem.solution_count"
                variant="secondary"
                class="ml-2 h-5 px-1.5 text-xs"
              >
                {{ problem.solution_count }}
              </Badge>
            </TabsTrigger>
          </TabsList>
        </div>

        <TabsContent value="overview" class="mt-6 focus-visible:outline-none focus-visible:ring-0">
          <OverviewTab :problem="problem" />
        </TabsContent>

        <TabsContent value="testcases" class="mt-6 focus-visible:outline-none focus-visible:ring-0">
          <TestCasesTab :examples="problem.examples || []" />
        </TabsContent>

        <TabsContent
          value="submissions"
          class="mt-6 focus-visible:outline-none focus-visible:ring-0"
        >
          <SubmissionsTab :problem-id="problem.id" />
        </TabsContent>

        <TabsContent value="solutions" class="mt-6 focus-visible:outline-none focus-visible:ring-0">
          <SolutionsTab :problem-id="problem.id" />
        </TabsContent>
      </Tabs>
    </div>

    <!-- Not Found State -->
    <div v-else class="flex h-96 items-center justify-center flex-col gap-2">
      <div class="text-4xl">😕</div>
      <p class="text-muted-foreground text-lg">Problem not found</p>
      <Button variant="link" @click="router.push({ name: 'problems' })">Back to Problems</Button>
    </div>
  </div>
</template>
