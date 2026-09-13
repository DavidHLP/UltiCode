import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { useProblemListPermissions } from './useProblemListPermissions'

vi.mock('@/stores/auth')

describe('useProblemListPermissions', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('exposes one update predicate for all problem-list editing surfaces', () => {
    const mockHasPermission = vi.fn((action: string, resource: string) =>
      action === 'UPDATE' && resource === 'PROBLEM_LIST',
    )
    vi.mocked(useAuthStore).mockReturnValue({
      hasPermission: mockHasPermission,
    } as unknown as ReturnType<typeof useAuthStore>)

    const { can } = useProblemListPermissions()

    expect(can.problemList.update.value).toBe(true)
    expect(can.problemList.manageProblems.value).toBe(false)
    expect(mockHasPermission).toHaveBeenCalledWith('UPDATE', 'PROBLEM_LIST')
  })

  it('keeps problem-list management fail-closed', () => {
    const mockHasPermission = vi.fn().mockReturnValue(false)
    vi.mocked(useAuthStore).mockReturnValue({
      hasPermission: mockHasPermission,
    } as unknown as ReturnType<typeof useAuthStore>)

    const { can } = useProblemListPermissions()

    expect(can.problemList.read.value).toBe(false)
    expect(can.problemList.create.value).toBe(false)
    expect(can.problemList.update.value).toBe(false)
    expect(can.problemList.delete.value).toBe(false)
    expect(can.problemList.manageProblems.value).toBe(false)
  })
})
