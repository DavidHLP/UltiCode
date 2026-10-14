<script setup lang="ts">
import { Checkbox } from "@ulticode/design-system/ui/checkbox";
import { FieldLabel } from "@ulticode/design-system/ui/field";

import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { renderMarkdown } from "@ulticode/markdown-utils";
import {
  agentApi,
  type AgentLearningPlan,
  type AgentThreadStatus,
} from "@/api/agent";
import type { SubmissionRecord } from "@/types/submission";
import { useAuthStore } from "@/stores/auth";
import { useAgentWorkflow } from "@/composables/useAgentWorkflow";
import { isAgentReviewEligible } from "../agentReviewPolicy";
import { Badge } from "@ulticode/design-system/ui/badge";
import { Button } from "@ulticode/design-system/ui/button";
import { Input } from "@ulticode/design-system/ui/input";
import { Label } from "@ulticode/design-system/ui/label";
import { Textarea } from "@ulticode/design-system/ui/textarea";

const props = defineProps<{
  submission: SubmissionRecord;
  contestId?: string;
}>();
const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const workflow = useAgentWorkflow();
const expanded = ref(Boolean(route.query.threadId));
const capability = ref<{
  analysisAvailable: boolean;
  persistenceAvailable: boolean;
  sourceMode: "metadata" | "owner_source";
} | null>(null);
const capabilityLoading = ref(false);
const capabilityError = ref(false);
const allowSource = ref(false);
const question = ref("");
const panelTitle = ref<HTMLElement | null>(null);
const panelRoot = ref<HTMLElement | null>(null);
const persistedPlan = ref<AgentLearningPlan | null>(null);
const planReadbackError = ref(false);
const requestGeneration = ref(0);
const loadedThreadId = ref<string | null>(null);
const authStore = useAuthStore();
const enabled = import.meta.env.VITE_AGENT_REVIEW_ENABLED === "true";
const userId = computed(() => authStore.fetchCurrentUserId());
const eligible = computed(() =>
  isAgentReviewEligible(
    props.submission,
    userId.value,
    enabled,
    props.contestId,
  ),
);
const isAccepted = computed(() => props.submission.status === "Accepted");
const suggestedQuestion = computed(() =>
  t(isAccepted.value ? "agent.promptComplexity" : "agent.promptMistakes"),
);
const entryLabel = computed(() =>
  t(isAccepted.value ? "agent.reviewComplexity" : "agent.reviewError"),
);
const draftDirty = computed(
  () =>
    Boolean(workflow.thread.value) &&
    (workflow.draftTitle.value !== workflow.thread.value?.draft.title ||
      workflow.draftContent.value !== workflow.thread.value?.draft.content),
);
const threadMatchesSubmission = computed(
  () => workflow.thread.value?.sourceSubmissionId === props.submission.id,
);
const toolEvents = computed(() =>
  workflow.events.value.filter(
    (event) => event.kind === "tool_started" || event.kind === "tool_completed",
  ),
);
const hasUnsavedReview = computed(() => {
  const current = workflow.thread.value;
  return Boolean(
    current &&
    threadMatchesSubmission.value &&
    current.status !== "saved" &&
    current.status !== "cancelled" &&
    (draftDirty.value || workflow.generatedText.value || current.draft.content),
  );
});
const newThreadBlocked = computed(
  () =>
    workflow.busy.value ||
    workflow.loading.value ||
    workflow.canceling.value ||
    workflow.thread.value?.status === "analyzing" ||
    workflow.thread.value?.status === "saving",
);
const hasEvidence = computed(() => {
  const draft = workflow.thread.value?.draft;
  return Boolean(
    toolEvents.value.length ||
    draft?.facts.length ||
    draft?.hypotheses.length ||
    draft?.citations.length ||
    draft?.citationChecks.length,
  );
});
const threadQuery = computed(() => {
  const value = route.query.threadId;
  return Array.isArray(value) ? value[0] : value;
});
const errorText = computed(() => {
  const key = workflow.error.value;
  return key
    ? t(`agent.errors.${key}`)
    : capabilityError.value
      ? t("agent.errors.requestFailed")
      : "";
});
const statusText = computed(() => {
  if (workflow.loading.value) return t("agent.statuses.loading");
  if (workflow.analyzing.value) return t("agent.statuses.analyzing");
  if (workflow.saving.value) return t("agent.statuses.saving");
  const status: AgentThreadStatus | "idle" =
    workflow.thread.value?.status ?? "idle";
  return t(`agent.statuses.${status}`);
});
const receiptFailure = computed(
  () => workflow.thread.value?.receipt.failureReason ?? null,
);
const analysisFailed = computed(() =>
  ["analysis_failed", "analysis_interrupted"].includes(
    receiptFailure.value ?? "",
  ),
);
const canCancel = computed(
  () =>
    !workflow.saving.value &&
    ["analyzing", "awaiting_confirmation", "confirmed"].includes(
      workflow.thread.value?.status ?? "",
    ),
);
watch(
  suggestedQuestion,
  (value) => {
    if (!question.value.trim()) question.value = value;
  },
  { immediate: true },
);

