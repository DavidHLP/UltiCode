<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { IconClock } from '@tabler/icons-vue'
import type { RateLimitSettings } from '@/api/admin/settings'

const { t } = useI18n()

defineProps<{
  settings: RateLimitSettings
}>()

const emit = defineEmits<{
  'update:settings': [patch: Partial<RateLimitSettings>]
}>()

function updateField<K extends keyof RateLimitSettings>(key: K, value: RateLimitSettings[K]) {
  emit('update:settings', { [key]: value })
}
</script>

<template>
  <Card>
    <CardHeader>
      <div class="flex items-center gap-2">
        <IconClock class="h-5 w-5 text-muted-foreground" />
        <CardTitle>{{ t('settings.rateLimits.title') }}</CardTitle>
      </div>
      <CardDescription>{{ t('settings.rateLimits.description') }}</CardDescription>
    </CardHeader>
    <CardContent class="space-y-4">
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div class="space-y-2">
          <Label>{{ t('settings.rateLimits.api') }}</Label>
          <Input
            :model-value="settings.rate_limit_api"
            @update:model-value="updateField('rate_limit_api', String($event))"
            type="number"
            min="1"
          />
          <p class="text-xs text-muted-foreground">{{ t('settings.rateLimits.apiDescription') }}</p>
        </div>
        <div class="space-y-2">
          <Label>{{ t('settings.rateLimits.submission') }}</Label>
          <Input
            :model-value="settings.rate_limit_submission"
            @update:model-value="updateField('rate_limit_submission', String($event))"
            type="number"
            min="1"
          />
          <p class="text-xs text-muted-foreground">
            {{ t('settings.rateLimits.submissionDescription') }}
          </p>
        </div>
        <div class="space-y-2">
          <Label>{{ t('settings.rateLimits.auth') }}</Label>
          <Input
            :model-value="settings.rate_limit_auth"
            @update:model-value="updateField('rate_limit_auth', String($event))"
            type="number"
            min="1"
          />
          <p class="text-xs text-muted-foreground">
            {{ t('settings.rateLimits.authDescription') }}
          </p>
        </div>
        <div class="space-y-2">
          <Label>{{ t('settings.rateLimits.upload') }}</Label>
          <Input
            :model-value="settings.rate_limit_upload"
            @update:model-value="updateField('rate_limit_upload', String($event))"
            type="number"
            min="1"
          />
          <p class="text-xs text-muted-foreground">
            {{ t('settings.rateLimits.uploadDescription') }}
          </p>
        </div>
      </div>
    </CardContent>
  </Card>
</template>
