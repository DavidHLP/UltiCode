import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { createAbortController } from '@/utils/request'
import {
  testCasesApi,
  type CreateTestCaseDto,
  type TestCase,
} from '@/api/admin/test-cases'
import { normalizeTestCaseImport } from '../model/testCaseImport'
import {
  mapCaseScopeToFlags,
  mapFlagsToCaseScope,
  type CaseScope,
} from '../model/testCaseScope'

/**
 * Problem test-case authoring module — owns the list/load + abort protocol,
 * the editor dialog + form state machine, the per-item CRUD + CaseScope
 * toggle, and the bulk import/export pipeline behind one seam so
 * HiddenTestCasesEditor.vue is left with template wiring.
 *
 * Four concerns share one closure: (1) list state + the identity-change
 * abort protocol, (2) the editor dialog + form + CaseScope vocabulary,
 * (3) CRUD HTTP + optimistic update, (4) the bulk import/export pipeline.
 * Free-form grammar and CaseScope canonicalization live in the focused
 * testCaseImport module; this workflow consumes only normalized cases.
 *
 * Precedent: useSolutionAuthoring (console/src/composables) and
 * useForumThread (console/src/composables) share this monolithic-authoring
 * shape — one composable per authoring workflow, even with a single
 * consumer. useSolutionAuthoring explicitly cites useForumThread as its
 * precedent; this composable follows the same convention. The bulk import
 * pipeline is a fourth concern unique to test-case authoring, but it stays
 * inline: splitting orchestration would break the established
 * one-composable-per-authoring-workflow convention.
 */
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
   * This is the one author intent the per-row UI may express — the dual
   * "toggle Sample / toggle Hidden" surface the list/detail views used to
   * expose leaked the backend's XOR invariant: callers could land on the
   * disallowed (false,false) "draft" or the illegal (true,true) combination
   * by toggling only one flag. Concentrating the toggle on a single scope
   * argument lets the wire payload always carry both flags together through
   * mapCaseScopeToFlags, so the row stays judging-eligible.
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
      const testCasesToImport = normalizeTestCaseImport(importText.value)
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
