import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import {
  testCasesApi,
  type TestCase,
  type CreateTestCaseDto,
  type BulkImportTestCaseDto,
} from '@/api/admin/test-cases'

/**
 * Parse pasted / imported test-case text into DTOs.
 *
 * Pure, side-effect-free owner of the two supported import grammars so the
 * rules are unit-testable independent of the dialog / HTTP / toast state:
 *   1. JSON array — accepts the camelCase export shape plus legacy
 *      snake_case / loose `{input, output}` pastes. Every emitted case
 *      carries both flags so the backend `isSample XOR isHidden` invariant
 *      holds (a case is SAMPLE only when explicitly so, HIDDEN unless
 *      explicitly marked visible).
 *   2. Line grammar — `Input:`/`Output:` (or `<`/`>`) blocks separated by
 *      `---` / `===`; emits HIDDEN, non-sample cases.
 *
 * Returns an empty array when the text is blank or yields no usable case;
 * the caller decides the user-facing message.
 */
export function parseImportText(text: string): BulkImportTestCaseDto[] {
  if (!text.trim()) {
    return []
  }

  // Grammar 1: JSON array (camelCase export + legacy snake_case / loose pastes).
  try {
    const parsed = JSON.parse(text)
    if (Array.isArray(parsed)) {
      return parsed.map(
        (tc: Record<string, unknown>): BulkImportTestCaseDto => ({
          inputText: String(tc.inputText ?? tc.input_text ?? tc.input ?? ''),
          outputText: String(tc.outputText ?? tc.output_text ?? tc.output ?? ''),
          // Coerce through comparison so the result is boolean (not unknown)
          // and the defaults match the wire invariant.
          isSample: (tc.isSample ?? tc.is_sample) === true,
          isHidden: (tc.isHidden ?? tc.is_hidden) !== false,
          explanation: tc.explanation != null ? String(tc.explanation) : undefined,
        }),
      )
    }
  } catch {
    // Not JSON — fall through to the line grammar.
  }

  // Grammar 2: line-oriented input/output blocks.
  const lines = text.split('\n').filter((l) => l.trim())
  const result: BulkImportTestCaseDto[] = []
  let currentInput = ''
  let currentOutput = ''
  let isOutput = false
  for (const line of lines) {
    if (line.startsWith('---') || line.startsWith('===')) {
      if (currentInput && currentOutput) {
        result.push({
          inputText: currentInput.trim(),
          outputText: currentOutput.trim(),
          isSample: false,
          isHidden: true,
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
  if (currentInput && currentOutput) {
    result.push({
      inputText: currentInput.trim(),
      outputText: currentOutput.trim(),
      isSample: false,
      isHidden: true,
    })
  }
  return result
}

export function useTestCases(problemId: () => string) {
  const { t } = useI18n()

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

  const formData = ref<CreateTestCaseDto>({
    inputText: '',
    outputText: '',
    isSample: false,
    isHidden: true,
    explanation: '',
  })

  const activeTestCase = computed(() => {
    if (!activeId.value) return null
    return testCases.value.find((tc) => tc.id === activeId.value) ?? null
  })

  const sampleCount = computed(() => testCases.value.filter((tc) => tc.isSample).length)
  const hiddenCount = computed(
    () => testCases.value.filter((tc) => tc.isHidden && !tc.isSample).length,
  )

  async function loadTestCases() {
    loading.value = true
    try {
      const response = await testCasesApi.getTestCases(problemId(), { limit: 1000 })
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
      inputText: '',
      outputText: '',
      isSample: false,
      isHidden: true,
      explanation: '',
    }
    editDialogOpen.value = true
  }

  function openEditDialog(testCase: TestCase) {
    editingTestCase.value = testCase
    formData.value = {
      inputText: testCase.inputText,
      outputText: testCase.outputText,
      isSample: testCase.isSample,
      isHidden: testCase.isHidden,
      explanation: testCase.explanation ?? '',
      constraints: testCase.constraints,
    }
    editDialogOpen.value = true
  }

  async function saveTestCase() {
    if (!formData.value.inputText.trim() || !formData.value.outputText.trim()) {
      toast.error(t('testCases.validation.inputOutputRequired'))
      return
    }
    saving.value = true
    try {
      if (editingTestCase.value) {
        await testCasesApi.updateTestCase(problemId(), editingTestCase.value.id, formData.value)
        toast.success(t('testCases.toast.updateSuccess'))
      } else {
        const created = await testCasesApi.createTestCase(problemId(), formData.value)
        testCases.value.push(created)
        activeId.value = created.id
        toast.success(t('testCases.toast.createSuccess'))
      }
      editDialogOpen.value = false
      await loadTestCases()
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
      await testCasesApi.deleteTestCase(problemId(), testCase.id)
      testCases.value = testCases.value.filter((tc) => tc.id !== testCase.id)
      if (activeId.value === testCase.id) {
        activeId.value = testCases.value[0]?.id ?? null
      }
      toast.success(t('testCases.toast.deleteSuccess'))
    } catch (error) {
      console.error('Failed to delete test case:', error)
      toast.error(t('testCases.toast.deleteFailed'))
    }
  }

  async function toggleSample(testCase: TestCase) {
    try {
      await testCasesApi.updateTestCase(problemId(), testCase.id, {
        isSample: !testCase.isSample,
      })
      testCase.isSample = !testCase.isSample
    } catch (error) {
      console.error('Failed to toggle sample:', error)
      toast.error(t('testCases.toast.updateFailed'))
    }
  }

  async function toggleHidden(testCase: TestCase) {
    try {
      await testCasesApi.updateTestCase(problemId(), testCase.id, {
        isHidden: !testCase.isHidden,
      })
      testCase.isHidden = !testCase.isHidden
    } catch (error) {
      console.error('Failed to toggle hidden:', error)
      toast.error(t('testCases.toast.updateFailed'))
    }
  }

  async function exportTestCases() {
    try {
      await testCasesApi.exportTestCasesAsFile(problemId())
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
      const testCasesToImport = parseImportText(importText.value)
      if (testCasesToImport.length === 0) {
        toast.error(t('testCases.validation.noValidTestCases'))
        return
      }
      await testCasesApi.bulkImportTestCases(problemId(), {
        testCases: testCasesToImport,
        replaceExisting: replaceExisting.value,
      })
      toast.success(t('testCases.toast.importSuccess', { count: testCasesToImport.length }))
      importDialogOpen.value = false
      await loadTestCases()
    } catch (error) {
      console.error('Failed to import test cases:', error)
      toast.error(t('testCases.toast.importFailed'))
    } finally {
      importing.value = false
    }
  }

  return {
    testCases,
    loading,
    saving,
    activeId,
    activeTestCase,
    sampleCount,
    hiddenCount,
    editDialogOpen,
    importDialogOpen,
    editingTestCase,
    formData,
    importText,
    replaceExisting,
    importing,
    loadTestCases,
    selectTestCase,
    openCreateDialog,
    openEditDialog,
    saveTestCase,
    deleteTestCase,
    toggleSample,
    toggleHidden,
    exportTestCases,
    openImportDialog,
    importTestCases,
  }
}
