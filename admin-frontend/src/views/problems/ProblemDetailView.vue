<script setup lang="ts">
import { onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useProblemsStore } from '@/stores/admin/problems'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

const router = useRouter()
const route = useRoute()
const problemsStore = useProblemsStore()

const problemId = computed(() => route.params.id as string)

onMounted(() => loadProblem())

async function loadProblem() {
  await problemsStore.fetchProblem(problemId.value)
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
        <p class="text-muted-foreground">Detailed view of the problem information</p>
      </div>
      <div class="flex gap-2">
        <Button variant="outline" @click="router.push({ name: 'problems' })">Back to List</Button>
      </div>
    </div>

    <div v-if="problemsStore.loading" class="text-center py-8">
      <div
        class="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent mx-auto"
      ></div>
      <p class="mt-2 text-muted-foreground">Loading problem details...</p>
    </div>

    <div v-else-if="problemsStore.currentProblem" class="grid gap-4 md:grid-cols-2">
      <!-- Main Info Card -->
      <Card class="md:col-span-2">
        <CardHeader>
          <CardTitle>{{ problemsStore.currentProblem.title }}</CardTitle>
          <CardDescription>{{ problemsStore.currentProblem.slug }}</CardDescription>
        </CardHeader>
        <CardContent class="space-y-4">
          <div class="flex gap-2 flex-wrap">
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

          <div v-if="problemsStore.currentProblem.detail" class="space-y-4">
            <div class="prose prose-sm dark:prose-invert max-w-none">
              <h4 class="text-sm font-medium uppercase tracking-wider text-muted-foreground">
                Summary
              </h4>
              <p>{{ problemsStore.currentProblem.detail.summary }}</p>
            </div>

            <div class="flex gap-6 text-sm text-muted-foreground">
              <div class="flex items-center gap-1">
                <span class="font-medium text-foreground">Likes:</span>
                {{ problemsStore.currentProblem.detail.likes }}
              </div>
              <div class="flex items-center gap-1">
                <span class="font-medium text-foreground">Dislikes:</span>
                {{ problemsStore.currentProblem.detail.dislikes }}
              </div>
              <div class="flex items-center gap-1">
                <span class="font-medium text-foreground">Rating:</span>
                {{ problemsStore.currentProblem.detail.difficulty_rating }}
              </div>
            </div>
          </div>

          <div v-if="problemsStore.currentProblem.tags.length > 0" class="flex items-center gap-2">
            <span class="text-sm font-medium">Tags:</span>
            <div class="flex flex-wrap gap-1">
              <Badge
                v-for="tag in problemsStore.currentProblem.tags"
                :key="tag.id"
                variant="outline"
              >
                {{ tag.label }}
              </Badge>
            </div>
          </div>
        </CardContent>
      </Card>

      <!-- Statistics Card -->
      <Card>
        <CardHeader>
          <CardTitle>Statistics</CardTitle>
        </CardHeader>
        <CardContent class="space-y-2">
          <div class="flex justify-between border-b pb-2">
            <span class="text-muted-foreground">Submissions</span>
            <span class="font-semibold tabular-nums">{{
              problemsStore.currentProblem.submission_count || 0
            }}</span>
          </div>
          <div class="flex justify-between border-b pb-2">
            <span class="text-muted-foreground">Solutions</span>
            <span class="font-semibold tabular-nums">{{
              problemsStore.currentProblem.solution_count || 0
            }}</span>
          </div>
          <div class="flex justify-between border-b pb-2">
            <span class="text-muted-foreground">Acceptance Rate</span>
            <span class="font-semibold tabular-nums">
              {{
                problemsStore.currentProblem.submission_count && problemsStore.currentProblem.solution_count
                  ? (
                      (problemsStore.currentProblem.solution_count /
                        problemsStore.currentProblem.submission_count) *
                      100
                    ).toFixed(1) + '%'
                  : 'N/A'
              }}
            </span>
          </div>
          <div class="flex justify-between">
            <span class="text-muted-foreground">Has Official Solution</span>
            <Badge :variant="problemsStore.currentProblem.has_solution ? 'default' : 'secondary'">
              {{ problemsStore.currentProblem.has_solution ? 'Yes' : 'No' }}
            </Badge>
          </div>
        </CardContent>
      </Card>

      <!-- Metadata Card -->
      <Card>
        <CardHeader>
          <CardTitle>Timeline</CardTitle>
        </CardHeader>
        <CardContent class="space-y-2">
          <div class="flex justify-between border-b pb-2">
            <span class="text-muted-foreground">Created</span>
            <span class="font-medium">{{
              new Date(problemsStore.currentProblem.created_at).toLocaleString()
            }}</span>
          </div>
          <div class="flex justify-between border-b pb-2">
            <span class="text-muted-foreground">Last Updated</span>
            <span class="font-medium">{{
              new Date(problemsStore.currentProblem.updated_at).toLocaleString()
            }}</span>
          </div>
          <div v-if="problemsStore.currentProblem.published_at" class="flex justify-between border-b pb-2">
            <span class="text-muted-foreground">Published On</span>
            <span class="font-medium">{{
              new Date(problemsStore.currentProblem.published_at).toLocaleString()
            }}</span>
          </div>
          <div v-if="problemsStore.currentProblem.is_deleted" class="flex justify-between pt-2">
            <span class="text-destructive font-bold">Deleted Problem</span>
            <Badge variant="destructive">True</Badge>
          </div>
        </CardContent>
      </Card>

      <!-- Examples Card -->
      <Card
        v-if="problemsStore.currentProblem.examples?.length"
        class="md:col-span-2 border-primary/20"
      >
        <CardHeader>
          <CardTitle>Test Case Examples</CardTitle>
        </CardHeader>
        <CardContent>
          <div class="space-y-4">
            <div
              v-for="(example, idx) in problemsStore.currentProblem.examples"
              :key="example.id"
              class="p-4 border rounded-lg space-y-3 bg-muted/10"
            >
              <div class="flex items-center justify-between">
                <p class="text-sm font-bold uppercase text-primary">Example {{ idx + 1 }}</p>
              </div>
              <div class="grid md:grid-cols-2 gap-4">
                <div class="space-y-1">
                  <p class="text-xs font-medium text-muted-foreground uppercase">Input</p>
                  <pre class="text-xs bg-muted p-2 rounded border">{{ example.input }}</pre>
                </div>
                <div class="space-y-1">
                  <p class="text-xs font-medium text-muted-foreground uppercase">Output</p>
                  <pre class="text-xs bg-muted p-2 rounded border">{{ example.output }}</pre>
                </div>
              </div>
              <div v-if="example.explanation" class="space-y-1">
                <p class="text-xs font-medium text-muted-foreground uppercase">Explanation</p>
                <p class="text-sm italic text-muted-foreground">{{ example.explanation }}</p>
              </div>
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
          <div v-if="problemsStore.currentProblem.languages?.length" class="flex flex-wrap gap-2">
            <Badge
              v-for="lang in problemsStore.currentProblem.languages"
              :key="lang.id"
              variant="outline"
              class="bg-background shadow-xs"
            >
              {{ lang.language }}
            </Badge>
          </div>
          <p v-else class="text-sm text-muted-foreground italic">No specific languages restricted</p>
        </CardContent>
      </Card>

      <!-- Hints & Constraints -->
      <Card>
        <CardHeader>
          <CardTitle>Constraints & Hints</CardTitle>
        </CardHeader>
        <CardContent class="space-y-4">
          <div v-if="problemsStore.currentProblem.detail?.constraints_json?.length" class="space-y-2">
            <h4 class="text-xs font-semibold text-muted-foreground uppercase">Constraints</h4>
            <ul class="list-disc pl-4 text-sm space-y-1">
              <li v-for="(c, i) in problemsStore.currentProblem.detail.constraints_json" :key="i">{{ c }}</li>
            </ul>
          </div>
          <div v-if="problemsStore.currentProblem.detail?.hints?.length" class="space-y-2">
            <h4 class="text-xs font-semibold text-muted-foreground uppercase">Hints</h4>
            <div class="space-y-2">
              <div
                v-for="(h, i) in problemsStore.currentProblem.detail.hints"
                :key="i"
                class="text-sm p-2 rounded border bg-amber-50/10"
              >
                {{ h }}
              </div>
            </div>
          </div>
          <p
            v-if="
              !problemsStore.currentProblem.detail?.constraints_json?.length &&
              !problemsStore.currentProblem.detail?.hints?.length
            "
            class="text-sm text-muted-foreground italic"
          >
            No constraints or hints provided.
          </p>
        </CardContent>
      </Card>
    </div>

    <div v-else class="flex h-full items-center justify-center p-8">
      <p class="text-muted-foreground">Problem not found</p>
    </div>
  </div>
</template>
