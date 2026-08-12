<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { IconArrowRight, IconLoader2 } from '@tabler/icons-vue'
import { useUsersStore } from '@/stores/admin/users'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

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
    <DialogContent
      class="sm:max-w-[560px] border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] rounded-none p-0 gap-0"
    >
      <!-- Terminal Header -->
      <DialogHeader class="border-b border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] p-4">
        <div class="flex items-center gap-3">
          <DialogTitle class="text-lg font-medium tracking-tight">{{
            t('users.createUser')
          }}</DialogTitle>
        </div>
        <DialogDescription class="terminal-comment mt-1">{{
          t('users.createDescription')
        }}</DialogDescription>
      </DialogHeader>

      <!-- Form -->
      <form @submit.prevent="handleSubmit">
        <!-- Error Banner -->
        <div
          v-if="error"
          class="mx-4 mt-4 p-3 border border-[var(--status-error-mark)] bg-[color-mix(in_oklch,_var(--status-error-mark)_8%,_transparent)] flex items-center gap-2"
        >
          <span class="font-data text-xs text-[var(--foreground-strong)]">> ERROR:</span>
          <span class="text-sm text-[var(--foreground)]">{{ error }}</span>
        </div>

        <div class="max-h-[50vh] overflow-y-auto p-4 space-y-6">
          <!-- Section: General Information -->
          <div>
            <div class="terminal-comment mb-3">{{ t('users.form.sections.general') }}</div>
            <div class="space-y-4">
              <div class="space-y-1.5">
                <label class="terminal-label block">{{ t('users.form.fullName') }}</label>
                <Input
                  v-model="form.name"
                  type="text"
                  required
                  :disabled="loading"
                  class="terminal-input h-9"
                  :placeholder="t('users.form.fullNamePlaceholder')"
                />
              </div>

              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div class="space-y-1.5">
                  <label class="terminal-label block">{{ t('users.form.username') }}</label>
                  <Input
                    v-model="form.username"
                    type="text"
                    required
                    :disabled="loading"
                    class="terminal-input h-9 font-data"
                    :placeholder="t('users.form.usernamePlaceholder')"
                  />
                </div>

                <div class="space-y-1.5">
                  <label class="terminal-label block">{{ t('users.form.email') }}</label>
                  <Input
                    v-model="form.email"
                    type="email"
                    required
                    :disabled="loading"
                    class="terminal-input h-9 font-data"
                    :placeholder="t('users.form.emailPlaceholder')"
                  />
                </div>
              </div>
            </div>
          </div>

          <!-- Double-line Separator -->
          <div class="terminal-separator" />

          <!-- Section: Security & Access -->
          <div>
            <div class="terminal-comment mb-3">{{ t('users.form.sections.securityAccess') }}</div>
            <div class="space-y-4">
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div class="space-y-1.5">
                  <label class="terminal-label block">{{ t('users.form.password') }}</label>
                  <Input
                    v-model="form.password"
                    type="password"
                    required
                    :disabled="loading"
                    class="terminal-input h-9 font-data"
                  />
                </div>

                <div class="space-y-1.5">
                  <label class="terminal-label block">{{ t('users.form.role') }}</label>
                  <Select v-model="form.role" :disabled="loading">
                    <SelectTrigger class="terminal-input h-9 font-data">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="USER" class="font-data">{{
                        t('users.filters.role.USER')
                      }}</SelectItem>
                      <SelectItem value="MODERATOR" class="font-data">{{
                        t('users.filters.role.MODERATOR')
                      }}</SelectItem>
                      <SelectItem value="ADMIN" class="font-data">{{
                        t('users.filters.role.ADMIN')
                      }}</SelectItem>
                      <SelectItem value="SUPER_ADMIN" class="font-data">{{
                        t('users.filters.role.SUPER_ADMIN')
                      }}</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>

              <div class="space-y-1.5">
                <label class="terminal-label block">{{ t('users.form.status') }}</label>
                <div
                  class="flex items-center gap-3 h-9 px-3 border border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] bg-[var(--surface-sunken)]"
                >
                  <Checkbox
                    id="create-isActive"
                    v-model="form.isActive"
                    :disabled="loading"
                    class="border-[var(--foreground-muted)] data-[state=checked]:bg-[var(--status-success-mark)] data-[state=checked]:border-[var(--status-success-mark)]"
                  />
                  <label for="create-isActive" class="font-data text-xs cursor-pointer">
                    {{
                      form.isActive
                        ? t('users.filters.status.active')
                        : t('users.filters.status.inactive')
                    }}
                  </label>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Footer -->
        <DialogFooter
          class="border-t border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] p-4 gap-3"
        >
          <Button
            type="button"
            variant="terminal"
            class="font-data text-xs border-[var(--border-subtle)] hover:border-[var(--foreground-muted)]"
            @click="emit('update:open', false)"
          >
            {{ t('common.cancel') }}
          </Button>
          <Button
            type="submit"
            variant="terminal"
            :disabled="loading"
            class="font-data text-xs bg-[var(--primary)] hover:bg-[var(--primary)]/90"
          >
            <IconLoader2 v-if="loading" class="h-3.5 w-3.5 mr-1.5 animate-spin" />
            <IconArrowRight v-else class="h-3.5 w-3.5 mr-1.5" />
            {{ loading ? t('users.form.creating') : t('users.form.createUser') }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>