function openWorkspace() {
  void router.push({
    name: "agent-workspace",
    query: { submissionId: props.submission.id },
  });
}

async function setExpanded(value: boolean) {
  if (value && !threadMatchesSubmission.value && !question.value.trim())
    question.value = suggestedQuestion.value;
  expanded.value = value;
  if (value) {
    await nextTick();
    panelTitle.value?.focus();
  } else {
    await nextTick();
    panelRoot.value
      ?.querySelector<HTMLButtonElement>("[data-agent-panel-toggle]")
      ?.focus();
  }
}

async function syncCapabilities() {
  const generation = ++requestGeneration.value;
  capability.value = null;
  capabilityLoading.value = eligible.value;
  capabilityError.value = false;
  allowSource.value = false;
  persistedPlan.value = null;
  planReadbackError.value = false;
  if (!eligible.value) return;
  try {
    const result = await agentApi.getCapabilities();
    if (generation !== requestGeneration.value || !expanded.value) return;
    capability.value = result;
    if (
      threadQuery.value &&
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
        threadQuery.value,
      )
    ) {
      loadedThreadId.value = threadQuery.value;
      const thread = await workflow.loadThread(
        threadQuery.value,
        props.submission.id,
      );
      if (generation !== requestGeneration.value || !expanded.value) return;
      if (thread?.status === "saved" && thread.receipt.planId)
        void loadSavedPlan(thread.receipt.planId, generation);
    }
  } catch {
    if (generation === requestGeneration.value) capabilityError.value = true;
  } finally {
    if (generation === requestGeneration.value) capabilityLoading.value = false;
  }
}

let loadingPlanId: string | null = null;
async function loadSavedPlan(
  planId: string,
  generation = requestGeneration.value,
) {
  if (loadingPlanId === planId) return;
  loadingPlanId = planId;
  planReadbackError.value = false;
  try {
    const plan = await agentApi.getLearningPlan(planId);
    if (
      generation !== requestGeneration.value ||
      workflow.thread.value?.receipt.planId !== planId
    )
      return;
    if (plan.id !== planId || plan.sourceSubmissionId !== props.submission.id) {
      planReadbackError.value = true;
      return;
    }
    persistedPlan.value = plan;
  } catch {
    if (
      generation === requestGeneration.value &&
      workflow.thread.value?.receipt.planId === planId
    )
      planReadbackError.value = true;
  } finally {
    if (loadingPlanId === planId) loadingPlanId = null;
  }
}

watch(
  [
    expanded,
    () => props.submission.id,
    () => props.contestId,
    userId,
    eligible,
  ],
  ([isOpen, , , , isEligible], previous) => {
    const contextChanged = Boolean(
      previous &&
      (previous[1] !== props.submission.id ||
        previous[2] !== props.contestId ||
        previous[3] !== userId.value ||
        previous[4] !== isEligible),
    );
    if (contextChanged) {
      requestGeneration.value++;
      workflow.clear();
      capability.value = null;
      loadedThreadId.value = null;
    }
    if (isOpen) {
      void nextTick(() => panelTitle.value?.focus());
      if (isEligible && !capability.value) void syncCapabilities();
      else if (
        isEligible &&
        threadQuery.value &&
        threadQuery.value !== loadedThreadId.value &&
        /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
          threadQuery.value,
        )
      ) {
        loadedThreadId.value = threadQuery.value;
        void workflow.loadThread(threadQuery.value, props.submission.id);
      }
    }
  },
  { flush: "post", immediate: true },
);

