<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useProblemsStore } from '@/stores/admin/problems'
import { useAuthStore } from '@/stores/admin/auth'
import { Difficulty, ProblemStatus } from '@/api/admin/problems'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

const router = useRouter()
const problemsStore = useProblemsStore()
const authStore = useAuthStore()

const searchQuery = ref('')
const difficultyFilter = ref<string>('all')
const statusFilter = ref<string>('all')
const publishedFilter = ref<string>('all')
const page = ref(1)
const pageSize = ref(20)
const selectedIds = ref<string[]>([])

const canCreateProblem = computed(() => authStore.hasPermission('CREATE', 'PROBLEM'))
const canUpdateProblem = computed(() => authStore.hasPermission('UPDATE', 'PROBLEM'))

onMounted(() => loadProblems())

async function loadProblems() {
  await problemsStore.fetchProblems({
    search: searchQuery.value || undefined,
    difficulty:
      difficultyFilter.value === 'all' ? undefined : (difficultyFilter.value as Difficulty),
    status: statusFilter.value === 'all' ? undefined : (statusFilter.value as ProblemStatus),
    is_published:
      publishedFilter.value === 'all'
        ? undefined
        : publishedFilter.value === 'published'
          ? true
          : false,
    page: page.value,
    limit: pageSize.value,
  })
}

function viewProblem(id: string) {
  router.push({ name: 'problem-detail', params: { id } })
}

function editProblem(id: string) {
  router.push({ name: 'problem-edit', params: { id } })
}

async function publishProblem(id: string) {
  try {
    await problemsStore.publishProblem(id)
    await loadProblems()
  } catch {
    alert('Failed to publish problem')
  }
}

