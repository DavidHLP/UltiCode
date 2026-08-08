import { ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { useProblemsStore } from '@/stores/admin/problems'
import { type Problem, problemsApi, type Difficulty } from '@/api/admin/problems'
import { getErrorContext } from '@/utils/error'

export function useProblemActions(loadProblems: () => Promise<void>) {
  const { t } = useI18n()
  const router = useRouter()
  const problemsStore = useProblemsStore()

  // Single item dialog state
  const selectedProblemId = ref<string | null>(null)
  const selectedProblemTitle = ref<string | null>(null)
  const deleteDialogOpen = ref(false)
  const flagDialogOpen = ref(false)
  const selectedProblemForFlag = ref<string | null>(null)
  const selectedProblemForFlagTitle = ref<string | null>(null)
  const flagInfoDialogOpen = ref(false)
  const selectedProblemForFlagInfo = ref<Problem | null>(null)
  const auditDrawerOpen = ref(false)
  const auditDrawerProblemId = ref<string | number | null>(null)

  // Import/Export state
  const importing = ref(false)
  const importDialogOpen = ref(false)

  // Bulk action state
  const selectedRows = ref<Problem[]>([])
  const bulkActionDialogOpen = ref(false)
  const bulkActionType = ref<'publish' | 'unpublish' | 'delete' | 'restore'>('publish')
  const bulkActionLoading = ref(false)
  const bulkEditDialogOpen = ref(false)

  function viewProblem(id: string) {
    router.push({ name: 'problem-detail', params: { id } })
  }

  function viewProblemCode(id: string) {
    router.push({ name: 'problem-detail', params: { id, tab: 'code' } })
  }

  function viewProblemCases(id: string) {
    router.push({ name: 'problem-detail', params: { id, tab: 'cases' } })
  }

  function confirmDelete(problem: Problem) {
    selectedProblemId.value = problem.id
    selectedProblemTitle.value = problem.title
    nextTick(() => {
      deleteDialogOpen.value = true
    })
  }

  async function handleDeleteProblem(id: string | number) {
    await problemsStore.deleteProblem(String(id))
  }

  async function publishProblem(id: string) {
    try {
      await problemsStore.publishProblem(id)
      toast.success(t('problems.toast.publishSuccess'))
      await loadProblems()
    } catch (error) {
      const ctx = getErrorContext(error, t('problems.actions.publish'), t)
      toast.error(ctx.message, {
        description: ctx.suggestion,
        action: ctx.canRetry
          ? {
              label: t('common.retry'),
              onClick: () => publishProblem(id),
            }
          : undefined,
      })
    }
  }

  async function unpublishProblem(id: string) {
    try {
      await problemsStore.unpublishProblem(id)
      toast.success(t('problems.toast.unpublishSuccess'))
      await loadProblems()
    } catch (error) {
      const ctx = getErrorContext(error, t('problems.actions.unpublish'), t)
      toast.error(ctx.message, {
        description: ctx.suggestion,
        action: ctx.canRetry
          ? {
              label: t('common.retry'),
              onClick: () => unpublishProblem(id),
            }
          : undefined,
      })
    }
  }

  function openFlagDialog(problem: Problem) {
    selectedProblemForFlag.value = problem.id
    selectedProblemForFlagTitle.value = problem.title
    nextTick(() => {
      flagDialogOpen.value = true
    })
  }

  function viewFlagInfo(problem: Problem) {
    selectedProblemForFlagInfo.value = problem
    nextTick(() => {
      flagInfoDialogOpen.value = true
    })
  }

  function openAuditDrawer(problem: Problem) {
    auditDrawerProblemId.value = problem.id
    nextTick(() => {
      auditDrawerOpen.value = true
    })
  }

  async function handleFlagProblem(id: string | number, reason?: string) {
    try {
      await problemsApi.flagProblem(String(id), reason || '')
      toast.success(t('moderation.flagSuccess'))
      await loadProblems()
    } catch (error) {
      const ctx = getErrorContext(error, t('problems.actions.flag'), t)
      toast.error(ctx.message, { description: ctx.suggestion })
      throw error
    }
  }

  async function unflagProblem(id: string) {
    try {
      await problemsApi.moderateProblem(id, { status: 'DISMISSED' })
      toast.success(t('moderation.unflagSuccess'))
      await loadProblems()
    } catch (error) {
      const ctx = getErrorContext(error, t('problems.actions.unflag'), t)
      toast.error(ctx.message, { description: ctx.suggestion })
    }
  }

  async function exportProblems(
    exportParams: {
      search?: string
      difficulty?: Difficulty
      status?: Problem['status']
      isPublished?: boolean
    },
    format: 'json' | 'csv',
  ) {
    try {
      importing.value = true
      await problemsApi.exportProblems(exportParams, format)
      toast.success(t('problems.export.success'))
    } catch (error) {
      console.error('Failed to export problems:', error)
      const ctx = getErrorContext(error, t('problems.actions.export'), t)
      toast.error(ctx.message, {
        description: ctx.suggestion,
      })
    } finally {
      importing.value = false
    }
  }

  async function handleImported() {
    await loadProblems()
  }

  async function handleBulkAction(action: 'publish' | 'unpublish' | 'delete' | 'restore') {
    if (selectedRows.value.length === 0) {
      toast.error(t('problems.bulk.noSelection'))
      return
    }
    bulkActionType.value = action
    bulkActionDialogOpen.value = true
  }

  async function confirmBulkAction() {
    if (selectedRows.value.length === 0) return

    bulkActionLoading.value = true
    try {
      const response = await problemsApi.bulkAction({
        ids: selectedRows.value.map((p) => p.id),
        action: bulkActionType.value,
      })

      const successCount = response.results.filter((r) => r.success).length
      const failedCount = response.results.filter((r) => !r.success).length

      if (failedCount === 0) {
        toast.success(
          t('problems.bulk.success', {
            count: successCount,
            action: t(`problems.bulk.${bulkActionType.value}`, bulkActionType.value),
          }),
        )
      } else if (successCount === 0) {
        toast.error(
          t('problems.bulk.failed', {
            count: failedCount,
            action: t(`problems.bulk.${bulkActionType.value}`, bulkActionType.value),
          }),
        )
      } else {
        toast.warning(
          t('problems.bulk.partial', {
            success: successCount,
            failed: failedCount,
            action: t(`problems.bulk.${bulkActionType.value}`, bulkActionType.value),
          }),
        )
      }

      selectedRows.value = []
      bulkActionDialogOpen.value = false
      await loadProblems()
    } catch (error) {
      console.error('Failed to perform bulk action:', error)
      const ctx = getErrorContext(error, t('problems.bulk.action'), t)
      toast.error(ctx.message, {
        description: ctx.suggestion,
      })
    } finally {
      bulkActionLoading.value = false
    }
  }

  async function handleBulkEdited() {
    selectedRows.value = []
    bulkEditDialogOpen.value = false
    await loadProblems()
  }

  return {
    // Dialog state
    selectedProblemId,
    selectedProblemTitle,
    deleteDialogOpen,
    flagDialogOpen,
    selectedProblemForFlag,
    selectedProblemForFlagTitle,
    flagInfoDialogOpen,
    selectedProblemForFlagInfo,
    auditDrawerOpen,
    auditDrawerProblemId,
    importing,
    importDialogOpen,
    selectedRows,
    bulkActionDialogOpen,
    bulkActionType,
    bulkActionLoading,
    bulkEditDialogOpen,

    // Navigation
    viewProblem,
    viewProblemCode,
    viewProblemCases,

    // Single item actions
    confirmDelete,
    handleDeleteProblem,
    publishProblem,
    unpublishProblem,
    openFlagDialog,
    viewFlagInfo,
    openAuditDrawer,
    handleFlagProblem,
    unflagProblem,

    // Import/Export
    exportProblems,
    handleImported,

    // Bulk actions
    handleBulkAction,
    confirmBulkAction,
    handleBulkEdited,
  }
}
