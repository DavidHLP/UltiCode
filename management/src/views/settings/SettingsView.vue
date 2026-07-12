<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog'
import { settingsApi, type AllSettings } from '@/api/admin/settings'
import {
  IconSettings,
  IconMail,
  IconClock,
  IconUpload,
  IconToggleLeft,
  IconDeviceFloppy,
  IconRefresh,
  IconReload,
} from '@tabler/icons-vue'

import GeneralSettings from './components/GeneralSettings.vue'
import EmailSettings from './components/EmailSettings.vue'
import RateLimitSettings from './components/RateLimitSettings.vue'
import UploadSettings from './components/UploadSettings.vue'
import FeatureToggleSettings from './components/FeatureToggleSettings.vue'

const { t } = useI18n()

const loading = ref(false)
const saving = ref(false)
const clearingCache = ref(false)
const activeTab = ref('general')
const isLoaded = ref(false)

const settings = ref<AllSettings>({
  maintenance_mode: false,
  maintenance_message: '',
  enable_registrations: true,
  site_name: '',
  site_description: '',
  require_email_verification: false,
  smtp_host: '',
  smtp_port: '587',
  smtp_user: '',
  smtp_password: '',
  smtp_from: '',
  smtp_from_name: '',
  smtp_secure: true,
  rate_limit_api: '100',
  rate_limit_submission: '10',
  rate_limit_auth: '5',
  rate_limit_upload: '20',
  upload_max_size: '10485760',
  upload_allowed_types: '',
  upload_max_files: '5',
  feature_contest: true,
  feature_forum: true,
  feature_solutions: true,
  feature_subscriptions: true,
  feature_achievements: true,
  feature_notifications: true,
  feature_bookmarks: true,
  feature_problem_lists: true,
})

function onSettingsUpdate(newSettings: AllSettings) {
  settings.value = newSettings
}

async function loadSettings() {
  loading.value = true
  try {
    settings.value = await settingsApi.getAllSettings()
  } catch (error) {
    toast.error(t('settings.toast.loadFailed'))
    console.error(error)
  } finally {
    loading.value = false
  }
}

async function saveSettings() {
  saving.value = true
  try {
    const result = await settingsApi.updateSettings(settings.value)
    // Backend may return { message, settings } or AllSettings directly after unwrap
    const updatedSettings =
      result && typeof result === 'object' && 'settings' in result
        ? (result as { settings: AllSettings }).settings
        : result
    if (updatedSettings) {
      settings.value = updatedSettings
    }
    const message =
      result && typeof result === 'object' && 'message' in result
        ? (result as { message: string }).message
        : t('settings.toast.saveSuccess')
    toast.success(message)
  } catch (error) {
    toast.error(t('settings.toast.saveFailed'))
    console.error(error)
  } finally {
    saving.value = false
  }
}

async function clearCache() {
  clearingCache.value = true
  try {
    const { message } = await settingsApi.clearCache()
    toast.success(message)
  } catch (error) {
    toast.error(t('settings.toast.clearCacheFailed'))
    console.error(error)
  } finally {
    clearingCache.value = false
  }
}

async function resetToDefaults() {
  try {
    const result = await settingsApi.resetToDefaults()
    // Backend may return { message, settings } or AllSettings directly after unwrap
    const defaultSettings =
      result && typeof result === 'object' && 'settings' in result
        ? (result as { settings: AllSettings }).settings
        : result
    if (defaultSettings) {
      settings.value = defaultSettings
    }
    const message =
      result && typeof result === 'object' && 'message' in result
        ? (result as { message: string }).message
        : t('settings.toast.resetSuccess')
    toast.success(message)
  } catch (error) {
    toast.error(t('settings.toast.resetFailed'))
    console.error(error)
  }
}

onMounted(async () => {
  await loadSettings()
  isLoaded.value = true
})
</script>

