<script setup lang="ts">
import {
  Bell,
  ChevronsUpDown,
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
import { onMounted, computed } from "vue";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
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
import { useI18n } from "vue-i18n";
import { useRouter } from "vue-router";
import { toast } from "vue-sonner";
import { useNotificationStore } from "@/stores/notification";
import { useAuthStore } from "@/stores/auth";

const { user, isAuthenticated } = defineProps<{
  user: {
    name: string;
    email: string;
    avatar: string;
  };
  isAuthenticated: boolean;
}>();

const { t } = useI18n();
const { isMobile } = useSidebar();
const router = useRouter();
const authStore = useAuthStore();
const notificationStore = useNotificationStore();
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
  } catch (error) {
    // Don't show error toast for notification count failures
    // It's a non-critical UI element
    console.warn("Failed to load notification count:", error);
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
      <!-- Guest: show login/register buttons -->
      <template v-if="!isAuthenticated">
        <div class="flex items-center gap-2 px-2 py-1.5">
          <RouterLink to="/login" class="flex-1">
            <Button size="sm" class="w-full justify-center">
              <LogIn class="size-4" />
              {{ t("auth.login.submit") }}
            </Button>
          </RouterLink>
          <RouterLink to="/register" class="flex-1">
            <Button variant="outline" size="sm" class="w-full justify-center">
              <UserPlus class="size-4" />
              {{ t("auth.register.submit") }}
            </Button>
          </RouterLink>
        </div>
      </template>

      <!-- Authenticated: show user dropdown menu -->
      <DropdownMenu v-else>
        <DropdownMenuTrigger as-child>
          <SidebarMenuButton
            size="lg"
            class="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
          >
            <Avatar class="h-8 w-8 rounded-lg">
              <AvatarImage :src="user.avatar" :alt="user.name" />
              <AvatarFallback class="rounded-lg">
                {{ user.name.substring(0, 2).toUpperCase() }}
              </AvatarFallback>
            </Avatar>
            <div class="grid flex-1 text-left text-sm leading-tight">
              <span class="truncate font-medium">{{ user.name }}</span>
              <span class="truncate text-xs">{{ user.email }}</span>
            </div>
            <ChevronsUpDown class="ml-auto size-4" />
          </SidebarMenuButton>
        </DropdownMenuTrigger>
        <DropdownMenuContent
          class="w-[--reka-dropdown-menu-trigger-width] min-w-56 rounded-lg"
          :side="isMobile ? 'bottom' : 'right'"
          align="start"
          :side-offset="4"
        >
          <DropdownMenuLabel class="p-0 font-normal">
            <div class="flex items-center gap-2 px-1 py-1.5 text-left text-sm">
              <Avatar class="h-8 w-8 rounded-lg">
                <AvatarImage :src="user.avatar" :alt="user.name" />
                <AvatarFallback class="rounded-lg">
                  {{ user.name.substring(0, 2).toUpperCase() }}
                </AvatarFallback>
              </Avatar>
              <div class="grid flex-1 text-left text-sm leading-tight">
                <span class="truncate font-medium">{{ user.name }}</span>
                <span class="truncate text-xs">{{ user.email }}</span>
              </div>
              <LanguageSwitcher />
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
                  variant="destructive"
                  class="h-5 px-2 text-[10px] font-bold"
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
        </DropdownMenuContent>
      </DropdownMenu>
    </SidebarMenuItem>
  </SidebarMenu>
</template>
