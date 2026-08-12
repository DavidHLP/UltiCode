<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { SemanticBadge, USER_ROLE_COLOR_MAP } from '@/components/ui/terminal'
import { formatDateByLocale, formatDateTimeByLocale } from '@/i18n/utils'
import { accountApi, type AccountProfile, type UpdateProfileDto } from '@/api/admin/account'
import { toast } from 'vue-sonner'
import {
  IconUser,
  IconBrandGithub,
  IconBrandTwitter,
  IconWorld,
  IconLanguage,
  IconDeviceFloppy,
  IconShield,
  IconKey,
} from '@tabler/icons-vue'

const { t } = useI18n()

const loading = ref(false)
const saving = ref(false)
const changingPassword = ref(false)

const profile = ref<AccountProfile>({
  id: '',
  username: '',
  name: '',
  email: '',
  role: '',
  joined_at: '',
})

const formData = ref<UpdateProfileDto>({})

const passwordData = ref({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const showPasswordForm = ref(false)
const isLoaded = ref(false)

const canSave = computed(() => {
  return Object.keys(formData.value).length > 0
})

async function loadProfile() {
  loading.value = true
  try {
    profile.value = await accountApi.getProfile()
  } catch (error) {
    toast.error(t('account.toast.saveFailed'))
    console.error(error)
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  if (!canSave.value) return

  saving.value = true
  try {
    const updatedProfile = await accountApi.updateProfile(formData.value)
    profile.value = updatedProfile
    formData.value = {}
    toast.success(t('account.toast.saveSuccess'))
  } catch (error) {
    toast.error(t('account.toast.saveFailed'))
    console.error(error)
  } finally {
    saving.value = false
  }
}

async function changePassword() {
  if (passwordData.value.newPassword !== passwordData.value.confirmPassword) {
    toast.error(t('account.toast.passwordsDoNotMatch'))
    return
  }

  changingPassword.value = true
  try {
    await accountApi.changePassword({
      currentPassword: passwordData.value.currentPassword,
      newPassword: passwordData.value.newPassword,
      confirmPassword: passwordData.value.confirmPassword,
    })
    passwordData.value = {
      currentPassword: '',
      newPassword: '',
      confirmPassword: '',
    }
    showPasswordForm.value = false
    toast.success(t('account.toast.passwordSuccess'))
  } catch (error) {
    toast.error(t('account.toast.passwordFailed'))
    console.error(error)
  } finally {
    changingPassword.value = false
  }
}

function updateField<K extends keyof UpdateProfileDto>(key: K, value: UpdateProfileDto[K]) {
  formData.value[key] = value
}

onMounted(async () => {
  await loadProfile()
  isLoaded.value = true
})
</script>

<template>
  <div class="relative flex flex-col gap-4 w-full min-w-0">
    <!-- Terminal Header -->
    <div
      :class="[
        'border border-[var(--border-subtle)] dark:border-[var(--border-subtle)] bg-[var(--card)]',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <div class="space-y-1">
          <h1 class="text-xl font-medium tracking-tight text-[var(--foreground)]">
            {{ t('account.title') }}
          </h1>
          <p class="text-xs text-[var(--foreground-muted)]">{{ t('account.subtitle') }}</p>
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
        <IconUser class="h-8 w-8 animate-spin text-muted-foreground" />
      </div>

      <div v-else class="space-y-6">
        <!-- Basic Information -->
        <Card>
          <CardHeader>
            <div class="flex items-center gap-2">
              <IconUser class="h-5 w-5 text-muted-foreground" />
              <CardTitle>{{ t('account.sections.basic') }}</CardTitle>
            </div>
          </CardHeader>
          <CardContent class="space-y-4">
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div class="space-y-2">
                <Label>{{ t('account.fields.name') }}</Label>
                <Input
                  :model-value="formData.name ?? profile.name"
                  @update:model-value="(v: string | number) => updateField('name', String(v))"
                  :placeholder="profile.name"
                />
              </div>
              <div class="space-y-2">
                <Label>{{ t('account.fields.email') }}</Label>
                <Input
                  :model-value="formData.email ?? profile.email"
                  @update:model-value="(v: string | number) => updateField('email', String(v))"
                  type="email"
                  :placeholder="profile.email"
                />
              </div>
            </div>
            <div class="space-y-2">
              <Label>{{ t('account.fields.avatar') }}</Label>
              <Input
                :model-value="formData.avatar ?? profile.avatar"
                @update:model-value="(v: string | number) => updateField('avatar', String(v))"
                :placeholder="profile.avatar || 'https://example.com/avatar.png'"
              />
            </div>
          </CardContent>
        </Card>

        <!-- About -->
        <Card>
          <CardHeader>
            <div class="flex items-center gap-2">
              <IconUser class="h-5 w-5 text-muted-foreground" />
              <CardTitle>{{ t('account.sections.about') }}</CardTitle>
            </div>
          </CardHeader>
          <CardContent class="space-y-4">
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div class="space-y-2">
                <Label>{{ t('account.fields.company') }}</Label>
                <Input
                  :model-value="formData.company ?? profile.company"
                  @update:model-value="(v: string | number) => updateField('company', String(v))"
                  :placeholder="profile.company || 'Your company'"
                />
              </div>
              <div class="space-y-2">
                <Label>{{ t('account.fields.location') }}</Label>
                <Input
                  :model-value="formData.location ?? profile.location"
                  @update:model-value="(v: string | number) => updateField('location', String(v))"
                  :placeholder="profile.location || 'Your location'"
                />
              </div>
            </div>
            <div class="space-y-2">
              <Label>{{ t('account.fields.bio') }}</Label>
              <Textarea
                :model-value="formData.bio ?? profile.bio"
                @update:model-value="(v: string | number) => updateField('bio', String(v))"
                :placeholder="profile.bio || 'Tell us about yourself...'"
                rows="3"
              />
            </div>
          </CardContent>
        </Card>

        <!-- Social Links -->
        <Card>
          <CardHeader>
            <div class="flex items-center gap-2">
              <IconWorld class="h-5 w-5 text-muted-foreground" />
              <CardTitle>{{ t('account.sections.social') }}</CardTitle>
            </div>
          </CardHeader>
          <CardContent class="space-y-4">
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div class="space-y-2">
                <Label class="flex items-center gap-2">
                  <IconBrandGithub class="h-4 w-4" />
                  {{ t('account.fields.github') }}
                </Label>
                <Input
                  :model-value="formData.github ?? profile.github"
                  @update:model-value="(v: string | number) => updateField('github', String(v))"
                  :placeholder="profile.github || 'https://github.com/username'"
                />
              </div>
              <div class="space-y-2">
                <Label class="flex items-center gap-2">
                  <IconBrandTwitter class="h-4 w-4" />
                  {{ t('account.fields.twitter') }}
                </Label>
                <Input
                  :model-value="formData.twitter ?? profile.twitter"
                  @update:model-value="(v: string | number) => updateField('twitter', String(v))"
                  :placeholder="profile.twitter || 'https://twitter.com/username'"
                />
              </div>
            </div>
            <div class="space-y-2">
              <Label class="flex items-center gap-2">
                <IconWorld class="h-4 w-4" />
                {{ t('account.fields.website') }}
              </Label>
              <Input
                :model-value="formData.website ?? profile.website"
                @update:model-value="(v: string | number) => updateField('website', String(v))"
                :placeholder="profile.website || 'https://yourwebsite.com'"
              />
            </div>
          </CardContent>
        </Card>

        <!-- Preferences -->
        <Card>
          <CardHeader>
            <div class="flex items-center gap-2">
              <IconLanguage class="h-5 w-5 text-muted-foreground" />
              <CardTitle>{{ t('account.sections.preferences') }}</CardTitle>
            </div>
          </CardHeader>
          <CardContent class="space-y-4">
            <div class="space-y-2">
              <Label>{{ t('account.fields.preferredLanguage') }}</Label>
              <Input
                :model-value="formData.preferred_language ?? profile.preferred_language"
                @update:model-value="
                  (v: string | number) => updateField('preferred_language', String(v))
                "
                :placeholder="profile.preferred_language || 'en-US'"
              />
            </div>
          </CardContent>
        </Card>

        <!-- Security -->
        <Card>
          <CardHeader>
            <div class="flex items-center gap-2">
              <IconShield class="h-5 w-5 text-muted-foreground" />
              <CardTitle>{{ t('account.sections.security') }}</CardTitle>
            </div>
          </CardHeader>
          <CardContent class="space-y-4">
            <div v-if="!showPasswordForm">
              <Button variant="outline" @click="showPasswordForm = true">
                <IconKey class="h-4 w-4 mr-2" />
                {{ t('account.actions.changePassword') }}
              </Button>
            </div>
            <div v-else class="space-y-4">
              <div class="space-y-2">
                <Label>{{ t('account.fields.currentPassword') }}</Label>
                <Input
                  v-model="passwordData.currentPassword"
                  type="password"
                  :placeholder="t('account.fields.currentPassword')"
                />
              </div>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div class="space-y-2">
                  <Label>{{ t('account.fields.newPassword') }}</Label>
                  <Input
                    v-model="passwordData.newPassword"
                    type="password"
                    :placeholder="t('account.fields.newPassword')"
                  />
                </div>
                <div class="space-y-2">
                  <Label>{{ t('account.fields.confirmPassword') }}</Label>
                  <Input
                    v-model="passwordData.confirmPassword"
                    type="password"
                    :placeholder="t('account.fields.confirmPassword')"
                  />
                </div>
              </div>
              <div class="flex gap-2">
                <Button @click="changePassword" :disabled="changingPassword">
                  {{ changingPassword ? t('common.saving') : t('account.actions.changePassword') }}
                </Button>
                <Button variant="outline" @click="showPasswordForm = false">
                  {{ t('account.actions.cancel') }}
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>

        <!-- Account Information (Read-only) -->
        <Card>
          <CardHeader>
            <div class="flex items-center gap-2">
              <IconShield class="h-5 w-5 text-muted-foreground" />
              <CardTitle>{{ t('account.sections.accountInfo') }}</CardTitle>
            </div>
          </CardHeader>
          <CardContent class="space-y-4">
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div class="space-y-1">
                <Label class="text-muted-foreground">{{ t('account.fields.role') }}</Label>
                <div>
                  <SemanticBadge
                    :color="USER_ROLE_COLOR_MAP[profile.role] || 'neutral'"
                    :label="
                      t(`users.filters.role.${profile.role}`) !==
                      `users.filters.role.${profile.role}`
                        ? t(`users.filters.role.${profile.role}`)
                        : profile.role
                    "
                  />
                </div>
              </div>
              <div class="space-y-1">
                <Label class="text-muted-foreground">{{ t('account.fields.joinedAt') }}</Label>
                <div class="text-sm">
                  {{ formatDateByLocale(profile.joined_at) }}
                </div>
              </div>
            </div>
            <Separator />
            <div class="space-y-1">
              <Label class="text-muted-foreground">{{ t('account.fields.username') }}</Label>
              <div class="text-sm">@{{ profile.username }}</div>
            </div>
            <div v-if="profile.last_login_at" class="space-y-1">
              <Label class="text-muted-foreground">{{ t('account.fields.lastLogin') }}</Label>
              <div class="text-sm">
                {{ formatDateTimeByLocale(profile.last_login_at) }}
              </div>
            </div>
          </CardContent>
        </Card>

        <!-- Actions -->
        <div class="flex justify-end gap-2">
          <Button
            v-if="canSave"
            variant="outline"
            @click="
              () => {
                formData = {}
                showPasswordForm = false
              }
            "
          >
            {{ t('account.actions.cancel') }}
          </Button>
          <Button @click="saveProfile" :disabled="saving || !canSave">
            <IconDeviceFloppy class="h-4 w-4 mr-2" />
            {{ saving ? t('common.saving') : t('account.actions.save') }}
          </Button>
        </div>
      </div>
    </div>
  </div>
</template>
