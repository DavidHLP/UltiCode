<script setup lang="ts">
import { Checkbox } from "@ulticode/design-system/ui/checkbox";
import { FieldLabel } from "@ulticode/design-system/ui/field";

import { computed, ref, watch, nextTick } from "vue";
import { useMediaQuery } from "@vueuse/core";
import {
  ResizablePanelGroup,
  ResizablePanel,
  ResizableHandle,
} from "@ulticode/design-system/ui/resizable";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { renderMarkdown } from "@ulticode/markdown-utils";
import { getStatusLabelI18nKey } from "@ulticode/submission-status";
import { fetchSubmission, fetchUserSubmissions } from "@/api/submission";
import type { SubmissionRecord } from "@/types/submission";
import { useAuthStore } from "@/stores/auth";
import { isAgentReviewEligible } from "@/views/problems/submissions/agentReviewPolicy";
import {
  agentApi,
  type AgentCapabilities,
  type AgentConversationTurn,
  type AgentAnalysisContext,
} from "@/api/agent";
import { Button } from "@ulticode/design-system/ui/button";
import {
  InputGroup,
  InputGroupTextarea,
  InputGroupInput,
  InputGroupAddon,
  InputGroupButton,
} from "@ulticode/design-system/ui/input-group";
import { Message, MessageContent } from "@ulticode/design-system/ui/message";
import { Bubble, BubbleContent } from "@ulticode/design-system/ui/bubble";
import {
  Trace,
  TraceCollapsible,
  TraceCollapsibleTrigger,
  TraceCollapsibleContent,
  TraceLine,
  TraceIcon,
  TraceTitle,
  TraceDescription,
} from "@ulticode/design-system/ui/trace";
import { Input } from "@ulticode/design-system/ui/input";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
} from "@ulticode/design-system/ui/dropdown-menu";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@ulticode/design-system/ui/alert-dialog";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@ulticode/design-system/ui/tooltip";
import {
  HoverCard,
  HoverCardContent,
  HoverCardTrigger,
} from "@ulticode/design-system/ui/hover-card";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@ulticode/design-system/ui/collapsible";
import {
  ArrowDown,
  ArrowUp,
  PanelLeft,
  Sparkles,
  SquarePen,
  Ellipsis,
  RefreshCw,
  Trash2,
  CircleAlert,
  ChevronDown,
  BookOpen,
  Wrench,
  LoaderCircle,
  Clock,
  Search,
  X,
} from "@lucide/vue";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@ulticode/design-system/ui/sheet";
import { useAgentConversations } from "@/composables/useAgentConversations";
import AgentContextPicker from "./components/AgentContextPicker.vue";
import { Badge } from "@ulticode/design-system/ui/badge";
import {
  Popover,
  PopoverTrigger,
  PopoverContent,
} from "@ulticode/design-system/ui/popover";
import {
  Item,
  ItemContent,
  ItemTitle,
  ItemDescription,
  ItemActions,
} from "@ulticode/design-system/ui/item";
import AgentMessageActions from "./components/AgentMessageActions.vue";
import AgentCitationPreview from "./components/AgentCitationPreview.vue";

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const workspace = useAgentConversations();
const auth = useAuthStore();
const enabled = computed(() => capability.value?.featureEnabled !== false);
const capability = ref<AgentCapabilities | null>(null);
const capabilityLoading = ref(false);
const capabilityError = ref(false);
const sourceConsent = ref(false);
const selectedContext = ref<AgentAnalysisContext>({
  includeProblem: false,
  comments: [],
});
const selectedProblemTitle = ref("");
const contextPicker = ref<InstanceType<typeof AgentContextPicker> | null>(null);
const contextOverviewOpen = ref(false);
watch(
  [workspace.sourceSubmissionId, () => auth.user?.id],
  async ([id], _old, onCleanup) => {
    selectedContext.value = { includeProblem: false, comments: [] };
    selectedProblemTitle.value = "";
    let current = true;
    onCleanup(() => {
      current = false;
    });
    if (!id) return;
    try {
      const item = await fetchSubmission(id);
      if (current)
        selectedProblemTitle.value =
          item.problem?.title ?? `#${item.problem_id}`;
    } catch {
      /* The context picker exposes a retryable lookup error. */
    }
  },
  { immediate: true, flush: "sync" },
);
function applyContext(
  submission: SubmissionRecord,
  context: AgentAnalysisContext,
) {
  if (workspace.busy.value || submitting.value) return;
  if (submission.id !== workspace.sourceSubmissionId.value) {
    const question = draft.value;
    selectSubmission(submission.id);
    draft.value = question;
  }
  selectedContext.value = context;
  sourceConsent.value = false;
  selectedProblemTitle.value =
    submission.problem?.title ?? `#${submission.problem_id}`;
}
const draft = ref("");
const submitting = ref(false);
let sendGeneration = 0;
watch(
  () => workspace.busy.value,
  (busy) => {
    if (busy && submitting.value) {
      submitting.value = false;
      draft.value = "";
    }
  },
);
const historyOpen = ref(false);
const desktopHistoryOpen = ref(true);
const desktopViewport = useMediaQuery("(min-width: 1024px)");
const sidebarSize = ref(20);
async function toggleDesktopHistory() {
  desktopHistoryOpen.value = !desktopHistoryOpen.value;
  await nextTick();
  document
    .querySelector<HTMLButtonElement>('[aria-controls="agent-desktop-history"]')
    ?.focus();
}
async function clearHistorySearch() {
  historySearch.value = "";
  await nextTick();
  document.getElementById("agent-history-search")?.focus();
}
const deleteOpen = ref(false);
const startingConversation = computed(
  () =>
    Boolean(workspace.sourceSubmissionId.value) &&
    !workspace.conversation.value &&
    !workspace.loadingConversation.value,
);
const historySearch = ref("");
function toolLabel(name: unknown) {
  if (name === "get_submission_source") return t("agent.workspace.readSource");
  if (name === "search_evidence") return t("agent.workspace.searchEvidence");
  return typeof name === "string" ? name : t("agent.workspace.analysisStep");
}
function failureMessage(turn: AgentConversationTurn) {
  const reason = turn.failureReason ?? "";
  if (
    reason === "ModelProtocolError" ||
    reason.startsWith("model_answer_") ||
    reason === "model_citation_invalid"
  )
    return t("agent.workspace.modelResponseInvalid");
  if (
    ["context_unavailable", "context_mismatch", "context_too_large"].includes(
      reason,
    )
  )
    return t("agent.context.invalid");
  if (reason === "answer_source_reference_unverified")
    return t("agent.workspace.sourceResponseBlocked");
  if (
    reason === "answer_source_evidence_missing" ||
    reason === "answer_source_diagnosis_unverified" ||
    reason.startsWith("citation_")
  )
    return t("agent.workspace.evidenceNotVerified");
  return t("agent.workspace.turnFailed");
}
const followLatest = ref(true);
const filteredConversations = computed(() => {
  const query = historySearch.value.trim().toLocaleLowerCase();
  return workspace.conversations.value.filter((item) =>
    `${item.title} ${item.lastQuestion}`.toLocaleLowerCase().includes(query),
  );
});
function trackScroll() {
  const el = transcript.value;
  if (el)
    followLatest.value = el.scrollHeight - el.scrollTop - el.clientHeight < 80;
}
async function scrollToLatest() {
  followLatest.value = true;
  await nextTick();
  if (transcript.value)
    transcript.value.scrollTop = transcript.value.scrollHeight;
}
function composerKeydown(event: KeyboardEvent) {
  if (
    event.key !== "Enter" ||
    event.shiftKey ||
    event.ctrlKey ||
    event.altKey ||
    event.metaKey ||
    event.isComposing ||
    event.keyCode === 229
  )
    return;
  event.preventDefault();
  void send();
}
function useSuggestion(key: string) {
  if (workspace.busy.value || submitting.value || draft.value.trim()) return;
  draft.value = t(key);
  document.getElementById("agent-conversation-draft")?.focus();
}
async function reuseQuestion(turn: AgentConversationTurn) {
  if (workspace.busy.value || submitting.value || draft.value.trim()) return;
  draft.value = turn.question;
  sourceConsent.value = false;
  selectedContext.value = {
    includeProblem: turn.context?.includeProblem ?? false,
    comments: [...(turn.context?.comments ?? [])],
  };
  await nextTick();
  document.getElementById("agent-conversation-draft")?.focus();
}
function changeMaterials() {
  contextOverviewOpen.value = false;
  contextPicker.value?.show();
}
function clearReferences() {
  selectedContext.value = { includeProblem: false, comments: [] };
  sourceConsent.value = false;
}
const transcript = ref<HTMLElement | null>(null);
const recentSubmissions = ref<SubmissionRecord[]>([]);
const submissionsLoading = ref(false);
const submissionsError = ref(false);
const submissionsRefresh = ref(0);
const conversationIdQuery = computed(() => {
  const value = route.query.conversationId;
  return Array.isArray(value) ? value[0] : value;
});
const submissionIdQuery = computed(() => {
  const value = route.query.submissionId;
  return Array.isArray(value) ? value[0] : value;
});
const canSend = computed(
  () =>
    enabled.value &&
    capability.value?.analysisAvailable === true &&
    Boolean(workspace.sourceSubmissionId.value) &&
    !workspace.busy.value &&
    !submitting.value &&
    (!capability.value ||
      capability.value.sourceMode !== "owner_source" ||
      sourceConsent.value),
);
const showConsent = computed(
  () => capability.value?.sourceMode === "owner_source",
);
const emptyTitle = computed(() =>
  t(
    workspace.sourceSubmissionId.value
      ? "agent.workspace.readyTitle"
      : "agent.workspace.emptyTitle",
  ),
);
const emptyDescription = computed(() =>
  t(
    workspace.sourceSubmissionId.value
      ? "agent.workspace.readyDescription"
      : "agent.workspace.emptyDescription",
  ),
);
const currentTurnIsRunning = computed(
  () => workspace.currentTurn.value?.status === "running",
);

