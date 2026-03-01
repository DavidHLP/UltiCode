<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
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
import { toast } from 'vue-sonner'
import {
  IconSettings,
  IconMail,
  IconClock,
  IconUpload,
  IconToggleLeft,
  IconServer,
  IconUsers,
  IconDeviceFloppy,
  IconRefresh,
  IconReload,
  IconEye,
  IconEyeOff,
} from '@tabler/icons-vue'

const { t } = useI18n()

const loading = ref(false)
const saving = ref(false)
const clearingCache = ref(false)
const activeTab = ref('general')

// Show/hide password state
const showSmtpPassword = ref(false)

const settings = ref<AllSettings>({
  // General
  maintenance_mode: false,
  maintenance_message: '',
  enable_registrations: true,
  site_name: '',
  site_description: '',
  require_email_verification: false,
  // Email
  smtp_host: '',
  smtp_port: '587',
  smtp_user: '',
  smtp_password: '',
  smtp_from: '',
  smtp_from_name: '',
  smtp_secure: true,
  // Rate Limits
  rate_limit_api: '100',
  rate_limit_submission: '10',
  rate_limit_auth: '5',
  rate_limit_upload: '20',
  // Uploads
  upload_max_size: '10485760',
  upload_allowed_types: '',
  upload_max_files: '5',
  // Feature Toggles
  feature_contest: true,
  feature_forum: true,
  feature_solutions: true,
  feature_subscriptions: true,
  feature_achievements: true,
  feature_notifications: true,
  feature_bookmarks: true,
  feature_problem_lists: true,
})

// Helper to format bytes to human readable
function formatBytes(bytes: string): string {
  const value = parseInt(bytes, 10)
  if (isNaN(value)) return '0 B'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  if (value < 1024 * 1024 * 1024) return `${(value / (1024 * 1024)).toFixed(1)} MB`
  return `${(value / (1024 * 1024 * 1024)).toFixed(1)} GB`
}

// Convert human readable to bytes
function parseSizeToBytes(sizeStr: string): string {
  const match = sizeStr.match(/^(\d+(?:\.\d+)?)\s*(B|KB|MB|GB)?$/i)
  if (!match || !match[1]) return sizeStr
  const value = parseFloat(match[1])
  const unit = (match[2] || 'B').toUpperCase()
  const multipliers: Record<string, number> = {
    B: 1,
    KB: 1024,
    MB: 1024 * 1024,
    GB: 1024 * 1024 * 1024,
  }
  return String(Math.round(value * (multipliers[unit] || 1)))
}

// Human readable upload size
const uploadSizeReadable = computed({
  get: () => formatBytes(settings.value.upload_max_size),
  set: (val: string) => {
    settings.value.upload_max_size = parseSizeToBytes(val)
  },
})

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
    const { settings: updatedSettings, message } = await settingsApi.updateSettings(settings.value)
    settings.value = updatedSettings
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
    const { settings: defaultSettings, message } = await settingsApi.resetToDefaults()
    settings.value = defaultSettings
    toast.success(message)
  } catch (error) {
    toast.error(t('settings.toast.resetFailed'))
    console.error(error)
  }
}

onMounted(() => {
  loadSettings()
})
</script>

