<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { renderMarkdown } from "@ulticode/markdown-utils";
import { agentApi, type AgentCapabilities, type AgentConversationTurn } from "@/api/agent";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Textarea } from "@/components/ui/textarea";
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { useAgentConversations } from "@/composables/useAgentConversations";

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const workspace = useAgentConversations();
const enabled = import.meta.env.VITE_AGENT_REVIEW_ENABLED === "true";
const capability = ref<AgentCapabilities | null>(null);
const capabilityLoading = ref(false);
const capabilityError = ref(false);
const sourceConsent = ref(false);
const draft = ref("");
const submitting = ref(false);
const historyOpen = ref(false);
const transcript = ref<HTMLElement | null>(null);
const conversationIdQuery = computed(() => {
  const value = route.query.conversationId;
  return Array.isArray(value) ? value[0] : value;
});
const submissionIdQuery = computed(() => {
  const value = route.query.submissionId;
  return Array.isArray(value) ? value[0] : value;
});
const canSend = computed(() => enabled
  && capability.value?.analysisAvailable === true
  && Boolean(workspace.sourceSubmissionId.value)
  && !workspace.busy.value
  && !submitting.value
  && (!capability.value || capability.value.sourceMode !== "owner_source" || sourceConsent.value));
const showConsent = computed(() => capability.value?.sourceMode === "owner_source");
const emptyTitle = computed(() => t(workspace.sourceSubmissionId.value
  ? "agent.workspace.readyTitle"
  : "agent.workspace.emptyTitle"));
const emptyDescription = computed(() => t(workspace.sourceSubmissionId.value
  ? "agent.workspace.readyDescription"
  : "agent.workspace.emptyDescription"));
const currentTurnIsRunning = computed(() => workspace.currentTurn.value?.status === "running");

function validUuid(value: unknown): value is string {
  return typeof value === "string"
    && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
}

function updateRoute(conversationId?: string, submissionId?: string) {
  void router.replace({
    name: "agent-workspace",
    query: conversationId ? { conversationId } : submissionId ? { submissionId } : {},
  });
}

watch([conversationIdQuery, submissionIdQuery], ([conversationId, submissionId]) => {
  if (!enabled) return;
  if (validUuid(conversationId)) {
    if (workspace.conversation.value?.conversationId !== conversationId) {
      sourceConsent.value = false;
      void workspace.openConversation(conversationId);
    }
    return;
  }
  const selectedSubmission = validUuid(submissionId) ? submissionId : "";
  if (workspace.conversation.value || workspace.sourceSubmissionId.value !== selectedSubmission) {
    sourceConsent.value = false;
    workspace.startNewConversation(selectedSubmission);
  }
}, { immediate: true });

watch(() => workspace.conversation.value?.conversationId, (conversationId) => {
  if (conversationId && conversationId !== conversationIdQuery.value) updateRoute(conversationId);
});

watch(workspace.turns, async () => {
  await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
  if (transcript.value) transcript.value.scrollTop = transcript.value.scrollHeight;
}, { deep: true });

onMounted(async () => {
  if (!enabled) return;
  capabilityLoading.value = true;
  const capabilityRequest = agentApi.getCapabilities().then((value) => {
    capability.value = value;
  }).catch(() => {
    capabilityError.value = true;
  }).finally(() => {
    capabilityLoading.value = false;
  });
  await Promise.all([capabilityRequest, workspace.loadConversations(true)]);
});

async function send() {
  const question = draft.value.trim();
  if (!canSend.value || question.length < 2 || question.length > 200) return;
  const conversationBefore = workspace.conversation.value?.conversationId;
  const turnCountBefore = workspace.turns.value.length;
  const consent = sourceConsent.value;
  sourceConsent.value = false;
  submitting.value = true;
  try {
    await workspace.sendTurn(question, consent);
    const turnWasSaved = conversationBefore
      ? workspace.turns.value.length > turnCountBefore
      : Boolean(workspace.conversation.value);
    if (turnWasSaved) draft.value = "";
  } finally {
    submitting.value = false;
  }
}