function validUuid(value: unknown): value is string {
  return (
    typeof value === "string" &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
      value,
    )
  );
}

function updateRoute(conversationId?: string, submissionId?: string) {
  void router.replace({
    name: "agent-workspace",
    query: conversationId
      ? { conversationId }
      : submissionId
        ? { submissionId }
        : {},
  });
}

watch(
  [conversationIdQuery, submissionIdQuery, enabled],
  ([conversationId, submissionId]) => {
    if (!enabled.value) return;
    if (validUuid(conversationId)) {
      if (workspace.conversation.value?.conversationId !== conversationId) {
        sourceConsent.value = false;
        void workspace.openConversation(conversationId);
      }
      return;
    }
    const selectedSubmission = validUuid(submissionId) ? submissionId : "";
    if (
      workspace.conversation.value ||
      workspace.sourceSubmissionId.value !== selectedSubmission
    ) {
      sourceConsent.value = false;
      workspace.startNewConversation(selectedSubmission);
    }
  },
  { immediate: true },
);

watch(
  () => workspace.conversation.value?.conversationId,
  (conversationId) => {
    if (conversationId && conversationId !== conversationIdQuery.value)
      updateRoute(conversationId);
  },
);

watch(
  workspace.turns,
  async () => {
    const shouldFollow = followLatest.value;
    await new Promise<void>((resolve) =>
      requestAnimationFrame(() => resolve()),
    );
    if (shouldFollow) scrollToLatest();
  },
  { deep: true },
);

watch(
  enabled,
  async (active) => {
    if (!active) return;
    capabilityLoading.value = true;
    const capabilityRequest = agentApi
      .getCapabilities()
      .then((value) => {
        capability.value = value;
      })
      .catch(() => {
        capabilityError.value = true;
      })
      .finally(() => {
        capabilityLoading.value = false;
      });
    await capabilityRequest;
    if (!capabilityError.value && capability.value?.featureEnabled !== false) {
      await workspace.loadConversations(true);
    }
  },
  { immediate: true },
);

watch(
  [
    () => auth.user?.id,
    () => !workspace.sourceSubmissionId.value && !workspace.conversation.value,
    () => enabled.value && capability.value?.analysisAvailable === true,
    submissionsRefresh,
  ],
  async ([userId, empty, available], _previous, onCleanup) => {
    let current = true;
    onCleanup(() => {
      current = false;
    });
    recentSubmissions.value = [];
    submissionsError.value = false;
    submissionsLoading.value = false;
    if (!userId || !empty || !available) return;
    submissionsLoading.value = true;
    try {
      const page = await fetchUserSubmissions();
      if (!current) return;
      // shortcut: inspect only the latest three; use the full submissions page for older results.
      const details = await Promise.all(
        page.items.slice(0, 3).map((item) => fetchSubmission(item.id)),
      );
      if (current) {
        recentSubmissions.value = details.filter((item) =>
          isAgentReviewEligible(item, userId, true),
        );
      }
    } catch {
      if (current) submissionsError.value = true;
    } finally {
      if (current) submissionsLoading.value = false;
    }
  },
  { immediate: true },
);

function selectSubmission(id: string) {
  if (submitting.value || !workspace.startNewConversation(id)) return;
  followLatest.value = true;
  sourceConsent.value = false;
  draft.value = "";
  updateRoute(undefined, id);
}

async function send() {
  const question = draft.value.trim();
  if (!canSend.value || question.length < 2 || question.length > 200) return;
  followLatest.value = true;
  const token = ++sendGeneration;
  const consent = sourceConsent.value;
  sourceConsent.value = false;
  submitting.value = true;
  try {
    if (
      selectedContext.value.includeProblem ||
      selectedContext.value.comments.length
    ) {
      await workspace.sendTurn(question, consent, selectedContext.value);
    } else await workspace.sendTurn(question, consent);
  } finally {
    if (token === sendGeneration) submitting.value = false;
  }
}