<template>
  <div class="relative flex flex-col gap-4 w-full min-w-0">
    <!-- Terminal Header -->
    <div
      :class="[
        'border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)]',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <div class="space-y-1">
          <h1 class="text-xl font-medium tracking-tight text-[var(--foreground)]">
            {{ t('settings.title') }}
          </h1>
          <p class="text-xs text-[var(--silver-500)]">{{ t('settings.description') }}</p>
        </div>
      </div>
    </div>

    <!-- Main Content Area -->
    <div
      :class="[
        'mt-6 space-y-6 transition-all duration-500 delay-100',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2',
      ]"
    >
      <div v-if="loading" class="flex items-center justify-center py-12">
        <IconSettings class="h-8 w-8 animate-spin text-muted-foreground" />
      </div>

      <Tabs v-else v-model="activeTab" class="space-y-6">
        <TabsList class="grid grid-cols-5 w-full max-w-2xl">
          <TabsTrigger value="general">
            <IconSettings class="h-4 w-4 mr-2" />
            {{ t('settings.tabs.general') }}
          </TabsTrigger>
          <TabsTrigger value="email">
            <IconMail class="h-4 w-4 mr-2" />
            {{ t('settings.tabs.email') }}
          </TabsTrigger>
          <TabsTrigger value="rateLimits">
            <IconClock class="h-4 w-4 mr-2" />
            {{ t('settings.tabs.rateLimits') }}
          </TabsTrigger>
          <TabsTrigger value="uploads">
            <IconUpload class="h-4 w-4 mr-2" />
            {{ t('settings.tabs.uploads') }}
          </TabsTrigger>
          <TabsTrigger value="features">
            <IconToggleLeft class="h-4 w-4 mr-2" />
            {{ t('settings.tabs.features') }}
          </TabsTrigger>
        </TabsList>

        <TabsContent value="general" class="space-y-6">
          <GeneralSettings :settings="settings" @update:settings="onSettingsUpdate" />
        </TabsContent>

        <TabsContent value="email" class="space-y-6">
          <EmailSettings :settings="settings" @update:settings="onSettingsUpdate" />
        </TabsContent>

        <TabsContent value="rateLimits" class="space-y-6">
          <RateLimitSettings :settings="settings" @update:settings="onSettingsUpdate" />
        </TabsContent>

        <TabsContent value="uploads" class="space-y-6">
          <UploadSettings :settings="settings" @update:settings="onSettingsUpdate" />
        </TabsContent>

        <TabsContent value="features" class="space-y-6">
          <FeatureToggleSettings :settings="settings" @update:settings="onSettingsUpdate" />
        </TabsContent>

        <!-- Actions (always visible) -->
        <Card>
          <CardHeader>
            <CardTitle>{{ t('settings.actions.title') }}</CardTitle>
          </CardHeader>
          <CardContent class="flex items-center justify-between flex-wrap gap-4">
            <div class="flex gap-2">
              <Button variant="outline" @click="clearCache" :disabled="clearingCache">
                <IconRefresh class="h-4 w-4 mr-2" :class="{ 'animate-spin': clearingCache }" />
                {{ t('settings.actions.clearCache') }}
              </Button>

              <AlertDialog>
                <AlertDialogTrigger as-child>
                  <Button variant="outline">
                    <IconReload class="h-4 w-4 mr-2" />
                    {{ t('settings.actions.resetToDefaults') }}
                  </Button>
                </AlertDialogTrigger>
                <AlertDialogContent>
                  <AlertDialogHeader>
                    <AlertDialogTitle>{{
                      t('settings.actions.resetConfirmTitle')
                    }}</AlertDialogTitle>
                    <AlertDialogDescription>{{
                      t('settings.actions.resetConfirmDescription')
                    }}</AlertDialogDescription>
                  </AlertDialogHeader>
                  <AlertDialogFooter>
                    <AlertDialogCancel>{{ t('common.cancel') }}</AlertDialogCancel>
                    <AlertDialogAction @click="resetToDefaults">{{
                      t('settings.actions.resetConfirm')
                    }}</AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
            </div>

            <Button @click="saveSettings" :disabled="saving">
              <IconDeviceFloppy class="h-4 w-4 mr-2" />
              {{ saving ? t('settings.actions.saving') : t('settings.actions.saveChanges') }}
            </Button>
          </CardContent>
        </Card>
      </Tabs>
    </div>
  </div>
</template>
