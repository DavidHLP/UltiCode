<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'
import { Switch } from '@/components/ui/switch'
import { Label } from '@/components/ui/label'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  IconFlask,
  IconPlus,
  IconDotsVertical,
  IconUpload,
  IconDownload,
  IconEyeOff,
  IconLoader2,
  IconGripVertical,
} from '@tabler/icons-vue'
import { Checkbox } from '@/components/ui/checkbox'
import {
  testCasesApi,
  type TestCase,
  type CreateTestCaseDto,
  type BulkImportTestCaseDto,
} from '@/api/admin/test-cases'

const props = defineProps<{
  problemId: string
}>()

const emit = defineEmits<{
  change: []
}>()

const { t } = useI18n()

// State
const testCases = ref<TestCase[]>([])
const loading = ref(false)
const saving = ref(false)
const activeId = ref<string | null>(null)
const editDialogOpen = ref(false)
const importDialogOpen = ref(false)
const editingTestCase = ref<TestCase | null>(null)
const importText = ref('')
const replaceExisting = ref(false)
const importing = ref(false)

// Form state for create/edit
const formData = ref<CreateTestCaseDto>({
  input_text: '',
  output_text: '',
  is_sample: false,
  is_hidden: true,
  explanation: '',
})

// Computed
const activeTestCase = computed(() => {
  if (!activeId.value) return null
  return testCases.value.find((tc) => tc.id === activeId.value) ?? null
})

const sampleCount = computed(() => testCases.value.filter((tc) => tc.is_sample).length)
const hiddenCount = computed(
  () => testCases.value.filter((tc) => tc.is_hidden && !tc.is_sample).length,
)

// Methods
async function loadTestCases() {
  loading.value = true
  try {
    const response = await testCasesApi.getTestCases(props.problemId, {
      limit: 1000,
    })
    testCases.value = response.items
    if (testCases.value.length > 0 && !activeId.value) {
      activeId.value = testCases.value[0]?.id ?? null
    }
  } catch (error) {
    console.error('Failed to load test cases:', error)
    toast.error(t('testCases.toast.loadFailed'))
  } finally {
    loading.value = false
  }
}

function selectTestCase(id: string) {
  activeId.value = id
}

function openCreateDialog() {
  editingTestCase.value = null
  formData.value = {
    input_text: '',
    output_text: '',
    is_sample: false,
    is_hidden: true,
    explanation: '',
  }
  editDialogOpen.value = true
}

function openEditDialog(testCase: TestCase) {
  editingTestCase.value = testCase
  formData.value = {
    input_text: testCase.input_text,
    output_text: testCase.output_text,
    is_sample: testCase.is_sample,
    is_hidden: testCase.is_hidden,
    explanation: testCase.explanation ?? '',
    constraints: testCase.constraints,
  }
  editDialogOpen.value = true
}

