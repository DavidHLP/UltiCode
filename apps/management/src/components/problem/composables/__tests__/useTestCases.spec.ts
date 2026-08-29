import { beforeEach, describe, expect, it, vi } from 'vitest'
import { testCasesApi } from '@/api/admin/test-cases'
import type { TestCase, TestCasesResponse } from '@/api/admin/test-cases'

vi.mock('@/api/admin/test-cases', async () => {
  const actual =
    await vi.importActual<typeof import('@/api/admin/test-cases')>('@/api/admin/test-cases')
  return {
    ...actual,
    testCasesApi: {
      getTestCases: vi.fn(),
      createTestCase: vi.fn(),
      updateTestCase: vi.fn(),
      deleteTestCase: vi.fn(),
      bulkImportTestCases: vi.fn(),
      exportTestCasesAsFile: vi.fn(),
      reorderTestCases: vi.fn(),
    },
  }
})

vi.mock('vue-i18n', async () => {
  const actual = await vi.importActual<typeof import('vue-i18n')>('vue-i18n')
  return {
    ...actual,
    useI18n: () => ({
      t: (key: string) => key,
      locale: { value: 'en-US' },
    }),
  }
})
const toastSuccess = vi.fn()
const toastError = vi.fn()
const toastWarning = vi.hoisted(() => vi.fn())
vi.mock('vue-sonner', () => ({
  toast: {
    success: (...args: unknown[]) => toastSuccess(...args),
    error: (...args: unknown[]) => toastError(...args),
    warning: (...args: unknown[]) => toastWarning(...args),
  },
}))

import { useTestCases } from '../useTestCases'

const PROBLEM_ID = () => 'p-1'

const fakeCase = (overrides: Partial<TestCase> = {}): TestCase => ({
  id: 'tc-1',
  problemId: 1,
  isSample: false,
  isHidden: true,
  testOrder: 1,
  inputText: '1\n',
  outputText: '1\n',
  explanation: '',
  createdAt: '2026-06-14T00:00:00Z',
  updatedAt: '2026-06-14T00:00:00Z',
  ...overrides,
})

const emptyResponse: TestCasesResponse = { items: [], total: 0, page: 1, limit: 1000 }

/**
 * Workflow tests for the test-case editor composable.
 *
 * <p>The focused import-normalization module has its own grammar tests; this
 * file pins the actual editor workflow surface: state transitions, mutation
 * convergence, validation gating, error recovery and the XOR invariant on
 * every emitted DTO. It is the test surface the deep-module review asked
 * for &mdash; before this file the editor logic could only be exercised
 * through a full component mount.
 */
