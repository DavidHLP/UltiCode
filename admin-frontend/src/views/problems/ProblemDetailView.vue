<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { useProblemsStore } from '@/stores/admin/problems'
import { useAuthStore } from '@/stores/admin/auth'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

const router = useRouter()
const route = useRoute()
const problemsStore = useProblemsStore()
const authStore = useAuthStore()

const submissionsLoading = ref(false)

const canUpdateProblem = computed(() => authStore.hasPermission('UPDATE', 'PROBLEM'))
const canDeleteProblem = computed(() => authStore.hasPermission('DELETE', 'PROBLEM'))
const canPublishProblem = computed(() => authStore.hasPermission('PUBLISH', 'PROBLEM'))

const problemId = computed(() => route.params.id as string)

onMounted(() => loadProblem())

async function loadProblem() {
  await problemsStore.fetchProblem(problemId.value)
  loadSubmissions()
}

async function loadSubmissions() {
  submissionsLoading.value = true
  try {
    await problemsStore.fetchProblems() // This should be a proper API call
    // For now, just skip submissions
  } catch {
    // Ignore
  } finally {
    submissionsLoading.value = false
  }
}

function editProblem() {
  router.push({ name: 'problem-edit', params: { id: problemId.value } })
}

async function publishProblem() {
  try {
    await problemsStore.publishProblem(problemId.value)
    toast.success('Problem published successfully')
    await loadProblem()
  } catch {
    toast.error('Failed to publish problem')
  }
}

async function unpublishProblem() {
  try {
    await problemsStore.unpublishProblem(problemId.value)
    toast.success('Problem unpublished successfully')
    await loadProblem()
  } catch {
    toast.error('Failed to unpublish problem')
  }
}

