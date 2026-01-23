<script setup lang="ts">
import {
  Bell,
  ChevronsUpDown,
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
import { logout } from "@/api/auth";
import { isAuthenticated, removeToken, removeUserId } from "@/utils/auth";
import { toast } from "vue-sonner";
import { useNotificationStore } from "@/stores/notification";

const { user } = defineProps<{
  user: {
    name: string;
    email: string;
    avatar: string;
  };
}>();

const { t } = useI18n();
const { isMobile } = useSidebar();
const router = useRouter();
const notificationStore = useNotificationStore();
const unreadCount = computed(() => notificationStore.unreadCount);
const unreadLabel = computed(() =>
  unreadCount.value > 99 ? "99+" : `${unreadCount.value}`,
);

onMounted(() => {
  if (!isAuthenticated()) return;
  notificationStore.loadUnreadCount().catch((error) => {
    console.error("Failed to load notification count", error);
  });
});

async function handleLogout() {
  try {
    await logout();
  } catch (error) {
    console.error("Logout failed", error);
    // Continue with local cleanup anyway
  } finally {
    removeToken();
    removeUserId();
    toast.success(t("auth.messages.logoutSuccess"));
    router.push("/login");
  }
}
</script>

<template>
  <SidebarMenu>
    <SidebarMenuItem>
      <DropdownMenu>
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
