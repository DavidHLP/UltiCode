<script setup lang="ts">
import { ref, watch } from 'vue'
import { useUsersStore } from '@/stores/admin/users'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
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
    <DialogContent>
      <DialogHeader>
        <DialogTitle>{{ t('users.actions.resetPassword') }}</DialogTitle>
        <DialogDescription>
          {{
            t('users.actions.resetPasswordDescription', {
              username: username || t('users.actions.thisUser'),
            })
          }}
        </DialogDescription>
      </DialogHeader>
      <div class="grid gap-4 py-4">
        <div class="grid gap-2">
          <Label for="new-password">{{ t('users.form.newPassword') }}</Label>
          <Input
            id="new-password"
            v-model="newPassword"
            type="password"
            :placeholder="t('users.form.newPasswordPlaceholder')"
            :disabled="loading"
          />
        </div>
      </div>
      <DialogFooter>
        <Button variant="outline" @click="emit('update:open', false)" :disabled="loading">
          {{ t('users.actions.cancel') }}
        </Button>
        <Button @click="handleReset" :disabled="!newPassword || loading">
          {{ loading ? t('users.actions.resetting') : t('users.actions.resetPasswordAction') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
