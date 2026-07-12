<script setup lang="ts">
import { IconDotsVertical, IconLogout, IconNotification, IconUserCircle } from '@tabler/icons-vue'

import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from '@/components/ui/sidebar'

interface User {
  name: string
  email: string
  avatar?: string
  role?: string
}

defineProps<{
  user: User
}>()

const { isMobile } = useSidebar()
const router = useRouter()
const { t } = useI18n()
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
          <SidebarMenuButton
            size="lg"
            tooltip="User Menu"
            class="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground h-11"
          >
            <Avatar
              class="h-8 w-8 rounded-none border border-[var(--silver-200)] dark:border-[var(--silver-300)]"
              shape="square"
            >
              <AvatarImage v-if="user.avatar" :src="user.avatar" :alt="user.name" />
              <AvatarFallback class="rounded-none"> CN </AvatarFallback>
            </Avatar>
            <div class="grid flex-1 text-left text-xs leading-tight">
              <div class="flex items-center gap-1.5 min-w-0">
                <span class="truncate font-bold text-sm text-foreground">{{ user.name }}</span>
                <span
                  v-if="user.role"
                  class="inline-flex items-center px-1.5 py-0.2 text-2xs font-mono font-bold uppercase border border-[var(--silver-300)] bg-[var(--silver-100)] text-[var(--silver-500)] tracking-wide shrink-0"
                >
                  {{ user.role }}
                </span>
              </div>
              <span class="text-[var(--silver-400)] truncate mt-0.5 font-mono text-2xs">
                {{ user.email }}
              </span>
            </div>
            <IconDotsVertical
              class="ml-auto size-4 text-[var(--silver-400)] group-hover:text-foreground"
            />
          </SidebarMenuButton>
        </DropdownMenuTrigger>
        <DropdownMenuContent
          variant="terminal"
          class="w-(--reka-dropdown-menu-trigger-width) min-w-56"
          :side="isMobile ? 'bottom' : 'right'"
          :side-offset="4"
          align="end"
        >
          <DropdownMenuLabel class="p-0 font-normal">
            <div class="flex items-center gap-2 px-1.5 py-1.5 text-left text-sm">
              <Avatar
                class="h-8 w-8 rounded-none border border-[var(--silver-200)] dark:border-[var(--silver-300)]"
                shape="square"
              >
                <AvatarImage v-if="user.avatar" :src="user.avatar" :alt="user.name" />
                <AvatarFallback class="rounded-none"> CN </AvatarFallback>
              </Avatar>
              <div class="grid flex-1 text-left text-xs leading-tight">
                <div class="flex items-center gap-1.5">
                  <span class="truncate font-bold text-sm text-foreground">{{ user.name }}</span>
                  <span
                    v-if="user.role"
                    class="inline-flex items-center px-1.5 py-0.2 text-2xs font-mono font-bold uppercase border border-[var(--silver-300)] bg-[var(--silver-100)] text-[var(--silver-500)] tracking-wide shrink-0"
                  >
                    {{ user.role }}
                  </span>
                </div>
                <span class="text-[var(--silver-400)] truncate mt-0.5 font-mono text-2xs">
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
