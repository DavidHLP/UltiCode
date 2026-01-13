<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { z } from 'zod'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { toast } from 'vue-sonner'
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
  FormDescription,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useAdminProblemListsStore } from '@/stores/admin/problem-lists'
import type {
  ProblemListDetail,
  CreateProblemListDto,
  UpdateProblemListDto,
} from '@/api/admin/problem-lists'

const props = defineProps<{
  list: ProblemListDetail | null
  mode: 'create' | 'edit'
}>()

const emit = defineEmits<{
  (e: 'success', id: string): void
}>()

const { t } = useI18n()
const store = useAdminProblemListsStore()
const loading = ref(false)

const formSchema = toTypedSchema(
  z.object({
    name: z.string().min(1, t('problemLists.form.validation.nameRequired')).max(100),
    description: z.string().optional(),
    is_public: z.boolean(),
    is_featured: z.boolean(),
    banner_tag: z.string().optional(),
    banner_theme: z.string().optional(),
    banner_order: z.number().int().optional(),
  }),
)

const form = useForm({
  validationSchema: formSchema,
  initialValues: {
    name: props.list?.name || '',
    description: props.list?.description || '',
    is_public: props.list?.is_public ?? true,
    is_featured: props.list?.is_featured ?? false,
    banner_tag: props.list?.banner_tag || '',
    banner_theme: props.list?.banner_theme || 'blue',
    banner_order: props.list?.banner_order || 0,
  },
})

async function onSubmit(values: Record<string, unknown>) {
  loading.value = true
  try {
    if (props.mode === 'create') {
      const newList = await store.createList(values as unknown as CreateProblemListDto)
      toast.success(t('problemLists.toast.createdSuccess'))
      emit('success', newList.id)
    } else if (props.list) {
      await store.updateList(props.list.id, values as unknown as UpdateProblemListDto)
      toast.success(t('problemLists.toast.updatedSuccess'))
    }
  } catch {
    toast.error(t('problemLists.toast.createFailed'))
  } finally {
    loading.value = false
  }
}

const bannerThemes = [
  { value: 'blue', label: t('problemLists.themes.blue') },
  { value: 'green', label: t('problemLists.themes.green') },
  { value: 'purple', label: t('problemLists.themes.purple') },
  { value: 'orange', label: t('problemLists.themes.orange') },
  { value: 'red', label: t('problemLists.themes.red') },
]
</script>

<template>
  <div class="space-y-6 max-w-2xl">
    <form @submit="form.handleSubmit(onSubmit)" class="space-y-6">
      <FormField v-slot="{ componentField }" name="name">
        <FormItem>
          <FormLabel>{{ t('problemLists.form.name') }}</FormLabel>
          <FormControl>
            <Input v-bind="componentField" :placeholder="t('problemLists.form.namePlaceholder')" />
          </FormControl>
          <FormMessage />
        </FormItem>
      </FormField>

      <FormField v-slot="{ componentField }" name="description">
        <FormItem>
          <FormLabel>{{ t('problemLists.form.description') }}</FormLabel>
          <FormControl>
            <Textarea
              v-bind="componentField"
              :placeholder="t('problemLists.form.descriptionPlaceholder')"
              class="h-32"
            />
          </FormControl>
          <FormMessage />
        </FormItem>
      </FormField>

      <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
        <FormField v-slot="{ value, handleChange }" name="is_public">
          <FormItem class="flex flex-row items-center justify-between rounded-lg border p-4">
            <div class="space-y-0.5">
              <FormLabel class="text-base">{{ t('problemLists.form.isPublic') }}</FormLabel>
              <FormDescription>{{ t('problemLists.form.isPublicDescription') }}</FormDescription>
            </div>
            <FormControl>
              <Switch :checked="value" @update:checked="handleChange" />
            </FormControl>
          </FormItem>
        </FormField>

        <FormField v-slot="{ value, handleChange }" name="is_featured">
          <FormItem class="flex flex-row items-center justify-between rounded-lg border p-4">
            <div class="space-y-0.5">
              <FormLabel class="text-base">{{ t('problemLists.form.isFeatured') }}</FormLabel>
              <FormDescription>{{ t('problemLists.form.isFeaturedDescription') }}</FormDescription>
            </div>
            <FormControl>
              <Switch :checked="value" @update:checked="handleChange" />
            </FormControl>
          </FormItem>
        </FormField>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-2 gap-6" v-if="form.values.is_featured">
        <FormField v-slot="{ componentField }" name="banner_tag">
          <FormItem>
            <FormLabel>{{ t('problemLists.form.bannerTag') }}</FormLabel>
            <FormControl>
              <Input v-bind="componentField" :placeholder="t('problemLists.form.bannerTagPlaceholder')" />
            </FormControl>
            <FormDescription>{{ t('problemLists.form.bannerTagDescription') }}</FormDescription>
            <FormMessage />
          </FormItem>
        </FormField>

        <FormField v-slot="{ componentField }" name="banner_theme">
          <FormItem>
            <FormLabel>{{ t('problemLists.form.bannerTheme') }}</FormLabel>
            <Select v-bind="componentField">
              <FormControl>
                <SelectTrigger>
                  <SelectValue :placeholder="t('problemLists.form.bannerThemePlaceholder')" />
                </SelectTrigger>
              </FormControl>
              <SelectContent>
                <SelectItem v-for="theme in bannerThemes" :key="theme.value" :value="theme.value">
                  {{ theme.label }}
                </SelectItem>
              </SelectContent>
            </Select>
            <FormMessage />
          </FormItem>
        </FormField>

        <FormField v-slot="{ componentField }" name="banner_order">
          <FormItem>
            <FormLabel>{{ t('problemLists.form.sortOrder') }}</FormLabel>
            <FormControl>
              <Input type="number" v-bind="componentField" />
            </FormControl>
            <FormDescription>{{ t('problemLists.form.sortOrderDescription') }}</FormDescription>
            <FormMessage />
          </FormItem>
        </FormField>
      </div>

      <div class="flex justify-end gap-2">
        <Button type="submit" :disabled="loading">
          {{ loading ? t('problemLists.form.saving') : t('problemLists.form.saveChanges') }}
        </Button>
      </div>
    </form>
  </div>
</template>