async function updateThreadQuery(threadId: string | null) {
  await router.replace({
    query: { ...route.query, threadId: threadId ?? undefined },
  });
}
async function startReview() {
  if (
    !capability.value?.analysisAvailable ||
    !eligible.value ||
    (capability.value.sourceMode === "owner_source" && !allowSource.value)
  )
    return;
  const generation = requestGeneration.value;
  const sourceSubmissionId = props.submission.id;
  const thread = await workflow.createThread(
    sourceSubmissionId,
    question.value.trim() || t("agent.defaultQuestion"),
  );
  if (
    generation !== requestGeneration.value ||
    !eligible.value ||
    props.submission.id !== sourceSubmissionId
  )
    return;
  if (!thread || thread.sourceSubmissionId !== sourceSubmissionId) {
    workflow.clear();
    return;
  }
  loadedThreadId.value = thread.threadId;
  await updateThreadQuery(thread.threadId);
  if (
    generation === requestGeneration.value &&
    eligible.value &&
    props.submission.id === sourceSubmissionId
  )
    await workflow.analyze();
}
async function recoverFromUrl() {
  if (!threadQuery.value || !capability.value) return;
  if (hasUnsavedReview.value && !window.confirm(t("agent.reloadConfirm")))
    return;
  loadedThreadId.value = threadQuery.value;
  await workflow.loadThread(threadQuery.value, props.submission.id);
}
async function startFreshReview() {
  if (newThreadBlocked.value) return;
  if (hasUnsavedReview.value && !window.confirm(t("agent.newThreadConfirm")))
    return;
  if (!workflow.clear()) return;
  loadedThreadId.value = null;
  persistedPlan.value = null;
  question.value = suggestedQuestion.value;
  await updateThreadQuery(null);
}
watch(threadQuery, (id) => {
  if (!expanded.value || !capability.value) return;
  if (!id) {
    loadedThreadId.value = null;
    if (!workflow.saving.value) workflow.clear();
  } else if (
    id !== loadedThreadId.value &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)
  ) {
    loadedThreadId.value = id;
    void workflow.loadThread(id, props.submission.id);
  }
});
async function closeOnEscape(event: KeyboardEvent) {
  if (event.key === "Escape" && expanded.value) {
    event.preventDefault();
    await setExpanded(false);
  }
}
watch(
  () => workflow.thread.value?.receipt.planId,
  (id) => {
    if (id) void loadSavedPlan(id);
  },
);
watch(
  [() => workflow.saving.value, threadMatchesSubmission],
  ([saving, matches]) => {
    if (!saving && !matches && workflow.thread.value) workflow.clear();
  },
);
onBeforeUnmount(() => {
  requestGeneration.value++;
});
</script>