async function selectConversation(id: string) {
  if (submitting.value) return;
  historyOpen.value = false;
  sourceConsent.value = false;
  await workspace.openConversation(id);
}

function startNew() {
  historyOpen.value = false;
  if (submitting.value || !workspace.startNewConversation()) return;
  sourceConsent.value = false;
  draft.value = "";
  updateRoute(undefined, workspace.sourceSubmissionId.value || undefined);
}

async function reloadConversation() {
  const id = workspace.conversation.value?.conversationId;
  if (!id) return;
  sourceConsent.value = false;
  await workspace.openConversation(id);
}

async function deleteConversation() {
  if (!workspace.conversation.value || workspace.busy.value) return;
  if (!window.confirm(t("agent.workspace.deleteConfirm"))) return;
  const submissionId = workspace.sourceSubmissionId.value;
  if (await workspace.deleteCurrentConversation()) {
    sourceConsent.value = false;
    draft.value = "";
    updateRoute(undefined, submissionId || undefined);
  }
}

async function retryTurn(turn: AgentConversationTurn) {
  const consent = sourceConsent.value;
  sourceConsent.value = false;
  await workspace.retryTurn(turn, consent);
}

function formatDate(value: string) {
  return new Date(value).toLocaleString(undefined, { dateStyle: "short", timeStyle: "short" });
}
</script>