async function deleteProblem() {
  if (!confirm('Are you sure you want to delete this problem?')) return

  try {
    await problemsStore.deleteProblem(problemId.value)
    toast.success('Problem deleted successfully')
    router.push({ name: 'problems' })
  } catch {
    toast.error('Failed to delete problem')
  }
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
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold tracking-tight">Problem Details</h1>
        <p class="text-muted-foreground">View and manage problem information</p>
      </div>
      <div class="flex gap-2">
        <Button variant="outline" @click="router.push({ name: 'problems' })">Back</Button>
        <Button v-if="canUpdateProblem" @click="editProblem">Edit</Button>
        <Button
          v-if="canPublishProblem && problemsStore.currentProblem?.is_published"
          variant="outline"
          @click="unpublishProblem"
        >
          Unpublish
        </Button>
        <Button
          v-if="canPublishProblem && !problemsStore.currentProblem?.is_published"
          @click="publishProblem"
        >
          Publish
        </Button>
        <Button v-if="canDeleteProblem" variant="destructive" @click="deleteProblem">
          Delete
        </Button>
      </div>
    </div>

    <div v-if="problemsStore.loading" class="text-center py-8">Loading...</div>

    <div v-else-if="problemsStore.currentProblem" class="grid gap-4 md:grid-cols-2">
      <!-- Main Info Card -->
      <Card class="md:col-span-2">
        <CardHeader>
          <CardTitle>{{ problemsStore.currentProblem.title }}</CardTitle>
          <CardDescription>{{ problemsStore.currentProblem.slug }}</CardDescription>
        </CardHeader>
        <CardContent class="space-y-4">
          <div class="flex gap-2">
            <Badge :variant="getDifficultyBadgeVariant(problemsStore.currentProblem.difficulty)">
              {{ problemsStore.currentProblem.difficulty }}
            </Badge>
            <Badge variant="outline">{{ problemsStore.currentProblem.status }}</Badge>
            <Badge v-if="problemsStore.currentProblem.is_premium" variant="secondary">
              Premium
            </Badge>
            <Badge :variant="problemsStore.currentProblem.is_published ? 'default' : 'secondary'">
              {{ problemsStore.currentProblem.is_published ? 'Published' : 'Draft' }}
            </Badge>
          </div>

          <div v-if="problemsStore.currentProblem.detail" class="space-y-2">
            <p class="text-sm">{{ problemsStore.currentProblem.detail.summary }}</p>
            <div class="flex gap-4 text-sm text-muted-foreground">
              <span>Likes: {{ problemsStore.currentProblem.detail.likes }}</span>
              <span>Dislikes: {{ problemsStore.currentProblem.detail.dislikes }}</span>
              <span>Rating: {{ problemsStore.currentProblem.detail.difficulty_rating }}</span>
            </div>
          </div>

          <div v-if="problemsStore.currentProblem.tags.length > 0" class="flex gap-2">
            <span class="text-sm font-medium">Tags:</span>
            <Badge v-for="tag in problemsStore.currentProblem.tags" :key="tag.id" variant="outline">
              {{ tag.label }}
            </Badge>
          </div>
        </CardContent>
      </Card>

      <!-- Stats Card -->
      <Card>
        <CardHeader>
          <CardTitle>Statistics</CardTitle>
        </CardHeader>
        <CardContent class="space-y-2">
          <div class="flex justify-between">
            <span class="text-muted-foreground">Submissions:</span>
            <span class="font-medium">{{
              problemsStore.currentProblem.submission_count || 0
            }}</span>
          </div>
          <div class="flex justify-between">
            <span class="text-muted-foreground">Solutions:</span>
            <span class="font-medium">{{ problemsStore.currentProblem.solution_count || 0 }}</span>
          </div>
          <div class="flex justify-between">
            <span class="text-muted-foreground">Acceptance Rate:</span>
            <span class="font-medium">
              {{ problemsStore.currentProblem.detail?.difficulty_rating || 'N/A' }}
            </span>
          </div>
          <div class="flex justify-between">
            <span class="text-muted-foreground">Has Solution:</span>
            <span class="font-medium">
              {{ problemsStore.currentProblem.has_solution ? 'Yes' : 'No' }}
            </span>
          </div>
        </CardContent>
      </Card>

      <!-- Metadata Card -->
      <Card>
        <CardHeader>
          <CardTitle>Metadata</CardTitle>
        </CardHeader>
        <CardContent class="space-y-2">
          <div class="flex justify-between">
            <span class="text-muted-foreground">Created:</span>
            <span class="font-medium">{{
              new Date(problemsStore.currentProblem.created_at).toLocaleDateString()
            }}</span>
          </div>
          <div class="flex justify-between">
            <span class="text-muted-foreground">Updated:</span>
            <span class="font-medium">{{
              new Date(problemsStore.currentProblem.updated_at).toLocaleDateString()
            }}</span>
          </div>
          <div v-if="problemsStore.currentProblem.published_at" class="flex justify-between">
            <span class="text-muted-foreground">Published:</span>
            <span class="font-medium">{{
              new Date(problemsStore.currentProblem.published_at).toLocaleDateString()
            }}</span>
          </div>
          <div v-if="problemsStore.currentProblem.is_deleted" class="flex justify-between">
            <span class="text-muted-foreground">Deleted:</span>
            <span class="font-medium text-red-600">Yes</span>
          </div>
        </CardContent>
      </Card>

      <!-- Examples Card -->
      <Card v-if="problemsStore.currentProblem.examples" class="md:col-span-2">
        <CardHeader>
          <CardTitle>Examples</CardTitle>
        </CardHeader>
        <CardContent>
          <div class="space-y-4">
            <div
              v-for="(example, idx) in problemsStore.currentProblem.examples"
              :key="example.id"
              class="p-4 border rounded-lg space-y-2"
            >
              <p class="font-medium">Example {{ idx + 1 }}</p>
              <div class="grid md:grid-cols-2 gap-4">
                <div>
                  <p class="text-sm font-medium text-muted-foreground">Input:</p>
                  <code class="text-sm">{{ example.input }}</code>
                </div>
                <div>
                  <p class="text-sm font-medium text-muted-foreground">Output:</p>
                  <code class="text-sm">{{ example.output }}</code>
                </div>
              </div>
              <p v-if="example.explanation" class="text-sm text-muted-foreground">
                {{ example.explanation }}
              </p>
            </div>
          </div>
        </CardContent>
      </Card>

      <!-- Languages Card -->
      <Card>
        <CardHeader>
          <CardTitle>Supported Languages</CardTitle>
        </CardHeader>
        <CardContent>
          <div v-if="problemsStore.currentProblem.languages" class="flex flex-wrap gap-2">
            <Badge
              v-for="lang in problemsStore.currentProblem.languages"
              :key="lang.id"
              variant="outline"
            >
              {{ lang.language }}
            </Badge>
          </div>
          <p v-else class="text-sm text-muted-foreground">No languages specified</p>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
