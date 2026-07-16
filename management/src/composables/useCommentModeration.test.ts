import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useCommentsStore } from '@/stores/admin/comments'
import { useAuthStore } from '@/stores/auth'
import type { Comment } from '@/api/admin/comments'

vi.mock('@/api/admin/comments', () => ({
  commentsApi: {
    getComments: vi.fn(),
    getComment: vi.fn(),
    flagComment: vi.fn(),
    unflagComment: vi.fn(),
    deleteComment: vi.fn(),
    bulkAction: vi.fn(),
  },
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: vi.fn(),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
    locale: { value: 'en-US' },
  }),
  createI18n: () => ({
    global: {
      t: (key: string) => key,
    },
  }),
}))

const toastSuccess = vi.fn()
const toastError = vi.fn()
vi.mock('vue-sonner', () => ({
  toast: {
    success: (...args: unknown[]) => toastSuccess(...args),
    error: (...args: unknown[]) => toastError(...args),
  },
}))

import { useCommentModeration } from './useCommentModeration'

const makeComment = (overrides: Partial<Comment> = {}): Comment => ({
  id: 'c-1',
  content: 'hello',
  createdAt: '2026-07-16T00:00:00Z',
  updatedAt: '2026-07-16T00:00:00Z',
  authorId: 'u-1',
  type: 'forum',
  author: { id: 'u-1', username: 'alice' },
  isFlagged: false,
  isDeleted: false,
  ...overrides,
})

const mockAuth = (forum = true, solution = true, deleteForum = false, deleteSolution = false) => {
  vi.mocked(useAuthStore).mockReturnValue({
    hasPermission: vi.fn((action: string, resource: string) => {
      if (action === 'DELETE' && resource === 'FORUM_COMMENT') return deleteForum
      if (action === 'DELETE' && resource === 'SOLUTION_COMMENT') return deleteSolution
      if (resource === 'FORUM_COMMENT') return forum
      if (resource === 'SOLUTION_COMMENT') return solution
      return false
    }),
  } as unknown as ReturnType<typeof useAuthStore>)
}

