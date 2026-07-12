<script setup lang="ts">
import { ref, watch } from 'vue'
import { useUsersStore } from '@/stores/admin/users'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { IconArrowRight, IconLoader2, IconLock, IconShieldCheck } from '@tabler/icons-vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

const { t } = useI18n()

const props = defineProps<{
  open: boolean
  userId: string | null
  username: string | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const usersStore = useUsersStore()
const newPassword = ref('')
const loading = ref(false)

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      newPassword.value = ''
    }
  },
)

async function handleReset() {
  if (!props.userId || !newPassword.value) return
  if (newPassword.value.length < 8) {
    toast.error(t('users.toast.resetPasswordValidationFailed'), {
      description: t('users.toast.resetPasswordValidationFailedDescription'),
    })
    return
  }
  loading.value = true
  try {
    await usersStore.resetPassword(props.userId, newPassword.value)
    emit('success')
    emit('update:open', false)
  } catch {
    toast.error(t('users.toast.resetPasswordFailed'), {
      description: t('users.toast.resetPasswordFailedDescription'),
    })
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent
      class="sm:max-w-[420px] border-[var(--silver-200)] dark:border-[var(--silver-700)] rounded-none p-0 gap-0"
    >
      <!-- Terminal Header -->
      <DialogHeader class="border-b border-[var(--silver-200)] dark:border-[var(--silver-700)] p-4">
        <div class="flex items-center gap-3">
          <DialogTitle class="text-lg font-medium tracking-tight">
            {{ t('users.actions.resetPassword') }}
          </DialogTitle>
        </div>
        <DialogDescription class="terminal-comment mt-1">
          {{
            t('users.actions.resetPasswordDescription', {
              username: username || t('users.actions.thisUser'),
            })
          }}
        </DialogDescription>
      </DialogHeader>

      <!-- Form -->
      <form @submit.prevent="handleReset">
        <div class="p-4 space-y-4">
          <!-- Security Warning -->
          <div
            class="flex items-start gap-3 p-3 border border-[var(--terminal-amber)] bg-[color-mix(in_oklch,_var(--terminal-amber)_8%,_transparent)]"
          >
            <IconShieldCheck class="h-4 w-4 text-[var(--terminal-amber)] shrink-0 mt-0.5" />
            <div class="space-y-1">
              <p class="font-data text-xs text-[var(--terminal-amber)] uppercase tracking-wider">
                Security Notice
              </p>
              <p class="text-xs text-[var(--silver-500)]">
                {{ t('users.actions.resetPasswordWarning') }}
              </p>
            </div>
          </div>

          <!-- Password Field -->
          <div class="space-y-1.5">
            <label class="terminal-label block">{{ t('users.form.newPassword') }}</label>
            <div class="relative">
              <IconLock
                class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-[var(--silver-400)]"
              />
              <Input
                v-model="newPassword"
                type="password"
                :placeholder="t('users.form.newPasswordPlaceholder')"
                :disabled="loading"
                class="terminal-input h-9 pl-9 font-data"
              />
            </div>
          </div>

          <!-- User Info -->
          <div
            class="flex items-center gap-2 p-2 border border-[var(--silver-200)] dark:border-[var(--silver-700)] bg-[var(--surface-sunken)]"
          >
            <span class="terminal-label">{{ t('users.form.targetUser') }}:</span>
            <span class="font-data text-xs text-[var(--terminal-cyan)]">{{ username }}</span>
          </div>
        </div>

        <!-- Footer -->
        <DialogFooter
          class="border-t border-[var(--silver-200)] dark:border-[var(--silver-700)] p-4 gap-3"
        >
          <Button
            type="button"
            variant="terminal"
            class="font-data text-xs border-[var(--silver-300)] hover:border-[var(--silver-400)]"
            :disabled="loading"
            @click="emit('update:open', false)"
          >
            {{ t('users.actions.cancel') }}
          </Button>
          <Button
            type="submit"
            variant="terminal"
            :disabled="!newPassword || loading"
            class="font-data text-xs bg-[var(--terminal-amber)] hover:bg-[var(--terminal-amber)]/90 text-black"
          >
            <IconLoader2 v-if="loading" class="h-3.5 w-3.5 mr-1.5 animate-spin" />
            <IconArrowRight v-else class="h-3.5 w-3.5 mr-1.5" />
            {{ loading ? t('users.actions.resetting') : t('users.actions.resetPasswordAction') }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>
