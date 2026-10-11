<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { useAgentWorkflow } from "@/composables/useAgentWorkflow";
import type { AgentThreadStatus } from "@/api/agent";

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const workflow = useAgentWorkflow();
const sourceSubmissionId = ref("");
const question = ref("");
const draftDirty = computed(() => Boolean(workflow.thread.value)
  && (workflow.draftTitle.value !== workflow.thread.value?.draft.title
    || workflow.draftContent.value !== workflow.thread.value?.draft.content));
const statusText = computed(() => {
  if (workflow.loading.value) return t("agent.statuses.loading");
  if (workflow.analyzing.value) return t("agent.statuses.analyzing");
  if (workflow.saving.value) return t("agent.statuses.saving");
  if (workflow.busy.value && !workflow.thread.value) return t("agent.statuses.creating");
  const status: AgentThreadStatus | "idle" = workflow.thread.value?.status ?? "idle";
  switch (status) {
    case "awaiting_confirmation": return t("agent.statuses.awaiting_confirmation");
    case "analyzing": return t("agent.statuses.analyzing");
    case "confirmed": return t("agent.statuses.confirmed");
    case "saving": return t("agent.statuses.saving");
    case "saved": return t("agent.statuses.saved");
    case "unknown": return t("agent.statuses.unknown");
    case "failed": return t("agent.statuses.failed");
    case "cancelled": return t("agent.statuses.cancelled");
    default: return t("agent.statuses.idle");
  }
});
const errorText = computed(() => {
  switch (workflow.error.value) {
    case "streamFailed": return t("agent.errors.streamFailed");
    case "streamTimedOut": return t("agent.errors.streamTimedOut");
    case "runReplaced": return t("agent.errors.runReplaced");
    case "requestFailed": return t("agent.errors.requestFailed");
    default: return "";
  }
});
const canCancel = computed(() => !workflow.saving.value
  && ["analyzing", "awaiting_confirmation", "confirmed"]
    .includes(workflow.thread.value?.status ?? ""));
const canConfirm = computed(() => workflow.thread.value?.status === "awaiting_confirmation"
  && !draftDirty.value && Boolean(workflow.generatedText.value));
const toolEvents = computed(() => workflow.events.value.filter((event) =>
  event.kind === "tool_started" || event.kind === "tool_completed"));

async function updateThreadQuery(threadId: string | null) {
  await router.replace({
    query: { ...route.query, threadId: threadId ?? undefined },
  });
}

async function createThread() {
  const value = await workflow.createThread(sourceSubmissionId.value.trim(), question.value.trim());
  if (value) await updateThreadQuery(value.threadId);
}

async function loadFromUrl() {
  const threadId = route.query.threadId;
  if (typeof threadId === "string" && threadId) await workflow.loadThread(threadId);
}

async function startNewThread() {
  if (!workflow.clear()) return;
  await updateThreadQuery(null);
}

onMounted(loadFromUrl);
</script>

