<script setup lang="ts">
import { FieldLabel } from '@ulticode/design-system/ui/field'

import { ref, watch } from 'vue'
import { useUsersStore } from '@/stores/admin/users'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import {
  ArrowRight as IconArrowRight,
  Loader2 as IconLoader2,
  Lock as IconLock,
  ShieldCheck as IconShieldCheck,
} from '@lucide/vue'

import { Button } from '@ulticode/design-system/ui/button'
import { Input } from '@ulticode/design-system/ui/input'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@ulticode/design-system/ui/dialog'

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
    <DialogContent class="sm:max-w-[420px] p-0 gap-0">
      <!-- Page header -->
      <DialogHeader class="p-4">
        <div class="flex items-center gap-3">
          <DialogTitle>
            {{ t('users.actions.resetPassword') }}
          </DialogTitle>
        </div>
        <DialogDescription class="mt-1">
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
            class="flex items-start gap-3 p-3 border border-status-warning-mark bg-[color-mix(in_oklch,_var(--status-warning-mark)_8%,_transparent)]"
          >
            <IconShieldCheck class="h-4 w-4 text-status-warning-mark shrink-0 mt-0.5" />
            <div class="space-y-1">
              <p class="font-mono text-xs text-foreground uppercase tracking-wider">
                Security Notice
              </p>
              <p class="text-xs text-muted-foreground">
                {{ t('users.actions.resetPasswordWarning') }}
              </p>
            </div>
          </div>

          <!-- Password Field -->
          <div class="space-y-1.5">
            <FieldLabel class="block">{{ t('users.form.newPassword') }}</FieldLabel>
            <div class="relative">
              <IconLock
                class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground"
              />
              <Input
                v-model="newPassword"
                type="password"
                :placeholder="t('users.form.newPasswordPlaceholder')"
                :disabled="loading"
                class="pl-9"
              />
            </div>
          </div>

          <!-- User Info -->
          <div
            class="flex items-center gap-2 p-2 border border-border dark:border-foreground bg-muted"
          >
            <span class="text-sm font-medium">{{ t('users.form.targetUser') }}:</span>
            <span class="font-mono text-xs text-foreground">{{ username }}</span>
          </div>
        </div>

        <!-- Footer -->
        <DialogFooter class="p-4 gap-3">
          <Button
            type="button"
            variant="outline"
            :disabled="loading"
            @click="emit('update:open', false)"
          >
            {{ t('users.actions.cancel') }}
          </Button>
          <Button type="submit" variant="outline" :disabled="!newPassword || loading">
            <IconLoader2 v-if="loading" class="h-3.5 w-3.5 mr-1.5 animate-spin" />
            <IconArrowRight v-else class="h-3.5 w-3.5 mr-1.5" />
            {{ loading ? t('users.actions.resetting') : t('users.actions.resetPasswordAction') }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>
