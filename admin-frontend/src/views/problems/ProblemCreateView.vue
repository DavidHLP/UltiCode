<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useProblemsStore } from '@/stores/admin/problems'
import { Difficulty, ProblemStatus } from '@/api/admin/problems'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Checkbox } from '@/components/ui/checkbox'

const router = useRouter()
const problemsStore = useProblemsStore()

const formData = ref({
  slug: '',
  title: '',
  difficulty: Difficulty.MEDIUM,
  status: ProblemStatus.TODO,
  is_premium: false,
  is_published: false,
  summary: '',
  content: '',
  constraints: [] as string[],
  hints: [] as string[],
  languages: [] as string[],
  tags: [] as string[],
})

const newConstraint = ref('')
const newHint = ref('')
const newLanguage = ref('')
const newTag = ref('')

const loading = ref(false)

async function submit() {
  loading.value = true
  try {
    const problem = await problemsStore.createProblem({
      ...formData.value,
      examples: [],
    })
    router.push({ name: 'problem-detail', params: { id: problem.id } })
  } catch {
    alert('Failed to create problem')
  } finally {
    loading.value = false
  }
}

function addConstraint() {
  if (newConstraint.value.trim()) {
    formData.value.constraints.push(newConstraint.value.trim())
    newConstraint.value = ''
  }
}

function removeConstraint(index: number) {
  formData.value.constraints.splice(index, 1)
}

function addHint() {
  if (newHint.value.trim()) {
    formData.value.hints.push(newHint.value.trim())
    newHint.value = ''
  }
}

function removeHint(index: number) {
  formData.value.hints.splice(index, 1)
}

function addLanguage() {
  if (newLanguage.value.trim() && !formData.value.languages.includes(newLanguage.value.trim())) {
    formData.value.languages.push(newLanguage.value.trim())
    newLanguage.value = ''
  }
}

function removeLanguage(index: number) {
  formData.value.languages.splice(index, 1)
}

function addTag() {
  if (newTag.value.trim() && !formData.value.tags.includes(newTag.value.trim())) {
    formData.value.tags.push(newTag.value.trim())
    newTag.value = ''
  }
}

function removeTag(index: number) {
  formData.value.tags.splice(index, 1)
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold tracking-tight">Create Problem</h1>
        <p class="text-muted-foreground">Add a new problem to the platform</p>
      </div>
      <Button variant="outline" @click="router.push({ name: 'problems' })">Cancel</Button>
    </div>

    <Card class="max-w-3xl">
      <CardHeader>
        <CardTitle>Basic Information</CardTitle>
      </CardHeader>
      <CardContent class="space-y-4">
        <div class="space-y-2">
          <label class="text-sm font-medium">Slug</label>
          <Input v-model="formData.slug" placeholder="two-sum" />
        </div>

        <div class="space-y-2">
          <label class="text-sm font-medium">Title</label>
          <Input v-model="formData.title" placeholder="Two Sum" />
        </div>

        <div class="space-y-2">
          <label class="text-sm font-medium">Difficulty</label>
          <Select v-model="formData.difficulty">
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem :value="Difficulty.EASY">Easy</SelectItem>
              <SelectItem :value="Difficulty.MEDIUM">Medium</SelectItem>
              <SelectItem :value="Difficulty.HARD">Hard</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div class="flex gap-4">
          <div class="flex items-center space-x-2">
            <Checkbox v-model:checked="formData.is_premium" id="premium" />
            <label for="premium" class="text-sm">Premium</label>
          </div>

          <div class="flex items-center space-x-2">
            <Checkbox v-model:checked="formData.is_published" id="published" />
            <label for="published" class="text-sm">Publish immediately</label>
          </div>
        </div>

        <div class="space-y-2">
          <label class="text-sm font-medium">Summary</label>
          <Textarea v-model="formData.summary" rows="3" />
        </div>

        <div class="space-y-2">
          <label class="text-sm font-medium">Content</label>
          <Textarea v-model="formData.content" rows="10" />
        </div>
      </CardContent>
    </Card>

    <Card class="max-w-3xl">
      <CardHeader>
        <CardTitle>Constraints</CardTitle>
      </CardHeader>
      <CardContent class="space-y-4">
        <div class="flex gap-2">
          <Input
            v-model="newConstraint"
            placeholder="Add a constraint..."
            @keyup.enter="addConstraint"
          />
          <Button type="button" @click="addConstraint">Add</Button>
        </div>
        <div class="flex flex-wrap gap-2">
          <Badge v-for="(constraint, idx) in formData.constraints" :key="idx" variant="secondary">
            {{ constraint }}
            <button class="ml-2" @click="removeConstraint(idx)">×</button>
          </Badge>
        </div>
      </CardContent>
    </Card>

    <Card class="max-w-3xl">
      <CardHeader>
        <CardTitle>Hints</CardTitle>
      </CardHeader>
      <CardContent class="space-y-4">
        <div class="flex gap-2">
          <Input v-model="newHint" placeholder="Add a hint..." @keyup.enter="addHint" />
          <Button type="button" @click="addHint">Add</Button>
        </div>
        <div class="flex flex-wrap gap-2">
          <Badge v-for="(hint, idx) in formData.hints" :key="idx" variant="secondary">
            {{ hint }}
            <button class="ml-2" @click="removeHint(idx)">×</button>
          </Badge>
        </div>
      </CardContent>
    </Card>

    <Card class="max-w-3xl">
      <CardHeader>
        <CardTitle>Languages</CardTitle>
      </CardHeader>
      <CardContent class="space-y-4">
        <div class="flex gap-2">
          <Input
            v-model="newLanguage"
            placeholder="Add a language (e.g., Python, JavaScript)..."
            @keyup.enter="addLanguage"
          />
          <Button type="button" @click="addLanguage">Add</Button>
        </div>
        <div class="flex flex-wrap gap-2">
          <Badge v-for="(lang, idx) in formData.languages" :key="idx" variant="secondary">
            {{ lang }}
            <button class="ml-2" @click="removeLanguage(idx)">×</button>
          </Badge>
        </div>
      </CardContent>
    </Card>

    <Card class="max-w-3xl">
      <CardHeader>
        <CardTitle>Tags</CardTitle>
      </CardHeader>
      <CardContent class="space-y-4">
        <div class="flex gap-2">
          <Input v-model="newTag" placeholder="Add a tag..." @keyup.enter="addTag" />
          <Button type="button" @click="addTag">Add</Button>
        </div>
        <div class="flex flex-wrap gap-2">
          <Badge v-for="(tag, idx) in formData.tags" :key="idx" variant="secondary">
            {{ tag }}
            <button class="ml-2" @click="removeTag(idx)">×</button>
          </Badge>
        </div>
      </CardContent>
    </Card>

    <div class="flex gap-2">
      <Button :disabled="loading" @click="submit">
        {{ loading ? 'Creating...' : 'Create Problem' }}
      </Button>
      <Button variant="outline" @click="router.push({ name: 'problems' })">Cancel</Button>
    </div>
  </div>
</template>
