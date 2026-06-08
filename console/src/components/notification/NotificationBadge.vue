<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { Bell, Loader2 } from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import { useNotificationStore } from "@/stores/notification";
import { useAuthStore } from "@/stores/auth";
import { useNotificationI18n } from "@/composables/useNotificationI18n";
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

const isOpen = ref(false);
const isLoadingList = ref(false);
const hasLoadedList = ref(false);
let loadRequestId = 0;

async function refreshList() {
  if (!authStore.isAuthenticated) {
    notificationStore.$patch({
      notifications: [],
      total: 0,
      page: 1,
      totalPages: 1,
    });
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
          class="absolute -right-1 -top-1 h-5 min-w-5 px-1 text-[10px] font-bold"
        >
          {{ unreadLabel }}
        </Badge>
      </Button>
    </PopoverTrigger>
    <PopoverContent align="end" class="w-80 p-0">
      <div class="flex items-center justify-between border-b px-4 py-3">
        <h4 class="text-sm font-medium">{{ t("notification.title") }}</h4>
        <ConnectionStatus />
      </div>
      <div class="max-h-80 overflow-y-auto p-2">
        <div
          v-if="isLoadingList && !hasLoadedList"
          class="flex items-center justify-center gap-2 py-8 text-sm text-muted-foreground"
          role="status"
        >
          <Loader2 class="h-4 w-4 animate-spin" />
          <span>{{ t("notification.loading") }}</span>
        </div>
        <div
          v-else-if="notificationStore.notifications.length === 0"
          class="py-8 text-center text-sm text-muted-foreground"
        >
          {{ t("notification.noNotifications") }}
        </div>
        <div v-else class="space-y-1">
          <RouterLink
            v-for="notification in notificationStore.notifications.slice(0, 5)"
            :key="notification.id"
            :to="notification.link || '#'"
            class="block rounded-none p-2 transition-colors hover:bg-muted"
          >
            <div class="flex items-start gap-2">
              <span
                v-if="!notification.isRead"
                class="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-primary"
              />
              <div
                :class="cn('flex-1 space-y-1', notification.isRead && 'ml-4')"
              >
                <p class="text-sm font-medium leading-none">
                  {{ localizedNotification(notification).value.title }}
                </p>
                <p class="text-xs text-muted-foreground line-clamp-2">
                  {{ localizedNotification(notification).value.body }}
                </p>
              </div>
            </div>
          </RouterLink>
        </div>
      </div>
      <div class="border-t p-2">
        <Button
          variant="ghost"
          size="sm"
          class="w-full"
          @click="goToNotifications"
        >
          {{ t("notification.viewAll") }}
        </Button>
      </div>
    </PopoverContent>
  </Popover>
</template>