async function unpublishProblem(id: string) {
  try {
    await problemsStore.unpublishProblem(id)
    await loadProblems()
  } catch {
    alert('Failed to unpublish problem')
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

function toggleSelect(id: string) {
  const idx = selectedIds.value.indexOf(id)
  if (idx === -1) {
    selectedIds.value.push(id)
  } else {
    selectedIds.value.splice(idx, 1)
  }
}

function toggleSelectAll() {
  if (selectedIds.value.length === problemsStore.problems.length) {
    selectedIds.value = []
  } else {
    selectedIds.value = problemsStore.problems.map((p) => p.id)
  }
}

async function bulkPublish() {
  try {
    await problemsStore.bulkAction({ ids: selectedIds.value, action: 'publish' })
    selectedIds.value = []
    await loadProblems()
  } catch {
    alert('Failed to publish problems')
  }
}

async function bulkUnpublish() {
  try {
    await problemsStore.bulkAction({ ids: selectedIds.value, action: 'unpublish' })
    selectedIds.value = []
    await loadProblems()
  } catch {
    alert('Failed to unpublish problems')
  }
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold tracking-tight">Problems</h1>
        <p class="text-muted-foreground">Manage problems and their content</p>
      </div>
      <Button v-if="canCreateProblem" @click="router.push({ name: 'problem-create' })">
        Create Problem
      </Button>
    </div>

    <!-- Bulk Actions -->
    <div v-if="selectedIds.length > 0" class="flex items-center gap-2 p-4 bg-muted rounded-lg">
      <span class="text-sm">{{ selectedIds.length }} selected</span>
      <Button size="sm" variant="default" @click="bulkPublish">Publish All</Button>
      <Button size="sm" variant="outline" @click="bulkUnpublish">Unpublish All</Button>
      <Button size="sm" variant="ghost" @click="selectedIds = []">Cancel</Button>
    </div>

    <Card>
      <CardHeader>
        <div class="flex items-center gap-4">
          <Input
            v-model="searchQuery"
            placeholder="Search problems..."
            class="max-w-sm"
            @keyup.enter="loadProblems()"
          />
          <Select v-model="difficultyFilter" @update:model-value="loadProblems()">
            <SelectTrigger class="w-[180px]">
              <SelectValue placeholder="Filter by difficulty" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Difficulties</SelectItem>
              <SelectItem value="EASY">Easy</SelectItem>
              <SelectItem value="MEDIUM">Medium</SelectItem>
              <SelectItem value="HARD">Hard</SelectItem>
            </SelectContent>
          </Select>
          <Select v-model="statusFilter" @update:model-value="loadProblems()">
            <SelectTrigger class="w-[180px]">
              <SelectValue placeholder="Filter by status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Status</SelectItem>
              <SelectItem value="todo">Todo</SelectItem>
              <SelectItem value="attempted">Attempted</SelectItem>
              <SelectItem value="solved">Solved</SelectItem>
            </SelectContent>
          </Select>
          <Select v-model="publishedFilter" @update:model-value="loadProblems()">
            <SelectTrigger class="w-[180px]">
              <SelectValue placeholder="Filter by published" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All</SelectItem>
              <SelectItem value="published">Published</SelectItem>
              <SelectItem value="unpublished">Unpublished</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </CardHeader>
      <CardContent>
        <div v-if="problemsStore.loading" class="text-center py-8">Loading...</div>

        <div v-else-if="problemsStore.error" class="text-center py-8 text-red-600">
          {{ problemsStore.error }}
        </div>

        <div v-else>
          <table class="w-full">
            <thead>
              <tr class="border-b">
                <th class="text-left p-2">
                  <input type="checkbox" @change="toggleSelectAll()" />
                </th>
                <th class="text-left p-2">ID</th>
                <th class="text-left p-2">Title</th>
                <th class="text-left p-2">Difficulty</th>
                <th class="text-left p-2">Status</th>
                <th class="text-left p-2">Published</th>
                <th class="text-left p-2">Submissions</th>
                <th class="text-left p-2">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="problem in problemsStore.problems"
                :key="problem.id"
                class="border-b hover:bg-muted/50"
              >
                <td class="p-2">
                  <input
                    type="checkbox"
                    :checked="selectedIds.includes(problem.id)"
                    @change="toggleSelect(problem.id)"
                  />
                </td>
                <td class="p-2 text-muted-foreground">{{ problem.id.slice(0, 8) }}</td>
                <td class="p-2">
                  <div>
                    <p class="font-medium">{{ problem.title }}</p>
                    <p class="text-sm text-muted-foreground">{{ problem.slug }}</p>
                  </div>
                </td>
                <td class="p-2">
                  <Badge :variant="getDifficultyBadgeVariant(problem.difficulty)">
                    {{ problem.difficulty }}
                  </Badge>
                </td>
                <td class="p-2">
                  <Badge variant="outline">{{ problem.status }}</Badge>
                </td>
                <td class="p-2">
                  <Badge v-if="problem.is_published" variant="default">Published</Badge>
                  <Badge v-else variant="secondary">Draft</Badge>
                </td>
                <td class="p-2">{{ problem.submission_count || 0 }}</td>
                <td class="p-2">
                  <div class="flex gap-2">
                    <Button size="sm" variant="ghost" @click="viewProblem(problem.id)">
                      View
                    </Button>
                    <Button
                      v-if="canUpdateProblem"
                      size="sm"
                      variant="ghost"
                      @click="editProblem(problem.id)"
                    >
                      Edit
                    </Button>
                    <Button
                      v-if="canUpdateProblem"
                      size="sm"
                      :variant="problem.is_published ? 'outline' : 'default'"
                      @click="
                        problem.is_published
                          ? unpublishProblem(problem.id)
                          : publishProblem(problem.id)
                      "
                    >
                      {{ problem.is_published ? 'Unpublish' : 'Publish' }}
                    </Button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>

          <div
            v-if="problemsStore.problems.length === 0"
            class="text-center py-8 text-muted-foreground"
          >
            No problems found
          </div>

          <div class="flex items-center justify-between pt-4 border-t">
            <p class="text-sm text-muted-foreground">
              Showing {{ problemsStore.problems.length }} of {{ problemsStore.total }} problems
            </p>
            <div class="flex gap-2">
              <Button
                size="sm"
                variant="outline"
                :disabled="page === 1"
                @click="
                  () => {
                    page--
                    loadProblems()
                  }
                "
              >
                Previous
              </Button>
              <Button
                size="sm"
                variant="outline"
                :disabled="page * pageSize >= problemsStore.total"
                @click="
                  () => {
                    page++
                    loadProblems()
                  }
                "
              >
                Next
              </Button>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
