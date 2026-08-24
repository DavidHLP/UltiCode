<script setup lang="ts">
import {
  Bell,
  LogIn,
  UserPlus,
  LogOut,
  User,
  History,
  Settings,
  Bookmark,
  MessageSquare,
  List,
  CheckCircle2,
} from "lucide-vue-next";
import { IconDotsVertical } from "@tabler/icons-vue";
import { onMounted, computed } from "vue";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar";
import LanguageSwitcher from "@/components/LanguageSwitcher.vue";
import ThemeSwitcher from "@/components/ThemeSwitcher.vue";
import { useLocale } from "@/composables/useLocale";
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import { toast } from "vue-sonner";
import { useNotificationStore } from "@/stores/notification";
import { useAuthStore } from "@/stores/auth";
import { useAvatar } from "@/composables/useAvatar";

const { user, isAuthenticated } = defineProps<{
  user: {
    username?: string;
    name: string;
    email: string;
    avatar: string;
  };
  isAuthenticated: boolean;
}>();

const { t } = useI18n();
const {} = useLocale();
const { isMobile } = useSidebar();
const router = useRouter();
const authStore = useAuthStore();
const notificationStore = useNotificationStore();
const { normalizedAvatar } = useAvatar(
  computed(() => user.username || user.name),
  computed(() => user.avatar),
);
const unreadCount = computed(() => notificationStore.unreadCount);
const unreadLabel = computed(() =>
  unreadCount.value > 99 ? "99+" : `${unreadCount.value}`,
);

onMounted(async () => {
  // Only fetch unread count if user is authenticated
  // Check both prop and store to handle edge cases
  if (!isAuthenticated || !authStore.isAuthenticated) return;

  try {
    await notificationStore.loadUnreadCount();
  } catch {
    // Don't show error toast for notification count failures
    // It's a non-critical UI element
    // Failed to load notification count - non-critical UI element
  }
});

async function handleLogout() {
  try {
    await authStore.logout();
    toast.success(t("auth.messages.logoutSuccess"));
    router.push("/login");
  } catch (error) {
    console.error("Logout failed", error);
    // Still redirect to login even if API call fails
    toast.success(t("auth.messages.logoutSuccess"));
    router.push("/login");
  }
}
</script>

