<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Dialog, DialogContent, DialogFooter } from '@/components/ui/dialog'
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
    <DialogContent class="sm:max-w-[600px] terminal-card p-0 overflow-hidden">
      <!-- Terminal Header -->
      <div class="terminal-card-header flex items-center justify-between">
        <span class="font-data text-sm uppercase tracking-wider">{{
          t('notifications.dialog.createTitle')
        }}</span>
      </div>

      <!-- Content -->
      <div class="p-4">
        <p class="text-sm text-[var(--silver-500)] mb-4">
          {{ t('notifications.dialog.createDescription') }}
        </p>

        <form @submit.prevent="handleSubmit">
          <!-- Error Block - Terminal Style -->
          <div
            v-if="error"
            class="mb-4 p-3 border border-[var(--terminal-red)] bg-[oklch(0.6_0.2_25/0.08)] text-sm"
          >
            <span class="font-data text-[var(--terminal-red)]">&gt; ERROR: </span>
            <span class="text-[var(--foreground)]">{{ error }}</span>
          </div>

          <FieldGroup class="max-h-[60vh] overflow-y-auto px-1">
            <FieldSet>
              <FieldLegend
                class="font-data text-xs uppercase tracking-wider text-[var(--terminal-cyan)]"
                >{{ t('notifications.form.messageContent') }}</FieldLegend
              >
              <FieldDescription class="text-[var(--silver-500)]">{{
                t('notifications.form.messageContentDescription')
              }}</FieldDescription>
              <FieldGroup class="mt-3">
                <Field>
                  <FieldLabel for="notification-title" class="terminal-label">{{
                    t('notifications.form.notificationTitle')
                  }}</FieldLabel>
                  <Input
                    id="notification-title"
                    v-model="form.title"
                    type="text"
                    required
                    :disabled="loading"
                    :placeholder="t('notifications.form.notificationTitlePlaceholder')"
                    class="terminal-input font-data text-sm"
                  />
                </Field>

                <Field>
                  <FieldLabel for="notification-content" class="terminal-label">{{
                    t('notifications.form.notificationContent')
                  }}</FieldLabel>
                  <Textarea
                    id="notification-content"
                    v-model="form.content"
                    required
                    :disabled="loading"
                    :placeholder="t('notifications.form.notificationContentPlaceholder')"
                    rows="4"
                    class="terminal-input font-data text-sm min-h-[100px] resize-y"
                  />
                </Field>
              </FieldGroup>
            </FieldSet>

            <FieldSeparator class="border-[var(--silver-200)] dark:border-[var(--silver-300)]" />

            <FieldSet>
              <FieldLegend
                class="font-data text-xs uppercase tracking-wider text-[var(--terminal-cyan)]"
                >{{ t('notifications.form.classification') }}</FieldLegend
              >
              <FieldDescription class="text-[var(--silver-500)]">{{
                t('notifications.form.classificationDescription')
              }}</FieldDescription>
              <FieldGroup class="mt-3">
                <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <Field>
                    <FieldLabel for="notification-type" class="terminal-label">{{
                      t('notifications.form.type')
                    }}</FieldLabel>
                    <Select v-model="form.type" :disabled="loading">
                      <SelectTrigger
                        id="notification-type"
                        class="terminal-input font-data text-sm"
                      >
                        <SelectValue :placeholder="t('notifications.form.selectType')" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem
                          v-for="type in NotificationType"
                          :key="type"
                          :value="type"
                          class="font-data"
                        >
                          {{ type }}
                        </SelectItem>
                      </SelectContent>
                    </Select>
                  </Field>

                  <Field>
                    <FieldLabel for="notification-category" class="terminal-label">{{
                      t('notifications.form.category')
                    }}</FieldLabel>
                    <Select v-model="form.category" :disabled="loading">
                      <SelectTrigger
                        id="notification-category"
                        class="terminal-input font-data text-sm"
                      >
                        <SelectValue :placeholder="t('notifications.form.selectCategory')" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem
                          v-for="category in NotificationCategory"
                          :key="category"
                          :value="category"
                          class="font-data"
                        >
                          {{ category }}
                        </SelectItem>
                      </SelectContent>
                    </Select>
                  </Field>
                </div>
              </FieldGroup>
            </FieldSet>

            <FieldSeparator class="border-[var(--silver-200)] dark:border-[var(--silver-300)]" />

            <FieldSet>
              <FieldLegend
                class="font-data text-xs uppercase tracking-wider text-[var(--terminal-cyan)]"
                >{{ t('notifications.form.targetAudience') }}</FieldLegend
              >
              <FieldDescription class="text-[var(--silver-500)]">{{
                t('notifications.form.targetAudienceDescription')
              }}</FieldDescription>
              <FieldGroup class="mt-3">
                <Field>
                  <RadioGroup v-model="form.target" class="flex flex-col space-y-2">
                    <div
                      class="flex items-center space-x-3 p-2 border border-[var(--silver-200)] dark:border-[var(--silver-300)] rounded hover:border-[var(--terminal-cyan)] transition-colors cursor-pointer"
                      :class="{
                        'border-[var(--terminal-cyan)] bg-[oklch(0.65_0.15_200/0.08)]':
                          form.target === NotificationTarget.ALL,
                      }"
                    >
                      <RadioGroupItem :value="NotificationTarget.ALL" id="target-all" />
                      <FieldLabel for="target-all" class="font-normal cursor-pointer">
                        {{ t('notifications.form.allUsers') }}
                      </FieldLabel>
                    </div>
                    <div
                      class="flex items-center space-x-3 p-2 border border-[var(--silver-200)] dark:border-[var(--silver-300)] rounded hover:border-[var(--terminal-cyan)] transition-colors cursor-pointer"
                      :class="{
                        'border-[var(--terminal-cyan)] bg-[oklch(0.65_0.15_200/0.08)]':
                          form.target === NotificationTarget.USERS,
                      }"
                    >
                      <RadioGroupItem :value="NotificationTarget.USERS" id="target-users" />
                      <FieldLabel for="target-users" class="font-normal cursor-pointer">
                        {{ t('notifications.form.specificUsers') }}
                      </FieldLabel>
                    </div>
                  </RadioGroup>
                </Field>

                <Field v-if="form.target === NotificationTarget.USERS">
                  <FieldLabel for="notification-userIds" class="terminal-label">{{
                    t('notifications.form.userIds')
                  }}</FieldLabel>
                  <Textarea
                    id="notification-userIds"
                    v-model="form.userIds"
                    :disabled="loading"
                    :placeholder="t('notifications.form.userIdsPlaceholder')"
                    rows="2"
                    class="terminal-input font-data text-sm"
                  />
                </Field>
              </FieldGroup>
            </FieldSet>
          </FieldGroup>

          <!-- Footer Buttons - Terminal Style -->
          <DialogFooter class="mt-6 flex justify-end gap-2">
            <Button
              type="button"
              variant="terminal"
              size="sm"
              class="font-data text-xs border-[var(--silver-300)] hover:border-[var(--silver-400)]"
              @click="emit('update:open', false)"
            >
              {{ t('common.cancel') }}
            </Button>
            <Button
              type="submit"
              variant="terminal"
              size="sm"
              class="font-data text-xs border-[var(--accent-electric)] text-[var(--accent-electric)] hover:bg-[oklch(0.65_0.15_250/0.1)]"
              :disabled="loading"
            >
              <span v-if="loading" class="flex items-center gap-2">
                <span class="animate-spin">⟳</span>
                {{ t('notifications.dialog.sending') }}
              </span>
              <span v-else>
                {{ t('notifications.dialog.sendNotification') }}
              </span>
            </Button>
          </DialogFooter>
        </form>
      </div>
    </DialogContent>
  </Dialog>
</template>
