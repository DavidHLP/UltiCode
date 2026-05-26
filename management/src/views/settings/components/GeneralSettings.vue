<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { IconSettings, IconUsers, IconServer } from '@tabler/icons-vue'
import type { AllSettings } from '@/api/admin/settings'

const { t } = useI18n()

const props = defineProps<{
  settings: AllSettings
}>()

const emit = defineEmits<{
  'update:settings': [value: AllSettings]
}>()

function updateField<K extends keyof AllSettings>(key: K, value: AllSettings[K]) {
  emit('update:settings', { ...props.settings, [key]: value })
}
</script>

<template>
  <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
    <Card>
      <CardHeader>
        <div class="flex items-center gap-2">
          <IconSettings class="h-5 w-5 text-muted-foreground" />
          <CardTitle>{{ t('settings.general.title') }}</CardTitle>
        </div>
        <CardDescription>{{ t('settings.general.description') }}</CardDescription>
      </CardHeader>
      <CardContent class="space-y-4">
        <div class="space-y-2">
          <Label>{{ t('settings.siteName') }}</Label>
          <Input
            :model-value="settings.site_name"
            @update:model-value="updateField('site_name', String($event))"
            placeholder="UltiCode"
          />
        </div>
        <div class="space-y-2">
          <Label>{{ t('settings.siteDescription') }}</Label>
          <Input
            :model-value="settings.site_description"
            @update:model-value="updateField('site_description', String($event))"
            placeholder="Competitive Programming Platform"
          />
        </div>
      </CardContent>
    </Card>

    <Card>
      <CardHeader>
        <div class="flex items-center gap-2">
          <IconUsers class="h-5 w-5 text-muted-foreground" />
          <CardTitle>{{ t('settings.userRegistration.title') }}</CardTitle>
        </div>
        <CardDescription>{{ t('settings.userRegistration.description') }}</CardDescription>
      </CardHeader>
      <CardContent class="space-y-6">
        <div class="flex items-center justify-between space-x-2">
          <Label class="flex flex-col space-y-1">
            <span>{{ t('settings.userRegistration.enableRegistrations') }}</span>
            <span class="font-normal text-xs text-muted-foreground">{{
              t('settings.userRegistration.enableRegistrationsDescription')
            }}</span>
          </Label>
          <Switch
            :checked="settings.enable_registrations"
            @update:checked="updateField('enable_registrations', $event)"
          />
        </div>
        <div class="flex items-center justify-between space-x-2">
          <Label class="flex flex-col space-y-1">
            <span>{{ t('settings.userRegistration.requireEmailVerification') }}</span>
            <span class="font-normal text-xs text-muted-foreground">{{
              t('settings.userRegistration.requireEmailVerificationDescription')
            }}</span>
          </Label>
          <Switch
            :checked="settings.require_email_verification"
            @update:checked="updateField('require_email_verification', $event)"
          />
        </div>
      </CardContent>
    </Card>

    <Card
      class="md:col-span-2 border-orange-200 dark:border-orange-900 bg-orange-50/50 dark:bg-orange-950/20"
    >
      <CardHeader>
        <div class="flex items-center gap-2">
          <IconServer class="h-5 w-5 text-orange-600 dark:text-orange-400" />
          <CardTitle class="text-orange-700 dark:text-orange-300">{{
            t('settings.systemStatus.title')
          }}</CardTitle>
        </div>
        <CardDescription>{{ t('settings.systemStatus.description') }}</CardDescription>
      </CardHeader>
      <CardContent class="space-y-6">
        <div class="flex items-center justify-between space-x-2">
          <Label class="flex flex-col space-y-1">
            <span>{{ t('settings.systemStatus.maintenanceMode') }}</span>
            <span class="font-normal text-xs text-muted-foreground">{{
              t('settings.systemStatus.maintenanceModeDescription')
            }}</span>
          </Label>
          <Switch
            :checked="settings.maintenance_mode"
            @update:checked="updateField('maintenance_mode', $event)"
            class="data-[state=checked]:bg-orange-600"
          />
        </div>

        <div
          v-if="settings.maintenance_mode"
          class="space-y-2 animate-in fade-in slide-in-from-top-2"
        >
          <Label>{{ t('settings.systemStatus.maintenanceMessage') }}</Label>
          <Textarea
            :model-value="settings.maintenance_message"
            @update:model-value="updateField('maintenance_message', String($event))"
            placeholder="We are currently performing maintenance..."
          />
        </div>
      </CardContent>
    </Card>
  </div>
</template>
