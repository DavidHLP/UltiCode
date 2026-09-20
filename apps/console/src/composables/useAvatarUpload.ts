import { ref } from 'vue'
import { uploadMyAvatar } from '@/api/user'

type AvatarUploader = (file: File, onProgress?: (progress: number) => void) => Promise<string>

export function useAvatarUpload(uploader: AvatarUploader = uploadMyAvatar) {
  const uploading = ref(false)
  const progress = ref(0)

  async function upload(file: File): Promise<string> {
    uploading.value = true
    progress.value = 0
    try {
      return await uploader(file, (value) => {
        progress.value = value
      })
    } finally {
      uploading.value = false
    }
  }

  return { uploading, progress, upload }
}