<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex flex-col gap-2">
      <h1 class="text-3xl font-bold tracking-tight">{{ t('settings.title') }}</h1>
      <p class="text-muted-foreground">{{ t('settings.description') }}</p>
    </div>

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

      <!-- General Settings -->
      <TabsContent value="general" class="space-y-6">
        <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
          <!-- Site Settings -->
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
                <Input v-model="settings.site_name" placeholder="UltiCode" />
              </div>
              <div class="space-y-2">
                <Label>{{ t('settings.siteDescription') }}</Label>
                <Input
                  v-model="settings.site_description"
                  placeholder="Competitive Programming Platform"
                />
              </div>
            </CardContent>
          </Card>

          <!-- User Registration -->
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
                  @update:checked="(v: boolean) => (settings.enable_registrations = v)"
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
                  @update:checked="(v: boolean) => (settings.require_email_verification = v)"
                />
              </div>
            </CardContent>
          </Card>

          <!-- Maintenance Mode -->
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
                  @update:checked="(v: boolean) => (settings.maintenance_mode = v)"
                  class="data-[state=checked]:bg-orange-600"
                />
              </div>

              <div
                v-if="settings.maintenance_mode"
                class="space-y-2 animate-in fade-in slide-in-from-top-2"
              >
                <Label>{{ t('settings.systemStatus.maintenanceMessage') }}</Label>
                <Textarea
                  v-model="settings.maintenance_message"
                  placeholder="We are currently performing maintenance..."
                />
              </div>
            </CardContent>
          </Card>
        </div>
      </TabsContent>

      <!-- Email Settings -->
      <TabsContent value="email" class="space-y-6">
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
                <Input v-model="settings.smtp_host" placeholder="smtp.example.com" />
              </div>
              <div class="space-y-2">
                <Label>{{ t('settings.email.smtpPort') }}</Label>
                <Input v-model="settings.smtp_port" placeholder="587" />
              </div>
              <div class="space-y-2">
                <Label>{{ t('settings.email.smtpUser') }}</Label>
                <Input v-model="settings.smtp_user" placeholder="user@example.com" />
              </div>
              <div class="space-y-2">
                <Label>{{ t('settings.email.smtpPassword') }}</Label>
                <div class="relative">
                  <Input
                    v-model="settings.smtp_password"
                    :type="showSmtpPassword ? 'text' : 'password'"
                    placeholder="••••••••"
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
                <Input v-model="settings.smtp_from" placeholder="noreply@ulticode.com" />
              </div>
              <div class="space-y-2">
                <Label>{{ t('settings.email.smtpFromName') }}</Label>
                <Input v-model="settings.smtp_from_name" placeholder="UltiCode" />
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
                @update:checked="(v: boolean) => (settings.smtp_secure = v)"
              />
            </div>
          </CardContent>
        </Card>
      </TabsContent>

      <!-- Rate Limit Settings -->
      <TabsContent value="rateLimits" class="space-y-6">
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
                <Input v-model="settings.rate_limit_api" type="number" min="1" />
                <p class="text-xs text-muted-foreground">
                  {{ t('settings.rateLimits.apiDescription') }}
                </p>
              </div>
              <div class="space-y-2">
                <Label>{{ t('settings.rateLimits.submission') }}</Label>
                <Input v-model="settings.rate_limit_submission" type="number" min="1" />
                <p class="text-xs text-muted-foreground">
                  {{ t('settings.rateLimits.submissionDescription') }}
                </p>
              </div>
              <div class="space-y-2">
                <Label>{{ t('settings.rateLimits.auth') }}</Label>
                <Input v-model="settings.rate_limit_auth" type="number" min="1" />
                <p class="text-xs text-muted-foreground">
                  {{ t('settings.rateLimits.authDescription') }}
                </p>
              </div>
              <div class="space-y-2">
                <Label>{{ t('settings.rateLimits.upload') }}</Label>
                <Input v-model="settings.rate_limit_upload" type="number" min="1" />
                <p class="text-xs text-muted-foreground">
                  {{ t('settings.rateLimits.uploadDescription') }}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>
      </TabsContent>

      <!-- Upload Settings -->
      <TabsContent value="uploads" class="space-y-6">
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
                <Input v-model="settings.upload_max_files" type="number" min="1" />
                <p class="text-xs text-muted-foreground">
                  {{ t('settings.uploads.maxFilesDescription') }}
                </p>
              </div>
            </div>
            <div class="space-y-2">
              <Label>{{ t('settings.uploads.allowedTypes') }}</Label>
              <Input
                v-model="settings.upload_allowed_types"
                placeholder="jpg,jpeg,png,gif,pdf,zip"
              />
              <p class="text-xs text-muted-foreground">
                {{ t('settings.uploads.allowedTypesDescription') }}
              </p>
            </div>
          </CardContent>
        </Card>
      </TabsContent>

      <!-- Feature Toggles -->
      <TabsContent value="features" class="space-y-6">
        <Card>
          <CardHeader>
            <div class="flex items-center gap-2">
              <IconToggleLeft class="h-5 w-5 text-muted-foreground" />
              <CardTitle>{{ t('settings.features.title') }}</CardTitle>
            </div>
            <CardDescription>{{ t('settings.features.description') }}</CardDescription>
          </CardHeader>
          <CardContent>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div class="flex items-center justify-between space-x-2">
                <Label class="flex flex-col space-y-1">
                  <span>{{ t('settings.features.contest') }}</span>
                  <span class="font-normal text-xs text-muted-foreground">{{
                    t('settings.features.contestDescription')
                  }}</span>
                </Label>
                <Switch
                  :checked="settings.feature_contest"
                  @update:checked="(v: boolean) => (settings.feature_contest = v)"
                />
              </div>
              <div class="flex items-center justify-between space-x-2">
                <Label class="flex flex-col space-y-1">
                  <span>{{ t('settings.features.forum') }}</span>
                  <span class="font-normal text-xs text-muted-foreground">{{
                    t('settings.features.forumDescription')
                  }}</span>
                </Label>
                <Switch
                  :checked="settings.feature_forum"
                  @update:checked="(v: boolean) => (settings.feature_forum = v)"
                />
              </div>
              <div class="flex items-center justify-between space-x-2">
                <Label class="flex flex-col space-y-1">
                  <span>{{ t('settings.features.solutions') }}</span>
                  <span class="font-normal text-xs text-muted-foreground">{{
                    t('settings.features.solutionsDescription')
                  }}</span>
                </Label>
                <Switch
                  :checked="settings.feature_solutions"
                  @update:checked="(v: boolean) => (settings.feature_solutions = v)"
                />
              </div>
              <div class="flex items-center justify-between space-x-2">
                <Label class="flex flex-col space-y-1">
                  <span>{{ t('settings.features.subscriptions') }}</span>
                  <span class="font-normal text-xs text-muted-foreground">{{
                    t('settings.features.subscriptionsDescription')
                  }}</span>
                </Label>
                <Switch
                  :checked="settings.feature_subscriptions"
                  @update:checked="(v: boolean) => (settings.feature_subscriptions = v)"
                />
              </div>
              <div class="flex items-center justify-between space-x-2">
                <Label class="flex flex-col space-y-1">
                  <span>{{ t('settings.features.achievements') }}</span>
                  <span class="font-normal text-xs text-muted-foreground">{{
                    t('settings.features.achievementsDescription')
                  }}</span>
                </Label>
                <Switch
                  :checked="settings.feature_achievements"
                  @update:checked="(v: boolean) => (settings.feature_achievements = v)"
                />
              </div>
              <div class="flex items-center justify-between space-x-2">
                <Label class="flex flex-col space-y-1">
                  <span>{{ t('settings.features.notifications') }}</span>
                  <span class="font-normal text-xs text-muted-foreground">{{
                    t('settings.features.notificationsDescription')
                  }}</span>
                </Label>
                <Switch
                  :checked="settings.feature_notifications"
                  @update:checked="(v: boolean) => (settings.feature_notifications = v)"
                />
              </div>
              <div class="flex items-center justify-between space-x-2">
                <Label class="flex flex-col space-y-1">
                  <span>{{ t('settings.features.bookmarks') }}</span>
                  <span class="font-normal text-xs text-muted-foreground">{{
                    t('settings.features.bookmarksDescription')
                  }}</span>
                </Label>
                <Switch
                  :checked="settings.feature_bookmarks"
                  @update:checked="(v: boolean) => (settings.feature_bookmarks = v)"
                />
              </div>
              <div class="flex items-center justify-between space-x-2">
                <Label class="flex flex-col space-y-1">
                  <span>{{ t('settings.features.problemLists') }}</span>
                  <span class="font-normal text-xs text-muted-foreground">{{
                    t('settings.features.problemListsDescription')
                  }}</span>
                </Label>
                <Switch
                  :checked="settings.feature_problem_lists"
                  @update:checked="(v: boolean) => (settings.feature_problem_lists = v)"
                />
              </div>
            </div>
          </CardContent>
        </Card>
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
                  <AlertDialogTitle>{{ t('settings.actions.resetConfirmTitle') }}</AlertDialogTitle>
                  <AlertDialogDescription>
                    {{ t('settings.actions.resetConfirmDescription') }}
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>{{ t('common.cancel') }}</AlertDialogCancel>
                  <AlertDialogAction @click="resetToDefaults">
                    {{ t('settings.actions.resetConfirm') }}
                  </AlertDialogAction>
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
</template>
