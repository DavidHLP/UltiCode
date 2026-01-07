<script setup lang="ts">
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { IconArrowLeft, IconSend } from '@tabler/icons-vue'
import { useForm } from 'vee-validate'
import * as z from 'zod'
import { toTypedSchema } from '@vee-validate/zod'

import { Button } from '@/components/ui/button'
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { useNotificationsStore } from '@/stores/admin/notifications'
import { NotificationType, NotificationCategory, NotificationTarget } from '@/api/admin/notifications'

const router = useRouter()
const store = useNotificationsStore()

const formSchema = toTypedSchema(z.object({
  title: z.string().min(1, 'Title is required'),
  content: z.string().min(1, 'Content is required'),
  type: z.nativeEnum(NotificationType),
  category: z.nativeEnum(NotificationCategory).optional(),
  target: z.nativeEnum(NotificationTarget),
  userIds: z.string().optional(), // Comma separated string for input
}))

const form = useForm({
  validationSchema: formSchema,
  initialValues: {
    type: NotificationType.SYSTEM,
    category: NotificationCategory.SYSTEM,
    target: NotificationTarget.ALL,
    userIds: '',
  },
})

const onSubmit = form.handleSubmit(async (values) => {
  try {
    const payload = {
      ...values,
      userIds: values.target === NotificationTarget.USERS
        ? values.userIds?.split(',').map(id => id.trim()).filter(Boolean)
        : undefined
    }

    if (values.target === NotificationTarget.USERS && (!payload.userIds || payload.userIds.length === 0)) {
      form.setErrors({ userIds: 'At least one User ID is required' })
      return
    }

    await store.createNotification(payload)
    toast.success('Notification sent successfully')
    router.push('/notifications')
  } catch {
    toast.error('Failed to send notification')
  }
})
</script>

<template>
  <div class="flex flex-col gap-4 p-4 lg:p-6 h-full">
    <div class="flex items-center gap-4">
      <Button variant="ghost" size="icon" @click="router.back()">
        <IconArrowLeft class="h-4 w-4" />
      </Button>
      <div>
        <h2 class="text-2xl font-semibold tracking-tight">New Notification</h2>
        <p class="text-sm text-muted-foreground">
          Create and send a notification to users.
        </p>
      </div>
    </div>

    <div class="mx-auto w-full max-w-2xl">
      <form @submit="onSubmit" class="space-y-8">
        <FormField v-slot="{ componentField }" name="title">
          <FormItem>
            <FormLabel>Title</FormLabel>
            <FormControl>
              <Input v-bind="componentField" placeholder="Notification title" />
            </FormControl>
            <FormMessage />
          </FormItem>
        </FormField>

        <FormField v-slot="{ componentField }" name="content">
          <FormItem>
            <FormLabel>Content</FormLabel>
            <FormControl>
              <Textarea v-bind="componentField" placeholder="Notification content..." rows="5" />
            </FormControl>
            <FormMessage />
          </FormItem>
        </FormField>

        <div class="grid grid-cols-2 gap-4">
          <FormField v-slot="{ componentField }" name="type">
            <FormItem>
              <FormLabel>Type</FormLabel>
              <Select v-bind="componentField">
                <FormControl>
                  <SelectTrigger>
                    <SelectValue placeholder="Select type" />
                  </SelectTrigger>
                </FormControl>
                <SelectContent>
                  <SelectItem v-for="type in NotificationType" :key="type" :value="type">
                    {{ type }}
                  </SelectItem>
                </SelectContent>
              </Select>
              <FormMessage />
            </FormItem>
          </FormField>

           <FormField v-slot="{ componentField }" name="category">
            <FormItem>
              <FormLabel>Category</FormLabel>
              <Select v-bind="componentField">
                <FormControl>
                  <SelectTrigger>
                    <SelectValue placeholder="Select category" />
                  </SelectTrigger>
                </FormControl>
                <SelectContent>
                  <SelectItem v-for="category in NotificationCategory" :key="category" :value="category">
                    {{ category }}
                  </SelectItem>
                </SelectContent>
              </Select>
              <FormMessage />
            </FormItem>
          </FormField>
        </div>

        <FormField v-slot="{ componentField }" name="target">
          <FormItem class="space-y-3">
            <FormLabel>Target Audience</FormLabel>
            <FormControl>
              <RadioGroup
                v-bind="componentField"
                class="flex flex-col space-y-1"
              >
                <FormItem class="flex items-center space-x-3 space-y-0">
                  <FormControl>
                    <RadioGroupItem :value="NotificationTarget.ALL" />
                  </FormControl>
                  <FormLabel class="font-normal">
                    All Users (Broadcast)
                  </FormLabel>
                </FormItem>
                <FormItem class="flex items-center space-x-3 space-y-0">
                  <FormControl>
                    <RadioGroupItem :value="NotificationTarget.USERS" />
                  </FormControl>
                  <FormLabel class="font-normal">
                    Specific Users
                  </FormLabel>
                </FormItem>
              </RadioGroup>
            </FormControl>
            <FormMessage />
          </FormItem>
        </FormField>

        <FormField v-if="form.values.target === NotificationTarget.USERS" v-slot="{ componentField }" name="userIds">
          <FormItem>
            <FormLabel>User IDs</FormLabel>
            <FormControl>
              <Textarea
                v-bind="componentField"
                placeholder="Comma separated User IDs (e.g. user1, user2)"
              />
            </FormControl>
            <FormMessage />
          </FormItem>
        </FormField>

        <div class="flex justify-end gap-4">
          <Button type="button" variant="outline" @click="router.back()">Cancel</Button>
          <Button type="submit" :disabled="store.isLoading">
            <IconSend class="mr-2 h-4 w-4" />
            Send Notification
          </Button>
        </div>
      </form>
    </div>
  </div>
</template>