<template>
  <SidebarMenu>
    <SidebarMenuItem>
      <!-- Guest: unified dropdown -->
      <template v-if="!isAuthenticated">
        <DropdownMenu>
          <DropdownMenuTrigger as-child>
            <SidebarMenuButton
              size="lg"
              class="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
            >
              <Avatar class="h-8 w-8 rounded-none">
                <AvatarFallback class="rounded-none">
                  <User class="size-4" />
                </AvatarFallback>
              </Avatar>
              <div class="grid flex-1 text-left text-sm leading-tight">
                <span class="truncate font-medium">{{
                  t("auth.guest.name")
                }}</span>
                <span class="truncate text-xs text-muted-foreground">
                  {{ t("auth.guest.loginToContinue") }}
                </span>
              </div>
              <IconDotsVertical class="ml-auto size-4" />
            </SidebarMenuButton>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            class="w-[--reka-dropdown-menu-trigger-width] min-w-56 rounded-none"
            :side="isMobile ? 'bottom' : 'right'"
            align="end"
            :side-offset="4"
          >
            <DropdownMenuLabel class="p-0 font-normal">
              <div
                class="flex items-center gap-2 px-1 py-1.5 text-left text-sm"
              >
                <Avatar class="h-8 w-8 rounded-none">
                  <AvatarFallback class="rounded-none">
                    <User class="size-4" />
                  </AvatarFallback>
                </Avatar>
                <div class="grid flex-1 text-left text-sm leading-tight">
                  <span class="truncate font-medium">{{
                    t("auth.guest.name")
                  }}</span>
                  <span class="truncate text-xs text-muted-foreground">
                    {{ t("auth.guest.loginToContinue") }}
                  </span>
                </div>
              </div>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuGroup>
              <RouterLink to="/login">
                <DropdownMenuItem class="cursor-pointer">
                  <LogIn class="mr-2 h-4 w-4" />
                  {{ t("auth.login.submit") }}
                </DropdownMenuItem>
              </RouterLink>
              <RouterLink to="/register">
                <DropdownMenuItem class="cursor-pointer">
                  <UserPlus class="mr-2 h-4 w-4" />
                  {{ t("auth.register.submit") }}
                </DropdownMenuItem>
              </RouterLink>
            </DropdownMenuGroup>
            <DropdownMenuSeparator />
            <ThemeSwitcher />
            <LanguageSwitcher />
          </DropdownMenuContent>
        </DropdownMenu>
      </template>

      <!-- Authenticated: show user dropdown menu -->
      <DropdownMenu v-else>
        <DropdownMenuTrigger as-child>
          <SidebarMenuButton
            size="lg"
            class="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
          >
            <Avatar class="h-8 w-8 rounded-none">
              <AvatarImage :src="normalizedAvatar" :alt="user.name" />
              <AvatarFallback class="rounded-none">
                {{ user.name.substring(0, 2).toUpperCase() }}
              </AvatarFallback>
            </Avatar>
            <div class="grid flex-1 text-left text-sm leading-tight">
              <span class="truncate font-medium">{{ user.name }}</span>
              <span class="truncate text-xs">{{ user.email }}</span>
            </div>
            <IconDotsVertical class="ml-auto size-4" />
          </SidebarMenuButton>
        </DropdownMenuTrigger>
        <DropdownMenuContent
          class="w-[--reka-dropdown-menu-trigger-width] min-w-56 rounded-none"
          :side="isMobile ? 'bottom' : 'right'"
          align="end"
          :side-offset="4"
        >
          <DropdownMenuLabel class="p-0 font-normal">
            <div class="flex items-center gap-2 px-1 py-1.5 text-left text-sm">
              <Avatar class="h-8 w-8 rounded-none">
                <AvatarImage :src="normalizedAvatar" :alt="user.name" />
                <AvatarFallback class="rounded-none">
                  {{ user.name.substring(0, 2).toUpperCase() }}
                </AvatarFallback>
              </Avatar>
              <div class="grid flex-1 text-left text-sm leading-tight">
                <span class="truncate font-medium">{{ user.name }}</span>
                <span class="truncate text-xs">{{ user.email }}</span>
              </div>
            </div>
          </DropdownMenuLabel>
          <DropdownMenuSeparator />
          <DropdownMenuGroup>
            <RouterLink to="/personal">
              <DropdownMenuItem>
                <User class="mr-2 h-4 w-4" />
                {{ t("sidebar.personal.profile") }}
              </DropdownMenuItem>
            </RouterLink>
            <RouterLink to="/personal/account">
              <DropdownMenuItem>
                <Settings class="mr-2 h-4 w-4" />
                {{ t("sidebar.personal.accountSettings") }}
              </DropdownMenuItem>
            </RouterLink>
          </DropdownMenuGroup>
          <DropdownMenuSeparator />
          <DropdownMenuGroup>
            <RouterLink to="/personal/submissions">
              <DropdownMenuItem>
                <History class="mr-2 h-4 w-4" />
                {{ t("sidebar.personal.submissions") }}
              </DropdownMenuItem>
            </RouterLink>
            <RouterLink to="/personal/solutions">
              <DropdownMenuItem>
                <CheckCircle2 class="mr-2 h-4 w-4" />
                {{ t("sidebar.personal.solutions") }}
              </DropdownMenuItem>
            </RouterLink>
            <RouterLink to="/personal/problem-lists">
              <DropdownMenuItem>
                <List class="mr-2 h-4 w-4" />
                {{ t("sidebar.personal.problemLists") }}
              </DropdownMenuItem>
            </RouterLink>
            <RouterLink to="/personal/bookmarks">
              <DropdownMenuItem>
                <Bookmark class="mr-2 h-4 w-4" />
                {{ t("sidebar.personal.bookmarks") }}
              </DropdownMenuItem>
            </RouterLink>
            <RouterLink to="/personal/forum-posts">
              <DropdownMenuItem>
                <MessageSquare class="mr-2 h-4 w-4" />
                {{ t("sidebar.personal.forumPosts") }}
              </DropdownMenuItem>
            </RouterLink>
          </DropdownMenuGroup>
          <DropdownMenuSeparator />
          <DropdownMenuGroup>
            <RouterLink to="/personal/notifications">
              <DropdownMenuItem class="justify-between">
                <div class="flex items-center">
                  <Bell class="mr-2 h-4 w-4" />
                  {{ t("sidebar.personal.notifications") }}
                </div>
                <Badge
                  v-if="unreadCount > 0"
                  class="h-5 px-2 text-2xs font-bold bg-status-warning-surface text-foreground-strong border-status-warning-mark"
                >
                  {{ unreadLabel }}
                </Badge>
              </DropdownMenuItem>
            </RouterLink>
          </DropdownMenuGroup>
          <DropdownMenuSeparator />
          <DropdownMenuItem @click="handleLogout" class="text-destructive">
            <LogOut class="mr-2 h-4 w-4" />
            {{ t("sidebar.personal.logout") }}
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <ThemeSwitcher />
          <LanguageSwitcher />
        </DropdownMenuContent>
      </DropdownMenu>
    </SidebarMenuItem>
  </SidebarMenu>
</template>