<template>
  <main class="mx-auto w-full max-w-5xl space-y-6 p-4 md:p-8">
    <header class="space-y-2">
      <h1 class="text-2xl font-semibold tracking-tight">{{ t("agent.title") }}</h1>
      <p class="text-sm text-muted-foreground">{{ t("agent.description") }}</p>
    </header>

    <Card>
      <CardHeader>
        <CardTitle>{{ t("agent.newThread") }}</CardTitle>
      </CardHeader>
      <CardContent>
        <form class="space-y-4" @submit.prevent="createThread">
          <div class="space-y-2">
            <Label for="agent-source-id">{{ t("agent.sourceSubmissionId") }}</Label>
            <Input id="agent-source-id" v-model="sourceSubmissionId" required autocomplete="off" />
          </div>
          <div class="space-y-2">
            <Label for="agent-question">{{ t("agent.question") }}</Label>
            <Textarea
              id="agent-question"
              v-model="question"
              :placeholder="t('agent.questionPlaceholder')"
              minlength="2"
              maxlength="200"
              required
              rows="3"
            />
          </div>
          <Button type="submit" :disabled="workflow.busy.value">
            {{ t("agent.start") }}
          </Button>
        </form>
      </CardContent>
    </Card>

    <p v-if="errorText" role="alert" class="rounded-md border border-destructive/40 bg-destructive/5 p-3 text-sm">
      {{ errorText }}
    </p>

    <Card v-if="workflow.thread.value">
      <CardHeader>
        <div class="flex flex-wrap items-start justify-between gap-3">
          <div class="space-y-1">
            <CardTitle>{{ t("agent.status") }}</CardTitle>
            <CardDescription>
              <code class="break-all">{{ t("agent.thread") }}: {{ workflow.thread.value.threadId }}</code>
            </CardDescription>
          </div>
          <div class="flex flex-wrap gap-2">
            <Badge variant="secondary" role="status">{{ statusText }}</Badge>
            <Button
              type="button"
              variant="outline"
              :disabled="workflow.loading.value || workflow.saving.value"
              @click="loadFromUrl"
            >
              {{ t("agent.refresh") }}
            </Button>
            <Button
              type="button"
              variant="outline"
              :disabled="workflow.saving.value || workflow.thread.value?.status === 'saving'"
              @click="startNewThread"
            >
              {{ t("agent.newThread") }}
            </Button>
          </div>
        </div>
      </CardHeader>
      <CardContent class="space-y-6">
        <p v-if="workflow.saving.value || workflow.thread.value.status === 'saving'" class="rounded-md border p-3 text-sm">
          {{ t("agent.savingWarning") }}
        </p>
        <p v-if="workflow.thread.value.status === 'saved' && workflow.thread.value.receipt.planId" class="rounded-md border p-3 text-sm">
          {{ t("agent.savedPlan", { id: workflow.thread.value.receipt.planId }) }}
        </p>
        <p v-if="workflow.thread.value.receipt.failureReason" class="text-sm text-destructive">
          {{ workflow.thread.value.receipt.failureReason }}
        </p>

        <section class="space-y-3">
          <h2 class="text-lg font-medium">{{ t("agent.draft") }}</h2>
          <div class="space-y-2">
            <Label for="agent-draft-title">{{ t("agent.draftTitle") }}</Label>
            <Input
              id="agent-draft-title"
              v-model="workflow.draftTitle.value"
              :disabled="workflow.thread.value.status !== 'awaiting_confirmation' || workflow.busy.value"
            />
          </div>
          <div class="space-y-2">
            <Label for="agent-draft-content">{{ t("agent.draftContent") }}</Label>
            <Textarea
              id="agent-draft-content"
              v-model="workflow.draftContent.value"
              rows="8"
              :disabled="workflow.thread.value.status !== 'awaiting_confirmation' || workflow.busy.value"
            />
          </div>
          <div class="flex flex-wrap gap-2">
            <Button
              v-if="workflow.thread.value.status === 'awaiting_confirmation'"
              type="button"
              variant="outline"
              :disabled="workflow.busy.value || !draftDirty"
              @click="workflow.updateDraft"
            >{{ t("agent.updateDraft") }}</Button>
            <Button
              v-if="workflow.thread.value.status === 'awaiting_confirmation'"
              type="button"
              :disabled="workflow.busy.value || workflow.analyzing.value"
              @click="workflow.analyze"
            >{{ t("agent.analyze") }}</Button>
            <Button
              v-if="canConfirm"
              type="button"
              variant="outline"
              :disabled="workflow.busy.value"
              @click="workflow.confirmDraft"
            >{{ t("agent.confirm") }}</Button>
            <Button
              v-if="workflow.thread.value.status === 'confirmed'"
              type="button"
              :disabled="workflow.busy.value"
              @click="workflow.save"
            >{{ t("agent.save") }}</Button>
            <Button
              v-if="workflow.thread.value.status === 'unknown'"
              type="button"
              variant="outline"
              :disabled="workflow.busy.value"
              @click="workflow.recover"
            >{{ t("agent.recover") }}</Button>
            <Button
              v-if="canCancel"
              type="button"
              variant="destructive"
              :disabled="workflow.canceling.value"
              @click="workflow.cancel"
            >{{ t("agent.cancel") }}</Button>
          </div>
          <p v-if="canConfirm || workflow.thread.value.status === 'confirmed'" class="text-sm text-muted-foreground">
            {{ t("agent.waitingConfirmation") }}
          </p>
        </section>

        <section v-if="toolEvents.length" class="space-y-2">
          <h2 class="text-lg font-medium">{{ t("agent.toolEvents") }}</h2>
          <ul class="space-y-2">
            <li v-for="event in toolEvents" :key="event.seq" class="rounded-md border p-3 text-sm">
              <span class="font-medium">{{ event.detail.toolName ?? event.kind }}</span>
              <span class="ml-2 text-muted-foreground">
                {{ event.kind === "tool_started" ? t("agent.toolStarted") : t("agent.toolCompleted") }}
              </span>
              <Badge v-if="event.detail.failed === true" variant="destructive" class="ml-2">{{ t("agent.statuses.failed") }}</Badge>
            </li>
          </ul>
        </section>

        <section v-if="workflow.generatedText.value" class="space-y-2">
          <h2 class="text-lg font-medium">{{ t("agent.generated") }}</h2>
          <p class="whitespace-pre-wrap rounded-md border p-4 text-sm">{{ workflow.generatedText.value }}</p>
        </section>

        <section class="space-y-2">
          <h2 class="text-lg font-medium">{{ t("agent.citations") }}</h2>
          <p v-if="!workflow.thread.value.draft.citations.length" class="text-sm text-muted-foreground">
            {{ t("agent.noCitations") }}
          </p>
          <ol v-else class="space-y-3">
            <li
              v-for="citation in workflow.thread.value.draft.citations"
              :key="citation.chunk_id"
              class="space-y-2 rounded-md border p-4"
            >
              <p class="text-sm font-medium">{{ citation.claim }}</p>
              <p class="break-all text-xs text-muted-foreground">
                {{ t("agent.source") }}: {{ citation.source_path }} · {{ citation.source_position }} · {{ citation.chunk_id }}
              </p>
              <blockquote class="border-l-2 pl-3 text-sm text-muted-foreground">
                {{ citation.text }}
              </blockquote>
            </li>
          </ol>
        </section>

        <details v-if="workflow.thread.value.draft.citationChecks.length" class="text-sm">
          <summary class="cursor-pointer font-medium">{{ t("agent.citationChecks") }}</summary>
          <pre class="mt-2 overflow-auto rounded-md border p-3">{{ JSON.stringify(workflow.thread.value.draft.citationChecks, null, 2) }}</pre>
        </details>
      </CardContent>
    </Card>
  </main>
</template>