async function selectConversation(id: string) {
  if (submitting.value) return;
  draft.value = "";
  followLatest.value = true;
  historyOpen.value = false;
  sourceConsent.value = false;
  await workspace.openConversation(id);
}

function startNew() {
  historyOpen.value = false;
  if (submitting.value || !workspace.startNewConversation()) return;
  followLatest.value = true;
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
  const submissionId = workspace.sourceSubmissionId.value;
  if (await workspace.deleteCurrentConversation()) {
    sourceConsent.value = false;
    draft.value = "";
    updateRoute(undefined, submissionId || undefined);
  }
}

async function retryTurn(turn: AgentConversationTurn) {
  followLatest.value = true;
  const consent = sourceConsent.value;
  sourceConsent.value = false;
  await workspace.retryTurn(turn, consent);
}

function formatDate(value: string | number) {
  const date = new Date(typeof value === "number" ? value * 1000 : value);
  if (!Number.isFinite(date.getTime())) return "—";
  return date.toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    timeZoneName: "short",
  });
}
</script>

<template>
  <main
    v-if="!enabled"
    class="grid min-h-dvh place-items-center bg-background p-6 text-center"
  >
    <p class="max-w-md text-sm text-muted-foreground">
      {{ t("agent.workspace.unavailable") }}
    </p>
  </main>
  <main
    v-else
    class="flex h-dvh min-h-0 overflow-hidden bg-background text-foreground"
  >
    <ResizablePanelGroup direction="horizontal" class="min-h-0">
      <ResizablePanel
        v-if="desktopViewport && desktopHistoryOpen"
        id="agent-history-panel"
        :order="0"
        :default-size="sidebarSize"
        :min-size="16"
        :max-size="32"
        @resize="sidebarSize = $event"
      >
        <aside
          id="agent-desktop-history"
          class="flex h-full min-w-0 flex-col bg-muted/40"
        >
          <div
            class="flex h-16 shrink-0 items-center justify-between gap-2 border-b border-border/50 px-4"
          >
            <div class="min-w-0">
              <p
                class="text-xs font-semibold uppercase tracking-wide text-primary"
              >
                UltiCode
              </p>
              <h1 class="mt-1 truncate text-base font-semibold">
                {{ t("agent.workspace.title") }}
              </h1>
            </div>
            <TooltipProvider
              ><Tooltip
                ><TooltipTrigger as-child>
                  <Button
                    variant="ghost"
                    size="icon"
                    class="shrink-0"
                    :aria-label="t('agent.workspace.toggleSidebar')"
                    aria-controls="agent-desktop-history"
                    :aria-expanded="desktopHistoryOpen"
                    @click="toggleDesktopHistory"
                  >
                    <PanelLeft class="size-4" aria-hidden="true" />
                  </Button> </TooltipTrigger
                ><TooltipContent side="right">{{
                  t("agent.workspace.toggleSidebar")
                }}</TooltipContent></Tooltip
              ></TooltipProvider
            >
          </div>
          <div class="p-3">
            <Button
              variant="ghost"
              class="w-full justify-start"
              :disabled="workspace.busy.value || submitting"
              @click="startNew"
            >
              <SquarePen class="mr-2 size-4" aria-hidden="true" />
              {{ t("agent.workspace.new") }}
            </Button>
            <InputGroup
              v-if="workspace.conversations.value.length"
              class="mt-2"
            >
              <InputGroupAddon
                ><Search class="size-4" aria-hidden="true"
              /></InputGroupAddon>
              <InputGroupInput
                id="agent-history-search"
                v-model="historySearch"
                :placeholder="t('agent.workspace.searchHistory')"
                :aria-label="t('agent.workspace.searchHistory')"
                @keydown.esc="clearHistorySearch"
              />
              <InputGroupAddon v-if="historySearch" align="inline-end">
                <InputGroupButton
                  type="button"
                  size="icon-xs"
                  :aria-label="t('agent.workspace.clearSearch')"
                  @click="clearHistorySearch"
                >
                  <X class="size-3.5" aria-hidden="true" />
                </InputGroupButton>
              </InputGroupAddon>
            </InputGroup>
          </div>
          <div class="min-h-0 flex-1 overflow-y-auto px-3 pb-3">
            <div
              class="mb-2 flex items-center justify-between px-2 text-xs font-medium uppercase tracking-wide text-muted-foreground"
            >
              <span>{{ t("agent.workspace.history") }}</span>
            </div>
            <p
              v-if="
                !workspace.conversations.value.length &&
                !workspace.loadingHistory.value
              "
              class="px-2 py-3 text-sm text-muted-foreground"
            >
              {{ t("agent.workspace.noHistory") }}
            </p>
            <HoverCard
              v-for="item in filteredConversations"
              :key="item.conversationId"
              :open-delay="300"
              :close-delay="100"
              ><HoverCardTrigger as-child
                ><Button
                  type="button"
                  variant="ghost"
                  class="mb-1 h-9 w-full justify-start border-0 px-2 text-left"
                  :class="
                    workspace.conversation.value?.conversationId ===
                    item.conversationId
                      ? 'bg-accent text-accent-foreground'
                      : 'hover:bg-accent/60'
                  "
                  :disabled="workspace.busy.value || submitting"
                  :aria-current="
                    workspace.conversation.value?.conversationId ===
                    item.conversationId
                      ? 'page'
                      : undefined
                  "
                  @click="selectConversation(item.conversationId)"
                >
                  <span class="block min-w-0 text-left">
                    <span class="block truncate text-sm font-medium">{{
                      item.title
                    }}</span>
                    <span class="sr-only">{{ item.lastQuestion }}</span>
                    <span class="sr-only"
                      >{{ formatDate(item.updatedAt) }} · {{ item.turnCount }}
                      {{ t("agent.workspace.turns") }}</span
                    >
                  </span>
                </Button></HoverCardTrigger
              >
              <HoverCardContent
                side="right"
                align="start"
                :side-offset="12"
                class="w-72 space-y-2 break-words p-3"
              >
                <p class="line-clamp-2 text-sm font-medium leading-snug">
                  {{ item.title }}
                </p>
                <p
                  v-if="item.lastQuestion && item.lastQuestion !== item.title"
                  class="line-clamp-3 text-xs leading-relaxed text-muted-foreground"
                >
                  {{ item.lastQuestion }}
                </p>
                <div
                  class="flex items-center justify-between gap-3 text-xs text-muted-foreground"
                >
                  <span class="min-w-0 truncate">{{
                    formatDate(item.updatedAt)
                  }}</span>
                  <span class="shrink-0"
                    >{{ item.turnCount }} {{ t("agent.workspace.turns") }}</span
                  >
                </div>
              </HoverCardContent></HoverCard
            >
            <p
              v-if="historySearch && !filteredConversations.length"
              class="p-2 text-sm text-muted-foreground"
            >
              {{ t("agent.workspace.noSearchResults") }}
            </p>
            <Button
              v-if="workspace.hasMoreConversations.value"
              type="button"
              variant="ghost"
              class="mt-2 w-full"
              :disabled="workspace.loadingHistory.value"
              @click="workspace.loadConversations()"
            >
              {{
                workspace.loadingHistory.value
                  ? t("agent.workspace.loading")
                  : t("agent.workspace.loadMore")
              }}
            </Button>
          </div>
          <nav
            class="space-y-1 border-t border-border p-3"
            :aria-label="t('agent.workspace.navigation')"
          >
            <Button as-child variant="ghost" class="w-full justify-start"
              ><RouterLink to="/problemset">{{
                t("agent.workspace.backToProblems")
              }}</RouterLink></Button
            >
            <Button as-child variant="ghost" class="w-full justify-start"
              ><RouterLink :to="{ name: 'personal-submissions' }">{{
                t("agent.workspace.findSubmission")
              }}</RouterLink></Button
            >
          </nav>
        </aside>
      </ResizablePanel>
      <ResizableHandle
        v-if="desktopViewport"
        v-show="desktopHistoryOpen"
        :aria-label="t('agent.workspace.resizeSidebar')"
        class="z-20 w-1 bg-transparent after:w-3 transition-colors hover:bg-border focus-visible:bg-border"
      />
      <ResizablePanel id="agent-conversation-panel" :order="1" :min-size="60">
        <section
          class="relative flex h-full min-h-0 min-w-0 flex-1 flex-col"
          :class="startingConversation ? 'lg:justify-center lg:pt-16' : ''"
        >
          <header
            class="flex min-h-16 shrink-0 items-center border-b border-border/50 px-4 py-3 sm:px-6 lg:h-16 lg:py-0"
            :class="
              startingConversation ? 'lg:absolute lg:inset-x-0 lg:top-0' : ''
            "
          >
            <div
              class="flex w-full flex-wrap items-center justify-between gap-3"
            >
              <div
                class="flex w-full min-w-0 items-center gap-3 sm:w-auto sm:flex-1"
              >
                <TooltipProvider
                  ><Tooltip
                    ><TooltipTrigger as-child>
                      <Button
                        v-if="!desktopHistoryOpen"
                        variant="ghost"
                        size="icon"
                        class="hidden shrink-0 lg:inline-flex"
                        :aria-label="t('agent.workspace.toggleSidebar')"
                        aria-controls="agent-desktop-history"
                        :aria-expanded="desktopHistoryOpen"
                        @click="toggleDesktopHistory"
                        ><PanelLeft class="size-4" aria-hidden="true"
                      /></Button> </TooltipTrigger
                    ><TooltipContent>{{
                      t("agent.workspace.toggleSidebar")
                    }}</TooltipContent></Tooltip
                  ></TooltipProvider
                >
                <Button
                  variant="outline"
                  size="sm"
                  class="lg:hidden"
                  :aria-expanded="historyOpen"
                  @click="historyOpen = !historyOpen"
                >
                  {{ t("agent.workspace.history") }}
                </Button>
                <div class="min-w-0">
                  <h2 class="truncate font-semibold">
                    {{
                      workspace.conversation.value?.title ??
                      t("agent.workspace.new")
                    }}
                  </h2>
                  <p
                    v-if="workspace.sourceSubmissionId.value"
                    class="truncate text-xs text-muted-foreground"
                  >
                    {{
                      selectedProblemTitle || t("agent.workspace.submission")
                    }}
                    ·
                    <span :title="workspace.sourceSubmissionId.value">{{
                      workspace.sourceSubmissionId.value.slice(0, 8)
                    }}</span>
                  </p>
                </div>
              </div>
              <AgentContextPicker
                ref="contextPicker"
                :submission-id="workspace.sourceSubmissionId.value"
                :context="selectedContext"
                :disabled="
                  workspace.busy.value ||
                  submitting ||
                  !capability?.analysisAvailable
                "
                :has-conversation="Boolean(workspace.conversation.value)"
                @select="applyContext"
              />
              <div
                v-if="workspace.conversation.value"
                class="ml-auto flex shrink-0 items-center gap-2"
              >
                <DropdownMenu>
                  <DropdownMenuTrigger as-child>
                    <Button
                      variant="ghost"
                      size="icon"
                      :aria-label="t('agent.workspace.conversationActions')"
                    >
                      <Ellipsis class="size-5" aria-hidden="true" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem
                      :disabled="workspace.loadingConversation.value"
                      @select="reloadConversation"
                    >
                      <RefreshCw class="mr-2 size-4" aria-hidden="true" />{{
                        t("agent.workspace.reload")
                      }}
                    </DropdownMenuItem>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem
                      class="text-destructive focus:text-destructive"
                      :disabled="
                        workspace.busy.value || workspace.deleting.value
                      "
                      @select="deleteOpen = true"
                    >
                      <Trash2 class="mr-2 size-4" aria-hidden="true" />{{
                        t("agent.workspace.delete")
                      }}
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </div>
            </div>
          </header>
          <AlertDialog v-model:open="deleteOpen">
            <AlertDialogContent>
              <AlertDialogHeader
                ><AlertDialogTitle>{{
                  t("agent.workspace.delete")
                }}</AlertDialogTitle
                ><AlertDialogDescription
                  >{{ t("agent.workspace.deleteConfirm") }}
                  {{ t("agent.workspace.retention") }}</AlertDialogDescription
                ></AlertDialogHeader
              >
              <AlertDialogFooter
                ><AlertDialogCancel>{{
                  t("agent.workspace.keepConversation")
                }}</AlertDialogCancel
                ><AlertDialogAction
                  :disabled="workspace.busy.value || workspace.deleting.value"
                  @click="deleteConversation"
                  >{{ t("agent.workspace.delete") }}</AlertDialogAction
                ></AlertDialogFooter
              >
            </AlertDialogContent>
          </AlertDialog>

          <Sheet v-model:open="historyOpen">
            <SheetContent
              side="left"
              class="w-[min(20rem,85vw)] gap-0 p-0 sm:max-w-sm"
            >
              <SheetHeader>
                <SheetTitle>{{ t("agent.workspace.history") }}</SheetTitle>
              </SheetHeader>
              <div class="flex min-h-0 flex-1 flex-col overflow-y-auto p-3">
                <Button
                  variant="outline"
                  class="mb-3"
                  :disabled="workspace.busy.value || submitting"
                  @click="startNew"
                >
                  {{ t("agent.workspace.new") }}
                </Button>
                <Input
                  v-model="historySearch"
                  class="mb-3"
                  :placeholder="t('agent.workspace.searchHistory')"
                  :aria-label="t('agent.workspace.searchHistory')"
                />
                <p
                  v-if="historySearch && !filteredConversations.length"
                  class="p-2 text-sm text-muted-foreground"
                >
                  {{ t("agent.workspace.noSearchResults") }}
                </p>
                <Button
                  v-for="item in filteredConversations"
                  :key="item.conversationId"
                  type="button"
                  variant="ghost"
                  class="mb-1 h-auto justify-start whitespace-normal px-3 py-2 text-left"
                  :class="
                    workspace.conversation.value?.conversationId ===
                    item.conversationId
                      ? 'border border-primary/40 bg-muted/50'
                      : 'border border-transparent'
                  "
                  :disabled="workspace.busy.value || submitting"
                  @click="selectConversation(item.conversationId)"
                >
                  <span class="block min-w-0 text-left">
                    <span class="block truncate text-sm font-medium">{{
                      item.title
                    }}</span>
                    <span
                      class="mt-1 block truncate text-xs text-muted-foreground"
                      >{{ item.lastQuestion }}</span
                    >
                  </span>
                </Button>
                <Button
                  v-if="workspace.hasMoreConversations.value"
                  type="button"
                  variant="ghost"
                  class="mt-2"
                  :disabled="workspace.loadingHistory.value"
                  @click="workspace.loadConversations()"
                >
                  {{
                    workspace.loadingHistory.value
                      ? t("agent.workspace.loading")
                      : t("agent.workspace.loadMore")
                  }}
                </Button>
              </div>
              <p
                class="border-t border-border p-4 text-xs text-muted-foreground"
              >
                {{ t("agent.workspace.retention") }}
              </p>
            </SheetContent>
          </Sheet>

          <div
            v-if="capabilityLoading"
            role="status"
            aria-live="polite"
            class="border-b border-border px-6 py-2 text-sm text-muted-foreground"
          >
            {{ t("agent.workspace.loading") }}
          </div>
          <p
            v-else-if="capabilityError"
            role="alert"
            class="border-b border-destructive/40 px-6 py-3 text-sm text-destructive"
          >
            {{ t("agent.workspace.errors.requestFailed") }}
          </p>
          <p
            v-else-if="capability && !capability.conversationHistoryAvailable"
            role="alert"
            class="border-b border-border px-6 py-3 text-sm text-muted-foreground"
          >
            {{ t("agent.workspace.unavailable") }}
          </p>
          <p
            v-else-if="capability && !capability.analysisAvailable"
            role="status"
            class="border-b border-border px-6 py-3 text-sm text-muted-foreground"
          >
            {{ t("agent.analysisUnavailable") }}
          </p>
          <p
            v-if="workspace.error.value"
            role="alert"
            class="border-b border-destructive/40 px-6 py-2 text-sm text-destructive"
          >
            {{ t(`agent.workspace.errors.${workspace.error.value}`) }}
          </p>

          <div
            class="relative flex min-h-0 flex-1 flex-col"
            :class="startingConversation ? 'lg:flex-none' : ''"
          >
            <div
              ref="transcript"
              class="min-h-0 flex-1 overflow-y-auto px-4 py-6 sm:px-6"
              :class="
                startingConversation ? 'lg:flex-none lg:max-h-[45dvh]' : ''
              "
              @scroll="trackScroll"
            >
              <div
                v-if="workspace.loadingConversation.value"
                role="status"
                class="mx-auto max-w-3xl py-12 text-center text-sm text-muted-foreground"
              >
                {{ t("agent.workspace.loading") }}
              </div>
              <div
                v-else-if="!workspace.conversation.value"
                class="mx-auto flex min-h-full w-full max-w-3xl flex-col items-center justify-center py-8 text-center lg:min-h-0 lg:py-4"
              >
                <Sparkles
                  class="mb-5 size-8 text-muted-foreground lg:hidden"
                  aria-hidden="true"
                />
                <p
                  class="text-xs font-semibold uppercase tracking-wide text-primary lg:hidden"
                >
                  {{ t("agent.workspace.eyebrow") }}
                </p>
                <h2 class="mt-3 text-2xl font-semibold tracking-tight">
                  {{ emptyTitle }}
                </h2>
                <p
                  class="mt-3 max-w-lg text-sm leading-relaxed text-muted-foreground"
                >
                  {{ emptyDescription }}
                </p>
                <p
                  v-if="capability"
                  class="mt-4 max-w-xl text-xs leading-relaxed text-muted-foreground"
                >
                  {{ t(`agent.sourceModes.${capability.sourceMode}`) }}
                </p>
                <div
                  v-if="
                    workspace.sourceSubmissionId.value &&
                    capability?.analysisAvailable
                  "
                  class="mt-6 flex flex-wrap justify-center gap-2"
                >
                  <Button
                    v-for="key in [
                      'agent.defaultQuestion',
                      'agent.promptMistakes',
                      'agent.promptComplexity',
                    ]"
                    :key="key"
                    variant="outline"
                    class="h-auto whitespace-normal rounded-2xl px-4 py-3"
                    :disabled="workspace.busy.value || Boolean(draft.trim())"
                    @click="useSuggestion(key)"
                    >{{ t(key) }}</Button
                  >
                </div>
                <section
                  v-if="
                    !workspace.sourceSubmissionId.value &&
                    capability?.analysisAvailable
                  "
                  class="mt-8 w-full text-left"
                  :aria-label="t('agent.workspace.recentSubmissions')"
                >
                  <h3 class="mb-3 text-sm font-medium">
                    {{ t("agent.workspace.recentSubmissions") }}
                  </h3>
                  <p
                    v-if="submissionsLoading"
                    role="status"
                    class="text-sm text-muted-foreground"
                  >
                    {{ t("agent.workspace.loading") }}
                  </p>
                  <div
                    v-else-if="submissionsError"
                    role="alert"
                    class="space-y-3 text-sm"
                  >
                    <p class="text-destructive">
                      {{ t("agent.workspace.submissionsError") }}
                    </p>
                    <Button
                      variant="outline"
                      size="sm"
                      @click="submissionsRefresh++"
                      >{{ t("agent.retry") }}</Button
                    >
                  </div>
                  <div v-else-if="recentSubmissions.length" class="space-y-2">
                    <Button
                      v-for="item in recentSubmissions"
                      :key="item.id"
                      variant="outline"
                      class="h-auto w-full justify-between gap-4 whitespace-normal px-4 py-3 text-left"
                      @click="selectSubmission(item.id)"
                    >
                      <span class="min-w-0">
                        <span class="block truncate font-medium">{{
                          item.problem?.title ??
                          t("agent.workspace.problem", { id: item.problem_id })
                        }}</span>
                        <span
                          class="mt-1 block text-xs font-normal text-muted-foreground"
                          >{{ item.language }} ·
                          {{ formatDate(item.created_at) }}</span
                        >
                      </span>
                      <span class="shrink-0 text-xs font-normal">{{
                        t(getStatusLabelI18nKey(item.status) ?? item.status)
                      }}</span>
                    </Button>
                  </div>
                  <p v-else class="text-sm text-muted-foreground">
                    {{ t("agent.workspace.noRecentSubmissions") }}
                  </p>
                </section>
                <Button
                  v-if="!workspace.sourceSubmissionId.value"
                  as-child
                  class="mt-6"
                >
                  <RouterLink :to="{ name: 'personal-submissions' }">{{
                    t("agent.workspace.findSubmission")
                  }}</RouterLink>
                </Button>
              </div>
              <div v-else class="mx-auto flex max-w-3xl flex-col gap-10 pb-4">
                <Button
                  variant="ghost"
                  v-if="workspace.hasMoreTurns.value"
                  type="button"
                  class="self-center px-3 py-2"
                  :disabled="workspace.loadingConversation.value"
                  @click="workspace.loadOlderTurns()"
                >
                  {{ t("agent.workspace.loadOlder") }}
                </Button>
                <article
                  v-for="turn in workspace.turns.value"
                  :key="turn.turnId"
                  class="min-w-0 space-y-4"
                >
                  <Message align="end">
                    <MessageContent>
                      <Bubble align="end" variant="muted">
                        <BubbleContent class="px-5 py-3 leading-relaxed">
                          <p class="whitespace-pre-wrap break-words">
                            {{ turn.question }}
                          </p>
                          <p
                            v-if="
                              turn.context?.includeProblem ||
                              turn.context?.comments?.length
                            "
                            class="mt-2 text-xs text-muted-foreground"
                          >
                            {{ t("agent.context.title") }} ·
                            <span v-if="turn.context.includeProblem"
                              >{{
                                t("agent.context.problemReference")
                              }}
                              · </span
                            >{{ t("agent.context.comments") }}
                            {{ turn.context.comments?.length ?? 0 }}
                          </p>
                        </BubbleContent>
                      </Bubble>
                    </MessageContent>
                  </Message>
                  <Message>
                    <MessageContent class="gap-3 leading-7">
                      <div
                        v-if="turn.answer"
                        class="typeset typeset-docs max-w-none break-words text-foreground"
                        v-html="renderMarkdown(turn.answer)"
                      />
                      <TraceLine
                        v-else-if="turn.status === 'running'"
                        status="running"
                        role="status"
                        aria-live="polite"
                        class="text-muted-foreground"
                      >
                        <TraceIcon
                          ><LoaderCircle
                            class="animate-spin"
                            aria-hidden="true"
                        /></TraceIcon>
                        <TraceTitle>{{
                          t("agent.workspace.generating")
                        }}</TraceTitle>
                      </TraceLine>
                      <TraceLine
                        v-else-if="turn.status === 'queued'"
                        status="pending"
                        role="status"
                        class="text-muted-foreground"
                      >
                        <TraceIcon><Clock aria-hidden="true" /></TraceIcon>
                        <TraceTitle>{{
                          t("agent.workspace.queued")
                        }}</TraceTitle>
                        <Button
                          variant="ghost"
                          size="sm"
                          :disabled="workspace.canceling.value"
                          @click="workspace.cancelTurn(turn)"
                        >
                          {{ t("agent.workspace.cancelTurn") }}
                        </Button>
                      </TraceLine>
                      <Collapsible
                        v-else-if="turn.status === 'failed'"
                        :key="`${turn.turnId}-${turn.turnIndex === workspace.turns.value.at(-1)?.turnIndex}`"
                        :default-open="
                          turn.turnIndex ===
                          workspace.turns.value.at(-1)?.turnIndex
                        "
                        class="rounded-lg border border-border/60 bg-muted/20 px-3 py-2 text-muted-foreground"
                      >
                        <CollapsibleTrigger as-child>
                          <Button
                            variant="ghost"
                            size="sm"
                            class="h-auto w-full justify-start gap-2 whitespace-normal px-0 text-left font-normal"
                          >
                            <CircleAlert
                              class="size-4 shrink-0"
                              aria-hidden="true"
                            />
                            {{
                              t(
                                turn.turnIndex ===
                                  workspace.turns.value.at(-1)?.turnIndex
                                  ? "agent.workspace.analysisIncomplete"
                                  : "agent.workspace.earlierAnalysisIncomplete",
                              )
                            }}
                            <ChevronDown
                              class="ml-auto size-4 shrink-0"
                              aria-hidden="true"
                            />
                          </Button>
                        </CollapsibleTrigger>
                        <CollapsibleContent>
                          <p role="status" class="pt-2 text-sm leading-relaxed">
                            {{ failureMessage(turn) }}
                          </p>
                        </CollapsibleContent>
                      </Collapsible>
                      <p
                        v-else-if="turn.status === 'cancelled'"
                        role="status"
                        class="text-muted-foreground"
                      >
                        {{ t("agent.workspace.cancelled") }}
                      </p>
                      <div
                        v-if="turn.citations.length || turn.trace.length"
                        class="contents lg:flex lg:flex-wrap lg:items-center lg:gap-x-4 lg:gap-y-2"
                      >
                        <Trace
                          v-if="turn.citations.length"
                          class="lg:w-auto lg:max-w-full lg:has-[>[data-slot=trace-collapsible][data-state=open]]:w-full"
                        >
                          <TraceCollapsible>
                            <div
                              class="contents lg:flex lg:items-center lg:gap-2"
                            >
                              <div
                                v-if="turn.citations.length"
                                class="mb-3 flex flex-wrap items-center gap-1 lg:order-2 lg:mb-0"
                                :aria-label="
                                  t('agent.workspace.referencePreviews')
                                "
                                role="group"
                              >
                                <span
                                  class="mr-1 text-xs text-muted-foreground lg:sr-only"
                                  >{{
                                    t("agent.workspace.referencePreviews")
                                  }}</span
                                >
                                <AgentCitationPreview
                                  v-for="(citation, index) in turn.citations"
                                  :key="`${citation.chunk_id}-${index}`"
                                  :citation="citation"
                                  :index="index"
                                />
                              </div>
                              <TraceCollapsibleTrigger class="lg:order-1">
                                <TraceLine
                                  ><TraceIcon
                                    ><BookOpen aria-hidden="true"
                                  /></TraceIcon>
                                  <TraceTitle
                                    >{{ t("agent.citations") }} ·
                                    {{ turn.citations.length }}</TraceTitle
                                  >
                                </TraceLine>
                              </TraceCollapsibleTrigger>
                            </div>
                            <TraceCollapsibleContent>
                              <ol class="space-y-3">
                                <li
                                  v-for="(citation, index) in turn.citations"
                                  :key="`${citation.chunk_id}-${index}`"
                                >
                                  <TraceLine
                                    ><TraceTitle class="text-foreground">{{
                                      citation.claim
                                    }}</TraceTitle></TraceLine
                                  >
                                  <TraceDescription
                                    >{{ citation.source_path }} ·
                                    {{
                                      citation.source_position
                                    }}</TraceDescription
                                  >
                                </li>
                              </ol>
                            </TraceCollapsibleContent>
                          </TraceCollapsible>
                        </Trace>
                        <Trace
                          v-if="turn.trace.length"
                          class="lg:w-auto lg:max-w-full lg:has-[>[data-slot=trace-collapsible][data-state=open]]:w-full"
                        >
                          <TraceCollapsible>
                            <TraceCollapsibleTrigger>
                              <TraceLine
                                ><TraceIcon
                                  ><Wrench aria-hidden="true"
                                /></TraceIcon>
                                <TraceTitle>{{
                                  t("agent.workspace.activity")
                                }}</TraceTitle>
                              </TraceLine>
                            </TraceCollapsibleTrigger>
                            <TraceCollapsibleContent>
                              <Trace>
                                <TraceLine
                                  v-for="(event, index) in turn.trace"
                                  :key="`${turn.turnId}-${index}`"
                                  :status="event.failed ? 'error' : undefined"
                                >
                                  <TraceIcon
                                    ><Wrench aria-hidden="true"
                                  /></TraceIcon>
                                  <TraceTitle
                                    >{{ toolLabel(event.tool_name)
                                    }}<span v-if="event.failed">
                                      · {{ t("agent.statuses.failed") }}</span
                                    ></TraceTitle
                                  >
                                </TraceLine>
                              </Trace>
                            </TraceCollapsibleContent>
                          </TraceCollapsible>
                        </Trace>
                      </div>
                      <Trace
                        v-if="
                          workspace.busy.value &&
                          workspace.currentTurn.value?.turnId === turn.turnId &&
                          workspace.events.value.length
                        "
                      >
                        <TraceCollapsible default-open>
                          <TraceCollapsibleTrigger>
                            <TraceLine status="running"
                              ><TraceIcon
                                ><LoaderCircle
                                  class="animate-spin"
                                  aria-hidden="true" /></TraceIcon
                              ><TraceTitle>{{
                                t("agent.workspace.activity")
                              }}</TraceTitle></TraceLine
                            >
                          </TraceCollapsibleTrigger>
                          <TraceCollapsibleContent>
                            <Trace>
                              <TraceLine
                                v-for="event in workspace.events.value"
                                :key="event.seq"
                                :status="
                                  event.kind === 'turn_failed'
                                    ? 'error'
                                    : undefined
                                "
                              >
                                <TraceTitle>{{
                                  event.detail.toolName
                                    ? toolLabel(event.detail.toolName)
                                    : event.detail.node === "citation_judge"
                                      ? t("agent.workspace.verifySources")
                                      : t("agent.workspace.analysisStep")
                                }}</TraceTitle>
                              </TraceLine>
                            </Trace>
                          </TraceCollapsibleContent>
                        </TraceCollapsible>
                      </Trace>
                      <div
                        v-if="
                          ['queued', 'failed'].includes(turn.status) &&
                          turn.turnIndex ===
                            workspace.turns.value.at(-1)?.turnIndex &&
                          capability?.analysisAvailable
                        "
                        class="flex flex-wrap items-center gap-2"
                      >
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          :disabled="
                            workspace.busy.value ||
                            (showConsent && !sourceConsent)
                          "
                          @click="retryTurn(turn)"
                        >
                          {{ t("agent.workspace.retry") }}
                        </Button>
                        <p
                          v-if="
                            turn.context?.includeProblem ||
                            turn.context?.comments?.length
                          "
                          class="text-xs text-muted-foreground"
                        >
                          {{ t("agent.context.retryOriginal") }}
                        </p>
                      </div>
                      <AgentMessageActions
                        :turn="turn"
                        :disabled="workspace.busy.value || submitting"
                        :has-draft="Boolean(draft.trim())"
                        @reuse="reuseQuestion"
                      />
                      <div
                        v-if="
                          turn.answer &&
                          turn.turnId ===
                            workspace.turns.value.at(-1)?.turnId &&
                          !workspace.busy.value &&
                          capability?.analysisAvailable &&
                          !draft.trim()
                        "
                        class="flex flex-wrap gap-2 pt-2"
                        role="group"
                        :aria-label="t('agent.workspace.followupSuggestions')"
                      >
                        <Button
                          v-for="key in [
                            'agent.workspace.followupExplain',
                            'agent.workspace.followupEdges',
                            'agent.workspace.followupPractice',
                          ]"
                          :key="key"
                          type="button"
                          variant="outline"
                          size="sm"
                          @click="useSuggestion(key)"
                          >{{ t(key) }}</Button
                        >
                      </div>
                    </MessageContent>
                  </Message>
                </article>
              </div>
            </div>
            <TooltipProvider
              v-if="!followLatest && workspace.conversation.value"
            >
              <Tooltip>
                <TooltipTrigger as-child>
                  <Button
                    variant="outline"
                    size="sm"
                    class="mx-auto my-2 shrink-0 rounded-full lg:absolute lg:bottom-4 lg:left-1/2 lg:z-10 lg:m-0 lg:size-9 lg:-translate-x-1/2 lg:bg-background lg:p-0 lg:shadow-md"
                    @click="scrollToLatest"
                    ><ArrowDown
                      class="mr-2 size-4 lg:mr-0"
                      aria-hidden="true"
                    /><span class="lg:sr-only">{{
                      t("agent.workspace.latest")
                    }}</span></Button
                  >
                </TooltipTrigger>
                <TooltipContent side="top">{{
                  t("agent.workspace.latest")
                }}</TooltipContent>
              </Tooltip>
            </TooltipProvider>
          </div>

          <form
            v-if="
              workspace.sourceSubmissionId.value &&
              !workspace.loadingConversation.value
            "
            class="shrink-0 bg-background px-4 pt-4 sm:px-6"
            @submit.prevent="send"
          >
            <div
              class="lg:mx-auto lg:flex lg:max-w-3xl lg:flex-col lg:overflow-hidden lg:rounded-3xl lg:border lg:border-input lg:bg-muted/30 lg:shadow-sm lg:has-[[data-slot=input-group-control]:focus-visible]:border-ring lg:has-[[data-slot=input-group-control]:focus-visible]:ring-3 lg:has-[[data-slot=input-group-control]:focus-visible]:ring-ring/50"
            >
              <div
                v-if="
                  selectedContext.includeProblem ||
                  selectedContext.comments.length
                "
                class="mx-auto mb-3 flex max-w-3xl flex-wrap items-center gap-2 lg:mx-0 lg:mb-0 lg:px-4 lg:pt-3"
              >
                <Badge v-if="selectedContext.includeProblem" variant="secondary"
                  >{{ t("agent.context.problemReference") }} ·
                  {{ selectedProblemTitle }}</Badge
                >
                <Badge
                  v-if="
                    selectedContext.comments.some((item) => item.solutionId)
                  "
                  variant="secondary"
                  >{{ t("agent.context.solutionComments") }} ·
                  {{
                    selectedContext.comments.filter((item) => item.solutionId)
                      .length
                  }}</Badge
                >
                <Badge
                  v-if="selectedContext.comments.some((item) => item.postId)"
                  variant="secondary"
                  >{{ t("agent.context.forumComments") }} ·
                  {{
                    selectedContext.comments.filter((item) => item.postId)
                      .length
                  }}</Badge
                >
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  :disabled="workspace.busy.value || submitting"
                  @click="clearReferences"
                  >{{ t("agent.context.clear") }}</Button
                >
              </div>
              <p
                v-if="
                  workspace.conversation.value &&
                  capability &&
                  (!desktopViewport || capability.sourceMode !== 'owner_source')
                "
                class="mx-auto mb-3 max-w-3xl text-xs leading-relaxed text-muted-foreground"
              >
                {{ t(`agent.sourceModes.${capability.sourceMode}`) }}
              </p>
              <Item
                v-if="showConsent"
                variant="outline"
                size="sm"
                class="mx-auto mb-3 max-w-3xl lg:mx-3 lg:mb-0 lg:mt-2 lg:w-auto lg:max-w-none lg:gap-2 lg:rounded-none lg:border-0 lg:border-b lg:border-border/50 lg:px-1 lg:pb-2 lg:pt-0"
              >
                <ItemContent class="lg:gap-0">
                  <ItemTitle class="lg:sr-only">{{
                    t("agent.workspace.sourceAuthorization")
                  }}</ItemTitle>
                  <ItemDescription class="line-clamp-none">
                    <FieldLabel
                      for="agent-source-consent"
                      class="flex items-start gap-2 font-normal leading-relaxed"
                    >
                      <Checkbox
                        id="agent-source-consent"
                        :model-value="sourceConsent"
                        @update:model-value="sourceConsent = $event === true"
                        class="mt-1"
                        :disabled="workspace.busy.value || submitting"
                      />
                      <span>{{ t("agent.sourceConsent") }}</span>
                    </FieldLabel>
                  </ItemDescription>
                </ItemContent>
                <ItemActions
                  ><Badge variant="secondary">{{
                    t(
                      sourceConsent
                        ? "agent.workspace.sourceApproved"
                        : "agent.workspace.sourcePending",
                    )
                  }}</Badge></ItemActions
                >
              </Item>
              <InputGroup
                class="mx-auto max-w-3xl overflow-hidden rounded-3xl bg-muted/30 shadow-sm has-disabled:opacity-100 has-[[data-slot=input-group-control]:disabled]:opacity-50 lg:rounded-none lg:border-0 lg:bg-transparent lg:shadow-none lg:has-disabled:bg-transparent lg:dark:bg-transparent lg:dark:has-disabled:bg-transparent lg:has-[[data-slot=input-group-control]:focus-visible]:ring-0"
              >
                <FieldLabel class="sr-only" for="agent-conversation-draft">{{
                  t("agent.workspace.composerLabel")
                }}</FieldLabel>
                <InputGroupTextarea
                  id="agent-conversation-draft"
                  v-model="draft"
                  :rows="desktopViewport ? 1 : 3"
                  maxlength="200"
                  aria-describedby="agent-composer-hint"
                  class="max-h-40 min-h-20 w-full px-4 py-3 lg:min-h-14"
                  :placeholder="t('agent.workspace.composerPlaceholder')"
                  :disabled="
                    workspace.busy.value ||
                    !capability?.analysisAvailable ||
                    !workspace.sourceSubmissionId.value
                  "
                  @keydown="composerKeydown"
                />
                <InputGroupAddon
                  align="block-end"
                  class="flex flex-wrap items-center justify-between gap-3 px-3 py-2"
                >
                  <Popover v-model:open="contextOverviewOpen">
                    <PopoverTrigger as-child>
                      <Button type="button" variant="ghost" size="sm"
                        ><BookOpen class="mr-2 size-4" aria-hidden="true" />{{
                          t("agent.workspace.contextOverview")
                        }}</Button
                      >
                    </PopoverTrigger>
                    <PopoverContent align="start" class="w-80 space-y-3">
                      <p class="text-sm font-medium">
                        {{ t("agent.workspace.outgoingMaterials") }}
                      </p>
                      <dl class="space-y-2 text-xs">
                        <div>
                          <dt class="text-muted-foreground">
                            {{ t("agent.context.problem") }}
                          </dt>
                          <dd>{{ selectedProblemTitle }}</dd>
                        </div>
                        <div>
                          <dt class="text-muted-foreground">
                            {{ t("agent.workspace.submission") }}
                          </dt>
                          <dd class="break-all">
                            {{ workspace.sourceSubmissionId.value }}
                          </dd>
                        </div>
                        <div class="flex justify-between gap-3">
                          <dt>{{ t("agent.context.problemReference") }}</dt>
                          <dd>
                            {{
                              t(
                                selectedContext.includeProblem
                                  ? "agent.workspace.included"
                                  : "agent.workspace.notIncluded",
                              )
                            }}
                          </dd>
                        </div>
                        <div class="flex justify-between gap-3">
                          <dt>{{ t("agent.context.solutionComments") }}</dt>
                          <dd>
                            {{
                              selectedContext.comments.filter(
                                (item) => item.solutionId,
                              ).length
                            }}
                          </dd>
                        </div>
                        <div class="flex justify-between gap-3">
                          <dt>{{ t("agent.context.forumComments") }}</dt>
                          <dd>
                            {{
                              selectedContext.comments.filter(
                                (item) => item.postId,
                              ).length
                            }}
                          </dd>
                        </div>
                      </dl>
                      <p class="text-xs text-muted-foreground">
                        {{ t("agent.workspace.materialsNotice") }}
                      </p>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        :disabled="
                          workspace.busy.value ||
                          submitting ||
                          !capability?.analysisAvailable
                        "
                        @click="changeMaterials"
                        >{{ t("agent.workspace.changeMaterials") }}</Button
                      >
                    </PopoverContent>
                  </Popover>
                  <div class="ml-auto flex items-center gap-2">
                    <Button
                      v-if="currentTurnIsRunning"
                      type="button"
                      variant="outline"
                      size="sm"
                      :disabled="workspace.canceling.value"
                      @click="workspace.cancelTurn()"
                    >
                      {{
                        workspace.canceling.value
                          ? t("agent.workspace.cancelling")
                          : t("agent.workspace.cancelTurn")
                      }}
                    </Button>
                    <InputGroupButton
                      type="submit"
                      size="icon-sm"
                      variant="default"
                      class="shrink-0 rounded-full"
                      :aria-label="
                        workspace.busy.value
                          ? t('agent.workspace.generating')
                          : t('agent.workspace.send')
                      "
                      :disabled="!canSend || draft.trim().length < 2"
                    >
                      <ArrowUp class="size-5" aria-hidden="true" />
                      <span class="sr-only">{{
                        workspace.busy.value
                          ? t("agent.workspace.generating")
                          : t("agent.workspace.send")
                      }}</span>
                    </InputGroupButton>
                  </div>
                </InputGroupAddon>
              </InputGroup>
            </div>
            <p
              id="agent-composer-hint"
              class="mx-auto mt-2 flex max-w-3xl justify-between gap-3 px-2 text-xs text-muted-foreground"
            >
              <span>{{ t("agent.workspace.keyboardHint") }}</span
              ><span>{{ draft.length }}/200</span>
            </p>
          </form>
          <footer class="shrink-0 px-4 py-4 sm:px-6 lg:py-2">
            <p
              class="mx-auto max-w-3xl text-center text-xs leading-relaxed text-muted-foreground"
            >
              {{ t("agent.workspace.disclaimer") }}
            </p>
          </footer>
        </section>
      </ResizablePanel>
    </ResizablePanelGroup>
  </main>
</template>
