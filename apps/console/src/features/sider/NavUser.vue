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
} from "@lucide/vue";
import { ChevronsUpDown as IconDotsVertical } from "@lucide/vue";
import { AccountSwitcher } from "@ulticode/auth-ui";
import { accountSessions } from "@/api/accountSessions";

import { onMounted, computed } from "vue";

import {
  Avatar,
  AvatarFallback,
  AvatarImage,
} from "@ulticode/design-system/ui/avatar";
import { Badge } from "@ulticode/design-system/ui/badge";
import {
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
} from "@ulticode/design-system/ui/dropdown-menu";
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@ulticode/design-system/ui/sidebar";
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
        <AccountSwitcher :client="accountSessions">
          <template #trigger>
            <SidebarMenuButton size="lg" class="uc-sidebar-user-trigger">
              <Avatar class="uc-sidebar-avatar h-8 w-8">
                <AvatarFallback class="uc-sidebar-avatar-fallback">
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
          </template>
          <template #summary
            ><DropdownMenuLabel class="p-0">
              <div
                class="flex items-center gap-2 px-1 py-1.5 text-left text-sm"
              >
                <Avatar class="uc-sidebar-avatar h-8 w-8">
                  <AvatarFallback class="uc-sidebar-avatar-fallback">
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
            </DropdownMenuLabel></template
          >
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
        </AccountSwitcher>
      </template>

      <!-- Authenticated: show user dropdown menu -->
      <AccountSwitcher v-else :client="accountSessions">
        <template #trigger>
          <SidebarMenuButton size="lg" class="uc-sidebar-user-trigger">
            <Avatar class="uc-sidebar-avatar h-8 w-8">
              <AvatarImage :src="normalizedAvatar" :alt="user.name" />
              <AvatarFallback class="uc-sidebar-avatar-fallback">
                {{ user.name.substring(0, 2).toUpperCase() }}
              </AvatarFallback>
            </Avatar>
            <div class="grid flex-1 text-left text-sm leading-tight">
              <span class="truncate font-medium">{{ user.name }}</span>
              <span class="truncate text-xs">{{ user.email }}</span>
            </div>
            <IconDotsVertical class="ml-auto size-4" />
          </SidebarMenuButton>
        </template>
        <template #summary
          ><DropdownMenuLabel class="p-0">
            <div class="flex items-center gap-2 px-1 py-1.5 text-left text-sm">
              <Avatar class="uc-sidebar-avatar h-8 w-8">
                <AvatarImage :src="normalizedAvatar" :alt="user.name" />
                <AvatarFallback class="uc-sidebar-avatar-fallback">
                  {{ user.name.substring(0, 2).toUpperCase() }}
                </AvatarFallback>
              </Avatar>
              <div class="grid flex-1 text-left text-sm leading-tight">
                <span class="truncate font-medium">{{ user.name }}</span>
                <span class="truncate text-xs">{{ user.email }}</span>
              </div>
            </div>
          </DropdownMenuLabel></template
        >
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
              <Badge v-if="unreadCount > 0" class="h-5 px-2">
                {{ unreadLabel }}
              </Badge>
            </DropdownMenuItem>
          </RouterLink>
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuItem @click="handleLogout">
          <LogOut class="mr-2 h-4 w-4" />
          {{ t("sidebar.personal.logout") }}
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <ThemeSwitcher />
        <LanguageSwitcher />
      </AccountSwitcher>
    </SidebarMenuItem>
  </SidebarMenu>
</template>
