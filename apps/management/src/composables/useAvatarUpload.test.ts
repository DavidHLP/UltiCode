import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { usersApi } from '@/api/admin/users'
import { useAvatarUpload } from '@/composables/useAvatarUpload'

vi.mock('@/api/admin/users', () => ({
  usersApi: {
    uploadAvatar: vi.fn(),
  },
}))

function makeFile() {
  return new File(['avatar'], 'avatar.png', { type: 'image/png' })
}

describe('useAvatarUpload', () => {
  it('uploads the selected user avatar and tracks progress', async () => {
    vi.mocked(usersApi.uploadAvatar).mockImplementation(
      async (_id, _file, onProgress) => {
        onProgress?.(100)
        return '/api/users/avatars/user-7/avatar.webp'
      },
    )
    const upload = useAvatarUpload(ref('user-7'))

    await expect(upload.upload(makeFile())).resolves.toBe(
      '/api/users/avatars/user-7/avatar.webp',
    )
    expect(usersApi.uploadAvatar).toHaveBeenCalledWith(
      'user-7',
      expect.any(File),
      expect.any(Function),
    )
    expect(upload.progress.value).toBe(100)
    expect(upload.uploading.value).toBe(false)
  })

  it('propagates upload errors and clears the loading state', async () => {
    const error = new Error('file is too large')
    vi.mocked(usersApi.uploadAvatar).mockRejectedValue(error)
    const upload = useAvatarUpload('user-7')

    await expect(upload.upload(makeFile())).rejects.toBe(error)
    expect(upload.uploading.value).toBe(false)
  })
})
