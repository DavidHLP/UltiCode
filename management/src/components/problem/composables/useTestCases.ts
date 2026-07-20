import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { createAbortController } from '@/utils/request'
import {
  testCasesApi,
  type BulkImportTestCaseDto,
  type CaseScope,
  type CreateTestCaseDto,
  type TestCase,
  mapCaseScopeToFlags,
  mapFlagsToCaseScope,
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

  /**
   * Cancels any in-flight loadTestCases() request so a stale response cannot
   * overwrite state after a problemId change or after a save-triggered reload.
   */
  let loadController: AbortController | null = null

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
    if (loadController) {
      loadController.abort()
    }
    const controller = createAbortController()
    loadController = controller
    loading.value = true
    try {
      const response = await testCasesApi.getTestCases(problemId(), { limit: 1000 }, controller.signal)
      // Discard result if a newer request has already superseded this one.
      if (controller.signal.aborted) return
      testCases.value = response.items
      if (testCases.value.length > 0 && !activeId.value) {
        activeId.value = testCases.value[0]?.id ?? null
      }
    } catch (error) {
      if ((error as Error).name === 'CanceledError' || (error as Error).message?.includes('canceled')) return
      console.error('Failed to load test cases:', error)
      toast.error(t('testCases.toast.loadFailed'))
    } finally {
      if (!controller.signal.aborted) {
        loading.value = false
      }
    }
  }

  function selectTestCase(id: string) {
    activeId.value = id
  }

  /**
   * Reactive problemId coordination: moves ownership of the identity-change
   * protocol inside the composable. When the identity changes, the prior
   * in-flight load is aborted and a fresh load fires — stale responses
   * can no longer overwrite newer problem state.
   */
  watch(problemId, () => {
    activeId.value = null
    loadTestCases()
  })

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

  /**
   * Set the canonical CaseScope (SAMPLE | HIDDEN) on a persisted test case.
   *
   * <p>This is the one author intent the per-row UI may express — the dual
   * "toggle Sample / toggle Hidden" surface the list/detail views used to
   * expose leaked the backend's XOR invariant: callers could land on the
   * disallowed (false,false) "draft" or the illegal (true,true) combination
   * by toggling only one flag. Concentrating the toggle on a single scope
   * argument lets the wire payload always carry both flags together through
   * {@link mapCaseScopeToFlags}, so the row stays judging-eligible.
   */
  async function setCaseScope(testCase: TestCase, scope: CaseScope) {
    const flags = mapCaseScopeToFlags(scope)
    try {
      await testCasesApi.updateTestCase(problemId(), testCase.id, flags)
      testCase.isSample = flags.isSample
      testCase.isHidden = flags.isHidden
    } catch (error) {
      console.error('Failed to update test case scope:', error)
      toast.error(t('testCases.toast.updateFailed'))
    }
  }

  /**
   * Convenience: flip the row's current scope to the opposite canonical value.
   * Used by list/detail UIs that expose a single "Mark as Sample / Hidden"
   * affordance, so they never have to reason about the underlying flags.
   */
  async function toggleCaseScope(testCase: TestCase) {
    const next: CaseScope =
      mapFlagsToCaseScope(testCase.isSample, testCase.isHidden) === 'SAMPLE' ? 'HIDDEN' : 'SAMPLE'
    await setCaseScope(testCase, next)
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
    setCaseScope,
    toggleCaseScope,
    exportTestCases,
    openImportDialog,
    importTestCases,
  }
}
