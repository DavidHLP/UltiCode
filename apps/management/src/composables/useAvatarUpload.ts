import { ref, unref, type MaybeRef } from 'vue'
import { usersApi } from '@/api/admin/users'

type AvatarUploader = (
  userId: string,
  file: File,
  onProgress?: (progress: number) => void,
) => Promise<string>

export function useAvatarUpload(
  userId: MaybeRef<string | null>,
  uploader: AvatarUploader = usersApi.uploadAvatar,
) {
  const uploading = ref(false)
  const progress = ref(0)

  async function upload(file: File): Promise<string> {
    const id = unref(userId)
    if (!id) throw new Error('User ID is required')

    uploading.value = true
    progress.value = 0
    try {
      return await uploader(id, file, (value) => {
        progress.value = value
      })
    } finally {
      uploading.value = false
    }
  }

  return { uploading, progress, upload }
}
