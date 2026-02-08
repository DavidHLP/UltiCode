import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { useProblemsStore } from '@/stores/admin/problems'
import { problemsApi } from '@/api/admin/problems'
import { ApiError } from '@/utils/request'
import type { Problem, Difficulty } from '@/api/admin/problems'

interface ErrorContext {
  title: string
  message: string
  suggestion?: string
  canRetry: boolean
}

function getErrorContext(error: unknown, action: string): ErrorContext {
  const apiError = error instanceof ApiError ? error : null
  const statusCode = apiError?.code || 0
  const errorMessage = apiError?.response?.data?.message || apiError?.message || 'Unknown error'

  switch (statusCode) {
    case 400:
      return {
        title: 'Validation Error',
        message: errorMessage || 'Please check your input',
        suggestion: 'Please correct the errors and try again',
        canRetry: false,
      }
    case 401:
      return {
        title: 'Unauthorized',
        message: 'You need to login first',
        suggestion: 'Please login and try again',
        canRetry: false,
      }
    case 403:
      return {
        title: 'Forbidden',
        message: 'You do not have permission',
        suggestion: 'Contact administrator if you believe this is an error',
        canRetry: false,
      }
    case 404:
      return {
        title: 'Not Found',
        message: `${action} not found`,
        suggestion: 'The resource may have been deleted',
        canRetry: false,
      }
    case 500:
    case 502:
    case 503:
      return {
        title: 'Server Error',
        message: 'Server error occurred',
        suggestion: 'Please try again later',
        canRetry: true,
      }
    default:
      return {
        title: 'Network Error',
        message: errorMessage,
        suggestion: 'Check your connection and try again',
        canRetry: true,
      }
  }
}

export function useProblemsActions(loadProblems: () => Promise<void>) {
  const router = useRouter()
  const problemsStore = useProblemsStore()

  const selectedProblemId = ref<string | null>(null)
  const selectedProblemTitle = ref<string | null>(null)
  const deleteDialogOpen = ref(false)
  const importing = ref(false)
  const importDialogOpen = ref(false)
  const selectedRows = ref<Problem[]>([])
  const bulkActionDialogOpen = ref(false)
  const bulkActionType = ref<'publish' | 'unpublish' | 'delete' | 'restore'>('publish')
  const bulkActionLoading = ref(false)
  const bulkEditDialogOpen = ref(false)

  // Navigation actions
  function viewProblem(id: string) {
    router.push({ name: 'problem-view-description', params: { id } })
  }

  function viewProblemCode(id: string) {
    router.push({ name: 'problem-view-code', params: { id } })
  }

  function viewProblemCases(id: string) {
    router.push({ name: 'problem-view-cases', params: { id } })
  }

  function editProblem(id: string) {
    router.push({ name: 'problem-edit-description', params: { id } })
  }

  function editProblemCode(id: string) {
    router.push({ name: 'problem-edit-code', params: { id } })
  }

  function editProblemCases(id: string) {
    router.push({ name: 'problem-edit-cases', params: { id } })
  }

  function confirmDelete(problem: Problem) {
    selectedProblemId.value = problem.id
    selectedProblemTitle.value = problem.title
    deleteDialogOpen.value = true
  }

  async function handleDeleteProblem(id: string | number) {
    await problemsStore.deleteProblem(String(id))
  }

  // Problem actions
  async function publishProblem(id: string) {
    try {
      await problemsStore.publishProblem(id)
      toast.success('Problem published successfully')
      await loadProblems()
    } catch (error) {
      const ctx = getErrorContext(error, 'Publish')
      toast.error(ctx.message, {
        description: ctx.suggestion,
        action: ctx.canRetry
          ? {
              label: 'Retry',
              onClick: () => publishProblem(id),
            }
          : undefined,
      })
    }
  }

  async function unpublishProblem(id: string) {
    try {
      await problemsStore.unpublishProblem(id)
      toast.success('Problem unpublished successfully')
      await loadProblems()
    } catch (error) {
      const ctx = getErrorContext(error, 'Unpublish')
      toast.error(ctx.message, {
        description: ctx.suggestion,
        action: ctx.canRetry
          ? {
              label: 'Retry',
              onClick: () => unpublishProblem(id),
            }
          : undefined,
      })
    }
  }

  async function flagProblem(id: string) {
    try {
      const reason = prompt('Please enter the reason for flagging:')
      if (!reason) return
      await problemsApi.flagProblem(id, reason)
      toast.success('Problem flagged successfully')
      await loadProblems()
    } catch (error) {
      const ctx = getErrorContext(error, 'Flag')
      toast.error(ctx.message, { description: ctx.suggestion })
    }
  }

  async function unflagProblem(id: string) {
    try {
      await problemsApi.moderateProblem(id, { status: 'DISMISSED' })
      toast.success('Problem unflagged successfully')
      await loadProblems()
    } catch (error) {
      const ctx = getErrorContext(error, 'Unflag')
      toast.error(ctx.message, { description: ctx.suggestion })
    }
  }

  async function exportProblems(
    format: 'json' | 'csv',
    filters: {
      searchQuery: string
      difficultyFilter: string
      statusFilter: string
      publishedFilter: string
    },
  ) {
    try {
      importing.value = true
      await problemsApi.exportProblems(
        {
          search: filters.searchQuery || undefined,
          difficulty:
            filters.difficultyFilter === 'all'
              ? undefined
              : (filters.difficultyFilter as Difficulty),
          status:
            filters.statusFilter === 'all'
              ? undefined
              : (filters.statusFilter as Problem['status']),
          is_published:
            filters.publishedFilter === 'all' ? undefined : filters.publishedFilter === 'published',
        },
        format,
      )
      toast.success('Problems exported successfully')
    } catch (error) {
      console.error('Failed to export problems:', error)
      const ctx = getErrorContext(error, 'Export')
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
      toast.error('No problems selected')
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
        toast.success(`Successfully ${bulkActionType.value} ${successCount} problems`)
      } else if (successCount === 0) {
        toast.error(`Failed to ${bulkActionType.value} ${failedCount} problems`)
      } else {
        toast.warning(`${successCount} succeeded, ${failedCount} failed`)
      }

      selectedRows.value = []
      bulkActionDialogOpen.value = false
      await loadProblems()
    } catch (error) {
      console.error('Failed to perform bulk action:', error)
      const ctx = getErrorContext(error, 'Bulk Action')
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
    selectedProblemId,
    selectedProblemTitle,
    deleteDialogOpen,
    importing,
    importDialogOpen,
    selectedRows,
    bulkActionDialogOpen,
    bulkActionType,
    bulkActionLoading,
    bulkEditDialogOpen,
    viewProblem,
    viewProblemCode,
    viewProblemCases,
    editProblem,
    editProblemCode,
    editProblemCases,
    confirmDelete,
    handleDeleteProblem,
    publishProblem,
    unpublishProblem,
    flagProblem,
    unflagProblem,
    exportProblems,
    handleImported,
    handleBulkAction,
    confirmBulkAction,
    handleBulkEdited,
  }
}
