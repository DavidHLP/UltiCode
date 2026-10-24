<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { Button } from "@ulticode/design-system/ui/button";
import {
  HoverCard,
  HoverCardTrigger,
  HoverCardContent,
} from "@ulticode/design-system/ui/hover-card";
import type { AgentCitation } from "@/api/agent";
defineProps<{
  citation: Pick<AgentCitation, "claim" | "source_path" | "source_position">;
  index: number;
}>();
const { t } = useI18n();
</script>

<template>
  <HoverCard :open-delay="100" :close-delay="100">
    <HoverCardTrigger as-child>
      <Button
        type="button"
        variant="secondary"
        size="sm"
        class="h-6 px-2 text-xs"
        :aria-label="t('agent.workspace.previewCitation', { index: index + 1 })"
        >[{{ index + 1 }}]</Button
      >
    </HoverCardTrigger>
    <HoverCardContent class="max-w-sm space-y-2 break-words" align="start">
      <p class="text-sm font-medium">{{ citation.claim }}</p>
      <p class="text-xs text-muted-foreground">
        {{ citation.source_path }} · {{ citation.source_position }}
      </p>
      <p class="text-xs text-muted-foreground">
        {{ t("agent.workspace.verifiedReference") }}
      </p>
    </HoverCardContent>
  </HoverCard>
</template>
