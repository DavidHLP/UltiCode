<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { IconUpload } from '@tabler/icons-vue'
import type { UploadSettings } from '@/api/admin/settings'
import {
  formatBytes,
  parseSizeToBytes,
} from '@/stores/admin/system-settings'

const { t } = useI18n()

const props = defineProps<{
  settings: UploadSettings
}>()

const emit = defineEmits<{
  'update:settings': [patch: Partial<UploadSettings>]
}>()

const uploadSizeReadable = computed({
  get: () => formatBytes(props.settings.upload_max_size),
  set: (val: string) => {
    emit('update:settings', { upload_max_size: parseSizeToBytes(val) })
  },
})

function updateField<K extends keyof UploadSettings>(key: K, value: UploadSettings[K]) {
  emit('update:settings', { [key]: value })
}
</script>

<template>
  <Card>
    <CardHeader>
      <div class="flex items-center gap-2">
        <IconUpload class="h-5 w-5 text-muted-foreground" />
        <CardTitle>{{ t('settings.uploads.title') }}</CardTitle>
      </div>
      <CardDescription>{{ t('settings.uploads.description') }}</CardDescription>
    </CardHeader>
    <CardContent class="space-y-4">
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div class="space-y-2">
          <Label>{{ t('settings.uploads.maxSize') }}</Label>
          <Input v-model="uploadSizeReadable" placeholder="10 MB" />
          <p class="text-xs text-muted-foreground">
            {{ t('settings.uploads.maxSizeDescription') }}
          </p>
        </div>
        <div class="space-y-2">
          <Label>{{ t('settings.uploads.maxFiles') }}</Label>
          <Input
            :model-value="settings.upload_max_files"
            @update:model-value="updateField('upload_max_files', String($event))"
            type="number"
            min="1"
          />
          <p class="text-xs text-muted-foreground">
            {{ t('settings.uploads.maxFilesDescription') }}
          </p>
        </div>
      </div>
      <div class="space-y-2">
        <Label>{{ t('settings.uploads.allowedTypes') }}</Label>
        <Input
          :model-value="settings.upload_allowed_types"
          @update:model-value="updateField('upload_allowed_types', String($event))"
          placeholder="jpg,jpeg,png,gif,pdf,zip"
        />
        <p class="text-xs text-muted-foreground">
          {{ t('settings.uploads.allowedTypesDescription') }}
        </p>
      </div>
    </CardContent>
  </Card>
</template>
