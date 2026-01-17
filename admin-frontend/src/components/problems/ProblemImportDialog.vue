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
import { problemsApi, type ImportProblemsResponse } from '@/api/admin/problems'

interface Props {
  open: boolean
}

defineProps<Props>()
const emit = defineEmits<{
  'update:open': [value: boolean]
  'imported': []
}>()

const { t } = useI18n()

const file = ref<File | null>(null)
const onConflict = ref<'skip' | 'update' | 'create_new'>('skip')
const importing = ref(false)
const progress = ref(0)
const result = ref<ImportProblemsResponse | null>(null)

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
  return (
    validTypes.includes(file.value.type) || validExtensions.includes(extension)
  )
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
    const formData = new FormData()
    formData.append('file', file.value)

    const response = await problemsApi.importProblems({
      file: formData,
      onConflict: onConflict.value,
    })

    result.value = response
    progress.value = 100

    if (response.errors.length > 0) {
      toast.warning(
        t('problems.import.partialSuccess', {
          success: response.success,
          total: response.success + response.errors.length,
        }),
        {
          description: t('problems.import.someErrors'),
        },
      )
    } else {
      toast.success(
        t('problems.import.success', {
          count: response.success,
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
              'border-2 border-dashed rounded-lg p-8 text-center transition-colors',
              isDragging ? 'border-primary bg-primary/5' : 'border-muted-foreground/25',
              file ? 'bg-muted/50' : 'hover:border-primary/50 hover:bg-muted/30',
            ]"
          >
            <input
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
              <Button
                type="button"
                variant="outline"
                size="sm"
                @click="($event.target as HTMLInputElement).click()"
              >
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
          <div v-if="file && !isValidFile" class="mt-3 flex items-center gap-2 text-destructive text-sm">
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
            {{ t(`problems.import.strategyDescriptions.${onConflict}`) }}
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
          <div class="grid grid-cols-3 gap-4">
            <div class="rounded-lg border bg-card p-4 text-center">
              <div class="flex items-center justify-center gap-2 text-2xl font-bold text-emerald-600">
                <IconCheck class="h-5 w-5" />
                {{ result.success }}
              </div>
              <p class="text-xs text-muted-foreground mt-1">
                {{ t('problems.import.imported') }}
              </p>
            </div>
            <div class="rounded-lg border bg-card p-4 text-center">
              <div class="flex items-center justify-center gap-2 text-2xl font-bold text-destructive">
                <IconX class="h-5 w-5" />
                {{ result.errors.length }}
              </div>
              <p class="text-xs text-muted-foreground mt-1">
                {{ t('problems.import.failed') }}
              </p>
            </div>
            <div class="rounded-lg border bg-card p-4 text-center">
              <div class="flex items-center justify-center gap-2 text-2xl font-bold text-muted-foreground">
                <IconFile class="h-5 w-5" />
                {{ result.success + result.errors.length }}
              </div>
              <p class="text-xs text-muted-foreground mt-1">
                {{ t('problems.import.total') }}
              </p>
            </div>
          </div>

          <!-- Errors -->
          <div v-if="result.errors.length > 0" class="space-y-2">
            <div class="flex items-center gap-2 text-sm font-medium">
              <IconAlertTriangle class="h-4 w-4 text-amber-500" />
              <span>{{ t('problems.import.errors') }}</span>
            </div>
            <div class="max-h-48 overflow-y-auto space-y-2">
              <div
                v-for="(error, index) in result.errors"
                :key="index"
                class="rounded-md bg-destructive/10 border border-destructive/20 p-3 text-sm"
              >
                <div class="flex items-start gap-2">
                  <IconX class="h-4 w-4 text-destructive mt-0.5 flex-shrink-0" />
                  <div class="flex-1 min-w-0">
                    <p class="text-destructive font-medium">{{ error.title }}</p>
                    <p class="text-muted-foreground text-xs mt-1">{{ error.message }}</p>
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
        <Button
          v-if="!result"
          @click="handleImport"
          :disabled="!file || !isValidFile || importing"
        >
          <IconLoader v-if="importing" class="mr-2 h-4 w-4 animate-spin" />
          <IconUpload v-else class="mr-2 h-4 w-4" />
          {{ importing ? t('problems.import.importing') : t('problems.import.import') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>