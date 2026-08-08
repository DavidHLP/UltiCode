<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { Bell, Loader2 } from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import { useNotificationStore } from "@/stores/notification";
import { useAuthStore } from "@/stores/auth";
import { useNotificationI18n } from "@/composables/useNotificationI18n";
import { useNotificationNavigation } from "@/composables/useNotificationNavigation";
import type { NotificationItem } from "@/types/notification";
import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Badge } from "@/components/ui/badge";
import ConnectionStatus from "./ConnectionStatus.vue";
import { cn } from "@/lib/utils";
import { useRouter } from "vue-router";

const props = defineProps<{
  class?: string;
}>();

const { t } = useI18n();
const notificationStore = useNotificationStore();
const router = useRouter();
const authStore = useAuthStore();
const { display: localizedNotification } = useNotificationI18n();
const { open: openNotification } = useNotificationNavigation();

const isOpen = ref(false);
const isLoadingList = ref(false);
const hasLoadedList = ref(false);
let loadRequestId = 0;

async function refreshList() {
  if (!authStore.isAuthenticated) {
    // The feed owns mutations: clear local cache through the store action
    // rather than patching state directly from the presentation layer.
    notificationStore.resetLocalState();
    hasLoadedList.value = true;
    return;
  }
  const requestId = ++loadRequestId;
  isLoadingList.value = true;
  try {
    await Promise.all([
      notificationStore.loadNotifications({ page: 1, limit: 10 }),
      notificationStore.loadUnreadCount(),
    ]);
    if (requestId === loadRequestId) {
      hasLoadedList.value = true;
    }
  } catch {
    // Errors are surfaced through the store; keep UI responsive.
    if (requestId === loadRequestId) {
      hasLoadedList.value = true;
    }
  } finally {
    if (requestId === loadRequestId) {
      isLoadingList.value = false;
    }
  }
}

watch(isOpen, (open) => {
  if (open) {
    void refreshList();
  }
});

const unreadLabel = computed(() =>
  notificationStore.unreadCount > 99
    ? "99+"
    : `${notificationStore.unreadCount}`,
);

const hasUnread = computed(() => notificationStore.unreadCount > 0);

function goToNotifications() {
  router.push("/personal/notifications");
}

async function handleClick(notification: NotificationItem) {
  if (notification.link) {
    // Delegates mark-as-read + safe link classification to the inbox workflow.
    await openNotification(notification);
    return;
  }
  // No deep link: keep read-state consistent, then send to the full inbox.
  if (!notification.isRead) {
    try {
      await notificationStore.markAsRead(notification.id, true);
    } catch {
      // best-effort; navigation proceeds
    }
  }
  goToNotifications();
}
</script>

<template>
  <Popover v-model:open="isOpen">
    <PopoverTrigger as-child>
      <Button variant="ghost" size="icon" :class="cn('relative', props.class)">
        <Bell class="h-5 w-5" />
        <span class="sr-only">{{ t("notification.toggleNotifications") }}</span>
        <Badge
          v-if="hasUnread"
          variant="destructive"
          class="absolute -right-1 -top-1 h-5 min-w-5 px-1 text-2xs font-bold"
          :aria-label="
            t('notification.unreadCountLabel', {
              count: notificationStore.unreadCount,
            })
          "
        >
          {{ unreadLabel }}
        </Badge>
      </Button>
    </PopoverTrigger>
    <PopoverContent align="end" class="w-80 p-0">
      <div class="flex items-center justify-between border-b px-4 py-2.5">
        <h4 class="text-sm font-semibold tracking-tight">
          {{ t("notification.title") }}
        </h4>
        <ConnectionStatus />
      </div>
      <div class="max-h-80 overflow-y-auto p-2">
        <div
          v-if="isLoadingList && !hasLoadedList"
          class="flex items-center justify-center gap-2 py-10 text-xs text-muted-foreground"
          role="status"
        >
          <Loader2 class="h-3.5 w-3.5 animate-spin" />
          <span>{{ t("notification.loading") }}</span>
        </div>
        <div
          v-else-if="notificationStore.notifications.length === 0"
          class="py-10 text-center text-xs text-muted-foreground"
        >
          {{ t("notification.noNotifications") }}
        </div>
        <div v-else class="px-1 py-1">
          <button
            v-for="notification in notificationStore.notifications.slice(0, 5)"
            :key="notification.id"
            type="button"
            class="group block w-full rounded-none px-3 py-2.5 text-left transition-colors hover:bg-muted/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            @click="handleClick(notification)"
          >
            <div class="flex items-start gap-2.5">
              <span
                v-if="!notification.isRead"
                class="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-primary"
              />
              <div :class="cn('flex-1 min-w-0', notification.isRead && 'ml-4')">
                <p
                  class="text-sm font-semibold leading-snug text-foreground"
                >
                  {{ localizedNotification(notification).title }}
                </p>
                <p
                  class="mt-1 text-xs leading-relaxed text-muted-foreground line-clamp-2"
                >
                  {{ localizedNotification(notification).body }}
                </p>
              </div>
            </div>
          </button>
        </div>
      </div>
      <div class="border-t px-1 py-1">
        <Button
          variant="ghost"
          size="sm"
          class="w-full h-8 text-xs font-medium text-muted-foreground hover:text-foreground"
          @click="goToNotifications"
        >
          {{ t("notification.viewAll") }}
        </Button>
      </div>
    </PopoverContent>
  </Popover>
</template>
