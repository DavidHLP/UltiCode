<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { IconMail, IconEye, IconEyeOff } from '@tabler/icons-vue'
import type { EmailSettings } from '@/api/admin/settings'

const { t } = useI18n()

defineProps<{
  settings: EmailSettings
}>()

const emit = defineEmits<{
  'update:settings': [patch: Partial<EmailSettings>]
}>()

// Eye-toggle for the password input. The backend's "***" preserve-mask
// is enforced by the system-settings workspace and the API client; this
// component only renders whatever smtp_password the slice currently holds.
const showSmtpPassword = ref(false)

function updateField<K extends keyof EmailSettings>(key: K, value: EmailSettings[K]) {
  emit('update:settings', { [key]: value })
}
</script>

<template>
  <Card>
    <CardHeader>
      <div class="flex items-center gap-2">
        <IconMail class="h-5 w-5 text-muted-foreground" />
        <CardTitle>{{ t('settings.email.title') }}</CardTitle>
      </div>
      <CardDescription>{{ t('settings.email.description') }}</CardDescription>
    </CardHeader>
    <CardContent class="space-y-4">
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div class="space-y-2">
          <Label>{{ t('settings.email.smtpHost') }}</Label>
          <Input
            :model-value="settings.smtp_host"
            @update:model-value="updateField('smtp_host', String($event))"
            placeholder="smtp.example.com"
          />
        </div>
        <div class="space-y-2">
          <Label>{{ t('settings.email.smtpPort') }}</Label>
          <Input
            :model-value="settings.smtp_port"
            @update:model-value="updateField('smtp_port', String($event))"
            placeholder="587"
          />
        </div>
        <div class="space-y-2">
          <Label>{{ t('settings.email.smtpUser') }}</Label>
          <Input
            :model-value="settings.smtp_user"
            @update:model-value="updateField('smtp_user', String($event))"
            placeholder="user@example.com"
          />
        </div>
        <div class="space-y-2">
          <Label>{{ t('settings.email.smtpPassword') }}</Label>
          <div class="relative">
            <Input
              :model-value="settings.smtp_password"
              @update:model-value="updateField('smtp_password', String($event))"
              :type="showSmtpPassword ? 'text' : 'password'"
              placeholder="•••••••••"
              class="pr-10"
            />
            <button
              type="button"
              class="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
              @click="showSmtpPassword = !showSmtpPassword"
            >
              <IconEye v-if="!showSmtpPassword" class="h-4 w-4" />
              <IconEyeOff v-else class="h-4 w-4" />
            </button>
          </div>
        </div>
        <div class="space-y-2">
          <Label>{{ t('settings.email.smtpFrom') }}</Label>
          <Input
            :model-value="settings.smtp_from"
            @update:model-value="updateField('smtp_from', String($event))"
            placeholder="noreply@ulticode.com"
          />
        </div>
        <div class="space-y-2">
          <Label>{{ t('settings.email.smtpFromName') }}</Label>
          <Input
            :model-value="settings.smtp_from_name"
            @update:model-value="updateField('smtp_from_name', String($event))"
            placeholder="UltiCode"
          />
        </div>
      </div>
      <div class="flex items-center justify-between space-x-2 pt-4">
        <Label class="flex flex-col space-y-1">
          <span>{{ t('settings.email.smtpSecure') }}</span>
          <span class="font-normal text-xs text-muted-foreground">{{
            t('settings.email.smtpSecureDescription')
          }}</span>
        </Label>
        <Switch
          :checked="settings.smtp_secure"
          @update:checked="updateField('smtp_secure', $event)"
        />
      </div>
    </CardContent>
  </Card>
</template>
