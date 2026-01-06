<script setup lang="ts">
import { ref, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import { IconFileDescription, IconCheck } from '@tabler/icons-vue'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Checkbox } from '@/components/ui/checkbox'
import MarkdownEditor from '@/components/problem/MarkdownEditor.vue'
import { Difficulty, ProblemStatus } from '@/api/admin/problems'

export interface DescriptionFormData {
  slug: string
  title: string
  difficulty: Difficulty
  status: ProblemStatus
  is_premium: boolean
  is_published: boolean
  summary: string
  content: string
}

interface ProblemData {
  slug: string
  title: string
  difficulty: string
  status: string
  is_premium: boolean
  is_published: boolean
  summary: string
  content: string
}

const props = withDefaults(
  defineProps<{
    problem?: ProblemData
    isEdit?: boolean
  }>(),
  {
    isEdit: false,
  },
)

const emit = defineEmits<{
  submit: [data: DescriptionFormData]
  cancel: []
}>()

// Initialize form data with safe defaults
const formData = ref<DescriptionFormData>({
  slug: '',
  title: '',
  difficulty: Difficulty.MEDIUM,
  status: ProblemStatus.TODO,
  is_premium: false,
  is_published: false,
  summary: '',
  content: '',
})

// Function to reset/update form data safely
function updateForm(data?: ProblemData) {
  if (!data) return

  formData.value = {
    slug: data.slug || '',
    title: data.title || '',
    difficulty: (data.difficulty as Difficulty) || Difficulty.MEDIUM,
    status: (data.status as ProblemStatus) || ProblemStatus.TODO,
    is_premium: !!data.is_premium,
    is_published: !!data.is_published,
    summary: data.summary || '',
    content: data.content || '',
  }
}

// Watch for prop changes to update form
watch(
  () => props.problem,
  (newVal) => {
    if (newVal) {
      updateForm(newVal)
    }
  },
  { deep: true, immediate: true },
)

const loading = ref(false)

// Validation errors
const errors = ref<Record<string, string>>({})

function validate(): boolean {
  errors.value = {}

  if (!formData.value.slug?.trim()) {
    errors.value.slug = 'Slug is required'
  } else if (!/^[a-z0-9-]+$/.test(formData.value.slug)) {
    errors.value.slug = 'Slug must contain only lowercase letters, numbers, and hyphens'
  }

  if (!formData.value.title?.trim()) {
    errors.value.title = 'Title is required'
  }

  return Object.keys(errors.value).length === 0
}

function submit() {
  if (!validate()) return
  emit('submit', formData.value)
}

function cancel() {
  emit('cancel')
}

// Expose loading state for parent to control
defineExpose({
  setLoading: (value: boolean) => {
    loading.value = value
  },
})
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
    <!-- Main Content -->
    <div class="lg:col-span-8 space-y-6">
      <Card>
        <CardHeader>
          <div class="flex items-center gap-2">
            <IconFileDescription class="h-5 w-5 text-muted-foreground" />
            <CardTitle>Problem Description</CardTitle>
          </div>
          <CardDescription>Basic information and content of the problem.</CardDescription>
        </CardHeader>
        <CardContent class="space-y-6">
          <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div class="space-y-2">
              <Label>Title</Label>
              <Input v-model="formData.title" placeholder="e.g. Two Sum" />
              <p v-if="errors.title" class="text-sm text-destructive">{{ errors.title }}</p>
            </div>

            <div class="space-y-2">
              <Label>Slug</Label>
              <Input v-model="formData.slug" placeholder="e.g. two-sum" class="font-mono" />
              <p v-if="errors.slug" class="text-sm text-destructive">{{ errors.slug }}</p>
            </div>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div class="space-y-2">
              <Label>Difficulty</Label>
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

            <div class="space-y-2">
              <Label>Status</Label>
              <Select v-model="formData.status">
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem :value="ProblemStatus.TODO">Todo</SelectItem>
                  <SelectItem :value="ProblemStatus.ATTEMPTED">Attempted</SelectItem>
                  <SelectItem :value="ProblemStatus.SOLVED">Solved</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          <div class="space-y-2">
            <Label>Summary</Label>
            <Textarea
              v-model="formData.summary"
              rows="2"
              placeholder="Brief summary displayed in lists..."
              class="resize-none"
            />
          </div>

          <div class="space-y-2">
            <Label>Full Content</Label>
            <MarkdownEditor
              :model-value="formData.content ?? ''"
              @update:model-value="(v) => (formData.content = v)"
              placeholder="Write the full problem description in markdown..."
            />
          </div>
        </CardContent>
      </Card>
    </div>

    <!-- Sidebar -->
    <div class="lg:col-span-4 space-y-6 lg:sticky lg:top-6 h-fit">
      <!-- Publishing Card -->
      <Card class="border-primary/10 shadow-sm">
        <CardHeader class="pb-3 border-b bg-muted/20">
          <CardTitle class="text-base">Publishing</CardTitle>
        </CardHeader>
        <CardContent class="pt-6 space-y-6">
          <div class="space-y-4">
            <div
              class="flex items-center justify-between p-3 rounded-lg border bg-card hover:bg-muted/50 transition-colors cursor-pointer"
              @click="formData.is_premium = !formData.is_premium"
            >
              <div class="space-y-0.5">
                <Label class="text-base cursor-pointer">Premium</Label>
                <p class="text-xs text-muted-foreground">Only for premium users</p>
              </div>
              <Checkbox v-model:checked="formData.is_premium" />
            </div>

            <div
              class="flex items-center justify-between p-3 rounded-lg border bg-card hover:bg-muted/50 transition-colors cursor-pointer"
              @click="formData.is_published = !formData.is_published"
            >
              <div class="space-y-0.5">
                <Label class="text-base cursor-pointer">Published</Label>
                <p class="text-xs text-muted-foreground">Visible to all users</p>
              </div>
              <Checkbox v-model:checked="formData.is_published" />
            </div>
          </div>

          <div class="flex flex-col gap-3">
            <Button class="w-full" :disabled="loading" @click="submit">
              <IconCheck v-if="!loading" class="h-4 w-4 mr-2" />
              {{ loading ? 'Saving...' : isEdit ? 'Update Description' : 'Save Description' }}
            </Button>
            <Button variant="outline" class="w-full" @click="cancel"> Cancel </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
