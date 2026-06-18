<script setup lang="ts">
/**
 * ContestAnnouncementBell
 *
 * Bell button in the ContestProblemShell that opens a popover with
 * the contest's announcements. Wired up in Chunk E (P1-5).
 *
 * Data flow:
 *  - Initial list: `getAnnouncements(contestId)` on mount.
 *  - Live updates: `useContestSocket().onAnnouncement(cb)` to prepend
 *    new items while the user is on the page.
 *  - Unread count: stored in `useContestProblemShellStore().announceUnreadCount`
 *    so the shell can show a red badge even when the popover is closed.
 *
 * v1 "unread" policy (per the product spec):
 *   The backend has no per-user lastReadAt. We use "created in the
 *   last 24h" as a proxy — set on initial paint, incremented on
 *   STOMP pushes. "Mark all read" clears the badge in-memory for
 *   the session. A future schema change can replace this with a
 *   proper per-user read state.
 */
import { computed, inject, onMounted, onUnmounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { Bell, Pin } from "lucide-vue-next";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Button } from "@/components/ui/button";
import { useContestSocket } from "@/composables/contest/useContestSocket";
import { getAnnouncements } from "@/api/contest";
import { useContestProblemShellStore } from "@/stores/contestProblemShell";
import { ContestProblemContextKey } from "../problem-context";
import type { ContestAnnouncement } from "@/types/contest";

const { t } = useI18n();
const ctx = inject(ContestProblemContextKey, null);
const shellStore = useContestProblemShellStore();

const open = ref(false);
const announcements = ref<ContestAnnouncement[]>([]);
const loading = ref(false);
const isOpen = computed(() => open.value);

let unsubscribe: (() => void) | null = null;

const contestId = computed(() => ctx?.contestId.value ?? null);

// Initial paint: fetch announcements, derive the 24h unread count.
async function loadAnnouncements(): Promise<void> {
  const id = contestId.value;
  if (!id) return;
  loading.value = true;
  try {
    const list = await getAnnouncements(id);
    announcements.value = list;
    // 24h unread heuristic — see the policy in the spec doc.
    const cutoff = Date.now() - 24 * 60 * 60 * 1000;
    const recent = list.filter((a) => {
      const t = new Date(a.createdAt).getTime();
      return Number.isFinite(t) && t >= cutoff;
    });
    shellStore.setAnnounceUnreadCount(recent.length);
  } catch {
    // Leave announcements empty; the bell still works for STOMP pushes.
  } finally {
    loading.value = false;
  }
}

function handleSocketAnnouncement(payload: {
  id: string;
  contestId: string;
  title: string;
  content: string;
  createdAt: Date | string;
}): void {
  if (payload.contestId !== contestId.value) return;
  // Prepend, dedupe by id, cap at 50 entries (popover scroll).
  const existing = announcements.value.findIndex((a) => a.id === payload.id);
  if (existing >= 0) {
    // Update in place — STOMP resend?
    announcements.value[existing] = {
      id: payload.id,
      contestId: payload.contestId,
      title: payload.title,
      content: payload.content,
      createdAt: typeof payload.createdAt === "string" ? payload.createdAt : payload.createdAt.toISOString(),
      // The WS payload doesn't expose `isPinned`; default to false
      // unless the rest fetch later fills it in.
      isPinned: false,
      updatedAt: new Date().toISOString(),
      author: null,
    };
  } else {
    announcements.value.unshift({
      id: payload.id,
      contestId: payload.contestId,
      title: payload.title,
      content: payload.content,
      createdAt: typeof payload.createdAt === "string" ? payload.createdAt : payload.createdAt.toISOString(),
      isPinned: false,
      updatedAt: new Date().toISOString(),
      author: null,
    });
    // Trim to keep the popover responsive.
    if (announcements.value.length > 50) {
      announcements.value.length = 50;
    }
    // Bump the unread count if the popover is closed.
    if (!isOpen.value) {
      shellStore.setAnnounceUnreadCount(shellStore.announceUnreadCount + 1);
    }
  }
}

function handleOpenChange(value: boolean): void {
  open.value = value;
  if (value) {
    // Mark all as read on open.
    shellStore.markAnnouncementsRead();
  }
}

