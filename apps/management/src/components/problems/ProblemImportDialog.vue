<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import {
  IconUpload,
  IconFile,
  IconX,
  IconAlertTriangle,
  IconLoader,
  IconCheck,
} from '@tabler/icons-vue'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Progress } from '@/components/ui/progress'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  problemsApi,
  type ImportProblemsResponse,
  type ImportProblemDto,
  Difficulty,
  ProblemStatus,
} from '@/api/admin/problems'

interface Props {
  open: boolean
}

defineProps<Props>()
const emit = defineEmits<{
  'update:open': [value: boolean]
  imported: []
}>()

const { t } = useI18n()

const file = ref<File | null>(null)
const onConflict = ref<'skip' | 'update' | 'create_new'>('skip')
const importing = ref(false)
const progress = ref(0)
const result = ref<ImportProblemsResponse | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

const isDragging = ref(false)

const fileName = computed(() => file.value?.name || '')
const fileSize = computed(() => {
  if (!file.value) return ''
  const size = file.value.size
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(2)} KB`
  return `${(size / (1024 * 1024)).toFixed(2)} MB`
})

const isValidFile = computed(() => {
  if (!file.value) return false
  const validTypes = ['application/json', 'text/csv', 'application/vnd.ms-excel']
  const validExtensions = ['.json', '.csv']
  const extension = '.' + fileName.value.split('.').pop()?.toLowerCase()
  return validTypes.includes(file.value.type) || validExtensions.includes(extension)
})

function handleFileSelect(event: Event) {
  const target = event.target as HTMLInputElement
  if (target.files && target.files[0]) {
    file.value = target.files[0]
    result.value = null
  }
}

function handleDragOver(event: DragEvent) {
  event.preventDefault()
  isDragging.value = true
}

function handleDragLeave(event: DragEvent) {
  event.preventDefault()
  isDragging.value = false
}

function handleDrop(event: DragEvent) {
  event.preventDefault()
  isDragging.value = false

  if (event.dataTransfer?.files && event.dataTransfer.files[0]) {
    file.value = event.dataTransfer.files[0]
    result.value = null
  }
}

function clearFile() {
  file.value = null
  result.value = null
}

async function handleImport() {
  if (!file.value || !isValidFile.value) return

  importing.value = true
  progress.value = 0
  result.value = null

  try {
    // Read file content
    const fileContent = await readFileContent(file.value)
    progress.value = 30

    // Parse file content
    let problems: ImportProblemDto[] = []
    if (file.value.name.endsWith('.json')) {
      problems = parseJSONFile(fileContent)
    } else if (file.value.name.endsWith('.csv')) {
      problems = parseCSVFile(fileContent)
    }
    progress.value = 60

    // Import problems
    const response = await problemsApi.importProblems(problems, onConflict.value)

    result.value = response
    progress.value = 100

    if (response.failed > 0) {
      toast.warning(
        t('problems.import.partialSuccess', {
          success: response.created + response.updated,
          total: response.total,
        }),
        {
          description: t('problems.import.someErrors'),
        },
      )
    } else {
      toast.success(
        t('problems.import.success', {
          count: response.created + response.updated,
        }),
      )
    }

    emit('imported')
  } catch (error) {
    console.error('Failed to import problems:', error)
    toast.error(t('problems.import.error'))
  } finally {
    importing.value = false
  }
}

async function readFileContent(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => resolve(e.target?.result as string)
    reader.onerror = (e) => reject(e)
    reader.readAsText(file)
  })
}

// Transform export format to import format
function transformExportToImportFormat(problem: {
  slug: string
  title: string
  difficulty: Difficulty
  status?: ProblemStatus
  isPremium?: boolean
  hasSolution?: boolean
  isPublished?: boolean
  detail?: {
    summary?: string
    constraintsJson?: string[]
    hints?: string[]
  }
  examples?: Array<{ input: string; output: string; explanation?: string }>
  languages?: Array<{ label: string; value: string; starterCode: string }>
  tags?: string[]
}): ImportProblemDto {
  return {
    // Core fields
    slug: problem.slug,
    title: problem.title,
    difficulty: problem.difficulty,
    status: problem.status || ProblemStatus.TODO,
    isPremium: problem.isPremium || false,
    hasSolution: problem.hasSolution || false,
    isPublished: problem.isPublished || false,

    // Flatten detail object
    summary: problem.detail?.summary,
    constraints: problem.detail?.constraintsJson,
    hints: problem.detail?.hints,

    // Nested arrays pass through
    examples: problem.examples,
    languages: problem.languages,
    tags: problem.tags,
  }
}

function parseJSONFile(content: string): ImportProblemDto[] {
  try {
    const parsed = JSON.parse(content)

    // Handle export format: {exportedAt, count, data: [...]}
    if (
      parsed &&
      typeof parsed === 'object' &&
      !Array.isArray(parsed) &&
      'data' in parsed &&
      Array.isArray(parsed.data)
    ) {
      // Transform each problem to match ImportProblemDto format
      return parsed.data.map(transformExportToImportFormat)
    }

    // Handle direct array format
    if (Array.isArray(parsed)) {
      // Check if items have 'detail' property (export format)
      if (parsed.length > 0 && 'detail' in parsed[0]) {
        return parsed.map(transformExportToImportFormat)
      }
      return parsed
    }

    // Handle single problem object
    return [transformExportToImportFormat(parsed)]
  } catch (error) {
    console.error('Failed to parse JSON file:', error)
    throw new Error('Invalid JSON format')
  }
}

function parseCSVFile(content: string): ImportProblemDto[] {
  const lines = content.split('\n').filter((line) => line.trim())
  if (lines.length < 2) {
    return []
  }

  const headers = (lines[0] || '').split(',').map((h) => h.trim())
  const problems: ImportProblemDto[] = []

  for (let i = 1; i < lines.length; i++) {
    const values = (lines[i] || '').split(',').map((v) => v.trim())
    const problemObj: { [key: string]: string } = {}
    headers.forEach((header, index) => {
      problemObj[header] = values[index] || ''
    })
    const getValue = (key: string): string => {
      const value = problemObj[key]
      return value !== undefined && value !== null ? value : ''
    }

    const slug = getValue('slug')
    const title = getValue('title')
    const difficulty = (getValue('difficulty') || 'EASY').toUpperCase() as Difficulty
    const status = (getValue('status') || 'todo').toLowerCase() as ProblemStatus
    const isPremium = getValue('is_premium') === 'true'
    const hasSolution = getValue('has_solution') === 'true'
    const isPublished = getValue('is_published') === 'true'
    const summary = getValue('summary')

    problems.push({
      slug,
      title,
      difficulty,
      status,
      isPremium,
      hasSolution,
      isPublished,
      summary,
    })
  }

  return problems
}

function handleClose() {
  if (importing.value) return
  clearFile()
  emit('update:open', false)
}
</script>

<template>
  <Dialog :open="open" @update:open="handleClose">
    <DialogContent class="max-w-2xl max-h-[90vh] overflow-hidden flex flex-col">
      <DialogHeader>
        <div class="flex items-center gap-2">
          <IconUpload class="h-5 w-5 text-muted-foreground" />
          <DialogTitle>{{ t('problems.import.title') }}</DialogTitle>
        </div>
        <DialogDescription>
          {{ t('problems.import.description') }}
        </DialogDescription>
      </DialogHeader>

      <!-- Content -->
      <div class="flex-1 overflow-y-auto -mx-6 px-6 space-y-6">
        <!-- File Upload Area -->
        <div
          v-if="!result"
          class="relative"
          @dragover="handleDragOver"
          @dragleave="handleDragLeave"
          @drop="handleDrop"
        >
          <div
            :class="[
              'border-2 border-dashed rounded-none p-8 text-center transition-colors',
              isDragging ? 'border-primary bg-primary/5' : 'border-muted-foreground/25',
              file ? 'bg-muted/50' : 'hover:border-primary/50 hover:bg-muted/30',
            ]"
          >
            <input
              ref="fileInputRef"
              type="file"
              accept=".json,.csv"
              class="hidden"
              @change="handleFileSelect"
              :disabled="importing"
            />

            <div v-if="!file" class="space-y-3">
              <div class="flex justify-center">
                <div class="h-12 w-12 rounded-full bg-muted flex items-center justify-center">
                  <IconUpload class="h-6 w-6 text-muted-foreground" />
                </div>
              </div>
              <div>
                <p class="text-sm font-medium">{{ t('problems.import.dropFile') }}</p>
                <p class="text-xs text-muted-foreground mt-1">
                  {{ t('problems.import.supportedFormats') }}
                </p>
              </div>
              <Button type="button" variant="outline" size="sm" @click="fileInputRef?.click()">
                {{ t('problems.import.browse') }}
              </Button>
            </div>

            <div v-else class="space-y-3">
              <div class="flex items-center justify-center">
                <div class="h-12 w-12 rounded-full bg-primary/10 flex items-center justify-center">
                  <IconFile class="h-6 w-6 text-primary" />
                </div>
              </div>
              <div>
                <p class="text-sm font-medium truncate">{{ fileName }}</p>
                <p class="text-xs text-muted-foreground mt-1">{{ fileSize }}</p>
              </div>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                @click="clearFile"
                :disabled="importing"
              >
                <IconX class="mr-2 h-4 w-4" />
                {{ t('problems.import.clear') }}
              </Button>
            </div>
          </div>

          <!-- Validation Error -->
          <div
            v-if="file && !isValidFile"
            class="mt-3 flex items-center gap-2 text-destructive text-sm"
          >
            <IconAlertTriangle class="h-4 w-4" />
            <span>{{ t('problems.import.invalidFile') }}</span>
          </div>
        </div>

        <!-- Conflict Resolution Strategy -->
        <div v-if="!result" class="space-y-2">
          <Label for="onConflict">{{ t('problems.import.conflictStrategy') }}</Label>
          <Select v-model="onConflict" :disabled="importing">
            <SelectTrigger id="onConflict">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="skip">
                {{ t('problems.import.strategies.skip') }}
              </SelectItem>
              <SelectItem value="update">
                {{ t('problems.import.strategies.update') }}
              </SelectItem>
              <SelectItem value="create_new">
                {{ t('problems.import.strategies.createNew') }}
              </SelectItem>
            </SelectContent>
          </Select>
          <p class="text-xs text-muted-foreground">
            {{ t(`problems.import.strategyDescriptions.${onConflict}`, onConflict) }}
          </p>
        </div>

        <!-- Progress -->
        <div v-if="importing" class="space-y-2">
          <div class="flex items-center justify-between text-sm">
            <span class="text-muted-foreground">{{ t('problems.import.importing') }}</span>
            <span class="font-medium">{{ progress }}%</span>
          </div>
          <Progress :value="progress" class="h-2" />
        </div>

        <!-- Results -->
        <div v-if="result" class="space-y-4">
          <!-- Summary -->
          <div class="grid grid-cols-4 gap-4">
            <div class="rounded-none border bg-card p-4 text-center">
              <div
                class="flex items-center justify-center gap-2 text-2xl font-bold text-foreground-strong"
              >
                <IconCheck class="h-5 w-5 text-status-success-mark" />
                {{ result.created }}
              </div>
              <p class="text-xs text-muted-foreground mt-1">
                {{ t('problems.import.created') }}
              </p>
            </div>
            <div class="rounded-none border bg-card p-4 text-center">
              <div class="flex items-center justify-center gap-2 text-2xl font-bold text-foreground-strong">
                <IconFile class="h-5 w-5 text-status-info-mark" />
                {{ result.updated }}
              </div>
              <p class="text-xs text-muted-foreground mt-1">
                {{ t('problems.import.updated') }}
              </p>
            </div>
            <div class="rounded-none border bg-card p-4 text-center">
              <div
                class="flex items-center justify-center gap-2 text-2xl font-bold text-muted-foreground"
              >
                <IconAlertTriangle class="h-5 w-5" />
                {{ result.skipped }}
              </div>
              <p class="text-xs text-muted-foreground mt-1">
                {{ t('problems.import.skipped') }}
              </p>
            </div>
            <div class="rounded-none border bg-card p-4 text-center">
              <div
                class="flex items-center justify-center gap-2 text-2xl font-bold text-destructive"
              >
                <IconX class="h-5 w-5" />
                {{ result.failed }}
              </div>
              <p class="text-xs text-muted-foreground mt-1">
                {{ t('problems.import.failed') }}
              </p>
            </div>
          </div>

          <!-- Errors -->
          <div v-if="result.results.filter((r) => !r.success).length > 0" class="space-y-2">
            <div class="flex items-center gap-2 text-sm font-medium">
              <IconAlertTriangle class="h-4 w-4 text-status-warning-mark" />
              <span>{{ t('problems.import.errors') }}</span>
            </div>
            <div class="max-h-48 overflow-y-auto space-y-2">
              <div
                v-for="(error, index) in result.results.filter((r) => !r.success)"
                :key="index"
                class="rounded-none bg-destructive/10 border border-destructive/20 p-3 text-sm"
              >
                <div class="flex items-start gap-2">
                  <IconX class="h-4 w-4 text-destructive mt-0.5 flex-shrink-0" />
                  <div class="flex-1 min-w-0">
                    <p class="text-destructive font-medium">{{ error.slug }}</p>
                    <p class="text-muted-foreground text-xs mt-1">{{ error.error }}</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Footer -->
      <DialogFooter class="border-t pt-4">
        <Button variant="outline" @click="handleClose" :disabled="importing">
          {{ result ? t('common.close') : t('common.cancel') }}
        </Button>
        <Button v-if="!result" @click="handleImport" :disabled="!file || !isValidFile || importing">
          <IconLoader v-if="importing" class="mr-2 h-4 w-4 animate-spin" />
          <IconUpload v-else class="mr-2 h-4 w-4" />
          {{ importing ? t('problems.import.importing') : t('problems.import.import') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
