<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { agentApi } from "@/api/agent";

const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const failed = ref(false);
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

onMounted(async () => {
  const raw = route.query.threadId;
  const threadId = Array.isArray(raw) ? raw[0] : raw;
  if (!threadId) {
    await router.replace({ name: "personal-submissions" });
    return;
  }
  if (typeof threadId !== "string" || !uuidPattern.test(threadId)) {
    failed.value = true;
    return;
  }
  try {
    const thread = await agentApi.getThread(threadId);
    if (!uuidPattern.test(thread.sourceSubmissionId)) {
      failed.value = true;
      return;
    }
    await router.replace({
      name: "submission-detail",
      params: { id: thread.sourceSubmissionId },
      query: { threadId },
    });
  } catch {
    failed.value = true;
  }
});
</script>

<template>
  <main class="mx-auto flex min-h-48 max-w-3xl items-center justify-center p-6 text-sm text-muted-foreground">
    <p v-if="failed" role="alert">{{ t("agent.errors.requestFailed") }}</p>
    <p v-else>{{ t("agent.statuses.loading") }}</p>
  </main>
</template>