describe('useTestCases — editor workflow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(testCasesApi.getTestCases).mockResolvedValue(emptyResponse)
  })

  describe('loadTestCases', () => {
    it('populates testCases and selects the first when nothing is active', async () => {
      vi.mocked(testCasesApi.getTestCases).mockResolvedValue({
        items: [fakeCase({ id: 'a' }), fakeCase({ id: 'b', testOrder: 2 })],
        total: 2,
        page: 1,
        limit: 1000,
      })

      const { loadTestCases, testCases, activeId } = useTestCases(PROBLEM_ID)
      await loadTestCases()

      expect(testCasesApi.getTestCases).toHaveBeenCalledWith(
        PROBLEM_ID(),
        expect.objectContaining({ limit: 1000 }),
        expect.any(AbortSignal),
      )
      expect(testCases.value).toHaveLength(2)
      expect(activeId.value).toBe('a')
    })

    it('keeps the existing activeId when one is already set', async () => {
      vi.mocked(testCasesApi.getTestCases).mockResolvedValue({
        items: [fakeCase({ id: 'a' }), fakeCase({ id: 'b' })],
        total: 2,
        page: 1,
        limit: 1000,
      })

      const { loadTestCases, activeId, selectTestCase } = useTestCases(PROBLEM_ID)
      selectTestCase('b')
      await loadTestCases()

      expect(activeId.value).toBe('b')
    })

    it('emits a loadFailed toast on error and clears loading', async () => {
      vi.mocked(testCasesApi.getTestCases).mockRejectedValue(new Error('boom'))

      const { loadTestCases, loading } = useTestCases(PROBLEM_ID)
      await loadTestCases()

      expect(toastError).toHaveBeenCalledWith('testCases.toast.loadFailed')
      expect(loading.value).toBe(false)
    })
  })

  describe('saveTestCase — validation gate', () => {
    it('rejects an empty input and never calls the API', async () => {
      const { saveTestCase, openCreateDialog } = useTestCases(PROBLEM_ID)
      openCreateDialog()

      await saveTestCase()

      expect(testCasesApi.createTestCase).not.toHaveBeenCalled()
      expect(testCasesApi.updateTestCase).not.toHaveBeenCalled()
      expect(toastError).toHaveBeenCalledWith('testCases.validation.inputOutputRequired')
    })

    it('rejects an empty output and never calls the API', async () => {
      const { saveTestCase, openCreateDialog, formData } = useTestCases(PROBLEM_ID)
      openCreateDialog()
      formData.value.inputText = '1 2'
      formData.value.outputText = '   '

      await saveTestCase()

      expect(testCasesApi.createTestCase).not.toHaveBeenCalled()
      expect(testCasesApi.updateTestCase).not.toHaveBeenCalled()
      expect(toastError).toHaveBeenCalledWith('testCases.validation.inputOutputRequired')
    })
  })

  describe('saveTestCase — create flow', () => {
    it('POSTs and reloads the list, then closes the dialog', async () => {
      vi.mocked(testCasesApi.createTestCase).mockResolvedValue(fakeCase({ id: 'new' }))
      vi.mocked(testCasesApi.getTestCases).mockResolvedValue({
        items: [fakeCase({ id: 'new' })],
        total: 1,
        page: 1,
        limit: 1000,
      })

      const { saveTestCase, openCreateDialog, formData, editDialogOpen, activeId } =
        useTestCases(PROBLEM_ID)
      openCreateDialog()
      formData.value.inputText = '1 2'
      formData.value.outputText = '3'

      await saveTestCase()

      expect(testCasesApi.createTestCase).toHaveBeenCalledWith(PROBLEM_ID(), formData.value)
      expect(testCasesApi.getTestCases).toHaveBeenCalledTimes(1) // reload after save
      expect(editDialogOpen.value).toBe(false)
      expect(activeId.value).toBe('new')
      expect(toastSuccess).toHaveBeenCalledWith('testCases.toast.createSuccess')
    })

    it('emits a saveFailed toast on error and keeps the dialog open', async () => {
      vi.mocked(testCasesApi.createTestCase).mockRejectedValue(new Error('boom'))

      const { saveTestCase, openCreateDialog, formData, editDialogOpen, saving } =
        useTestCases(PROBLEM_ID)
      openCreateDialog()
      formData.value.inputText = '1'
      formData.value.outputText = '2'

      await saveTestCase()

      expect(editDialogOpen.value).toBe(true)
      expect(saving.value).toBe(false)
      expect(toastError).toHaveBeenCalledWith('testCases.toast.saveFailed')
    })
  })

  describe('saveTestCase — update flow', () => {
    it('PUTs against the existing id and reloads', async () => {
      vi.mocked(testCasesApi.updateTestCase).mockResolvedValue(fakeCase({ id: 'tc-1' }))
      const existing = fakeCase({ id: 'tc-1' })

      const { saveTestCase, openEditDialog, formData } = useTestCases(PROBLEM_ID)
      openEditDialog(existing)
      formData.value.inputText = 'updated'
      formData.value.outputText = 'updated-out'

      await saveTestCase()

      expect(testCasesApi.updateTestCase).toHaveBeenCalledWith(PROBLEM_ID(), 'tc-1', formData.value)
      expect(toastSuccess).toHaveBeenCalledWith('testCases.toast.updateSuccess')
    })
  })

  describe('deleteTestCase', () => {
    it('aborts when the confirm dialog is dismissed', async () => {
      const confirmSpy = vi.spyOn(globalThis, 'confirm').mockReturnValue(false)
      const target = fakeCase({ id: 'tc-1' })

      const { deleteTestCase } = useTestCases(PROBLEM_ID)
      await deleteTestCase(target)

      expect(testCasesApi.deleteTestCase).not.toHaveBeenCalled()
      confirmSpy.mockRestore()
    })

    it('DELETEs and drops the case from the local list', async () => {
      const confirmSpy = vi.spyOn(globalThis, 'confirm').mockReturnValue(true)
      vi.mocked(testCasesApi.deleteTestCase).mockResolvedValue(undefined)
      vi.mocked(testCasesApi.getTestCases).mockResolvedValue({
        items: [fakeCase({ id: 'a' }), fakeCase({ id: 'b' })],
        total: 2,
        page: 1,
        limit: 1000,
      })
      const { deleteTestCase, loadTestCases, testCases, activeId } = useTestCases(PROBLEM_ID)
      await loadTestCases()
      const target = testCases.value[0]! // 'a'

      await deleteTestCase(target)

      expect(testCasesApi.deleteTestCase).toHaveBeenCalledWith(PROBLEM_ID(), target.id)
      expect(testCases.value.find((tc) => tc.id === target.id)).toBeUndefined()
      // activeId rolls back to the first remaining case ('b' after 'a' is dropped)
      expect(activeId.value).toBe('b')
      confirmSpy.mockRestore()
    })

    it('emits deleteFailed on error and leaves the list intact', async () => {
      const confirmSpy = vi.spyOn(globalThis, 'confirm').mockReturnValue(true)
      vi.mocked(testCasesApi.getTestCases).mockResolvedValue({
        items: [fakeCase({ id: 'a' })],
        total: 1,
        page: 1,
        limit: 1000,
      })
      vi.mocked(testCasesApi.deleteTestCase).mockRejectedValue(new Error('boom'))

      const { deleteTestCase, loadTestCases, testCases } = useTestCases(PROBLEM_ID)
      await loadTestCases()
      const before = testCases.value.length
      await deleteTestCase(testCases.value[0]!)

      expect(testCases.value).toHaveLength(before)
      expect(toastError).toHaveBeenCalledWith('testCases.toast.deleteFailed')
      confirmSpy.mockRestore()
    })
  })

  describe('setCaseScope / toggleCaseScope', () => {
    it('setCaseScope SAMPLE PUTs isSample:true,isHidden:false and updates row', async () => {
      vi.mocked(testCasesApi.updateTestCase).mockResolvedValue(fakeCase())
      const target = fakeCase({ id: 'tc-1', isSample: false, isHidden: true })

      const { setCaseScope } = useTestCases(PROBLEM_ID)
      await setCaseScope(target, 'SAMPLE')

      expect(testCasesApi.updateTestCase).toHaveBeenCalledWith(PROBLEM_ID(), 'tc-1', {
        isSample: true,
        isHidden: false,
      })
      expect(target.isSample).toBe(true)
      expect(target.isHidden).toBe(false)
    })

    it('setCaseScope HIDDEN PUTs isSample:false,isHidden:true and updates row', async () => {
      vi.mocked(testCasesApi.updateTestCase).mockResolvedValue(fakeCase())
      const target = fakeCase({ id: 'tc-1', isSample: true, isHidden: false })

      const { setCaseScope } = useTestCases(PROBLEM_ID)
      await setCaseScope(target, 'HIDDEN')

      expect(testCasesApi.updateTestCase).toHaveBeenCalledWith(PROBLEM_ID(), 'tc-1', {
        isSample: false,
        isHidden: true,
      })
      expect(target.isSample).toBe(false)
      expect(target.isHidden).toBe(true)
    })

    it('toggleCaseScope flips SAMPLE→HIDDEN using current row flags', async () => {
      vi.mocked(testCasesApi.updateTestCase).mockResolvedValue(fakeCase())
      // start as SAMPLE
      const target = fakeCase({ id: 'tc-1', isSample: true, isHidden: false })

      const { toggleCaseScope } = useTestCases(PROBLEM_ID)
      await toggleCaseScope(target)

      expect(testCasesApi.updateTestCase).toHaveBeenCalledWith(PROBLEM_ID(), 'tc-1', {
        isSample: false,
        isHidden: true,
      })
      expect(target.isSample).toBe(false)
      expect(target.isHidden).toBe(true)
    })

    it('setCaseScope emits updateFailed and leaves the case unchanged on error', async () => {
      vi.mocked(testCasesApi.updateTestCase).mockRejectedValue(new Error('boom'))
      const target = fakeCase({ id: 'tc-1', isSample: false, isHidden: true })

      const { setCaseScope } = useTestCases(PROBLEM_ID)
      await setCaseScope(target, 'SAMPLE')

      expect(target.isSample).toBe(false)
      expect(target.isHidden).toBe(true)
      expect(toastError).toHaveBeenCalledWith('testCases.toast.updateFailed')
    })
  })

  describe('importTestCases', () => {
    it('rejects blank import text before any API call', async () => {
      const { importTestCases } = useTestCases(PROBLEM_ID)
      await importTestCases()

      expect(testCasesApi.bulkImportTestCases).not.toHaveBeenCalled()
      expect(toastError).toHaveBeenCalledWith('testCases.validation.importTextRequired')
    })

    it('rejects text that yields no parsed cases', async () => {
      const { importTestCases, importText } = useTestCases(PROBLEM_ID)
      importText.value = 'noise with no Input:/Output: blocks'

      await importTestCases()

      expect(testCasesApi.bulkImportTestCases).not.toHaveBeenCalled()
      expect(toastError).toHaveBeenCalledWith('testCases.validation.noValidTestCases')
    })

    it('POSTs the parsed cases with replaceExisting and reloads', async () => {
      vi.mocked(testCasesApi.bulkImportTestCases).mockResolvedValue({ count: 1 })
      const { importTestCases, importText, replaceExisting, importDialogOpen } =
        useTestCases(PROBLEM_ID)
      replaceExisting.value = true
      importText.value = ['Input:', '1 2', 'Output:', '3'].join('\n')

      await importTestCases()

      expect(testCasesApi.bulkImportTestCases).toHaveBeenCalledWith(PROBLEM_ID(), {
        testCases: [
          { inputText: '1 2', outputText: '3', isSample: false, isHidden: true },
        ],
        replaceExisting: true,
      })
      expect(importDialogOpen.value).toBe(false)
      expect(toastSuccess).toHaveBeenCalled()
    })

    it('emits importFailed on error and keeps the dialog open', async () => {
      vi.mocked(testCasesApi.bulkImportTestCases).mockRejectedValue(new Error('boom'))
      const { importTestCases, importText, openImportDialog, importDialogOpen } =
        useTestCases(PROBLEM_ID)
      openImportDialog()
      importText.value = ['Input:', '1 2', 'Output:', '3'].join('\n')

      await importTestCases()

      expect(importDialogOpen.value).toBe(true)
      expect(toastError).toHaveBeenCalledWith('testCases.toast.importFailed')
    })

    it('forwards replaceExisting=false on append-only import (default)', async () => {
      vi.mocked(testCasesApi.bulkImportTestCases).mockResolvedValue({ count: 1 })
      const { importTestCases, importText } = useTestCases(PROBLEM_ID)
      // replaceExisting is false by default (openImportDialog resets it).
      importText.value = JSON.stringify([
        { inputText: '1 2', outputText: '3', isSample: true, isHidden: false },
      ])

      await importTestCases()

      expect(testCasesApi.bulkImportTestCases).toHaveBeenCalledWith(
        PROBLEM_ID(),
        expect.objectContaining({
          testCases: [
            expect.objectContaining({
              inputText: '1 2',
              outputText: '3',
              isSample: true,
              isHidden: false,
            }),
          ],
          replaceExisting: false,
        }),
      )
    })

    it('canonicalises (isSample=true, isHidden=true) to HIDDEN before the wire round-trip', async () => {
      // Defensive: a JSON literal where isSample is true and isHidden is
      // also true is canonicalised to HIDDEN by the focused import module
      // before the wire round-trip, so the backend never sees a (true,true)
      // row. This pins the contract.
      vi.mocked(testCasesApi.bulkImportTestCases).mockResolvedValue({ count: 1 })
      const { importTestCases, importText } = useTestCases(PROBLEM_ID)
      importText.value = JSON.stringify([
        { inputText: '1 2', outputText: '3', isSample: true, isHidden: true },
      ])

      await importTestCases()

      expect(testCasesApi.bulkImportTestCases).toHaveBeenCalledWith(
        PROBLEM_ID(),
        expect.objectContaining({
          testCases: [
            expect.objectContaining({ isSample: false, isHidden: true }),
          ],
        }),
      )
    })

    it('keeps the import dialog open and the parsed text when bulkImportTestCases rejects', async () => {
      // Recovery path: after a failed import, the user should be able to
      // adjust the text and retry without losing their draft.
      vi.mocked(testCasesApi.bulkImportTestCases).mockRejectedValue(new Error('boom'))
      const { importTestCases, importText, openImportDialog, importDialogOpen, replaceExisting } =
        useTestCases(PROBLEM_ID)
      openImportDialog()
      replaceExisting.value = true
      const draft = JSON.stringify([
        { inputText: 'a', outputText: 'b', isSample: false, isHidden: true },
      ])
      importText.value = draft

      await importTestCases()

      expect(importDialogOpen.value).toBe(true)
      expect(importText.value).toBe(draft)
      expect(replaceExisting.value).toBe(true)
    })
  })

  describe('selection + dialog state transitions', () => {
    it('openCreateDialog resets the form to a fresh HIDDEN case', () => {
      const { openCreateDialog, formData, editDialogOpen, editingTestCase } = useTestCases(PROBLEM_ID)
      openCreateDialog()

      expect(editDialogOpen.value).toBe(true)
      expect(editingTestCase.value).toBeNull()
      // Fresh default case satisfies the XOR invariant
      expect(formData.value.isSample).toBe(false)
      expect(formData.value.isHidden).toBe(true)
    })

    it('openEditDialog copies the target into the form', () => {
      const target = fakeCase({
        id: 'tc-x',
        inputText: 'in',
        outputText: 'out',
        isSample: true,
        isHidden: false,
        explanation: 'exp',
      })
      const { openEditDialog, formData, editDialogOpen, editingTestCase } = useTestCases(PROBLEM_ID)
      openEditDialog(target)

      expect(editDialogOpen.value).toBe(true)
      expect(editingTestCase.value?.id).toBe(target.id)
      expect(formData.value.inputText).toBe('in')
      expect(formData.value.outputText).toBe('out')
      expect(formData.value.isSample).toBe(true)
      expect(formData.value.isHidden).toBe(false)
    })

    it('selectTestCase updates activeId', () => {
      const { selectTestCase, activeId } = useTestCases(PROBLEM_ID)
      selectTestCase('x')
      expect(activeId.value).toBe('x')
    })

    it('openImportDialog resets importText and replaceExisting', () => {
      const { openImportDialog, importText, replaceExisting, importDialogOpen } =
        useTestCases(PROBLEM_ID)
      openImportDialog()

      expect(importDialogOpen.value).toBe(true)
      expect(importText.value).toBe('')
      expect(replaceExisting.value).toBe(false)
    })
  })

  describe('derived counters', () => {
    it('sampleCount and hiddenCount respect the XOR filter', async () => {
      vi.mocked(testCasesApi.getTestCases).mockResolvedValue({
        items: [
          fakeCase({ id: 's1', isSample: true, isHidden: false }),
          fakeCase({ id: 'h1', isSample: false, isHidden: true }),
          fakeCase({ id: 'h2', isSample: false, isHidden: true }),
        ],
        total: 3,
        page: 1,
        limit: 1000,
      })
      const { loadTestCases, sampleCount, hiddenCount } = useTestCases(PROBLEM_ID)
      await loadTestCases()

      expect(sampleCount.value).toBe(1)
      expect(hiddenCount.value).toBe(2)
    })
  })
})
