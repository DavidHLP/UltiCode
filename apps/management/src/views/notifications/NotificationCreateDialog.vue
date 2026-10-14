<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { Button } from '@ulticode/design-system/ui/button'
import { Input } from '@ulticode/design-system/ui/input'
import { Textarea } from '@ulticode/design-system/ui/textarea'
import { RadioGroup, RadioGroupItem } from '@ulticode/design-system/ui/radio-group'
import { Dialog, DialogContent, DialogFooter } from '@ulticode/design-system/ui/dialog'
import {
  Field,
  FieldGroup,
  FieldLabel,
  FieldSet,
  FieldLegend,
  FieldDescription,
  FieldSeparator,
} from '@ulticode/design-system/ui/field'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  SelectGroup,
} from '@ulticode/design-system/ui/select'
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
    <DialogContent class="sm:max-w-[600px] p-0 overflow-hidden">
      <!-- Page header -->
      <div
        class="flex items-center gap-2 px-4 py-3 font-semibold flex items-center justify-between"
      >
        <span class="font-mono text-sm uppercase tracking-wider">{{
          isEditMode ? t('notifications.dialog.editTitle') : t('notifications.dialog.createTitle')
        }}</span>
      </div>

      <!-- Content -->
      <div class="p-4">
        <p class="text-sm text-muted-foreground mb-4">
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
            class="mb-4 p-3 border border-status-error-mark bg-[color-mix(in_oklch,_var(--status-error-mark)_8%,_transparent)] text-sm"
          >
            <span class="font-mono text-foreground">&gt; ERROR: </span>
            <span class="text-foreground">{{ error }}</span>
          </div>

          <FieldGroup class="max-h-[60vh] overflow-y-auto px-1">
            <FieldSet>
              <FieldLegend>{{ t('notifications.form.messageContent') }}</FieldLegend>
              <FieldDescription>{{
                t('notifications.form.messageContentDescription')
              }}</FieldDescription>
              <FieldGroup class="mt-3">
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
                    class="min-h-[100px] resize-y selection:bg-primary selection:text-primary-foreground"
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
              <FieldGroup class="mt-3">
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
                        <SelectGroup
                          ><SelectItem v-for="type in NOTIFICATION_TYPES" :key="type" :value="type">
                            {{ getNotificationTypeLabel(type, t) }}
                          </SelectItem></SelectGroup
                        >
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
                        <SelectGroup
                          ><SelectItem
                            v-for="category in NOTIFICATION_CATEGORIES"
                            :key="category"
                            :value="category"
                          >
                            {{ getNotificationCategoryLabel(category, t) }}
                          </SelectItem></SelectGroup
                        >
                      </SelectContent>
                    </Select>
                  </Field>
                </div>
              </FieldGroup>
            </FieldSet>

            <FieldSeparator v-if="!isEditMode" />

            <FieldSet v-if="!isEditMode">
              <FieldDescription>{{
                t('notifications.form.targetAudienceDescription')
              }}</FieldDescription>
              <FieldGroup class="mt-3">
                <Field>
                  <RadioGroup v-model="form.target" class="flex flex-col space-y-2">
                    <div
                      class="flex items-center space-x-3 p-2 border border-border dark:border-border rounded-lg hover:border-status-info-mark transition-colors cursor-pointer"
                      :class="{
                        'border-status-info-mark bg-[color-mix(in_oklch,_var(--status-info-mark)_8%,_transparent)]':
                          form.target === 'ALL',
                      }"
                    >
                      <RadioGroupItem :value="'ALL'" id="target-all" />
                      <FieldLabel for="target-all" class="cursor-pointer">
                        {{ t('notifications.form.allUsers') }}
                      </FieldLabel>
                    </div>
                    <div
                      class="flex items-center space-x-3 p-2 border border-border dark:border-border rounded-lg hover:border-status-info-mark transition-colors cursor-pointer"
                      :class="{
                        'border-status-info-mark bg-[color-mix(in_oklch,_var(--status-info-mark)_8%,_transparent)]':
                          form.target === 'USERS',
                      }"
                    >
                      <RadioGroupItem :value="'USERS'" id="target-users" />
                      <FieldLabel for="target-users" class="cursor-pointer">
                        {{ t('notifications.form.specificUsers') }}
                      </FieldLabel>
                    </div>
                  </RadioGroup>
                </Field>

                <Field v-if="form.target === 'USERS'">
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

          <!-- Footer Buttons - Terminal Style -->
          <DialogFooter class="mt-6 flex justify-end gap-2">
            <Button type="button" variant="outline" size="sm" @click="emit('update:open', false)">
              {{ t('common.cancel') }}
            </Button>
            <Button type="submit" variant="outline" size="sm" :disabled="loading">
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
