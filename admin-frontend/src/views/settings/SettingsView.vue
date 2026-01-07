<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { settingsApi, type SystemSettings } from '@/api/admin/settings'
import { toast } from 'vue-sonner'
import {
  IconSettings,
  IconServer,
  IconUsers,
  IconDeviceFloppy,
  IconRefresh,
} from '@tabler/icons-vue'

const loading = ref(false)
const saving = ref(false)
const clearingCache = ref(false)

const settings = ref<SystemSettings>({
  maintenance_mode: false,
  maintenance_message: '',
  enable_registrations: true,
  site_name: '',
  site_description: '',
  require_email_verification: false,
})

async function loadSettings() {
  loading.value = true
  try {
    settings.value = await settingsApi.getSettings()
  } catch (error) {
    toast.error('Failed to load settings')
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
    toast.error('Failed to save settings')
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
    toast.error('Failed to clear cache')
    console.error(error)
  } finally {
    clearingCache.value = false
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
      <h1 class="text-3xl font-bold tracking-tight">System Settings</h1>
      <p class="text-muted-foreground">Manage global system configuration and preferences.</p>
    </div>

    <div v-if="loading" class="flex items-center justify-center py-12">
      <IconSettings class="h-8 w-8 animate-spin text-muted-foreground" />
    </div>

    <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-6">
      <!-- General Settings -->
      <Card>
        <CardHeader>
          <div class="flex items-center gap-2">
            <IconSettings class="h-5 w-5 text-muted-foreground" />
            <CardTitle>General Settings</CardTitle>
          </div>
          <CardDescription>Basic site information.</CardDescription>
        </CardHeader>
        <CardContent class="space-y-4">
          <div class="space-y-2">
            <Label>Site Name</Label>
            <Input v-model="settings.site_name" placeholder="UltiCode" />
          </div>
          <div class="space-y-2">
            <Label>Site Description</Label>
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
            <CardTitle>User Registration</CardTitle>
          </div>
          <CardDescription>Control how users sign up.</CardDescription>
        </CardHeader>
        <CardContent class="space-y-6">
          <div class="flex items-center justify-between space-x-2">
            <Label class="flex flex-col space-y-1">
              <span>Enable Registrations</span>
              <span class="font-normal text-xs text-muted-foreground"
                >Allow new users to create accounts.</span
              >
            </Label>
            <Switch
              :checked="settings.enable_registrations"
              @update:checked="(v: boolean) => (settings.enable_registrations = v)"
            />
          </div>
          <div class="flex items-center justify-between space-x-2">
            <Label class="flex flex-col space-y-1">
              <span>Require Email Verification</span>
              <span class="font-normal text-xs text-muted-foreground"
                >Users must verify email before logging in.</span
              >
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
            <CardTitle class="text-orange-700 dark:text-orange-300">System Status</CardTitle>
          </div>
          <CardDescription>Control site availability.</CardDescription>
        </CardHeader>
        <CardContent class="space-y-6">
          <div class="flex items-center justify-between space-x-2">
            <Label class="flex flex-col space-y-1">
              <span>Maintenance Mode</span>
              <span class="font-normal text-xs text-muted-foreground"
                >Only admins can access the site when enabled.</span
              >
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
            <Label>Maintenance Message</Label>
            <Textarea
              v-model="settings.maintenance_message"
              placeholder="We are currently performing maintenance..."
            />
          </div>
        </CardContent>
      </Card>

      <!-- Actions -->
      <Card class="md:col-span-2">
        <CardHeader>
          <CardTitle>Actions</CardTitle>
        </CardHeader>
        <CardContent class="flex items-center justify-between">
          <Button variant="outline" @click="clearCache" :disabled="clearingCache">
            <IconRefresh class="h-4 w-4 mr-2" :class="{ 'animate-spin': clearingCache }" />
            Clear System Cache
          </Button>

          <Button @click="saveSettings" :disabled="saving">
            <IconDeviceFloppy class="h-4 w-4 mr-2" />
            {{ saving ? 'Saving...' : 'Save Changes' }}
          </Button>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
