<script setup lang="ts">
import { ref, watch, computed } from 'vue'
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
  NOTIFICATION_TYPES,
  NOTIFICATION_CATEGORIES,
  type NotificationType,
  type NotificationCategory,
  type NotificationTarget,
  type SystemAnnouncement,
} from '@/api/admin/notifications'
import { getNotificationCategoryLabel, getNotificationTypeLabel } from './notificationLabels'

const { t } = useI18n()

const props = defineProps<{
  open: boolean
  notificationToEdit?: SystemAnnouncement | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const isEditMode = computed(() => !!props.notificationToEdit)

const store = useNotificationsStore()
const loading = ref(false)
const error = ref('')

const defaultForm = {
  title: '',
  content: '',
  type: 'SYSTEM' as NotificationType,
  category: 'SYSTEM' as NotificationCategory,
  target: 'ALL' as NotificationTarget,
  userIds: '',
}

const form = ref({ ...defaultForm })

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      error.value = ''
      if (props.notificationToEdit) {
        form.value = {
          title: props.notificationToEdit.title,
          content: props.notificationToEdit.content,
          type: props.notificationToEdit.type,
          category: props.notificationToEdit.category ?? ('SYSTEM' as NotificationCategory),
          target: 'ALL' as NotificationTarget,
          userIds: '',
        }
      } else {
        form.value = { ...defaultForm }
      }
    }
  },
)

async function handleSubmit() {
  error.value = ''
  loading.value = true

  try {
    if (isEditMode.value && props.notificationToEdit) {
      await store.updateNotification(props.notificationToEdit.id, {
        title: form.value.title,
        content: form.value.content,
        type: form.value.type,
        category: form.value.category,
      })
      toast.success(t('notifications.toast.updateSuccess'))
    } else {
      const payload = {
        title: form.value.title,
        content: form.value.content,
        type: form.value.type,
        category: form.value.category,
        target: form.value.target,
        userIds:
          form.value.target === 'USERS'
            ? form.value.userIds
                .split(',')
                .map((id) => id.trim())
                .filter(Boolean)
            : undefined,
      }

      if (form.value.target === 'USERS' && (!payload.userIds || payload.userIds.length === 0)) {
        error.value = t('notifications.form.atLeastOneUserId')
        loading.value = false
        return
      }

      await store.createNotification(payload)
      toast.success(t('notifications.toast.sentSuccessfully'))
    }

    emit('success')
    emit('update:open', false)
  } catch (err: unknown) {
    error.value =
      (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
      (isEditMode.value
        ? t('notifications.toast.updateFailed')
        : t('notifications.toast.failedToSend'))
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
          isEditMode ? t('notifications.dialog.editTitle') : t('notifications.dialog.createTitle')
        }}</span>
      </div>

      <!-- Content -->
      <div class="p-4">
        <p class="text-sm text-[var(--silver-500)] mb-4">
          {{
            isEditMode
              ? t('notifications.dialog.editDescription')
              : t('notifications.dialog.createDescription')
          }}
        </p>

        <form @submit.prevent="handleSubmit">
          <!-- Error Block - Terminal Style -->
          <div
            v-if="error"
            class="mb-4 p-3 border border-[var(--terminal-red)] bg-[color-mix(in_oklch,_var(--terminal-red)_8%,_transparent)] text-sm"
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
                    variant="terminal"
                    required
                    :disabled="loading"
                    :placeholder="t('notifications.form.notificationTitlePlaceholder')"
                    class="font-data text-sm"
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
                    class="terminal-input font-data text-sm min-h-[100px] resize-y selection:bg-[var(--accent-electric)] selection:text-[var(--solarized-base3)]"
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
                          v-for="type in NOTIFICATION_TYPES"
                          :key="type"
                          :value="type"
                          class="font-data"
                        >
                          {{ getNotificationTypeLabel(type, t) }}
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
                          v-for="category in NOTIFICATION_CATEGORIES"
                          :key="category"
                          :value="category"
                          class="font-data"
                        >
                          {{ getNotificationCategoryLabel(category, t) }}
                        </SelectItem>
                      </SelectContent>
                    </Select>
                  </Field>
                </div>
              </FieldGroup>
            </FieldSet>

            <FieldSeparator
              v-if="!isEditMode"
              class="border-[var(--silver-200)] dark:border-[var(--silver-300)]"
            />

            <FieldSet v-if="!isEditMode">
              <FieldDescription class="text-[var(--silver-500)]">{{
                t('notifications.form.targetAudienceDescription')
              }}</FieldDescription>
              <FieldGroup class="mt-3">
                <Field>
                  <RadioGroup v-model="form.target" class="flex flex-col space-y-2">
                    <div
                      class="flex items-center space-x-3 p-2 border border-[var(--silver-200)] dark:border-[var(--silver-300)] rounded-none hover:border-[var(--terminal-cyan)] transition-colors cursor-pointer"
                      :class="{
                        'border-[var(--terminal-cyan)] bg-[color-mix(in_oklch,_var(--terminal-cyan)_8%,_transparent)]':
                          form.target === 'ALL',
                      }"
                    >
                      <RadioGroupItem :value="'ALL'" id="target-all" />
                      <FieldLabel for="target-all" class="font-normal cursor-pointer">
                        {{ t('notifications.form.allUsers') }}
                      </FieldLabel>
                    </div>
                    <div
                      class="flex items-center space-x-3 p-2 border border-[var(--silver-200)] dark:border-[var(--silver-300)] rounded-none hover:border-[var(--terminal-cyan)] transition-colors cursor-pointer"
                      :class="{
                        'border-[var(--terminal-cyan)] bg-[color-mix(in_oklch,_var(--terminal-cyan)_8%,_transparent)]':
                          form.target === 'USERS',
                      }"
                    >
                      <RadioGroupItem :value="'USERS'" id="target-users" />
                      <FieldLabel for="target-users" class="font-normal cursor-pointer">
                        {{ t('notifications.form.specificUsers') }}
                      </FieldLabel>
                    </div>
                  </RadioGroup>
                </Field>

                <Field v-if="form.target === 'USERS'">
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
              class="font-data text-xs border-[var(--accent-electric)] text-[var(--accent-electric)] hover:bg-[color-mix(in_oklch,_var(--accent-electric)_10%,_transparent)]"
              :disabled="loading"
            >
              <span v-if="loading" class="flex items-center gap-2">
                <span class="animate-spin">⟳</span>
                {{
                  isEditMode ? t('notifications.dialog.saving') : t('notifications.dialog.sending')
                }}
              </span>
              <span v-else>
                {{
                  isEditMode
                    ? t('notifications.dialog.saveChanges')
                    : t('notifications.dialog.sendNotification')
                }}
              </span>
            </Button>
          </DialogFooter>
        </form>
      </div>
    </DialogContent>
  </Dialog>
</template>
