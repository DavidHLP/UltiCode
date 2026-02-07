<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { RouterLink, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import PersonalPageHeader from "./components/PersonalPageHeader.vue";
import PersonalPageShell from "./components/PersonalPageShell.vue";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { toast } from "vue-sonner";
import {
  Bell,
  MessageSquare,
  CornerUpLeft,
  AtSign,
  ThumbsUp,
  UserPlus,
  ShieldAlert,
  CheckCircle2,
  Trophy,
  Trash2,
  ChevronLeft,
  ChevronRight,
  Loader2,
  Check,
  MoreHorizontal,
} from "lucide-vue-next";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { fetchCurrentUserId } from "@/utils/auth";
import { useNotificationStore } from "@/stores/notification";
import type { NotificationItem, NotificationType } from "@/types/notification";

const { t, locale } = useI18n();
const router = useRouter();
const notificationStore = useNotificationStore();
const hasUser = ref(false);
const currentPage = ref(1);
const pageSize = 20;

const notifications = computed(() => notificationStore.notifications);
const loading = computed(() => notificationStore.loading);
const totalPages = computed(() => notificationStore.totalPages);

const typeIconMap: Record<NotificationType, typeof Bell> = {
  comment: MessageSquare,
  reply: CornerUpLeft,
  mention: AtSign,
  upvote: ThumbsUp,
  follow: UserPlus,
  system: ShieldAlert,
  submission: CheckCircle2,
  contest: Trophy,
};

const groupedNotifications = computed(() => {
  const groups: { key: string; items: NotificationItem[] }[] = [];
  const today = new Date();
  const yesterday = new Date();
  yesterday.setDate(yesterday.getDate() - 1);

  const todayItems: NotificationItem[] = [];
  const yesterdayItems: NotificationItem[] = [];
  const earlierItems: NotificationItem[] = [];

  notifications.value.forEach((notification) => {
    const date = new Date(notification.createdAt);
    if (date.toDateString() === today.toDateString()) {
      todayItems.push(notification);
    } else if (date.toDateString() === yesterday.toDateString()) {
      yesterdayItems.push(notification);
    } else {
      earlierItems.push(notification);
    }
  });

  if (todayItems.length > 0) {
    groups.push({ key: "common.time.today", items: todayItems });
  }
  if (yesterdayItems.length > 0) {
    groups.push({ key: "common.time.yesterday", items: yesterdayItems });
  }
  if (earlierItems.length > 0) {
    groups.push({ key: "common.time.earlier", items: earlierItems });
  }

  if (groups.length === 0 && notifications.value.length > 0) {
    return [
      { key: "personal.notifications.recent", items: notifications.value },
    ];
  }

  return groups;
});

function formatTime(value: string) {
  const date = new Date(value);
  // Show only time for today, otherwise full date
  const today = new Date();
  if (date.toDateString() === today.toDateString()) {
    return date.toLocaleTimeString(locale.value, {
      hour: "2-digit",
      minute: "2-digit",
    });
  }

  return date.toLocaleString(locale.value, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

async function loadNotifications() {
  if (!hasUser.value) return;
  await notificationStore.loadNotifications({
    page: currentPage.value,
    limit: pageSize,
  });
  await notificationStore.loadUnreadCount();
}

async function handleMarkAllRead() {
  try {
    await notificationStore.markAllRead();
    toast.success(t("personal.messages.notificationsMarkedRead"));
  } catch (error) {
    console.error("Failed to mark notifications as read", error);
    toast.error(t("common.status.error"));
  }
}

async function handleClearAll() {
  try {
    await notificationStore.clearAll();
    toast.success(t("personal.messages.notificationsCleared"));
  } catch (error) {
    console.error("Failed to clear notifications", error);
    toast.error(t("common.status.error"));
  }
}

async function handleMarkAsRead(notification: NotificationItem) {
  if (!notification.isRead) {
    try {
      await notificationStore.markAsRead(notification.id, true);
    } catch {
      // silent fail or toast
    }
  }
}

async function handleNotificationClick(notification: NotificationItem) {
  await handleMarkAsRead(notification);
  if (notification.link) {
    if (notification.link.startsWith("http")) {
      window.open(notification.link, "_blank", "noopener");
      return;
    }
    router.push(notification.link);
  }
}

async function handleDelete(notificationId: string) {
  try {
    await notificationStore.removeNotification(notificationId);
  } catch (error) {
    console.error("Failed to delete notification", error);
    toast.error(t("common.status.error"));
  }
}

onMounted(async () => {
  const userId = fetchCurrentUserId();
  if (!userId) return;
  hasUser.value = true;
  await loadNotifications();
});

watch(currentPage, () => {
  loadNotifications();
});
</script>

<template>
  <PersonalPageShell>
    <PersonalPageHeader
      :title="t('personal.notifications.title')"
      :description="t('personal.account.sections.notificationsDesc')"
    >
      <template #actions>
        <div class="flex items-center gap-2">
          <Button
            variant="ghost"
            size="sm"
            class="text-muted-foreground hover:text-foreground"
            :disabled="loading || notifications.length === 0"
            @click="handleMarkAllRead"
          >
            <Check class="mr-2 h-4 w-4" />
            {{ t("personal.notifications.markAllAsRead") }}
          </Button>
          <Separator orientation="vertical" class="h-4" />
          <Button
            variant="ghost"
            size="sm"
            class="text-muted-foreground hover:text-destructive"
            :disabled="loading || notifications.length === 0"
            @click="handleClearAll"
          >
            <Trash2 class="mr-2 h-4 w-4" />
            {{ t("personal.notifications.clearAll") }}
          </Button>
        </div>
      </template>
    </PersonalPageHeader>

    <div
      v-if="loading"
      class="flex flex-col items-center justify-center py-20 gap-4 min-h-[400px]"
    >
      <Loader2 class="h-8 w-8 animate-spin text-primary/60" />
      <p class="text-sm text-muted-foreground">
        {{ t("common.status.loading") }}
      </p>
    </div>

    <div
      v-else-if="!hasUser"
      class="flex flex-col items-center justify-center py-24 border border-dashed rounded-xl bg-muted/5"
    >
      <p class="text-muted-foreground mb-4">
        {{ t("personal.account.loginToManage") }}
      </p>
      <Button as-child class="rounded-full px-6">
        <RouterLink to="/login">{{ t("personal.profile.signIn") }}</RouterLink>
      </Button>
    </div>

    <div v-else-if="notifications.length > 0" class="space-y-8">
      <div
        v-for="(group, groupIndex) in groupedNotifications"
        :key="groupIndex"
      >
        <div class="flex items-center gap-4 mb-4">
          <h3
            class="text-sm font-medium text-muted-foreground whitespace-nowrap px-1"
          >
            {{ t(group.key) }}
          </h3>
          <Separator class="flex-1" />
        </div>

        <div class="space-y-2">
          <div
            v-for="notification in group.items"
            :key="notification.id"
            class="group relative flex items-start gap-4 p-4 rounded-xl border bg-card transition-all hover:shadow-md hover:border-primary/20"
            :class="{
              'bg-muted/30 border-transparent': notification.isRead,
              'border-primary/10 bg-primary/5': !notification.isRead,
            }"
          >
            <!-- Unread Indicator Dot -->
            <div
              v-if="!notification.isRead"
              class="absolute left-3 top-5 h-2 w-2 rounded-full bg-primary"
            ></div>

            <!-- Icon -->
            <div
              class="ml-3 shrink-0 flex h-10 w-10 items-center justify-center rounded-full border shadow-sm"
              :class="
                notification.isRead
                  ? 'bg-background text-muted-foreground/70 border-muted'
                  : 'bg-background text-primary border-primary/20'
              "
            >
              <component
                :is="typeIconMap[notification.type] || Bell"
                class="h-5 w-5"
              />
            </div>

            <!-- Content -->
            <div
              class="flex-1 min-w-0 pt-0.5 cursor-pointer"
              @click="handleNotificationClick(notification)"
            >
              <div class="flex flex-col gap-1">
                <div class="flex items-center justify-between gap-2">
                  <span
                    class="text-sm font-semibold truncate pr-2 text-foreground/90"
                  >
                    {{ notification.title }}
                  </span>
                  <span
                    class="text-xs text-muted-foreground whitespace-nowrap tabular-nums"
                  >
                    {{ formatTime(notification.createdAt) }}
                  </span>
                </div>
                <p
                  class="text-sm text-muted-foreground line-clamp-2 leading-relaxed"
                >
                  {{ notification.body }}
                </p>
              </div>
            </div>

            <!-- Actions -->
            <div
              class="shrink-0 flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity focus-within:opacity-100"
            >
              <DropdownMenu>
                <DropdownMenuTrigger as-child>
                  <Button
                    variant="ghost"
                    size="icon"
                    class="h-8 w-8 rounded-full"
                  >
                    <MoreHorizontal class="h-4 w-4 text-muted-foreground" />
                    <span class="sr-only">Actions</span>
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuItem
                    @click="handleMarkAsRead(notification)"
                    :disabled="notification.isRead"
                  >
                    <Check class="mr-2 h-4 w-4" />
                    <span>{{ t("personal.notifications.markAsRead") }}</span>
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    @click="handleDelete(notification.id)"
                    class="text-destructive focus:text-destructive"
                  >
                    <Trash2 class="mr-2 h-4 w-4" />
                    <span>{{ t("common.actions.delete") }}</span>
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          </div>
        </div>
      </div>

      <!-- Pagination -->
      <div class="flex items-center justify-center py-6">
        <div
          class="flex items-center gap-2 bg-muted/30 p-1 rounded-full border"
        >
          <Button
            variant="ghost"
            size="icon"
            class="h-8 w-8 rounded-full"
            :disabled="currentPage === 1 || loading"
            @click="currentPage = Math.max(1, currentPage - 1)"
          >
            <ChevronLeft class="h-4 w-4" />
          </Button>
          <span class="text-xs font-medium text-muted-foreground px-3">
            {{ currentPage }} / {{ totalPages }}
          </span>
          <Button
            variant="ghost"
            size="icon"
            class="h-8 w-8 rounded-full"
            :disabled="currentPage === totalPages || loading"
            @click="currentPage = Math.min(totalPages, currentPage + 1)"
          >
            <ChevronRight class="h-4 w-4" />
          </Button>
        </div>
      </div>
    </div>

    <div
      v-else
      class="flex flex-col items-center justify-center py-32 border border-dashed rounded-xl bg-muted/5 text-center"
    >
      <div class="bg-muted/30 p-4 rounded-full mb-4">
        <Bell class="h-8 w-8 text-muted-foreground/40" />
      </div>
      <h3 class="text-lg font-medium text-foreground mb-1">
        {{ t("personal.notifications.noNotifications") }}
      </h3>
      <p class="text-sm text-muted-foreground max-w-xs">
        {{ t("personal.notifications.emptyStateDesc") }}
      </p>
    </div>
  </PersonalPageShell>
</template>