function formatTime(iso: string | Date | undefined | null): string {
  if (!iso) return "";
  const d = typeof iso === "string" ? new Date(iso) : iso;
  if (!Number.isFinite(d.getTime())) return "";
  return d.toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

// Build the socket once during setup (synchronously) so its internal
// `onMounted` / `onUnmounted` lifecycle hooks register against the
// current component instance. Calling `useContestSocket()` later
// (e.g. from inside an `async` onMounted) means those lifecycle hooks
// run after `currentInstance` is null, which Vue rejects with
// "Lifecycle injection APIs can only be used during execution of
// setup()". The composable's STOMP client itself is a module-level
// singleton, so initialising it here is idempotent.
const socket = useContestSocket();

onMounted(async () => {
  await loadAnnouncements();
  // Subscribe to live pushes. We share the underlying connection with
  // the rest of the contest-aware UI (ranking, etc.) and just listen
  // for our event type.
  try {
    unsubscribe = socket.onAnnouncement(handleSocketAnnouncement);
  } catch {
    // Socket not available (offline / anonymous / not joined);
    // initial fetch still works.
  }
});

onUnmounted(() => {
  unsubscribe?.();
  unsubscribe = null;
});
</script>

<template>
  <Popover :open="isOpen" @update:open="handleOpenChange">
    <PopoverTrigger as-child>
      <button
        type="button"
        class="relative flex h-7 w-7 items-center justify-center rounded-none border border-border text-muted-foreground transition-colors hover:bg-[var(--silver-100)]/50 hover:text-[var(--solarized-base01)] dark:hover:bg-[var(--solarized-base03)]/50 dark:hover:text-[var(--solarized-base1)] cursor-pointer"
        :data-testid="'shell-announcement-bell'"
        :title="t('contest.detail.announcements.title')"
      >
        <Bell class="h-3.5 w-3.5" />
        <span
          v-if="shellStore.announceUnreadCount > 0"
          class="absolute -right-1 -top-1 flex h-3.5 min-w-[14px] items-center justify-center rounded-full bg-[var(--terminal-amber)] px-1 text-[9px] font-black text-white"
          :data-testid="'shell-announcement-badge'"
        >
          {{ shellStore.announceUnreadCount > 99 ? "99+" : shellStore.announceUnreadCount }}
        </span>
      </button>
    </PopoverTrigger>

    <PopoverContent
      align="end"
      :side-offset="6"
      class="w-80 max-h-[60vh] overflow-y-auto rounded-none border border-border bg-[var(--solarized-base3)] p-0 font-mono shadow-lg dark:bg-[var(--solarized-base02)]"
    >
      <header
        class="flex items-center justify-between border-b border-border bg-[var(--silver-100)]/50 px-3 py-2 text-[10px] font-black uppercase tracking-widest text-muted-foreground dark:bg-[var(--solarized-base03)]/50"
      >
        <span>{{ t("contest.detail.announcements.title") }}</span>
        <span
          v-if="shellStore.announceUnreadCount > 0"
          class="text-[var(--terminal-amber)]"
        >
          {{ t("contest.detail.announcements.unread", {
            n: shellStore.announceUnreadCount,
          }) }}
        </span>
      </header>

      <div class="p-2">
        <p
          v-if="loading && announcements.length === 0"
          class="px-2 py-4 text-center text-[11px] text-muted-foreground"
        >
          ...
        </p>
        <p
          v-else-if="announcements.length === 0"
          class="px-2 py-4 text-center text-[11px] text-muted-foreground"
        >
          {{ t("contest.detail.announcements.empty") }}
        </p>
        <ul v-else class="space-y-2">
          <li
            v-for="a in announcements"
            :key="a.id"
            class="border-b border-border/40 pb-2 last:border-b-0 last:pb-0"
          >
            <div class="flex items-start gap-2">
              <Pin
                v-if="a.isPinned"
                class="mt-0.5 h-3 w-3 shrink-0 text-[var(--terminal-amber)]"
              />
              <div class="min-w-0 flex-1">
                <div class="flex items-baseline justify-between gap-2">
                  <h4 class="truncate text-[12px] font-black text-foreground">
                    {{ a.title }}
                  </h4>
                  <time class="shrink-0 text-[10px] text-muted-foreground">
                    {{ formatTime(a.createdAt) }}
                  </time>
                </div>
                <p class="mt-1 line-clamp-3 text-[11px] leading-snug text-muted-foreground">
                  {{ a.content }}
                </p>
                <p
                  v-if="a.author?.username"
                  class="mt-1 text-[10px] text-muted-foreground"
                >
                  — {{ a.author.username }}
                </p>
              </div>
            </div>
          </li>
        </ul>

        <Button
          v-if="shellStore.announceUnreadCount > 0 && !isOpen"
          variant="ghost"
          class="mt-2 h-7 w-full rounded-none text-[10px] uppercase tracking-widest"
          @click="shellStore.markAnnouncementsRead()"
        >
          {{ t("contest.detail.announcements.markRead") }}
        </Button>
      </div>
    </PopoverContent>
  </Popover>
</template>