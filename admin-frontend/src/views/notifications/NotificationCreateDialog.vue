<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Field,
  FieldGroup,
  FieldLabel,
  FieldSet,
  FieldLegend,
  FieldDescription,
  FieldSeparator,
} from '@/components/ui/field'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useNotificationsStore } from '@/stores/admin/notifications'
import {
  NotificationType,
  NotificationCategory,
  NotificationTarget,
} from '@/api/admin/notifications'

const { t } = useI18n()

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const store = useNotificationsStore()
const loading = ref(false)
const error = ref('')

const defaultForm = {
  title: '',
  content: '',
  type: NotificationType.SYSTEM,
  category: NotificationCategory.SYSTEM,
  target: NotificationTarget.ALL,
  userIds: '',
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
    const payload = {
      title: form.value.title,
      content: form.value.content,
      type: form.value.type,
      category: form.value.category,
      target: form.value.target,
      userIds:
        form.value.target === NotificationTarget.USERS
          ? form.value.userIds
              .split(',')
              .map((id) => id.trim())
              .filter(Boolean)
          : undefined,
    }

    if (
      form.value.target === NotificationTarget.USERS &&
      (!payload.userIds || payload.userIds.length === 0)
    ) {
      error.value = t('notifications.form.atLeastOneUserId')
      loading.value = false
      return
    }

    await store.createNotification(payload)
    toast.success(t('notifications.toast.sentSuccessfully'))
    emit('success')
    emit('update:open', false)
  } catch (err: unknown) {
    error.value =
      (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
      t('notifications.toast.failedToSend')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent class="sm:max-w-[600px]">
      <DialogHeader>
        <DialogTitle>{{ t('notifications.dialog.createTitle') }}</DialogTitle>
        <DialogDescription>{{ t('notifications.dialog.createDescription') }}</DialogDescription>
      </DialogHeader>

      <form @submit.prevent="handleSubmit">
        <div
          v-if="error"
          class="mb-4 p-3 text-sm text-red-600 bg-red-50 border border-red-200 rounded-md"
        >
          {{ error }}
        </div>

        <FieldGroup class="max-h-[60vh] overflow-y-auto px-1">
          <FieldSet>
            <FieldLegend>{{ t('notifications.form.messageContent') }}</FieldLegend>
            <FieldDescription>{{
              t('notifications.form.messageContentDescription')
            }}</FieldDescription>
            <FieldGroup>
              <Field>
                <FieldLabel for="notification-title">{{
                  t('notifications.form.notificationTitle')
                }}</FieldLabel>
                <Input
                  id="notification-title"
                  v-model="form.title"
                  type="text"
                  required
                  :disabled="loading"
                  :placeholder="t('notifications.form.notificationTitlePlaceholder')"
                />
              </Field>

              <Field>
                <FieldLabel for="notification-content">{{
                  t('notifications.form.notificationContent')
                }}</FieldLabel>
                <Textarea
                  id="notification-content"
                  v-model="form.content"
                  required
                  :disabled="loading"
                  :placeholder="t('notifications.form.notificationContentPlaceholder')"
                  rows="4"
                />
              </Field>
            </FieldGroup>
          </FieldSet>

          <FieldSeparator />

          <FieldSet>
            <FieldLegend>{{ t('notifications.form.classification') }}</FieldLegend>
            <FieldDescription>{{
              t('notifications.form.classificationDescription')
            }}</FieldDescription>
            <FieldGroup>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Field>
                  <FieldLabel for="notification-type">{{
                    t('notifications.form.type')
                  }}</FieldLabel>
                  <Select v-model="form.type" :disabled="loading">
                    <SelectTrigger id="notification-type">
                      <SelectValue :placeholder="t('notifications.form.selectType')" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem v-for="type in NotificationType" :key="type" :value="type">
                        {{ type }}
                      </SelectItem>
                    </SelectContent>
                  </Select>
                </Field>

                <Field>
                  <FieldLabel for="notification-category">{{
                    t('notifications.form.category')
                  }}</FieldLabel>
                  <Select v-model="form.category" :disabled="loading">
                    <SelectTrigger id="notification-category">
                      <SelectValue :placeholder="t('notifications.form.selectCategory')" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem
                        v-for="category in NotificationCategory"
                        :key="category"
                        :value="category"
                      >
                        {{ category }}
                      </SelectItem>
                    </SelectContent>
                  </Select>
                </Field>
              </div>
            </FieldGroup>
          </FieldSet>

          <FieldSeparator />

          <FieldSet>
            <FieldLegend>{{ t('notifications.form.targetAudience') }}</FieldLegend>
            <FieldDescription>{{
              t('notifications.form.targetAudienceDescription')
            }}</FieldDescription>
            <FieldGroup>
              <Field>
                <RadioGroup v-model="form.target" class="flex flex-col space-y-2">
                  <div class="flex items-center space-x-3">
                    <RadioGroupItem :value="NotificationTarget.ALL" id="target-all" />
                    <FieldLabel for="target-all" class="font-normal cursor-pointer">
                      {{ t('notifications.form.allUsers') }}
                    </FieldLabel>
                  </div>
                  <div class="flex items-center space-x-3">
                    <RadioGroupItem :value="NotificationTarget.USERS" id="target-users" />
                    <FieldLabel for="target-users" class="font-normal cursor-pointer">
                      {{ t('notifications.form.specificUsers') }}
                    </FieldLabel>
                  </div>
                </RadioGroup>
              </Field>

              <Field v-if="form.target === NotificationTarget.USERS">
                <FieldLabel for="notification-userIds">{{
                  t('notifications.form.userIds')
                }}</FieldLabel>
                <Textarea
                  id="notification-userIds"
                  v-model="form.userIds"
                  :disabled="loading"
                  :placeholder="t('notifications.form.userIdsPlaceholder')"
                  rows="2"
                />
              </Field>
            </FieldGroup>
          </FieldSet>
        </FieldGroup>

        <DialogFooter class="mt-6">
          <Button type="button" variant="outline" @click="emit('update:open', false)">
            {{ t('common.cancel') }}
          </Button>
          <Button type="submit" :disabled="loading">
            {{
              loading
                ? t('notifications.dialog.sending')
                : t('notifications.dialog.sendNotification')
            }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>
