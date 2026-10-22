<script setup lang="ts">
import {
  ChevronsUpDown as IconDotsVertical,
  LogOut as IconLogout,
  BellRing as IconNotification,
  UserCircle as IconUserCircle,
} from '@lucide/vue'

import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { Avatar, AvatarFallback, AvatarImage } from '@ulticode/design-system/ui/avatar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@ulticode/design-system/ui/dropdown-menu'
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from '@ulticode/design-system/ui/sidebar'

interface User {
  name: string
  email: string
  avatar?: string
  role?: string
}

defineProps<{
  user: User
}>()

const router = useRouter()
const { t } = useI18n()
const { isMobile } = useSidebar()
const authStore = useAuthStore()

function handleAccount() {
  router.push('/account')
}

function handleNotifications() {
  router.push('/notifications')
}

async function handleLogout() {
  await authStore.logout()
  await router.push('/login')
}
</script>

<template>
  <SidebarMenu>
    <SidebarMenuItem>
      <DropdownMenu>
        <DropdownMenuTrigger as-child>
          <SidebarMenuButton size="lg" :tooltip="t('nav.account')">
            <Avatar class="h-8 w-8 rounded-lg">
              <AvatarImage v-if="user.avatar" :src="user.avatar" :alt="user.name" />
              <AvatarFallback>{{ user.name.slice(0, 2).toUpperCase() }}</AvatarFallback>
            </Avatar>
            <div class="grid flex-1 text-left text-sm leading-tight">
              <div class="flex items-center gap-1.5 min-w-0">
                <span class="truncate font-medium">{{ user.name }}</span>
                <span v-if="user.role" class="shrink-0 rounded px-1 text-xs text-muted-foreground">
                  {{ user.role }}
                </span>
              </div>
              <span class="truncate text-xs text-muted-foreground">
                {{ user.email }}
              </span>
            </div>
            <IconDotsVertical
              class="ml-auto size-4 text-muted-foreground group-hover:text-foreground"
            />
          </SidebarMenuButton>
        </DropdownMenuTrigger>
        <DropdownMenuContent
          class="w-(--reka-dropdown-menu-trigger-width) min-w-56 rounded-lg"
          :side="isMobile ? 'bottom' : 'right'"
          align="end"
          :side-offset="4"
        >
          <DropdownMenuLabel class="p-0">
            <div class="flex items-center gap-2 px-1.5 py-1.5 text-left text-sm">
              <Avatar class="h-8 w-8 rounded-lg">
                <AvatarImage v-if="user.avatar" :src="user.avatar" :alt="user.name" />
                <AvatarFallback>{{ user.name.slice(0, 2).toUpperCase() }}</AvatarFallback>
              </Avatar>
              <div class="grid flex-1 text-left text-xs leading-tight">
                <div class="flex items-center gap-1.5">
                  <span class="truncate font-bold text-sm text-foreground">{{ user.name }}</span>
                  <span
                    v-if="user.role"
                    class="inline-flex items-center px-1.5 py-0.2 text-xs font-mono font-bold uppercase border border-border bg-accent text-muted-foreground tracking-wide shrink-0"
                  >
                    {{ user.role }}
                  </span>
                </div>
                <span class="text-muted-foreground truncate mt-0.5 font-mono text-xs">
                  {{ user.email }}
                </span>
              </div>
            </div>
          </DropdownMenuLabel>
          <DropdownMenuSeparator />
          <DropdownMenuGroup>
            <DropdownMenuItem @click="handleAccount">
              <IconUserCircle />
              {{ t('nav.account') }}
            </DropdownMenuItem>
            <DropdownMenuItem @click="handleNotifications">
              <IconNotification />
              {{ t('nav.notifications') }}
            </DropdownMenuItem>
          </DropdownMenuGroup>
          <DropdownMenuSeparator />
          <DropdownMenuItem @click="handleLogout">
            <IconLogout />
            {{ t('nav.logout') }}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </SidebarMenuItem>
  </SidebarMenu>
</template>
