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
import { Checkbox } from '@/components/ui/checkbox'
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
      <!-- Basic Information Section -->
      <div class="space-y-4">
        <div class="terminal-comment">// {{ t('problemLists.sections.basicInfo') }}</div>

        <FormField v-slot="{ componentField }" name="name">
          <FormItem>
            <FormLabel class="terminal-label">{{ t('problemLists.form.name') }}</FormLabel>
            <FormControl>
              <Input
                v-bind="componentField"
                :placeholder="t('problemLists.form.namePlaceholder')"
                class="terminal-input h-9"
              />
            </FormControl>
            <FormMessage class="font-data text-xs text-[var(--terminal-red)]" />
          </FormItem>
        </FormField>

        <FormField v-slot="{ componentField }" name="description">
          <FormItem>
            <FormLabel class="terminal-label">{{ t('problemLists.form.description') }}</FormLabel>
            <FormControl>
              <Textarea
                v-bind="componentField"
                :placeholder="t('problemLists.form.descriptionPlaceholder')"
                class="terminal-input min-h-[100px]"
              />
            </FormControl>
            <FormMessage class="font-data text-xs text-[var(--terminal-red)]" />
          </FormItem>
        </FormField>
      </div>

      <div class="terminal-separator" />

      <!-- Visibility & Featured Section -->
      <div class="space-y-4">
        <div class="terminal-comment">// {{ t('problemLists.sections.visibilityFeatured') }}</div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <FormField v-slot="{ value, handleChange }" name="is_public">
            <FormItem>
              <FormLabel class="terminal-label">{{ t('problemLists.form.isPublic') }}</FormLabel>
              <div
                class="flex items-center gap-3 h-9 px-3 border border-[var(--silver-200)] rounded-md bg-[var(--surface-sunken)]"
              >
                <Checkbox
                  :checked="value"
                  @update:checked="handleChange"
                  class="data-[state=checked]:bg-[var(--terminal-green)] data-[state=checked]:border-[var(--terminal-green)]"
                />
                <label class="font-data text-xs">
                  <span
                    :class="value ? 'text-[var(--terminal-green)]' : 'text-[var(--silver-400)]'"
                  >
                    {{ value ? 'PUBLIC' : 'PRIVATE' }}
                  </span>
                </label>
              </div>
              <FormDescription class="text-xs text-[var(--silver-400)]">
                {{ t('problemLists.form.isPublicDescription') }}
              </FormDescription>
            </FormItem>
          </FormField>

          <FormField v-slot="{ value, handleChange }" name="is_featured">
            <FormItem>
              <FormLabel class="terminal-label">{{ t('problemLists.form.isFeatured') }}</FormLabel>
              <div
                class="flex items-center gap-3 h-9 px-3 border border-[var(--silver-200)] rounded-md bg-[var(--surface-sunken)]"
              >
                <Checkbox
                  :checked="value"
                  @update:checked="handleChange"
                  class="data-[state=checked]:bg-[var(--terminal-amber)] data-[state=checked]:border-[var(--terminal-amber)]"
                />
                <label class="font-data text-xs">
                  <span
                    :class="value ? 'text-[var(--terminal-amber)]' : 'text-[var(--silver-400)]'"
                  >
                    {{ value ? 'FEATURED' : 'STANDARD' }}
                  </span>
                </label>
              </div>
              <FormDescription class="text-xs text-[var(--silver-400)]">
                {{ t('problemLists.form.isFeaturedDescription') }}
              </FormDescription>
            </FormItem>
          </FormField>
        </div>
      </div>

      <!-- Banner Settings Section (conditionally visible) -->
      <template v-if="form.values.is_featured">
        <div class="terminal-separator" />

        <div class="space-y-4">
          <div class="terminal-comment">// {{ t('problemLists.sections.bannerSettings') }}</div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <FormField v-slot="{ componentField }" name="banner_tag">
              <FormItem>
                <FormLabel class="terminal-label">{{ t('problemLists.form.bannerTag') }}</FormLabel>
                <FormControl>
                  <Input
                    v-bind="componentField"
                    :placeholder="t('problemLists.form.bannerTagPlaceholder')"
                    class="terminal-input h-9"
                  />
                </FormControl>
                <FormDescription class="text-xs text-[var(--silver-400)]">
                  {{ t('problemLists.form.bannerTagDescription') }}
                </FormDescription>
                <FormMessage class="font-data text-xs text-[var(--terminal-red)]" />
              </FormItem>
            </FormField>

            <FormField v-slot="{ componentField }" name="banner_theme">
              <FormItem>
                <FormLabel class="terminal-label">{{
                  t('problemLists.form.bannerTheme')
                }}</FormLabel>
                <Select v-bind="componentField">
                  <FormControl>
                    <SelectTrigger class="terminal-input h-9 font-data text-xs">
                      <SelectValue :placeholder="t('problemLists.form.bannerThemePlaceholder')" />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    <SelectItem
                      v-for="theme in bannerThemes"
                      :key="theme.value"
                      :value="theme.value"
                      class="font-data text-xs"
                    >
                      {{ theme.label }}
                    </SelectItem>
                  </SelectContent>
                </Select>
                <FormMessage class="font-data text-xs text-[var(--terminal-red)]" />
              </FormItem>
            </FormField>

            <FormField v-slot="{ componentField }" name="banner_order">
              <FormItem>
                <FormLabel class="terminal-label">{{ t('problemLists.form.sortOrder') }}</FormLabel>
                <FormControl>
                  <Input type="number" v-bind="componentField" class="terminal-input h-9" />
                </FormControl>
                <FormDescription class="text-xs text-[var(--silver-400)]">
                  {{ t('problemLists.form.sortOrderDescription') }}
                </FormDescription>
                <FormMessage class="font-data text-xs text-[var(--terminal-red)]" />
              </FormItem>
            </FormField>
          </div>
        </div>
      </template>

      <div class="terminal-separator" />

      <!-- Submit Button -->
      <div class="flex justify-end gap-2">
        <Button
          type="submit"
          :disabled="loading"
          variant="terminal"
          class="font-data text-xs bg-[var(--accent-electric)] hover:bg-[var(--accent-electric)]/90"
        >
          <span v-if="loading" class="animate-pulse">{{ t('problemLists.form.saving') }}</span>
          <span v-else>{{ t('problemLists.form.saveChanges') }}</span>
        </Button>
      </div>
    </form>
  </div>
</template>