<template>
  <main v-if="!enabled" class="grid min-h-dvh place-items-center bg-background p-6 text-center">
    <p class="max-w-md text-sm text-muted-foreground">{{ t("agent.workspace.unavailable") }}</p>
  </main>
  <main v-else class="flex h-dvh min-h-0 overflow-hidden bg-background text-foreground">
    <aside class="hidden w-72 shrink-0 flex-col border-r border-border bg-muted/30 lg:flex">
      <div class="flex items-center justify-between border-b border-border px-4 py-4">
        <div>
          <p class="text-xs font-semibold uppercase tracking-[0.16em] text-primary">UltiCode</p>
          <h1 class="mt-1 text-lg font-semibold">{{ t("agent.workspace.title") }}</h1>
        </div>
        <Button variant="outline" size="sm" :disabled="workspace.busy.value || submitting" @click="startNew">
          {{ t("agent.workspace.new") }}
        </Button>
      </div>
      <div class="min-h-0 flex-1 overflow-y-auto p-3">
        <div class="mb-2 flex items-center justify-between px-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
          <span>{{ t("agent.workspace.history") }}</span>
        </div>
        <p v-if="!workspace.conversations.value.length && !workspace.loadingHistory.value" class="px-2 py-3 text-sm text-muted-foreground">
          {{ t("agent.workspace.noHistory") }}
        </p>
        <Button v-for="item in workspace.conversations.value" :key="item.conversationId" type="button" variant="ghost"
          class="mb-1 h-auto w-full justify-start whitespace-normal border px-3 py-2 text-left transition-colors"
          :class="workspace.conversation.value?.conversationId === item.conversationId ? 'border-primary/40 bg-background' : 'border-transparent hover:bg-background/70'"
          :disabled="workspace.busy.value || submitting" @click="selectConversation(item.conversationId)">
          <span class="block min-w-0 text-left">
            <span class="block truncate text-sm font-medium">{{ item.title }}</span>
            <span class="mt-1 block truncate text-xs text-muted-foreground">{{ item.lastQuestion }}</span>
            <span class="mt-2 block text-[11px] text-muted-foreground">{{ formatDate(item.updatedAt) }} · {{ item.turnCount }} {{ t("agent.workspace.turns") }}</span>
          </span>
        </Button>
        <Button v-if="workspace.hasMoreConversations.value" type="button" variant="ghost" class="mt-2 w-full text-sm text-primary"
          :disabled="workspace.loadingHistory.value" @click="workspace.loadConversations()">
          {{ workspace.loadingHistory.value ? t("agent.workspace.loading") : t("agent.workspace.loadMore") }}
        </Button>
      </div>
      <div class="border-t border-border px-4 py-3 text-xs leading-relaxed text-muted-foreground">
        {{ t("agent.workspace.retention") }}
      </div>
    </aside>

    <section class="flex min-w-0 flex-1 flex-col">
      <header class="flex min-h-16 flex-wrap items-center justify-between gap-2 border-b border-border px-4 py-3 sm:px-6">
        <div class="flex w-full min-w-0 items-center gap-3 sm:w-auto sm:flex-1">
          <Button variant="outline" size="sm" class="lg:hidden" :aria-expanded="historyOpen" @click="historyOpen = !historyOpen">
            {{ t("agent.workspace.history") }}
          </Button>
          <div class="min-w-0">
            <h2 class="truncate font-semibold">{{ workspace.conversation.value?.title ?? t("agent.workspace.emptyTitle") }}</h2>
            <p class="truncate text-xs text-muted-foreground">
              <template v-if="workspace.sourceSubmissionId.value">{{ t("agent.workspace.submission") }} · {{ workspace.sourceSubmissionId.value }}</template>
              <template v-else>{{ t("agent.workspace.objectHint") }}</template>
            </p>
          </div>
        </div>
        <div class="flex shrink-0 items-center gap-2">
          <Badge v-if="capability" variant="secondary" class="hidden max-w-md whitespace-normal text-left sm:inline-flex">
            {{ t(`agent.sourceModes.${capability.sourceMode}`) }}
          </Badge>
          <Button v-if="workspace.conversation.value" type="button" variant="ghost" size="sm"
            :disabled="workspace.loadingConversation.value" @click="reloadConversation">
            {{ t("agent.workspace.reload") }}
          </Button>
          <Button v-if="workspace.conversation.value" type="button" variant="ghost" size="sm"
            class="text-destructive hover:text-destructive" :disabled="workspace.busy.value || workspace.deleting.value" @click="deleteConversation">
            {{ workspace.deleting.value ? t("agent.workspace.deleting") : t("agent.workspace.delete") }}
          </Button>
        </div>
      </header>

      <Sheet v-model:open="historyOpen">
        <SheetContent side="left" class="w-[min(20rem,85vw)] gap-0 p-0 sm:max-w-sm">
          <SheetHeader class="border-b border-border">
            <SheetTitle>{{ t("agent.workspace.history") }}</SheetTitle>
          </SheetHeader>
          <div class="flex min-h-0 flex-1 flex-col overflow-y-auto p-3">
            <Button variant="outline" class="mb-3" :disabled="workspace.busy.value || submitting" @click="startNew">
              {{ t("agent.workspace.new") }}
            </Button>
            <Button v-for="item in workspace.conversations.value" :key="item.conversationId" type="button"
              variant="ghost" class="mb-1 h-auto justify-start whitespace-normal px-3 py-2 text-left"
              :class="workspace.conversation.value?.conversationId === item.conversationId ? 'border border-primary/40 bg-muted/50' : 'border border-transparent'"
              :disabled="workspace.busy.value || submitting" @click="selectConversation(item.conversationId)">
              <span class="block min-w-0 text-left">
                <span class="block truncate text-sm font-medium">{{ item.title }}</span>
                <span class="mt-1 block truncate text-xs text-muted-foreground">{{ item.lastQuestion }}</span>
              </span>
            </Button>
            <Button v-if="workspace.hasMoreConversations.value" type="button" variant="ghost" class="mt-2 text-sm text-primary"
              :disabled="workspace.loadingHistory.value" @click="workspace.loadConversations()">
              {{ workspace.loadingHistory.value ? t("agent.workspace.loading") : t("agent.workspace.loadMore") }}
            </Button>
          </div>
          <p class="border-t border-border p-4 text-xs text-muted-foreground">{{ t("agent.workspace.retention") }}</p>
        </SheetContent>
      </Sheet>

      <div v-if="capabilityLoading" role="status" aria-live="polite" class="border-b border-border px-6 py-2 text-sm text-muted-foreground">
        {{ t("agent.workspace.loading") }}
      </div>
      <p v-else-if="capabilityError" role="alert" class="border-b border-destructive/40 px-6 py-3 text-sm text-destructive">
        {{ t("agent.workspace.errors.requestFailed") }}
      </p>
      <p v-else-if="capability && !capability.conversationHistoryAvailable" role="alert" class="border-b border-border px-6 py-3 text-sm text-muted-foreground">
        {{ t("agent.workspace.unavailable") }}
      </p>
      <p v-else-if="capability && !capability.analysisAvailable" role="status" class="border-b border-border px-6 py-3 text-sm text-muted-foreground">
        {{ t("agent.analysisUnavailable") }}
      </p>
      <p v-if="workspace.error.value" role="alert" class="border-b border-destructive/40 px-6 py-2 text-sm text-destructive">
        {{ t(`agent.workspace.errors.${workspace.error.value}`) }}
      </p>

      <div ref="transcript" class="min-h-0 flex-1 overflow-y-auto px-4 py-6 sm:px-8">
        <div v-if="workspace.loadingConversation.value" role="status" class="mx-auto max-w-3xl py-12 text-center text-sm text-muted-foreground">
          {{ t("agent.workspace.loading") }}
        </div>
        <div v-else-if="!workspace.conversation.value" class="mx-auto flex h-full max-w-2xl flex-col items-center justify-center text-center">
          <p class="text-xs font-semibold uppercase tracking-[0.16em] text-primary">{{ t("agent.workspace.eyebrow") }}</p>
          <h2 class="mt-3 text-2xl font-semibold tracking-tight">{{ emptyTitle }}</h2>
          <p class="mt-3 max-w-lg text-sm leading-relaxed text-muted-foreground">{{ emptyDescription }}</p>
          <Button v-if="!workspace.sourceSubmissionId.value" as-child class="mt-6">
            <RouterLink :to="{ name: 'personal-submissions' }">{{ t("agent.workspace.findSubmission") }}</RouterLink>
          </Button>
        </div>
        <div v-else class="mx-auto flex max-w-3xl flex-col gap-7">
          <button v-if="workspace.hasMoreTurns.value" type="button" class="self-center rounded-md px-3 py-2 text-sm text-primary hover:bg-muted"
            :disabled="workspace.loadingConversation.value" @click="workspace.loadOlderTurns()">
            {{ t("agent.workspace.loadOlder") }}
          </button>
          <article v-for="turn in workspace.turns.value" :key="turn.turnId" class="min-w-0 space-y-4">
            <div class="ml-auto max-w-[90%] rounded-2xl rounded-br-sm bg-primary/10 px-4 py-3 text-sm leading-relaxed sm:max-w-[80%]">
              <p class="whitespace-pre-wrap break-words">{{ turn.question }}</p>
            </div>
            <div class="max-w-[90%] space-y-3 text-sm leading-7 sm:max-w-[80%]">
              <div v-if="turn.answer" class="prose prose-sm max-w-none break-words text-foreground" v-html="renderMarkdown(turn.answer)" />
              <p v-else-if="turn.status === 'running'" role="status" aria-live="polite" class="text-muted-foreground">
                {{ t("agent.workspace.generating") }}
              </p>
              <p v-else-if="turn.status === 'queued'" class="text-muted-foreground">{{ t("agent.workspace.queued") }}</p>
              <p v-else-if="turn.status === 'failed'" role="alert" class="text-destructive">{{ t("agent.workspace.turnFailed") }}</p>
              <p v-else-if="turn.status === 'cancelled'" role="status" class="text-muted-foreground">{{ t("agent.workspace.cancelled") }}</p>
              <ol v-if="turn.citations.length" class="space-y-2 border-l-2 border-primary/30 pl-4 text-xs text-muted-foreground">
                <li v-for="citation in turn.citations" :key="citation.chunk_id" class="break-words">
                  <strong class="font-medium text-foreground">{{ citation.claim }}</strong>
                  <span class="block">{{ citation.source_path }} · {{ citation.source_position }}</span>
                </li>
              </ol>
              <details v-if="turn.trace.length" class="rounded-md border border-border px-3 py-2 text-xs">
                <summary class="cursor-pointer font-medium">{{ t("agent.workspace.activity") }}</summary>
                <ul class="mt-2 space-y-1 text-muted-foreground">
                  <li v-for="(event, index) in turn.trace" :key="`${turn.turnId}-${index}`">
                    {{ event.tool_name ?? t("agent.workspace.analysisStep") }}
                    <span v-if="event.failed"> · {{ t("agent.statuses.failed") }}</span>
                  </li>
                </ul>
              </details>
              <details v-if="workspace.busy.value && workspace.currentTurn.value?.turnId === turn.turnId && workspace.events.value.length"
                open class="rounded-md border border-border px-3 py-2 text-xs">
                <summary class="cursor-pointer font-medium">{{ t("agent.workspace.activity") }}</summary>
                <ul class="mt-2 space-y-1 text-muted-foreground">
                  <li v-for="event in workspace.events.value" :key="event.seq">{{ event.detail.toolName ?? event.kind }}</li>
                </ul>
              </details>
              <div v-if="['queued', 'failed'].includes(turn.status) && turn.turnIndex === workspace.turns.value.at(-1)?.turnIndex && capability?.analysisAvailable"
                class="flex flex-wrap items-center gap-2">
                <Button type="button" variant="outline" size="sm" :disabled="workspace.busy.value || (showConsent && !sourceConsent)" @click="retryTurn(turn)">
                  {{ t("agent.workspace.retry") }}
                </Button>
              </div>
            </div>
          </article>
        </div>
      </div>

      <form class="border-t border-border bg-background px-4 py-4 sm:px-8" @submit.prevent="send">
        <div class="mx-auto max-w-3xl rounded-xl border border-border bg-card shadow-sm focus-within:ring-2 focus-within:ring-ring/40">
          <label class="sr-only" for="agent-conversation-draft">{{ t("agent.workspace.composerLabel") }}</label>
          <Textarea id="agent-conversation-draft" v-model="draft" rows="3" maxlength="200"
            class="max-h-40 min-h-20 w-full resize-y rounded-none border-0 bg-transparent px-4 py-3 text-sm shadow-none outline-none focus-visible:ring-0"
            :placeholder="t('agent.workspace.composerPlaceholder')"
            :disabled="workspace.busy.value || !capability?.analysisAvailable || !workspace.sourceSubmissionId.value"
            @keydown.enter.exact.prevent="send" />
          <div class="flex flex-wrap items-center justify-between gap-3 border-t border-border px-3 py-2">
            <div class="min-w-0 flex-1 space-y-2">
              <p class="text-xs leading-relaxed text-muted-foreground">{{ t("agent.workspace.retention") }}</p>
              <label v-if="showConsent" class="flex max-w-2xl items-start gap-2 text-xs leading-relaxed text-muted-foreground">
                <input v-model="sourceConsent" type="checkbox" class="mt-0.5 accent-primary" :disabled="workspace.busy.value || submitting" />
                <span>{{ t("agent.sourceConsent") }}</span>
              </label>
            </div>
            <div class="ml-auto flex items-center gap-2">
              <Button v-if="currentTurnIsRunning" type="button" variant="outline" size="sm"
                :disabled="workspace.canceling.value" @click="workspace.cancelTurn()">
                {{ workspace.canceling.value ? t("agent.workspace.cancelling") : t("agent.workspace.cancelTurn") }}
              </Button>
              <Button type="submit" size="sm" :disabled="!canSend || draft.trim().length < 2">
                {{ workspace.busy.value ? t("agent.workspace.generating") : t("agent.workspace.send") }}
              </Button>
            </div>
          </div>
        </div>
        <p class="mx-auto mt-2 max-w-3xl text-center text-[11px] text-muted-foreground">{{ t("agent.workspace.disclaimer") }}</p>
      </form>
    </section>
  </main>
</template>
