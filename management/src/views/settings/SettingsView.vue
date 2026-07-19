<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { storeToRefs } from 'pinia'
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
import { useSystemSettingsStore } from '@/stores/admin/system-settings'

const { t } = useI18n()

const store = useSystemSettingsStore()
const {
  general,
  email,
  rateLimits,
  uploads,
  features,
  loading,
  saving,
  clearingCache,
  isDirty,
} = storeToRefs(store)

const activeTab = ref('general')
const isLoaded = ref(false)

async function onSave() {
  try {
    await store.saveAllDirty()
    toast.success(t('settings.toast.saveSuccess'))
  } catch (error) {
    toast.error(t('settings.toast.saveFailed'))
    console.error(error)
  }
}

async function onClearCache() {
  try {
    await store.clearCache()
    toast.success(t('settings.toast.cacheCleared'))
  } catch (error) {
    toast.error(t('settings.toast.clearCacheFailed'))
    console.error(error)
  }
}

async function onResetToDefaults() {
  try {
    await store.resetToDefaultsServer()
    toast.success(t('settings.toast.resetSuccess'))
  } catch (error) {
    toast.error(t('settings.toast.resetFailed'))
    console.error(error)
  }
}

onMounted(async () => {
  try {
    await store.load()
  } catch (error) {
    toast.error(t('settings.toast.loadFailed'))
    console.error(error)
  } finally {
    isLoaded.value = true
  }
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

        <!--
          Each category adapter receives ONLY its own slice (focused props)
          and emits a per-category patch. The system-settings workspace
          owns the AllSettings bag, the per-category dirty tracking, and
          routes each save to its own typed backend endpoint — the previous
          design POSTed the whole bag to /admin/settings and silently
          dropped every non-general edit at Jackson binding.
        -->
        <TabsContent value="general" class="space-y-6">
          <GeneralSettings :settings="general" @update:settings="store.patchGeneral" />
        </TabsContent>

        <TabsContent value="email" class="space-y-6">
          <EmailSettings :settings="email" @update:settings="store.patchEmail" />
        </TabsContent>

        <TabsContent value="rateLimits" class="space-y-6">
          <RateLimitSettings :settings="rateLimits" @update:settings="store.patchRateLimits" />
        </TabsContent>

        <TabsContent value="uploads" class="space-y-6">
          <UploadSettings :settings="uploads" @update:settings="store.patchUploads" />
        </TabsContent>

        <TabsContent value="features" class="space-y-6">
          <FeatureToggleSettings :settings="features" @update:settings="store.patchFeatures" />
        </TabsContent>

        <!-- Actions (always visible) -->
        <Card>
          <CardHeader>
            <CardTitle>{{ t('settings.actions.title') }}</CardTitle>
          </CardHeader>
          <CardContent class="flex items-center justify-between flex-wrap gap-4">
            <div class="flex gap-2">
              <Button variant="outline" @click="onClearCache" :disabled="clearingCache">
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
                    <AlertDialogAction @click="onResetToDefaults">{{
                      t('settings.actions.resetConfirm')
                    }}</AlertDialogAction>
                  </AlertDialogFooter>
                </AlertDialogContent>
              </AlertDialog>
            </div>

            <Button @click="onSave" :disabled="saving || !isDirty">
              <IconDeviceFloppy class="h-4 w-4 mr-2" />
              {{ saving ? t('settings.actions.saving') : t('settings.actions.saveChanges') }}
            </Button>
          </CardContent>
        </Card>
      </Tabs>
    </div>
  </div>
</template>
