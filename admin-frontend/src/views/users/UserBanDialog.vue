<script setup lang="ts">
import { ref, watch } from 'vue'
import { useUsersStore } from '@/stores/admin/users'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
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
const banReason = ref('')
const loading = ref(false)

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      banReason.value = ''
    }
  },
)

async function handleBan() {
  if (!props.userId || !banReason.value) return
  loading.value = true
  try {
    await usersStore.banUser(props.userId, banReason.value)
    emit('success')
    emit('update:open', false)
  } catch {
    toast.error(t('users.toast.banFailed'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>{{ t('users.actions.banUser') }}</DialogTitle>
        <DialogDescription>
          {{
            t('users.actions.banUserDescription', {
              username: username || t('users.actions.thisUser'),
            })
          }}
        </DialogDescription>
      </DialogHeader>
      <div class="grid gap-4 py-4">
        <div class="grid gap-2">
          <Label for="ban-reason">{{ t('users.form.banReason') }}</Label>
          <Textarea
            id="ban-reason"
            v-model="banReason"
            :placeholder="t('users.form.banReasonPlaceholder')"
            :disabled="loading"
          />
        </div>
      </div>
      <DialogFooter>
        <Button variant="outline" @click="emit('update:open', false)" :disabled="loading">
          {{ t('users.actions.cancel') }}
        </Button>
        <Button variant="destructive" @click="handleBan" :disabled="!banReason || loading">
          {{ loading ? t('users.actions.banning') : t('users.actions.confirmBan') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
