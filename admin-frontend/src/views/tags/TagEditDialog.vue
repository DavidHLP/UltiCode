<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import * as z from 'zod'
import { toast } from 'vue-sonner'
import { IconLoader, IconTag } from '@tabler/icons-vue'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Button } from '@/components/ui/button'
import { useTagsStore } from '@/stores/admin/tags'
import { TagType, type Tag } from '@/api/admin/tags'

const { t } = useI18n()

const props = defineProps<{
  open: boolean
  tagToEdit: Tag | null
  tagType: TagType
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const tagsStore = useTagsStore()
const loading = ref(false)

const formSchema = toTypedSchema(
  z.object({
    name: z.string().min(1, t('tags.form.nameRequired')).max(50, t('tags.form.nameTooLong')),
    slug: z.string().optional(),
    description: z.string().optional(),
    color: z.string().optional(),
  }),
)

const form = useForm({
  validationSchema: formSchema,
})

watch(
  () => props.tagToEdit,
  (tag) => {
    if (tag) {
      form.setValues({
        name: tag.name,
        slug: tag.slug,
        description: tag.description,
        color: tag.color,
      })
    } else {
      form.resetForm()
    }
  },
  { immediate: true },
)

const onSubmit = form.handleSubmit(async (values) => {
  loading.value = true
  try {
    if (props.tagToEdit) {
      await tagsStore.updateTag(props.tagToEdit.id, { ...values, type: props.tagType })
      toast.success(t('tags.toast.updatedSuccessfully'))
    } else {
      await tagsStore.createTag({ ...values, type: props.tagType })
      toast.success(t('tags.toast.createdSuccessfully'))
    }
    emit('update:open', false)
    emit('success')
  } catch (error) {
    toast.error(props.tagToEdit ? t('tags.toast.failedToUpdate') : t('tags.toast.failedToCreate'))
    console.error(error)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <Dialog :open="open" @update:open="$emit('update:open', $event)">
    <DialogContent class="sm:max-w-[425px]">
      <DialogHeader>
        <DialogTitle class="flex items-center gap-2">
          <IconTag class="h-5 w-5" />
          {{ tagToEdit ? t('tags.form.editTitle') : t('tags.form.createTitle') }}
        </DialogTitle>
        <DialogDescription>
          {{ tagToEdit ? t('tags.form.editDescription') : t('tags.form.createDescription') }}
        </DialogDescription>
      </DialogHeader>

      <form @submit="onSubmit" class="grid gap-4 py-4">
        <FormField v-slot="{ componentField }" name="name">
          <FormItem>
            <FormLabel>{{ t('tags.form.name') }}</FormLabel>
            <FormControl>
              <Input v-bind="componentField" :placeholder="t('tags.form.namePlaceholder')" />
            </FormControl>
            <FormMessage />
          </FormItem>
        </FormField>

        <FormField v-slot="{ componentField }" name="slug">
          <FormItem>
            <FormLabel>{{ t('tags.form.slug') }}</FormLabel>
            <FormControl>
              <Input v-bind="componentField" :placeholder="t('tags.form.slugPlaceholder')" />
            </FormControl>
            <FormMessage />
          </FormItem>
        </FormField>

        <FormField v-slot="{ componentField }" name="color">
          <FormItem>
            <FormLabel>{{ t('tags.form.colorHex') }}</FormLabel>
            <FormControl>
              <div class="flex gap-2">
                <Input type="color" v-bind="componentField" class="w-12 p-1 h-10" />
                <Input v-bind="componentField" :placeholder="t('tags.form.colorPlaceholder')" />
              </div>
            </FormControl>
            <FormMessage />
          </FormItem>
        </FormField>

        <FormField v-slot="{ componentField }" name="description">
          <FormItem>
            <FormLabel>{{ t('tags.form.description') }}</FormLabel>
            <FormControl>
              <Textarea
                v-bind="componentField"
                :placeholder="t('tags.form.descriptionPlaceholder')"
              />
            </FormControl>
            <FormMessage />
          </FormItem>
        </FormField>

        <DialogFooter>
          <Button type="button" variant="outline" @click="$emit('update:open', false)">
            {{ t('common.cancel') }}
          </Button>
          <Button type="submit" :disabled="loading">
            <IconLoader v-if="loading" class="mr-2 h-4 w-4 animate-spin" />
            {{ tagToEdit ? t('tags.form.saveChanges') : t('tags.form.createTag') }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>
