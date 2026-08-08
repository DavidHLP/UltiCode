import { beforeEach, describe, expect, it, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { commentsApi } from '@/api/admin/comments'
import { useAuthStore } from '@/stores/auth'
import { useCommentsStore } from '../comments'
import type { Comment, CommentQueryParams } from '@/api/admin/comments'

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

const makeRow = (id: string, type: 'forum' | 'solution'): Pick<Comment, 'id' | 'type'> => ({
  id,
  type,
})

const emptyPage = { items: [], total: 0 }

describe('useCommentsStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockAuth()
    vi.mocked(commentsApi.getComments).mockResolvedValue(emptyPage)
  })

  describe('canModerate policy (routes by type + auth permission)', () => {
    it('returns the forum moderate permission for a forum comment', () => {
      mockAuth(true, false)
      const store = useCommentsStore()
      expect(store.canModerate(makeRow('1', 'forum'))).toBe(true)
      expect(store.canModerate(makeRow('2', 'solution'))).toBe(false)
    })

    it('returns the solution moderate permission for a solution comment', () => {
      mockAuth(false, true)
      const store = useCommentsStore()
      expect(store.canModerate(makeRow('1', 'solution'))).toBe(true)
      expect(store.canModerate(makeRow('2', 'forum'))).toBe(false)
    })

    it('returns false when neither moderate permission is granted', () => {
      mockAuth(false, false)
      const store = useCommentsStore()
      expect(store.canModerate(makeRow('1', 'forum'))).toBe(false)
      expect(store.canModerate(makeRow('2', 'solution'))).toBe(false)
    })
  })

  describe('canDelete policy (routes by type + auth permission)', () => {
    it('routes the delete permission by comment type', () => {
      mockAuth(true, true, true, false)
      const store = useCommentsStore()
      expect(store.canDelete(makeRow('1', 'forum'))).toBe(true)
      expect(store.canDelete(makeRow('2', 'solution'))).toBe(false)
    })

    it('returns false when neither delete permission is granted', () => {
      mockAuth(true, true, false, false)
      const store = useCommentsStore()
      expect(store.canDelete(makeRow('1', 'forum'))).toBe(false)
      expect(store.canDelete(makeRow('2', 'solution'))).toBe(false)
    })
  })

  describe('groupByType', () => {
    it('groups ids by CommentType, preserving insertion order', () => {
      const store = useCommentsStore()
      const grouped = store.groupByType([
        makeRow('1', 'forum'),
        makeRow('2', 'solution'),
        makeRow('3', 'forum'),
      ])
      expect(grouped.forum).toEqual(['1', '3'])
      expect(grouped.solution).toEqual(['2'])
    })

    it('returns an empty record for no rows', () => {
      const store = useCommentsStore()
      expect(store.groupByType([])).toEqual({})
    })
  })

  describe('bulkModerate workflow', () => {
    it('groups mixed-type rows and calls the bulk API once per type with the right payload', async () => {
      const bulkSpy = vi.mocked(commentsApi.bulkAction).mockResolvedValue(undefined)
      // fetchComments is invoked by runBulkAction after each bulk call.
      const fetchSpy = vi.mocked(commentsApi.getComments).mockResolvedValue(emptyPage)

      const store = useCommentsStore()
      await store.fetchComments({ page: 1 } as CommentQueryParams)
      fetchSpy.mockClear()

      await store.bulkModerate(
        [makeRow('a', 'forum'), makeRow('b', 'forum'), makeRow('c', 'solution')],
        'delete',
      )

      expect(bulkSpy).toHaveBeenCalledTimes(2)
      expect(bulkSpy).toHaveBeenCalledWith({
        ids: ['a', 'b'],
        type: 'forum',
        action: 'delete',
      })
      expect(bulkSpy).toHaveBeenCalledWith({
        ids: ['c'],
        type: 'solution',
        action: 'delete',
      })
      // one refetch per non-empty group
      expect(fetchSpy).toHaveBeenCalledTimes(2)
    })

    it('passes the action through (unflag)', async () => {
      const bulkSpy = vi.mocked(commentsApi.bulkAction).mockResolvedValue(undefined)

      const store = useCommentsStore()
      await store.bulkModerate([makeRow('x', 'solution')], 'unflag')

      expect(bulkSpy).toHaveBeenCalledWith({
        ids: ['x'],
        type: 'solution',
        action: 'unflag',
      })
    })

    it('is a no-op (no api call, no refetch) on empty rows', async () => {
      const bulkSpy = vi.mocked(commentsApi.bulkAction).mockResolvedValue(undefined)
      const fetchSpy = vi.mocked(commentsApi.getComments).mockResolvedValue(emptyPage)

      const store = useCommentsStore()
      await store.bulkModerate([], 'delete')

      expect(bulkSpy).not.toHaveBeenCalled()
      expect(fetchSpy).not.toHaveBeenCalled()
      expect(store.loading).toBe(false)
    })

    it('rethrows on failure (so callers can toast) and surfaces the error on the store', async () => {
      vi.mocked(commentsApi.bulkAction).mockRejectedValue(new Error('bulk boom'))

      const store = useCommentsStore()
      await expect(store.bulkModerate([makeRow('a', 'forum')], 'delete')).rejects.toThrow(
        'bulk boom',
      )
      expect(store.error).not.toBeNull()
      expect(store.loading).toBe(false)
    })

    it('does not expose bulkAction on the public store surface', () => {
      const store = useCommentsStore() as unknown as Record<string, unknown>
      expect(store.bulkAction).toBeUndefined()
    })
  })
})