<template>
  <section
    v-if="eligible"
    ref="panelRoot"
    class="w-full min-w-0 border border-border bg-card"
  >
    <div class="flex flex-wrap items-center justify-between gap-3 p-3">
      <div class="min-w-0">
        <h2 class="font-medium">{{ t("agent.reviewTitle") }}</h2>
        <p class="text-sm text-muted-foreground">
          {{ t("agent.reviewDescription") }}
        </p>
      </div>
      <div class="flex items-center gap-2">
        <Button
          data-agent-panel-toggle
          type="button"
          variant="outline"
          size="sm"
          :aria-expanded="expanded"
          @click="setExpanded(!expanded)"
        >
          {{ expanded ? t("agent.closePanel") : entryLabel }}
        </Button>
        <Button
          data-agent-workspace-entry
          type="button"
          variant="outline"
          size="sm"
          @click="openWorkspace"
        >
          {{ t("agent.openWorkspace") }}
        </Button>
      </div>
    </div>
    <div
      v-if="expanded"
      class="space-y-5 border-t border-border p-3 sm:p-5"
      @keydown="closeOnEscape"
    >
      <h3
        ref="panelTitle"
        tabindex="-1"
        class="text-lg font-semibold outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        {{ t("agent.reviewTitle") }}
      </h3>
      <p
        v-if="
          workflow.error.value &&
          (!workflow.thread.value || threadMatchesSubmission)
        "
        role="alert"
        class="text-sm text-destructive"
      >
        {{ errorText }}
      </p>
      <div
        v-if="capabilityError"
        class="flex flex-wrap items-center gap-2"
        role="alert"
      >
        <p class="text-sm text-destructive">{{ errorText }}</p>
        <Button
          type="button"
          size="sm"
          variant="outline"
          :disabled="capabilityLoading"
          @click="syncCapabilities"
          >{{ t("agent.retry") }}</Button
        >
      </div>
      <p
        v-if="capabilityLoading"
        role="status"
        aria-live="polite"
        class="text-sm text-muted-foreground"
      >
        {{ t("agent.capabilityLoading") }}
      </p>
      <template v-if="capability">
        <p class="text-sm text-muted-foreground">
          {{ t(`agent.sourceModes.${capability.sourceMode}`) }}
        </p>
        <p
          v-if="!capability.analysisAvailable"
          class="rounded border p-3 text-sm"
        >
          {{ t("agent.analysisUnavailable") }}
        </p>
        <p
          v-if="!capability.persistenceAvailable"
          class="rounded border p-3 text-sm"
        >
          {{ t("agent.persistenceUnavailable") }}
        </p>
        <template
          v-if="capability.analysisAvailable && !threadMatchesSubmission"
        >
          <FieldLabel
            for="agent-review-source-consent"
            v-if="capability.sourceMode === 'owner_source'"
            class="flex items-start gap-2"
          >
            <Checkbox
              id="agent-review-source-consent"
              :model-value="allowSource"
              @update:model-value="allowSource = $event === true"
              class="mt-1"
            />
            <span>{{ t("agent.sourceConsent") }}</span>
          </FieldLabel>
          <div class="space-y-3">
            <Label for="agent-review-question">{{ t("agent.question") }}</Label>
            <Textarea
              id="agent-review-question"
              v-model="question"
              :placeholder="t('agent.questionPlaceholder')"
              minlength="2"
              maxlength="200"
              rows="3"
            />
            <div class="flex flex-wrap gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                @click="question = t('agent.promptMistakes')"
                >{{ t("agent.promptMistakes") }}</Button
              >
              <Button
                type="button"
                variant="outline"
                size="sm"
                @click="question = t('agent.promptComplexity')"
                >{{ t("agent.promptComplexity") }}</Button
              >
            </div>
            <Button
              type="button"
              :disabled="
                workflow.busy.value ||
                (capability.sourceMode === 'owner_source' && !allowSource)
              "
              @click="startReview"
              >{{ t("agent.start") }}</Button
            >
          </div>
        </template>
        <template v-if="workflow.thread.value && threadMatchesSubmission">
          <div class="flex flex-wrap items-center gap-2">
            <Badge variant="secondary" role="status">{{ statusText }}</Badge>
            <Button
              type="button"
              size="sm"
              variant="outline"
              :disabled="newThreadBlocked"
              @click="recoverFromUrl"
              >{{ t("agent.refresh") }}</Button
            >
            <Button
              type="button"
              size="sm"
              variant="outline"
              :disabled="newThreadBlocked"
              @click="startFreshReview"
              >{{ t("agent.newThread") }}</Button
            >
          </div>
          <p
            v-if="
              workflow.thread.value.status === 'saving' || workflow.saving.value
            "
            class="rounded border p-3 text-sm"
          >
            {{ t("agent.savingWarning") }}
          </p>
          <p
            v-if="analysisFailed"
            role="alert"
            class="text-sm text-destructive"
          >
            {{ t("agent.analysisFailed") }}
          </p>
          <p
            v-if="workflow.analyzing.value && !workflow.generatedText.value"
            role="status"
            aria-live="polite"
            class="text-sm text-muted-foreground"
          >
            {{ t("agent.analysisWaiting") }}
          </p>
          <section
            v-if="workflow.generatedText.value"
            class="space-y-2 rounded border border-primary/40 bg-muted/30 p-3"
          >
            <h4 class="font-medium">{{ t("agent.generated") }}</h4>
            <p class="whitespace-pre-wrap break-words text-sm">
              {{ workflow.generatedText.value }}
            </p>
          </section>
          <details v-if="hasEvidence" class="rounded border p-3">
            <summary class="cursor-pointer text-sm font-medium">
              {{ t("agent.evidence") }}
            </summary>
            <div class="mt-3 space-y-4">
              <ul v-if="toolEvents.length" class="space-y-2 text-sm">
                <li
                  v-for="event in toolEvents"
                  :key="event.seq"
                  class="break-words rounded border p-2"
                >
                  <span>{{ event.detail.toolName ?? event.kind }}</span>
                  <span class="ml-2 text-muted-foreground">{{
                    event.kind === "tool_started"
                      ? t("agent.toolStarted")
                      : t("agent.toolCompleted")
                  }}</span>
                  <Badge
                    v-if="event.detail.failed === true"
                    variant="destructive"
                    class="ml-2"
                    >{{ t("agent.statuses.failed") }}</Badge
                  >
                </li>
              </ul>
              <div class="grid min-w-0 gap-3 sm:grid-cols-2">
                <section class="min-w-0 rounded border p-3">
                  <h4 class="mb-2 font-medium">{{ t("agent.facts") }}</h4>
                  <ul class="space-y-1 text-sm">
                    <li
                      v-for="(fact, index) in workflow.thread.value.draft.facts"
                      :key="index"
                      class="break-words"
                    >
                      {{
                        typeof fact === "string" ? fact : JSON.stringify(fact)
                      }}
                    </li>
                    <li
                      v-if="!workflow.thread.value.draft.facts.length"
                      class="text-muted-foreground"
                    >
                      {{ t("agent.noFacts") }}
                    </li>
                  </ul>
                </section>
                <section class="min-w-0 rounded border p-3">
                  <h4 class="mb-2 font-medium">{{ t("agent.hypotheses") }}</h4>
                  <ul class="space-y-1 text-sm">
                    <li
                      v-for="(item, index) in workflow.thread.value.draft
                        .hypotheses"
                      :key="index"
                      class="break-words"
                    >
                      {{
                        typeof item === "string" ? item : JSON.stringify(item)
                      }}
                    </li>
                    <li
                      v-if="!workflow.thread.value.draft.hypotheses.length"
                      class="text-muted-foreground"
                    >
                      {{ t("agent.noHypotheses") }}
                    </li>
                  </ul>
                </section>
              </div>
              <section
                v-if="workflow.thread.value.draft.citations.length"
                class="space-y-2"
              >
                <h4 class="font-medium">{{ t("agent.citations") }}</h4>
                <ol class="space-y-2">
                  <li
                    v-for="citation in workflow.thread.value.draft.citations"
                    :key="citation.chunk_id"
                    class="break-words rounded border p-3 text-sm"
                  >
                    <strong>{{ citation.claim }}</strong>
                    <p class="text-xs text-muted-foreground">
                      {{ citation.source_path }} ·
                      {{ citation.source_position }}
                    </p>
                    <blockquote class="mt-2 border-l-2 pl-3">
                      {{ citation.text }}
                    </blockquote>
                  </li>
                </ol>
              </section>
              <details v-if="workflow.thread.value.draft.citationChecks.length">
                <summary class="cursor-pointer text-sm">
                  {{ t("agent.citationChecks") }}
                </summary>
                <pre
                  class="mt-2 max-w-full overflow-auto rounded border p-3 text-xs"
                  >{{
                    JSON.stringify(
                      workflow.thread.value.draft.citationChecks,
                      null,
                      2,
                    )
                  }}</pre>
              </details>
            </div>
          </details>
          <details
            v-if="
              workflow.thread.value.status === 'awaiting_confirmation' ||
              workflow.thread.value.status === 'confirmed' ||
              workflow.thread.value.status === 'saving' ||
              workflow.thread.value.status === 'saved' ||
              workflow.thread.value.status === 'unknown' ||
              (workflow.thread.value.status === 'failed' &&
                receiptFailure &&
                !analysisFailed) ||
              persistedPlan
            "
            class="rounded border p-3"
          >
            <summary class="cursor-pointer text-sm font-medium">
              {{ t("agent.optionalPlan") }}
            </summary>
            <div class="mt-3 space-y-3">
              <p
                v-if="receiptFailure && !analysisFailed && !planReadbackError"
                role="status"
                class="text-sm text-muted-foreground"
              >
                {{ t("agent.planFailure") }}
              </p>
              <p
                v-if="workflow.thread.value.status === 'confirmed'"
                class="text-sm text-muted-foreground"
              >
                {{ t("agent.waitingConfirmation") }}
              </p>
              <p
                v-if="workflow.thread.value.status === 'saved'"
                class="rounded border p-3 text-sm"
              >
                {{
                  t("agent.savedPlan", {
                    id: workflow.thread.value.receipt.planId,
                  })
                }}
              </p>
              <div
                v-if="persistedPlan"
                class="min-w-0 break-words rounded border p-3 text-sm"
              >
                <h4 class="mb-2 font-medium">
                  {{ t("agent.savedPlanReadback") }}
                </h4>
                <h5 class="font-medium">{{ persistedPlan.title }}</h5>
                <div
                  class="typeset typeset-docs mt-2 max-w-none break-words"
                  v-html="renderMarkdown(persistedPlan.content)"
                />
              </div>
              <p
                v-if="planReadbackError"
                role="status"
                class="text-sm text-muted-foreground"
              >
                {{ t("agent.planReadbackUnavailable") }}
              </p>
              <template
                v-if="workflow.thread.value.status === 'awaiting_confirmation'"
              >
                <Label for="agent-draft-title">{{
                  t("agent.draftTitle")
                }}</Label>
                <Input
                  id="agent-draft-title"
                  v-model="workflow.draftTitle.value"
                  :disabled="workflow.busy.value"
                />
                <Label for="agent-draft-content">{{
                  t("agent.draftContent")
                }}</Label>
                <Textarea
                  id="agent-draft-content"
                  v-model="workflow.draftContent.value"
                  rows="7"
                  :disabled="workflow.busy.value"
                />
                <Button
                  v-if="draftDirty"
                  type="button"
                  variant="outline"
                  :disabled="workflow.busy.value"
                  @click="workflow.updateDraft"
                  >{{ t("agent.updateDraft") }}</Button
                >
                <Button
                  v-if="
                    capability.persistenceAvailable &&
                    workflow.generatedText.value
                  "
                  type="button"
                  variant="outline"
                  :disabled="workflow.busy.value || draftDirty"
                  @click="workflow.confirmDraft"
                  >{{ t("agent.confirm") }}</Button
                >
                <div v-if="workflow.draftContent.value" class="text-sm">
                  <span class="font-medium">{{ t("agent.preview") }}</span>
                  <div
                    class="typeset typeset-docs mt-2 max-w-none break-words"
                    v-html="renderMarkdown(workflow.draftContent.value)"
                  />
                </div>
              </template>
              <Button
                v-if="
                  workflow.thread.value.status === 'confirmed' &&
                  capability.persistenceAvailable
                "
                type="button"
                :disabled="workflow.busy.value"
                @click="workflow.save"
                >{{ t("agent.save") }}</Button
              >
              <Button
                v-if="
                  workflow.thread.value.status === 'unknown' &&
                  capability.persistenceAvailable
                "
                type="button"
                variant="outline"
                :disabled="workflow.busy.value"
                @click="workflow.recover"
                >{{ t("agent.recover") }}</Button
              >
            </div>
          </details>
          <Button
            v-if="canCancel"
            type="button"
            variant="destructive"
            :disabled="workflow.canceling.value"
            @click="workflow.cancel"
            >{{ t("agent.cancel") }}</Button
          >
        </template>
      </template>
    </div>
  </section>
</template>
