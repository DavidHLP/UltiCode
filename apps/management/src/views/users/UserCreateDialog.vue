<script setup lang="ts">
import { FieldLabel } from '@ulticode/design-system/ui/field'

import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowRight as IconArrowRight, Loader2 as IconLoader2 } from '@lucide/vue'

import { useUsersStore } from '@/stores/admin/users'
import { Button } from '@ulticode/design-system/ui/button'
import { Input } from '@ulticode/design-system/ui/input'
import { Checkbox } from '@ulticode/design-system/ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@ulticode/design-system/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  SelectGroup,
} from '@ulticode/design-system/ui/select'

const { t } = useI18n()

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const usersStore = useUsersStore()
const loading = ref(false)
const error = ref('')

const defaultForm = {
  username: '',
  email: '',
  name: '',
  password: '',
  role: 'USER',
  isActive: true,
}

const form = ref({ ...defaultForm })

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      form.value = { ...defaultForm }
      error.value = ''
    }
  },
)

async function handleSubmit() {
  error.value = ''
  loading.value = true

  try {
    await usersStore.createUser({
      username: form.value.username,
      email: form.value.email,
      name: form.value.name,
      password: form.value.password,
      role: form.value.role as 'USER' | 'MODERATOR' | 'ADMIN' | 'SUPER_ADMIN',
      isActive: form.value.isActive,
    })

    emit('success')
    emit('update:open', false)
  } catch (err: unknown) {
    error.value =
      (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
      'Failed to create user'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent class="sm:max-w-[560px] p-0 gap-0">
      <!-- Page header -->
      <DialogHeader class="p-4">
        <div class="flex items-center gap-3">
          <DialogTitle>{{ t('users.createUser') }}</DialogTitle>
        </div>
        <DialogDescription class="mt-1">{{ t('users.createDescription') }}</DialogDescription>
      </DialogHeader>

      <!-- Form -->
      <form @submit.prevent="handleSubmit">
        <!-- Error Banner -->
        <div
          v-if="error"
          class="mx-4 mt-4 p-3 border border-status-error-mark bg-[color-mix(in_oklch,_var(--status-error-mark)_8%,_transparent)] flex items-center gap-2"
        >
          <span class="font-mono text-xs text-foreground">> ERROR:</span>
          <span class="text-sm text-foreground">{{ error }}</span>
        </div>

        <div class="max-h-[50vh] overflow-y-auto p-4 space-y-6">
          <!-- Section: General Information -->
          <div>
            <div class="text-sm text-muted-foreground mb-3">
              {{ t('users.form.sections.general') }}
            </div>
            <div class="space-y-4">
              <div class="space-y-1.5">
                <FieldLabel class="block">{{ t('users.form.fullName') }}</FieldLabel>
                <Input
                  v-model="form.name"
                  type="text"
                  required
                  :disabled="loading"
                  :placeholder="t('users.form.fullNamePlaceholder')"
                />
              </div>

              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div class="space-y-1.5">
                  <FieldLabel class="block">{{ t('users.form.username') }}</FieldLabel>
                  <Input
                    v-model="form.username"
                    type="text"
                    required
                    :disabled="loading"
                    :placeholder="t('users.form.usernamePlaceholder')"
                  />
                </div>

                <div class="space-y-1.5">
                  <FieldLabel class="block">{{ t('users.form.email') }}</FieldLabel>
                  <Input
                    v-model="form.email"
                    type="email"
                    required
                    :disabled="loading"
                    :placeholder="t('users.form.emailPlaceholder')"
                  />
                </div>
              </div>
            </div>
          </div>

          <!-- Double-line Separator -->
          <div class="border-t border-border" />

          <!-- Section: Security & Access -->
          <div>
            <div class="text-sm text-muted-foreground mb-3">
              {{ t('users.form.sections.securityAccess') }}
            </div>
            <div class="space-y-4">
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div class="space-y-1.5">
                  <FieldLabel class="block">{{ t('users.form.password') }}</FieldLabel>
                  <Input v-model="form.password" type="password" required :disabled="loading" />
                </div>

                <div class="space-y-1.5">
                  <FieldLabel class="block">{{ t('users.form.role') }}</FieldLabel>
                  <Select v-model="form.role" :disabled="loading">
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup
                        ><SelectItem value="USER">{{ t('users.filters.role.USER') }}</SelectItem>
                        <SelectItem value="MODERATOR">{{
                          t('users.filters.role.MODERATOR')
                        }}</SelectItem>
                        <SelectItem value="ADMIN">{{ t('users.filters.role.ADMIN') }}</SelectItem>
                        <SelectItem value="SUPER_ADMIN">{{
                          t('users.filters.role.SUPER_ADMIN')
                        }}</SelectItem></SelectGroup
                      >
                    </SelectContent>
                  </Select>
                </div>
              </div>

              <div class="space-y-1.5">
                <FieldLabel class="block">{{ t('users.form.status') }}</FieldLabel>
                <div
                  class="flex items-center gap-3 h-9 px-3 border border-border dark:border-foreground bg-muted"
                >
                  <Checkbox id="create-isActive" v-model="form.isActive" :disabled="loading" />
                  <FieldLabel for="create-isActive" class="cursor-pointer">
                    {{
                      form.isActive
                        ? t('users.filters.status.active')
                        : t('users.filters.status.inactive')
                    }}
                  </FieldLabel>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Footer -->
        <DialogFooter class="p-4 gap-3">
          <Button type="button" variant="outline" @click="emit('update:open', false)">
            {{ t('common.cancel') }}
          </Button>
          <Button type="submit" variant="outline" :disabled="loading">
            <IconLoader2 v-if="loading" class="h-3.5 w-3.5 mr-1.5 animate-spin" />
            <IconArrowRight v-else class="h-3.5 w-3.5 mr-1.5" />
            {{ loading ? t('users.form.creating') : t('users.form.createUser') }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>