async function saveTestCase() {
  if (!formData.value.input_text.trim() || !formData.value.output_text.trim()) {
    toast.error(t('testCases.validation.inputOutputRequired'))
    return
  }

  saving.value = true
  try {
    if (editingTestCase.value) {
      await testCasesApi.updateTestCase(props.problemId, editingTestCase.value.id, formData.value)
      toast.success(t('testCases.toast.updateSuccess'))
    } else {
      const created = await testCasesApi.createTestCase(props.problemId, formData.value)
      testCases.value.push(created)
      activeId.value = created.id
      toast.success(t('testCases.toast.createSuccess'))
    }
    editDialogOpen.value = false
    await loadTestCases()
    emit('change')
  } catch (error) {
    console.error('Failed to save test case:', error)
    toast.error(t('testCases.toast.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function deleteTestCase(testCase: TestCase) {
  if (!confirm(t('testCases.confirmDelete'))) return

  try {
    await testCasesApi.deleteTestCase(props.problemId, testCase.id)
    testCases.value = testCases.value.filter((tc) => tc.id !== testCase.id)
    if (activeId.value === testCase.id) {
      activeId.value = testCases.value[0]?.id ?? null
    }
    toast.success(t('testCases.toast.deleteSuccess'))
    emit('change')
  } catch (error) {
    console.error('Failed to delete test case:', error)
    toast.error(t('testCases.toast.deleteFailed'))
  }
}

async function toggleSample(testCase: TestCase) {
  try {
    await testCasesApi.updateTestCase(props.problemId, testCase.id, {
      is_sample: !testCase.is_sample,
    })
    testCase.is_sample = !testCase.is_sample
    emit('change')
  } catch (error) {
    console.error('Failed to toggle sample:', error)
    toast.error(t('testCases.toast.updateFailed'))
  }
}

async function toggleHidden(testCase: TestCase) {
  try {
    await testCasesApi.updateTestCase(props.problemId, testCase.id, {
      is_hidden: !testCase.is_hidden,
    })
    testCase.is_hidden = !testCase.is_hidden
    emit('change')
  } catch (error) {
    console.error('Failed to toggle hidden:', error)
    toast.error(t('testCases.toast.updateFailed'))
  }
}

async function exportTestCases() {
  try {
    await testCasesApi.exportTestCasesAsFile(props.problemId)
    toast.success(t('testCases.toast.exportSuccess'))
  } catch (error) {
    console.error('Failed to export test cases:', error)
    toast.error(t('testCases.toast.exportFailed'))
  }
}

function openImportDialog() {
  importText.value = ''
  replaceExisting.value = false
  importDialogOpen.value = true
}

async function importTestCases() {
  if (!importText.value.trim()) {
    toast.error(t('testCases.validation.importTextRequired'))
    return
  }

  importing.value = true
  try {
    let testCasesToImport: BulkImportTestCaseDto[] = []

    // Try to parse as JSON array first
    try {
      const parsed = JSON.parse(importText.value)
      if (Array.isArray(parsed)) {
        testCasesToImport = parsed.map((tc) => ({
          input_text: tc.input_text ?? tc.input ?? '',
          output_text: tc.output_text ?? tc.output ?? '',
          is_sample: tc.is_sample ?? false,
          is_hidden: tc.is_hidden ?? true,
          explanation: tc.explanation,
        }))
      }
    } catch {
      // If not JSON, try to parse as custom format (input/output pairs)
      const lines = importText.value.split('\n').filter((l) => l.trim())
      let currentInput = ''
      let currentOutput = ''
      let isOutput = false

      for (const line of lines) {
        if (line.startsWith('---') || line.startsWith('===')) {
          if (currentInput && currentOutput) {
            testCasesToImport.push({
              input_text: currentInput.trim(),
              output_text: currentOutput.trim(),
              is_hidden: true,
            })
          }
          currentInput = ''
          currentOutput = ''
          isOutput = false
          continue
        }
        if (line.toLowerCase().startsWith('output:') || line.startsWith('>')) {
          isOutput = true
          continue
        }
        if (line.toLowerCase().startsWith('input:') || line.startsWith('<')) {
          isOutput = false
          continue
        }
        if (isOutput) {
          currentOutput += (currentOutput ? '\n' : '') + line
        } else {
          currentInput += (currentInput ? '\n' : '') + line
        }
      }
      // Don't forget the last one
      if (currentInput && currentOutput) {
        testCasesToImport.push({
          input_text: currentInput.trim(),
          output_text: currentOutput.trim(),
          is_hidden: true,
        })
      }
    }

    if (testCasesToImport.length === 0) {
      toast.error(t('testCases.validation.noValidTestCases'))
      return
    }

    await testCasesApi.bulkImportTestCases(props.problemId, {
      test_cases: testCasesToImport,
      replace_existing: replaceExisting.value,
    })

    toast.success(t('testCases.toast.importSuccess', { count: testCasesToImport.length }))
    importDialogOpen.value = false
    await loadTestCases()
    emit('change')
  } catch (error) {
    console.error('Failed to import test cases:', error)
    toast.error(t('testCases.toast.importFailed'))
  } finally {
    importing.value = false
  }
}

// Lifecycle
onMounted(() => {
  loadTestCases()
})

// Watch for problemId changes
watch(
  () => props.problemId,
  () => {
    activeId.value = null
    loadTestCases()
  },
)
</script>

<template>
  <div class="hidden-test-cases-editor">
    <!-- Header -->
    <div class="flex items-center justify-between mb-4">
      <div class="flex items-center gap-2">
        <IconFlask class="h-5 w-5 text-muted-foreground" />
        <h3 class="font-semibold">{{ t('testCases.title') }}</h3>
        <div class="flex gap-2 ml-2">
          <Badge v-if="sampleCount > 0" variant="secondary" class="text-xs">
            {{ sampleCount }} {{ t('testCases.sample') }}
          </Badge>
          <Badge v-if="hiddenCount > 0" variant="outline" class="text-xs">
            {{ hiddenCount }} {{ t('testCases.hidden') }}
          </Badge>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <Button size="sm" variant="ghost" @click="openImportDialog">
          <IconUpload class="h-4 w-4 mr-1" />
          {{ t('testCases.import') }}
        </Button>
        <Button
          size="sm"
          variant="ghost"
          :disabled="testCases.length === 0"
          @click="exportTestCases"
        >
          <IconDownload class="h-4 w-4 mr-1" />
          {{ t('testCases.export') }}
        </Button>
        <Button size="sm" @click="openCreateDialog">
          <IconPlus class="h-4 w-4 mr-1" />
          {{ t('testCases.add') }}
        </Button>
      </div>
    </div>

    <!-- Loading state -->
    <div v-if="loading" class="flex items-center justify-center py-8">
      <IconLoader2 class="h-6 w-6 animate-spin text-muted-foreground" />
      <span class="ml-2 text-muted-foreground">{{ t('common.loading') }}</span>
    </div>

    <!-- Empty state -->
    <div
      v-else-if="testCases.length === 0"
      class="flex flex-col items-center justify-center py-8 text-muted-foreground"
    >
      <IconFlask class="h-12 w-12 mb-2 opacity-50" />
      <p>{{ t('testCases.noTestCases') }}</p>
      <Button size="sm" variant="outline" class="mt-2" @click="openCreateDialog">
        <IconPlus class="h-4 w-4 mr-1" />
        {{ t('testCases.addFirst') }}
      </Button>
    </div>

    <!-- Test cases list with editor -->
    <div v-else class="grid grid-cols-12 gap-4">
      <!-- Sidebar with test case list -->
      <div class="col-span-4 space-y-1 max-h-[500px] overflow-y-auto">
        <div
          v-for="(testCase, index) in testCases"
          :key="testCase.id"
          class="flex items-center gap-2 p-2 rounded-md cursor-pointer transition-colors"
          :class="
            activeId === testCase.id
              ? 'bg-primary/10 border border-primary/20'
              : 'hover:bg-muted/50'
          "
          @click="selectTestCase(testCase.id)"
        >
          <IconGripVertical class="h-4 w-4 text-muted-foreground cursor-grab" />
          <span class="text-sm font-medium flex-1">
            #{{ index + 1 }}
            <Badge v-if="testCase.is_sample" variant="secondary" class="ml-1 text-[10px]">
              {{ t('testCases.sample') }}
            </Badge>
            <Badge v-if="testCase.is_hidden" variant="outline" class="ml-1 text-[10px]">
              <IconEyeOff class="h-3 w-3 mr-0.5" />
              {{ t('testCases.hidden') }}
            </Badge>
          </span>
          <DropdownMenu>
            <DropdownMenuTrigger as-child>
              <Button variant="ghost" size="icon" class="h-6 w-6">
                <IconDotsVertical class="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem @click.stop="openEditDialog(testCase)">
                {{ t('common.edit') }}
              </DropdownMenuItem>
              <DropdownMenuItem @click.stop="toggleSample(testCase)">
                {{ testCase.is_sample ? t('testCases.markAsHidden') : t('testCases.markAsSample') }}
              </DropdownMenuItem>
              <DropdownMenuItem @click.stop="toggleHidden(testCase)">
                {{ testCase.is_hidden ? t('testCases.makeVisible') : t('testCases.makeHidden') }}
              </DropdownMenuItem>
              <DropdownMenuItem
                class="text-destructive focus:text-destructive"
                @click.stop="deleteTestCase(testCase)"
              >
                {{ t('common.delete') }}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      <!-- Active test case editor -->
      <div v-if="activeTestCase" class="col-span-8 space-y-4">
        <div class="p-4 rounded-lg border bg-card">
          <div class="flex items-center justify-between mb-4">
            <h4 class="font-medium">Test Case Details</h4>
            <div class="flex items-center gap-4">
              <div class="flex items-center gap-2">
                <Label class="text-xs">{{ t('testCases.sample') }}</Label>
                <Switch
                  :checked="activeTestCase.is_sample"
                  @update:checked="toggleSample(activeTestCase)"
                />
              </div>
              <div class="flex items-center gap-2">
                <Label class="text-xs">{{ t('testCases.hidden') }}</Label>
                <Switch
                  :checked="activeTestCase.is_hidden"
                  @update:checked="toggleHidden(activeTestCase)"
                />
              </div>
            </div>
          </div>

          <div class="space-y-4">
            <div>
              <Label class="text-sm text-muted-foreground mb-1 block">
                {{ t('testCases.input') }}
              </Label>
              <Textarea
                :model-value="activeTestCase.input_text"
                readonly
                class="font-mono text-sm bg-muted min-h-[100px]"
              />
            </div>
            <div>
              <Label class="text-sm text-muted-foreground mb-1 block">
                {{ t('testCases.output') }}
              </Label>
              <Textarea
                :model-value="activeTestCase.output_text"
                readonly
                class="font-mono text-sm bg-muted min-h-[100px]"
              />
            </div>
            <div v-if="activeTestCase.explanation">
              <Label class="text-sm text-muted-foreground mb-1 block">
                {{ t('testCases.explanation') }}
              </Label>
              <Input :model-value="activeTestCase.explanation" readonly />
            </div>
          </div>

          <div class="flex justify-end mt-4">
            <Button size="sm" @click="openEditDialog(activeTestCase)">
              {{ t('common.edit') }}
            </Button>
          </div>
        </div>
      </div>
    </div>

    <!-- Create/Edit Dialog -->
    <Dialog v-model:open="editDialogOpen">
      <DialogContent class="max-w-2xl">
        <DialogHeader>
          <DialogTitle>
            {{ editingTestCase ? t('testCases.editTestCase') : t('testCases.createTestCase') }}
          </DialogTitle>
        </DialogHeader>

        <div class="space-y-4 py-4">
          <div class="flex items-center gap-6">
            <div class="flex items-center gap-2">
              <Switch v-model="formData.is_sample" id="is_sample" />
              <Label for="is_sample" class="text-sm">{{ t('testCases.isSample') }}</Label>
            </div>
            <div class="flex items-center gap-2">
              <Switch v-model="formData.is_hidden" id="is_hidden" />
              <Label for="is_hidden" class="text-sm">{{ t('testCases.isHidden') }}</Label>
            </div>
          </div>

          <div>
            <Label class="text-sm text-muted-foreground mb-1 block">
              {{ t('testCases.input') }} *
            </Label>
            <Textarea
              v-model="formData.input_text"
              :placeholder="t('testCases.inputPlaceholder')"
              class="font-mono text-sm min-h-[120px]"
            />
          </div>

          <div>
            <Label class="text-sm text-muted-foreground mb-1 block">
              {{ t('testCases.output') }} *
            </Label>
            <Textarea
              v-model="formData.output_text"
              :placeholder="t('testCases.outputPlaceholder')"
              class="font-mono text-sm min-h-[120px]"
            />
          </div>

          <div>
            <Label class="text-sm text-muted-foreground mb-1 block">
              {{ t('testCases.explanation') }} ({{ t('common.optional') }})
            </Label>
            <Input
              v-model="formData.explanation"
              :placeholder="t('testCases.explanationPlaceholder')"
            />
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" @click="editDialogOpen = false">
            {{ t('common.cancel') }}
          </Button>
          <Button :disabled="saving" @click="saveTestCase">
            <IconLoader2 v-if="saving" class="h-4 w-4 mr-1 animate-spin" />
            {{ saving ? t('common.saving') : t('common.save') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Import Dialog -->
    <Dialog v-model:open="importDialogOpen">
      <DialogContent class="max-w-2xl">
        <DialogHeader>
          <DialogTitle>{{ t('testCases.importTestCases') }}</DialogTitle>
        </DialogHeader>

        <div class="space-y-4 py-4">
          <div>
            <Label class="text-sm text-muted-foreground mb-1 block">
              {{ t('testCases.importData') }}
            </Label>
            <Textarea
              v-model="importText"
              :placeholder="t('testCases.importPlaceholder')"
              class="font-mono text-sm min-h-[200px]"
            />
            <p class="text-xs text-muted-foreground mt-1">
              {{ t('testCases.importHelp') }}
            </p>
          </div>

          <div class="flex items-center gap-2">
            <Checkbox v-model="replaceExisting" id="replace_existing" />
            <Label for="replace_existing" class="text-sm">
              {{ t('testCases.replaceExisting') }}
            </Label>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" @click="importDialogOpen = false">
            {{ t('common.cancel') }}
          </Button>
          <Button :disabled="importing" @click="importTestCases">
            <IconLoader2 v-if="importing" class="h-4 w-4 mr-1 animate-spin" />
            <IconUpload v-else class="h-4 w-4 mr-1" />
            {{ importing ? t('testCases.importing') : t('testCases.import') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
