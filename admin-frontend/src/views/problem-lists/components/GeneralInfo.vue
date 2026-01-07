<script setup lang="ts">
import { ref } from 'vue'
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

const store = useAdminProblemListsStore()
const loading = ref(false)

const formSchema = toTypedSchema(
  z.object({
    name: z.string().min(1, 'Name is required').max(100),
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
      toast.success('List created successfully')
      emit('success', newList.id)
    } else if (props.list) {
      await store.updateList(props.list.id, values as unknown as UpdateProblemListDto)
      toast.success('List updated successfully')
    }
  } catch {
    toast.error('Failed to save list')
  } finally {
    loading.value = false
  }
}

const bannerThemes = [
  { value: 'blue', label: 'Blue' },
  { value: 'green', label: 'Green' },
  { value: 'purple', label: 'Purple' },
  { value: 'orange', label: 'Orange' },
  { value: 'red', label: 'Red' },
]
</script>

<template>
  <div class="space-y-6 max-w-2xl">
    <form @submit="form.handleSubmit(onSubmit)" class="space-y-6">
      <FormField v-slot="{ componentField }" name="name">
        <FormItem>
          <FormLabel>Name</FormLabel>
          <FormControl>
            <Input v-bind="componentField" placeholder="e.g. Top 100 Dynamic Programming" />
          </FormControl>
          <FormMessage />
        </FormItem>
      </FormField>

      <FormField v-slot="{ componentField }" name="description">
        <FormItem>
          <FormLabel>Description</FormLabel>
          <FormControl>
            <Textarea
              v-bind="componentField"
              placeholder="Describe what this list is about..."
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
              <FormLabel class="text-base">Public</FormLabel>
              <FormDescription> Make this list visible to all users </FormDescription>
            </div>
            <FormControl>
              <Switch :checked="value" @update:checked="handleChange" />
            </FormControl>
          </FormItem>
        </FormField>

        <FormField v-slot="{ value, handleChange }" name="is_featured">
          <FormItem class="flex flex-row items-center justify-between rounded-lg border p-4">
            <div class="space-y-0.5">
              <FormLabel class="text-base">Featured</FormLabel>
              <FormDescription> Show this list on the home page </FormDescription>
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
            <FormLabel>Banner Tag</FormLabel>
            <FormControl>
              <Input v-bind="componentField" placeholder="e.g. POPULAR" />
            </FormControl>
            <FormDescription>Small tag shown on the banner card</FormDescription>
            <FormMessage />
          </FormItem>
        </FormField>

        <FormField v-slot="{ componentField }" name="banner_theme">
          <FormItem>
            <FormLabel>Banner Theme</FormLabel>
            <Select v-bind="componentField">
              <FormControl>
                <SelectTrigger>
                  <SelectValue placeholder="Select a theme" />
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
            <FormLabel>Sort Order</FormLabel>
            <FormControl>
              <Input type="number" v-bind="componentField" />
            </FormControl>
            <FormDescription>Order in featured lists section (lower first)</FormDescription>
            <FormMessage />
          </FormItem>
        </FormField>
      </div>

      <div class="flex justify-end gap-2">
        <Button type="submit" :disabled="loading">
          {{ loading ? 'Saving...' : 'Save Changes' }}
        </Button>
      </div>
    </form>
  </div>
</template>
