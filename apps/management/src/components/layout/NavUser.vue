<script setup lang="ts">
import {
  ChevronsUpDown as IconDotsVertical,
  LogOut as IconLogout,
  BellRing as IconNotification,
  UserCircle as IconUserCircle,
} from '@lucide/vue'

import { computed } from 'vue'
import { useAvatar } from '@ulticode/auth-ui'
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
  username?: string
  email: string
  avatar?: string
  role?: string
}

const props = defineProps<{
  user: User
}>()

const router = useRouter()
const { t } = useI18n()
const { isMobile } = useSidebar()
const authStore = useAuthStore()
const { normalizedAvatar } = useAvatar(
  computed(() => props.user.username || props.user.name),
  computed(() => props.user.avatar),
)

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
          <SidebarMenuButton
            size="lg"
            :tooltip="t('nav.account')"
            class="uc-sidebar-user-trigger"
          >
            <Avatar class="uc-sidebar-avatar size-8 rounded-lg">
              <AvatarImage :src="normalizedAvatar" :alt="user.name" />
              <AvatarFallback class="uc-sidebar-avatar-fallback rounded-lg">{{
                user.name.slice(0, 2).toUpperCase()
              }}</AvatarFallback>
            </Avatar>
            <div class="grid min-w-0 flex-1 text-left text-sm leading-tight">
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
            <IconDotsVertical class="ml-auto size-4 text-muted-foreground" />
          </SidebarMenuButton>
        </DropdownMenuTrigger>
        <DropdownMenuContent
          class="w-(--reka-dropdown-menu-trigger-width) min-w-56 rounded-lg"
          :side="isMobile ? 'bottom' : 'right'"
          align="end"
          :side-offset="4"
        >
          <DropdownMenuLabel class="p-0">
            <div class="flex items-center gap-2 px-1 py-1.5 text-left text-sm">
              <Avatar class="uc-sidebar-avatar size-8 rounded-lg">
                <AvatarImage :src="normalizedAvatar" :alt="user.name" />
                <AvatarFallback class="uc-sidebar-avatar-fallback rounded-lg">{{
                  user.name.slice(0, 2).toUpperCase()
                }}</AvatarFallback>
              </Avatar>
              <div class="grid min-w-0 flex-1 text-left text-sm leading-tight">
                <div class="flex min-w-0 items-center gap-1.5">
                  <span class="truncate font-medium">{{ user.name }}</span>
                  <span
                    v-if="user.role"
                    class="shrink-0 rounded px-1 text-xs text-muted-foreground"
                  >
                    {{ user.role }}
                  </span>
                </div>
                <span class="truncate text-xs text-muted-foreground">
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
