import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import {
  testCasesApi,
  type TestCase,
  type CreateTestCaseDto,
  type BulkImportTestCaseDto,
} from '@/api/admin/test-cases'

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
    input_text: '',
    output_text: '',
    is_sample: false,
    is_hidden: true,
    explanation: '',
  })

  const activeTestCase = computed(() => {
    if (!activeId.value) return null
    return testCases.value.find((tc) => tc.id === activeId.value) ?? null
  })

  const sampleCount = computed(() => testCases.value.filter((tc) => tc.is_sample).length)
  const hiddenCount = computed(
    () => testCases.value.filter((tc) => tc.is_hidden && !tc.is_sample).length,
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
        is_sample: !testCase.is_sample,
      })
      testCase.is_sample = !testCase.is_sample
    } catch (error) {
      console.error('Failed to toggle sample:', error)
      toast.error(t('testCases.toast.updateFailed'))
    }
  }

  async function toggleHidden(testCase: TestCase) {
    try {
      await testCasesApi.updateTestCase(problemId(), testCase.id, {
        is_hidden: !testCase.is_hidden,
      })
      testCase.is_hidden = !testCase.is_hidden
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
      let testCasesToImport: BulkImportTestCaseDto[] = []
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
      await testCasesApi.bulkImportTestCases(problemId(), {
        test_cases: testCasesToImport,
        replace_existing: replaceExisting.value,
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
