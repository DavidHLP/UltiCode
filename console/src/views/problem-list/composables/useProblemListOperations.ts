import { ref, computed, watch, type Ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { useI18n } from 'vue-i18n'
import type { Problem } from '@/types/problem'
import type { ProblemList, ProblemListCategoryOption } from '@/types/problem-list'
import {
  fetchProblemListOverview,
  forkProblemList,
  deleteProblemList,
  updateProblemList,
  addProblemToList,
  removeProblemFromList,
  saveList,
  unsaveList,
  moveListToCategory,
} from '@/api/problem-list'

export function useProblemListOperations(listId: Ref<string>) {
  const router = useRouter()
  const { t, locale } = useI18n()

  const currentList = ref<ProblemList | null>(null)
  const problems = ref<Problem[]>([])
  const isSaved = ref(false)
  const isSaving = ref(false)
  const isForking = ref(false)
  const isDeleting = ref(false)
  const userCategories = ref<ProblemListCategoryOption[]>([])
  const currentCategoryId = ref<string | null>(null)

  const currentUser = useAuthStore().fetchCurrentUserId()

  const editForm = ref({
    name: '',
    description: '',
    isPublic: false,
  })

  const isOwner = computed(() => {
    return !!(currentUser && currentList.value?.authorId === currentUser)
  })

  const canSave = computed(() => {
    return !!(
      currentUser &&
      currentList.value &&
      currentList.value.authorId !== currentUser &&
      currentList.value.isPublic
    )
  })

  const problemIdsInList = computed(() => {
    return new Set(problems.value.map((p) => p.id))
  })

  async function loadProblemList(id?: string) {
    if (!id) {
      currentList.value = null
      problems.value = []
      isSaved.value = false
      return
    }
    try {
      const overview = await fetchProblemListOverview(id)
      currentList.value = overview.list
      problems.value = overview.problems
      isSaved.value = overview.viewer?.isSaved ?? false
      currentCategoryId.value = overview.viewer?.categoryId ?? null
      userCategories.value = overview.categories ?? []

      if (currentList.value) {
        editForm.value = {
          name: currentList.value.name,
          description: currentList.value.description || '',
          isPublic: currentList.value.isPublic || false,
        }
      }
    } catch (error) {
      console.error('Failed to load problem list overview', error)
      problems.value = []
      currentList.value = null
      isSaved.value = false
      currentCategoryId.value = null
      userCategories.value = []
    }
  }

  watch(
    listId,
    (id) => {
      void loadProblemList(id)
    },
    { immediate: true },
  )

  function formatDate(date?: Date | string): string {
    if (!date) return ''
    const d = typeof date === 'string' ? new Date(date) : date
    return new Intl.DateTimeFormat(locale.value, {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    }).format(d)
  }

  async function handleShare() {
    const url = window.location.href
    await navigator.clipboard.writeText(url)
    toast.success(t('problem.problemList.messages.linkCopied'), {
      description: t('problem.problemList.messages.linkCopiedDesc'),
    })
  }

  async function handleFork() {
    if (!currentUser) {
      toast.error(t('problem.problemList.messages.authRequired'), {
        description: t('problem.problemList.messages.signInToFork'),
      })
      return
    }

    isForking.value = true
    try {
      const newListId = await forkProblemList(listId.value)
      toast.success(t('problem.problemList.messages.forkSuccess'), {
        description: t('problem.problemList.messages.forkSuccessDesc'),
      })
      router.push(`/problemset/list/${newListId}`)
    } catch {
      toast.error(t('problem.problemList.messages.forkFailed'))
    } finally {
      isForking.value = false
    }
  }

  async function handleDelete() {
    if (!currentUser || !currentList.value) return

    isDeleting.value = true
    try {
      await deleteProblemList(listId.value)
      toast.success(t('problem.problemList.messages.deleteSuccess'), {
        description: t('problem.problemList.messages.deleteSuccessDesc'),
      })
      router.push('/problemset')
    } catch {
      toast.error(t('problem.problemList.messages.deleteFailed'))
    } finally {
      isDeleting.value = false
    }
  }

  async function handleSaveEdit() {
    if (!currentUser || !currentList.value) return

    try {
      await updateProblemList(listId.value, {
        name: editForm.value.name,
        description: editForm.value.description,
        isPublic: editForm.value.isPublic,
      })

      await loadProblemList(listId.value)

      toast.success(t('problem.problemList.messages.updateSuccess'), {
        description: t('problem.problemList.messages.updateSuccessDesc'),
      })
      return true
    } catch {
      toast.error(t('problem.problemList.messages.updateFailed'))
      return false
    }
  }

  async function handleToggleSave() {
    if (!currentUser || !currentList.value) return

    isSaving.value = true
    try {
      if (isSaved.value) {
        await unsaveList(listId.value)
        isSaved.value = false
        currentCategoryId.value = null
        toast.success(t('problem.problemList.messages.unsaveSuccess'))
      } else {
        await saveList(listId.value)
        isSaved.value = true
        toast.success(t('problem.problemList.messages.saveSuccess'))
      }
    } catch {
      toast.error(
        isSaved.value
          ? t('problem.problemList.messages.unsaveFailed')
          : t('problem.problemList.messages.saveFailed'),
      )
    } finally {
      isSaving.value = false
    }
  }

  async function handleMoveToCategory(categoryId: string | null) {
    if (!currentUser || !isSaved.value) return

    try {
      await moveListToCategory(listId.value, categoryId)
      currentCategoryId.value = categoryId
      toast.success(
        categoryId
          ? t('problem.problemList.messages.moveSuccess')
          : t('problem.problemList.messages.removeCategorySuccess'),
      )
    } catch {
      toast.error(t('problem.problemList.messages.moveFailed'))
    }
  }

  async function handleAddProblem(problem: Problem) {
    if (!currentUser || problemIdsInList.value.has(problem.id)) return

    try {
      await addProblemToList(listId.value, problem.id)
      problems.value = [...problems.value, problem]
      toast.success(
        t('problem.problemList.messages.addSuccess', { title: problem.title }),
      )
    } catch (e) {
      console.error('Failed to add problem', e)
      toast.error(t('problem.problemList.messages.addFailed'))
    }
  }

  async function handleRemoveProblem(problem: Problem) {
    if (!currentUser) return

    try {
      await removeProblemFromList(listId.value, problem.id)
      problems.value = problems.value.filter((p) => p.id !== problem.id)
      toast.success(
        t('problem.problemList.messages.removeSuccess', { title: problem.title }),
      )
    } catch (e) {
      console.error('Failed to remove problem', e)
      toast.error(t('problem.problemList.messages.removeFailed'))
    }
  }

  function getDifficultyColor(difficulty: string): string {
    switch (difficulty?.toLowerCase()) {
      case 'easy':
        return 'text-[var(--terminal-green)] bg-[oklch(0.6444_0.1508_118.6_/_0.12)]'
      case 'medium':
        return 'text-[var(--terminal-amber)] bg-[oklch(0.6545_0.1340_85.7_/_0.12)]'
      case 'hard':
        return 'text-[var(--terminal-red)] bg-[oklch(0.5863_0.2064_27.1_/_0.12)]'
      default:
        return 'text-muted-foreground bg-muted'
    }
  }

  return {
    currentList,
    problems,
    isSaved,
    isSaving,
    isForking,
    isDeleting,
    editForm,
    userCategories,
    currentCategoryId,
    isOwner,
    canSave,
    problemIdsInList,
    formatDate,
    handleShare,
    handleFork,
    handleDelete,
    handleSaveEdit,
    handleToggleSave,
    handleMoveToCategory,
    handleAddProblem,
    handleRemoveProblem,
    getDifficultyColor,
  }
}