describe('useCommentModeration', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockAuth()
  })

  const spyOnStore = (
    method: 'deleteComment' | 'flagComment' | 'unflagComment' | 'bulkAction',
  ): ReturnType<typeof vi.spyOn> => {
    const store = useCommentsStore()
    return vi.spyOn(store, method)
  }

  describe('permission gating (action routing)', () => {
    it('canModerateForum reflects the FORUM_COMMENT permission', () => {
      mockAuth(true, false)
      const { canModerateForum } = useCommentModeration()
      expect(canModerateForum.value).toBe(true)
    })

    it('canModerateSolution reflects the SOLUTION_COMMENT permission', () => {
      mockAuth(false, true)
      const { canModerateSolution } = useCommentModeration()
      expect(canModerateSolution.value).toBe(true)
    })

    it('canModerate returns true for forum when forum permission is granted', () => {
      mockAuth(true, false)
      const { canModerate } = useCommentModeration()
      expect(canModerate(makeComment({ type: 'forum' }))).toBe(true)
      expect(canModerate(makeComment({ type: 'solution' }))).toBe(false)
    })

    it('canModerate returns true for solution when solution permission is granted', () => {
      mockAuth(false, true)
      const { canModerate } = useCommentModeration()
      expect(canModerate(makeComment({ type: 'solution' }))).toBe(true)
      expect(canModerate(makeComment({ type: 'forum' }))).toBe(false)
    })

    it('canModerate returns false when neither permission is granted', () => {
      mockAuth(false, false)
      const { canModerate } = useCommentModeration()
      expect(canModerate(makeComment({ type: 'forum' }))).toBe(false)
      expect(canModerate(makeComment({ type: 'solution' }))).toBe(false)
    })

    it('canDeleteForum reflects the DELETE:FORUM_COMMENT permission', () => {
      mockAuth(true, true, true, false)
      const { canDeleteForum } = useCommentModeration()
      expect(canDeleteForum.value).toBe(true)
    })

    it('canDeleteSolution reflects the DELETE:SOLUTION_COMMENT permission', () => {
      mockAuth(true, true, false, true)
      const { canDeleteSolution } = useCommentModeration()
      expect(canDeleteSolution.value).toBe(true)
    })

    it('canDelete picks the forum or solution variant based on comment type', () => {
      mockAuth(true, true, true, false)
      const { canDelete } = useCommentModeration()
      expect(canDelete(makeComment({ type: 'forum' }))).toBe(true)
      expect(canDelete(makeComment({ type: 'solution' }))).toBe(false)
    })

    it('canDelete returns false when neither delete permission is granted', () => {
      mockAuth(true, true, false, false)
      const { canDelete } = useCommentModeration()
      expect(canDelete(makeComment({ type: 'forum' }))).toBe(false)
      expect(canDelete(makeComment({ type: 'solution' }))).toBe(false)
    })
  })

  describe('selection and dialog state (action routing)', () => {
    it('confirmDelete populates selection and opens the delete dialog', () => {
      const { confirmDelete, selectedCommentId, selectedCommentType, selectedCommentContent, deleteDialogOpen } =
        useCommentModeration()

      const c = makeComment({ id: 'c-42', type: 'solution', content: 'payload' })
      confirmDelete(c)

      expect(selectedCommentId.value).toBe('c-42')
      expect(selectedCommentType.value).toBe('solution')
      expect(selectedCommentContent.value).toBe('payload')
      expect(deleteDialogOpen.value).toBe(true)
    })

    it('openFlagDialog populates selection and opens the flag dialog', () => {
      const { openFlagDialog, selectedCommentId, selectedCommentType, selectedCommentContent, flagDialogOpen } =
        useCommentModeration()

      const c = makeComment({ id: 'c-7', type: 'forum', content: 'spammy' })
      openFlagDialog(c)

      expect(selectedCommentId.value).toBe('c-7')
      expect(selectedCommentType.value).toBe('forum')
      expect(selectedCommentContent.value).toBe('spammy')
      expect(flagDialogOpen.value).toBe(true)
    })

    it('closeDialogs resets selection and closes both dialogs', () => {
      const {
        openFlagDialog,
        closeDialogs,
        deleteDialogOpen,
        flagDialogOpen,
        bulkDeleteDialogOpen,
        selectedCommentId,
      } = useCommentModeration()

      openFlagDialog(makeComment({ id: 'c-9', type: 'forum' }))
      bulkDeleteDialogOpen.value = true
      deleteDialogOpen.value = true

      closeDialogs()

      expect(deleteDialogOpen.value).toBe(false)
      expect(flagDialogOpen.value).toBe(false)
      expect(bulkDeleteDialogOpen.value).toBe(false)
      expect(selectedCommentId.value).toBeNull()
    })

    it('promptBulkDelete opens the bulk delete dialog', () => {
      const { promptBulkDelete, bulkDeleteDialogOpen } = useCommentModeration()
      expect(bulkDeleteDialogOpen.value).toBe(false)
      promptBulkDelete()
      expect(bulkDeleteDialogOpen.value).toBe(true)
    })

    it('dismissBulkDelete closes the bulk delete dialog', () => {
      const { promptBulkDelete, dismissBulkDelete, bulkDeleteDialogOpen } = useCommentModeration()
      promptBulkDelete()
      dismissBulkDelete()
      expect(bulkDeleteDialogOpen.value).toBe(false)
    })
  })

  describe('single-item action handlers (action routing)', () => {
    it('handleDeleteComment calls the store with the selected type', async () => {
      const deleteSpy = spyOnStore('deleteComment').mockResolvedValue(undefined)

      const { confirmDelete, handleDeleteComment } = useCommentModeration()
      confirmDelete(makeComment({ id: 'c-d', type: 'forum' }))

      await handleDeleteComment('c-d')

      expect(deleteSpy).toHaveBeenCalledWith('c-d', 'forum')
    })

    it('handleFlagComment forwards the reason to the store', async () => {
      const flagSpy = spyOnStore('flagComment').mockResolvedValue(makeComment())

      const { confirmDelete, handleFlagComment } = useCommentModeration()
      confirmDelete(makeComment({ id: 'c-f', type: 'solution' }))

      await handleFlagComment('c-f', 'spam')

      expect(flagSpy).toHaveBeenCalledWith('c-f', 'solution', 'spam')
    })

    it('handleFlagComment defaults to an empty reason when none provided', async () => {
      const flagSpy = spyOnStore('flagComment').mockResolvedValue(makeComment())

      const { confirmDelete, handleFlagComment } = useCommentModeration()
      confirmDelete(makeComment({ id: 'c-f2', type: 'forum' }))

      await handleFlagComment('c-f2')

      expect(flagSpy).toHaveBeenCalledWith('c-f2', 'forum', '')
    })

    it('handleDeleteComment rethrows so EntityActionDialog can surface the failure', async () => {
      spyOnStore('deleteComment').mockRejectedValue(new Error('boom'))

      const { confirmDelete, handleDeleteComment } = useCommentModeration()
      confirmDelete(makeComment({ id: 'c-x', type: 'forum' }))

      await expect(handleDeleteComment('c-x')).rejects.toThrow('boom')
    })
  })

  describe('unflagComment (mutation result handling + refresh policy)', () => {
    it('calls the store and refreshes on success', async () => {
      const unflagSpy = spyOnStore('unflagComment').mockResolvedValue(makeComment())

      const refresh = vi.fn().mockResolvedValue(undefined)
      const { unflagComment } = useCommentModeration({ refresh })

      await unflagComment(makeComment({ id: 'c-u', type: 'forum' }))

      expect(unflagSpy).toHaveBeenCalledWith('c-u', 'forum')
      expect(refresh).toHaveBeenCalledTimes(1)
      expect(toastSuccess).toHaveBeenCalledWith('comments.toast.unflaggedSuccessfully')
      expect(toastError).not.toHaveBeenCalled()
    })

    it('toasts the failure and skips refresh when the store rejects', async () => {
      spyOnStore('unflagComment').mockRejectedValue(new Error('nope'))

      const refresh = vi.fn().mockResolvedValue(undefined)
      const { unflagComment } = useCommentModeration({ refresh })

      await expect(
        unflagComment(makeComment({ id: 'c-u', type: 'solution' })),
      ).resolves.toBeUndefined()

      expect(toastError).toHaveBeenCalledWith('comments.toast.failedToUnflag')
      expect(refresh).not.toHaveBeenCalled()
    })
  })

  describe('bulk grouping', () => {
    it('groupByType splits rows by CommentType', () => {
      const { groupByType } = useCommentModeration()
      const grouped = groupByType([
        makeComment({ id: '1', type: 'forum' }),
        makeComment({ id: '2', type: 'solution' }),
        makeComment({ id: '3', type: 'forum' }),
        makeComment({ id: '4', type: 'solution' }),
        makeComment({ id: '5', type: 'solution' }),
      ])

      expect(grouped.forum).toEqual(['1', '3'])
      expect(grouped.solution).toEqual(['2', '4', '5'])
    })

    it('groupByType returns an empty record for no rows', () => {
      const { groupByType } = useCommentModeration()
      expect(groupByType([])).toEqual({})
    })

    it('groupByType only returns the keys that had at least one row', () => {
      const { groupByType } = useCommentModeration()
      const grouped = groupByType([makeComment({ id: '1', type: 'forum' })])
      expect(grouped.forum).toEqual(['1'])
      expect(grouped.solution).toBeUndefined()
    })
  })

  describe('bulk actions (refresh policy + bulk grouping)', () => {
    it('bulkUnflag groups by type, dispatches one store call per group, then refreshes', async () => {
      const bulkSpy = spyOnStore('bulkAction').mockResolvedValue(undefined)

      const refresh = vi.fn().mockResolvedValue(undefined)
      const { bulkUnflag } = useCommentModeration({ refresh })

      const ok = await bulkUnflag([
        makeComment({ id: 'a', type: 'forum' }),
        makeComment({ id: 'b', type: 'forum' }),
        makeComment({ id: 'c', type: 'solution' }),
      ])

      expect(ok).toBe(true)
      expect(bulkSpy).toHaveBeenCalledTimes(2)
      expect(bulkSpy).toHaveBeenCalledWith({
        ids: ['a', 'b'],
        type: 'forum',
        action: 'unflag',
      })
      expect(bulkSpy).toHaveBeenCalledWith({
        ids: ['c'],
        type: 'solution',
        action: 'unflag',
      })
      expect(refresh).toHaveBeenCalledTimes(1)
      expect(toastSuccess).toHaveBeenCalledWith('comments.toast.bulkUnflaggedSuccessfully')
    })

    it('bulkDelete dispatches delete actions grouped by type then refreshes', async () => {
      const bulkSpy = spyOnStore('bulkAction').mockResolvedValue(undefined)

      const refresh = vi.fn().mockResolvedValue(undefined)
      const { bulkDelete } = useCommentModeration({ refresh })

      const ok = await bulkDelete([
        makeComment({ id: 'x', type: 'solution' }),
        makeComment({ id: 'y', type: 'forum' }),
      ])

      expect(ok).toBe(true)
      expect(bulkSpy).toHaveBeenCalledTimes(2)
      expect(bulkSpy).toHaveBeenCalledWith({
        ids: ['x'],
        type: 'solution',
        action: 'delete',
      })
      expect(bulkSpy).toHaveBeenCalledWith({
        ids: ['y'],
        type: 'forum',
        action: 'delete',
      })
      expect(refresh).toHaveBeenCalledTimes(1)
      expect(toastSuccess).toHaveBeenCalledWith('comments.toast.bulkDeletedSuccessfully')
    })

    it('bulkUnflag is a no-op (returns true) when called with no rows', async () => {
      const bulkSpy = spyOnStore('bulkAction').mockResolvedValue(undefined)

      const refresh = vi.fn().mockResolvedValue(undefined)
      const { bulkUnflag } = useCommentModeration({ refresh })

      const ok = await bulkUnflag([])

      expect(ok).toBe(true)
      expect(bulkSpy).not.toHaveBeenCalled()
      expect(refresh).not.toHaveBeenCalled()
    })
  })

  describe('failure state (mutation result handling)', () => {
    it('bulkUnflag returns false, toasts failure, and skips refresh on rejection', async () => {
      spyOnStore('bulkAction').mockRejectedValue(new Error('bulk fail'))

      const refresh = vi.fn().mockResolvedValue(undefined)
      const { bulkUnflag } = useCommentModeration({ refresh })

      const ok = await bulkUnflag([makeComment({ id: 'a', type: 'forum' })])

      expect(ok).toBe(false)
      expect(toastError).toHaveBeenCalledWith('comments.toast.failedToBulkUnflag')
      expect(refresh).not.toHaveBeenCalled()
    })

    it('bulkDelete returns false, toasts failure, and skips refresh on rejection', async () => {
      spyOnStore('bulkAction').mockRejectedValue(new Error('bulk fail'))

      const refresh = vi.fn().mockResolvedValue(undefined)
      const { bulkDelete } = useCommentModeration({ refresh })

      const ok = await bulkDelete([makeComment({ id: 'a', type: 'solution' })])

      expect(ok).toBe(false)
      expect(toastError).toHaveBeenCalledWith('comments.toast.failedToBulkDelete')
      expect(refresh).not.toHaveBeenCalled()
    })
  })
})
