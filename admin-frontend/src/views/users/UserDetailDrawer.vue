<script setup lang="ts">
import { ref, watch } from 'vue'
import { useUsersStore } from '@/stores/admin/users'
import {
  Drawer,
  DrawerContent,
  DrawerDescription,
  DrawerHeader,
  DrawerTitle,
} from '@/components/ui/drawer'
import { Badge } from '@/components/ui/badge'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Separator } from '@/components/ui/separator'
import {
  IconMail,
  IconCalendar,
  IconUser,
  IconShield,
  IconBan,
  IconClock,
  IconTrophy,
  IconFlame,
} from '@tabler/icons-vue'
import { Progress } from '@/components/ui/progress'

const props = defineProps<{
  open: boolean
  userId: string | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const usersStore = useUsersStore()
const loading = ref(false)

async function loadUser() {
  if (!props.userId) return
  loading.value = true
  try {
    await usersStore.fetchUser(props.userId)
  } finally {
    loading.value = false
  }
}

watch(
  () => props.open,
  (newOpen) => {
    if (newOpen && props.userId) {
      loadUser()
    }
  },
)

function getRoleBadgeVariant(role: string): 'default' | 'secondary' | 'destructive' | 'outline' {
  switch (role) {
    case 'SUPER_ADMIN':
      return 'destructive'
    case 'ADMIN':
      return 'default'
    case 'MODERATOR':
      return 'secondary'
    default:
      return 'outline'
  }
}
</script>

<template>
  <Drawer :open="open" @update:open="emit('update:open', $event)" direction="right">
    <DrawerContent class="h-full w-[400px] sm:w-[540px]">
      <DrawerHeader class="border-b px-6 py-4">
        <div class="flex items-center justify-between">
          <div>
            <DrawerTitle>User Details</DrawerTitle>
            <DrawerDescription>View comprehensive information about the user.</DrawerDescription>
          </div>
        </div>
      </DrawerHeader>

      <div v-if="loading" class="flex h-full items-center justify-center p-8">
        <div class="flex flex-col items-center gap-2">
          <div
            class="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent"
          ></div>
          <p class="text-sm text-muted-foreground">Loading user details...</p>
        </div>
      </div>

      <ScrollArea v-else-if="usersStore.currentUser" class="flex-1">
        <div class="flex flex-col gap-6 p-6">
          <!-- Profile Header -->
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-4">
              <Avatar class="h-20 w-20 border-2 border-background shadow-sm">
                <AvatarImage
                  :src="usersStore.currentUser.avatar ?? ''"
                  :alt="usersStore.currentUser.username"
                />
                <AvatarFallback class="text-xl">
                  {{ usersStore.currentUser.name?.[0] || usersStore.currentUser.username[0] }}
                </AvatarFallback>
              </Avatar>
              <div class="flex flex-col gap-1">
                <h3 class="text-xl font-semibold leading-none">
                  {{ usersStore.currentUser.name || usersStore.currentUser.username }}
                </h3>
                <p class="text-sm text-muted-foreground flex items-center gap-1">
                  <IconMail class="h-3.5 w-3.5" />
                  {{ usersStore.currentUser.email || 'No email provided' }}
                </p>
                <div class="flex flex-wrap gap-2 mt-1">
                  <Badge :variant="getRoleBadgeVariant(usersStore.currentUser.role)">
                    {{ usersStore.currentUser.role.replace('_', ' ') }}
                  </Badge>
                  <Badge v-if="usersStore.currentUser.is_banned" variant="destructive"
                    >Banned</Badge
                  >
                  <Badge v-else-if="!usersStore.currentUser.is_active" variant="secondary"
                    >Inactive</Badge
                  >
                  <Badge v-else variant="default">Active</Badge>
                </div>
              </div>
            </div>
          </div>

          <Separator />

          <!-- User Statistics -->
          <div v-if="usersStore.currentUser.stats" class="space-y-4">
            <h4 class="text-sm font-medium text-muted-foreground uppercase tracking-wider">
              Performance Overview
            </h4>
            <div class="grid grid-cols-2 gap-4">
              <div
                class="rounded-lg border bg-card p-4 flex flex-col items-center justify-center text-center"
              >
                <IconTrophy class="h-8 w-8 text-yellow-500 mb-2" />
                <span class="text-2xl font-bold">{{
                  usersStore.currentUser.stats.totalSolved
                }}</span>
                <span class="text-xs text-muted-foreground uppercase">Problems Solved</span>
              </div>
              <div
                class="rounded-lg border bg-card p-4 flex flex-col items-center justify-center text-center"
              >
                <IconFlame class="h-8 w-8 text-orange-500 mb-2" />
                <span class="text-2xl font-bold">{{ usersStore.currentUser.stats.streak }}</span>
                <span class="text-xs text-muted-foreground uppercase">Day Streak</span>
              </div>
            </div>

            <div class="space-y-3 mt-4">
              <div
                v-for="(data, diff) in usersStore.currentUser.stats.stats"
                :key="diff"
                class="space-y-1.5"
              >
                <div class="flex items-center justify-between text-xs">
                  <span class="font-medium">{{ diff }}</span>
                  <span class="text-muted-foreground">{{ data.count }}/{{ data.total }}</span>
                </div>
                <Progress
                  :value="data.total > 0 ? (data.count / data.total) * 100 : 0"
                  class="h-1.5"
                  :class="{
                    'bg-emerald-100': diff === 'Easy',
                    'bg-amber-100': diff === 'Medium',
                    'bg-rose-100': diff === 'Hard',
                  }"
                  :indicator-class="
                    diff === 'Easy'
                      ? 'bg-emerald-500'
                      : diff === 'Medium'
                        ? 'bg-amber-500'
                        : 'bg-rose-500'
                  "
                />
              </div>
            </div>
          </div>

          <Separator v-if="usersStore.currentUser.stats" />

          <!-- Details Grid -->
          <div class="grid gap-6">
            <div class="space-y-4">
              <h4 class="text-sm font-medium text-muted-foreground uppercase tracking-wider">
                Account Information
              </h4>
              <div class="grid grid-cols-2 gap-4">
                <div class="space-y-1">
                  <p class="text-sm font-medium flex items-center gap-2">
                    <IconUser class="h-4 w-4 text-muted-foreground" />
                    Username
                  </p>
                  <p class="text-sm text-muted-foreground pl-6">
                    {{ usersStore.currentUser.username }}
                  </p>
                </div>
                <div class="space-y-1">
                  <p class="text-sm font-medium flex items-center gap-2">
                    <IconShield class="h-4 w-4 text-muted-foreground" />
                    Role
                  </p>
                  <p class="text-sm text-muted-foreground pl-6">
                    {{ usersStore.currentUser.role }}
                  </p>
                </div>
                <div class="space-y-1">
                  <p class="text-sm font-medium flex items-center gap-2">
                    <IconCalendar class="h-4 w-4 text-muted-foreground" />
                    Joined At
                  </p>
                  <p class="text-sm text-muted-foreground pl-6">
                    {{ new Date(usersStore.currentUser.joined_at).toLocaleDateString() }}
                  </p>
                </div>
                <div class="space-y-1">
                  <p class="text-sm font-medium flex items-center gap-2">
                    <IconClock class="h-4 w-4 text-muted-foreground" />
                    Last Login
                  </p>
                  <p class="text-sm text-muted-foreground pl-6">
                    {{
                      usersStore.currentUser.last_login_at
                        ? new Date(usersStore.currentUser.last_login_at).toLocaleString()
                        : 'Never'
                    }}
                  </p>
                </div>
              </div>
            </div>

            <div
              v-if="usersStore.currentUser.is_banned"
              class="space-y-4 rounded-lg border border-destructive/20 bg-destructive/5 p-4"
            >
              <h4 class="text-sm font-medium text-destructive flex items-center gap-2">
                <IconBan class="h-4 w-4" />
                Ban Information
              </h4>
              <div class="space-y-2">
                <p class="text-sm font-medium">Reason:</p>
                <p class="text-sm text-muted-foreground italic">
                  {{ usersStore.currentUser.ban_reason || 'No reason provided' }}
                </p>
                <p class="text-xs text-muted-foreground">
                  Banned on:
                  {{
                    usersStore.currentUser.banned_at
                      ? new Date(usersStore.currentUser.banned_at).toLocaleString()
                      : 'Unknown'
                  }}
                </p>
              </div>
            </div>
          </div>
        </div>
      </ScrollArea>

      <div v-else class="flex h-full items-center justify-center p-8">
        <p class="text-muted-foreground">User not found</p>
      </div>
    </DrawerContent>
  </Drawer>
</template>