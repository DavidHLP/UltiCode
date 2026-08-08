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

  it('canEditBasicInfo returns true when user has UPDATE:PROBLEM_LIST permission', () => {
    const mockHasPermission = vi.fn().mockReturnValue(true)
    vi.mocked(useAuthStore).mockReturnValue({
      hasPermission: mockHasPermission,
    } as unknown as ReturnType<typeof useAuthStore>)

    const { canEditBasicInfo } = useProblemListPermissions()
    expect(canEditBasicInfo.value).toBe(true)
    expect(mockHasPermission).toHaveBeenCalledWith('UPDATE', 'PROBLEM_LIST')
  })

  it('canEditBasicInfo returns false when user lacks UPDATE:PROBLEM_LIST permission', () => {
    const mockHasPermission = vi.fn().mockReturnValue(false)
    vi.mocked(useAuthStore).mockReturnValue({
      hasPermission: mockHasPermission,
    } as unknown as ReturnType<typeof useAuthStore>)

    const { canEditBasicInfo } = useProblemListPermissions()
    expect(canEditBasicInfo.value).toBe(false)
  })

  it('canEditVisibility returns true when user has UPDATE:PROBLEM_LIST permission', () => {
    const mockHasPermission = vi.fn().mockReturnValue(true)
    vi.mocked(useAuthStore).mockReturnValue({
      hasPermission: mockHasPermission,
    } as unknown as ReturnType<typeof useAuthStore>)

    const { canEditVisibility } = useProblemListPermissions()
    expect(canEditVisibility.value).toBe(true)
    expect(mockHasPermission).toHaveBeenCalledWith('UPDATE', 'PROBLEM_LIST')
  })

  it('canEditVisibility returns false when user lacks UPDATE:PROBLEM_LIST permission', () => {
    const mockHasPermission = vi.fn().mockReturnValue(false)
    vi.mocked(useAuthStore).mockReturnValue({
      hasPermission: mockHasPermission,
    } as unknown as ReturnType<typeof useAuthStore>)

    const { canEditVisibility } = useProblemListPermissions()
    expect(canEditVisibility.value).toBe(false)
  })

  it('canEditBanner returns true when user has UPDATE:PROBLEM_LIST permission', () => {
    const mockHasPermission = vi.fn().mockReturnValue(true)
    vi.mocked(useAuthStore).mockReturnValue({
      hasPermission: mockHasPermission,
    } as unknown as ReturnType<typeof useAuthStore>)

    const { canEditBanner } = useProblemListPermissions()
    expect(canEditBanner.value).toBe(true)
    expect(mockHasPermission).toHaveBeenCalledWith('UPDATE', 'PROBLEM_LIST')
  })

  it('canEditBanner returns false when user lacks UPDATE:PROBLEM_LIST permission', () => {
    const mockHasPermission = vi.fn().mockReturnValue(false)
    vi.mocked(useAuthStore).mockReturnValue({
      hasPermission: mockHasPermission,
    } as unknown as ReturnType<typeof useAuthStore>)

    const { canEditBanner } = useProblemListPermissions()
    expect(canEditBanner.value).toBe(false)
  })

  it('canManageProblems returns true when user has MANAGE_PROBLEMS:PROBLEM_LIST permission', () => {
    const mockHasPermission = vi.fn().mockReturnValue(true)
    vi.mocked(useAuthStore).mockReturnValue({
      hasPermission: mockHasPermission,
    } as unknown as ReturnType<typeof useAuthStore>)

    const { canManageProblems } = useProblemListPermissions()
    expect(canManageProblems.value).toBe(true)
    expect(mockHasPermission).toHaveBeenCalledWith('MANAGE_PROBLEMS', 'PROBLEM_LIST')
  })

  it('canManageProblems returns false when user lacks MANAGE_PROBLEMS:PROBLEM_LIST permission', () => {
    const mockHasPermission = vi.fn().mockReturnValue(false)
    vi.mocked(useAuthStore).mockReturnValue({
      hasPermission: mockHasPermission,
    } as unknown as ReturnType<typeof useAuthStore>)

    const { canManageProblems } = useProblemListPermissions()
    expect(canManageProblems.value).toBe(false)
  })
})
