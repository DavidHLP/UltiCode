<script setup lang="ts">
import { ref, onMounted, onUnmounted } from "vue";
import { useI18n } from "vue-i18n";
import { useNetworkStatus } from "@/composables/useNetworkStatus";
import {
  getQueueLength,
  processQueue,
  type QueuedSubmission,
} from "@/utils/submitQueue";
import { CloudOff, CloudUpload, Loader2 } from "lucide-vue-next";
import { toast } from "vue-sonner";

const { t } = useI18n();
const { isOnline } = useNetworkStatus();

const queueLength = ref(0);
const isSyncing = ref(false);
let syncInterval: ReturnType<typeof setInterval> | null = null;

async function updateQueueLength(): Promise<void> {
  queueLength.value = await getQueueLength();
}

async function syncQueue(): Promise<void> {
  if (!isOnline.value || isSyncing.value) return;

  isSyncing.value = true;

  try {
    const { processed, failed } = await processQueue(
      async (submission: QueuedSubmission) => {
        // Actual submission logic will be handled by the caller
        // For now, just return true to simulate success
        console.log("[OfflineQueue] Would submit:", submission.problemId);
        return true;
      },
    );

    if (processed > 0) {
      toast.success(t("common.pwa.syncComplete", { count: processed }));
    }

    if (failed > 0) {
      toast.error(t("common.pwa.syncFailed"));
    }

    await updateQueueLength();
  } catch (error) {
    toast.error(t("common.pwa.syncFailed"));
    console.error("[OfflineQueue] Sync error:", error);
  } finally {
    isSyncing.value = false;
  }
}

function handleOnline(): void {
  if (queueLength.value > 0) {
    syncQueue();
  }
}

onMounted(() => {
  updateQueueLength();

  // Check for sync opportunity every 30 seconds
  syncInterval = setInterval(() => {
    if (isOnline.value && queueLength.value > 0) {
      syncQueue();
    }
  }, 30000);

  window.addEventListener("online", handleOnline);
});

onUnmounted(() => {
  if (syncInterval) {
    clearInterval(syncInterval);
  }
  window.removeEventListener("online", handleOnline);
});
</script>

<template>
  <div
    v-if="queueLength > 0"
    class="flex items-center gap-2 text-sm text-muted-foreground px-3 py-1.5 bg-muted/50 rounded-md"
    role="status"
    aria-live="polite"
  >
    <template v-if="isSyncing">
      <Loader2 class="size-4 animate-spin" />
      <span>{{ t("common.pwa.syncing") }}</span>
    </template>
    <template v-else-if="!isOnline">
      <CloudOff class="size-4" />
      <span>{{
        t("common.pwa.queuedSubmissions", { count: queueLength })
      }}</span>
    </template>
    <template v-else>
      <CloudUpload class="size-4" />
      <span>{{
        t("common.pwa.queuedSubmissions", { count: queueLength })
      }}</span>
    </template>
  </div>
</template>
