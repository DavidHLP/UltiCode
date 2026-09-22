import { describe, expect, it, vi } from 'vitest'
import { isAvatarUploadSessionCurrent, useAvatarUpload } from '@/composables/useAvatarUpload'

function makeFile() {
  return new File(['avatar'], 'avatar.png', { type: 'image/png' })
}


describe('isAvatarUploadSessionCurrent', () => {
  it('rejects a response after logout or account switching', () => {
    expect(isAvatarUploadSessionCurrent('user-a', 'user-a', 'user-a')).toBe(true)
    expect(isAvatarUploadSessionCurrent('user-a', 'user-a', null)).toBe(false)
    expect(isAvatarUploadSessionCurrent('user-a', 'user-b', 'user-b')).toBe(false)
  })
})
describe('useAvatarUpload', () => {
  it('uploads the file, tracks progress, and returns the display URL', async () => {
    const uploader = vi.fn(async (_file: File, onProgress?: (value: number) => void) => {
      onProgress?.(50)
      onProgress?.(100)
      return '/api/users/avatars/user-1/avatar.webp'
    })
    const upload = useAvatarUpload(uploader)

    await expect(upload.upload(makeFile())).resolves.toBe(
      '/api/users/avatars/user-1/avatar.webp',
    )
    expect(uploader).toHaveBeenCalledWith(expect.any(File), expect.any(Function))
    expect(upload.progress.value).toBe(100)
    expect(upload.uploading.value).toBe(false)
  })

  it('resets uploading state and propagates server errors', async () => {
    const error = new Error('unsupported image type')
    const uploader = vi.fn().mockRejectedValue(error)
    const upload = useAvatarUpload(uploader)

    await expect(upload.upload(makeFile())).rejects.toBe(error)
    expect(upload.uploading.value).toBe(false)
  })
})
