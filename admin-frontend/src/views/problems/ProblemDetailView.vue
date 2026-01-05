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
  <div class="flex flex-col gap-4">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-4">
        <Button variant="ghost" size="icon" @click="router.push({ name: 'problems' })">
          <ArrowLeft :size="20" />
        </Button>
        <div v-if="problem">
          <div class="flex items-center gap-3">
            <h1 class="text-2xl font-bold tracking-tight">{{ problem.title }}</h1>
            <Badge :variant="getDifficultyBadgeVariant(problem.difficulty)">
              {{ problem.difficulty }}
            </Badge>
            <Badge v-if="problem.is_premium" variant="secondary">Premium</Badge>
            <Badge
              :variant="problem.is_published ? 'default' : 'secondary'"
              class="flex items-center gap-1"
            >
              <component :is="problem.is_published ? Eye : EyeOff" :size="12" />
              {{ problem.is_published ? 'Published' : 'Draft' }}
            </Badge>
          </div>
          <p class="text-muted-foreground">{{ problem.slug }}</p>
        </div>
      </div>
      <div v-if="problem" class="flex gap-2">
        <Button variant="outline" @click="editProblem">
          <Edit :size="16" class="mr-2" />
          Edit
        </Button>
        <Button
          :variant="problem.is_published ? 'secondary' : 'default'"
          :disabled="publishing"
          @click="togglePublish"
        >
          <Eye v-if="!problem.is_published" :size="16" class="mr-2" />
          <EyeOff v-else :size="16" class="mr-2" />
          {{ problem.is_published ? 'Unpublish' : 'Publish' }}
        </Button>
      </div>
    </div>

    <!-- Loading State -->
    <div v-if="problemsStore.loading" class="text-center py-8">
      <div
        class="inline-block h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent"
      ></div>
      <p class="mt-2 text-muted-foreground">Loading problem details...</p>
    </div>

    <!-- Tabs Content -->
    <div v-else-if="problem" class="w-full">
      <Tabs v-model="activeTab" class="w-full">
        <TabsList class="w-full justify-start lg:justify-center">
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="testcases">
            Test Cases
            <Badge v-if="problem.examples?.length" variant="secondary" class="ml-2">
              {{ problem.examples.length }}
            </Badge>
          </TabsTrigger>
          <TabsTrigger value="submissions">
            Submissions
            <Badge v-if="problem.submission_count" variant="secondary" class="ml-2">
              {{ problem.submission_count }}
            </Badge>
          </TabsTrigger>
          <TabsTrigger value="solutions">
            Solutions
            <Badge v-if="problem.solution_count" variant="secondary" class="ml-2">
              {{ problem.solution_count }}
            </Badge>
          </TabsTrigger>
        </TabsList>

        <TabsContent value="overview" class="mt-4">
          <OverviewTab :problem="problem" />
        </TabsContent>

        <TabsContent value="testcases" class="mt-4">
          <TestCasesTab :examples="problem.examples || []" />
        </TabsContent>

        <TabsContent value="submissions" class="mt-4">
          <SubmissionsTab :problem-id="problem.id" />
        </TabsContent>

        <TabsContent value="solutions" class="mt-4">
          <SolutionsTab :problem-id="problem.id" />
        </TabsContent>
      </Tabs>
    </div>

    <!-- Not Found State -->
    <div v-else class="flex h-96 items-center justify-center">
      <p class="text-muted-foreground">Problem not found</p>
    </div>
  </div>
</template>
