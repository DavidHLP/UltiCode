<script setup lang="ts">
import { ref } from "vue";
import { useClipboard } from "@vueuse/core";
import { useI18n } from "vue-i18n";
import { Copy, Check, CornerDownLeft } from "@lucide/vue";
import { Button } from "@ulticode/design-system/ui/button";
import { MessageFooter } from "@ulticode/design-system/ui/message";
import {
  Tooltip,
  TooltipProvider,
  TooltipTrigger,
  TooltipContent,
} from "@ulticode/design-system/ui/tooltip";
import type { AgentConversationTurn } from "@/api/agent";

const props = defineProps<{
  turn: AgentConversationTurn;
  disabled: boolean;
  hasDraft: boolean;
}>();
const emit = defineEmits<{ reuse: [turn: AgentConversationTurn] }>();
const { t } = useI18n();
const { copy, copied } = useClipboard({ copiedDuring: 2000, legacy: true });
const copyFailed = ref(false);
async function copyAnswer() {
  if (!props.turn.answer) return;
  copyFailed.value = false;
  try {
    await copy(props.turn.answer);
  } catch {
    copyFailed.value = true;
  }
}
</script>

<template>
  <MessageFooter class="gap-1 px-0">
    <TooltipProvider :delay-duration="0">
      <Tooltip v-if="turn.answer">
        <TooltipTrigger as-child>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            class="size-8"
            :aria-label="
              t(
                copied
                  ? 'agent.workspace.copied'
                  : 'agent.workspace.copyAnswer',
              )
            "
            @click="copyAnswer"
          >
            <Check v-if="copied" class="size-4" aria-hidden="true" /><Copy
              v-else
              class="size-4"
              aria-hidden="true"
            />
          </Button>
        </TooltipTrigger>
        <TooltipContent>{{
          t(copied ? "agent.workspace.copied" : "agent.workspace.copyAnswer")
        }}</TooltipContent>
      </Tooltip>
      <Tooltip>
        <TooltipTrigger as-child>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            class="size-8"
            :aria-label="t('agent.workspace.reuseQuestion')"
            :disabled="disabled || hasDraft"
            @click="emit('reuse', turn)"
          >
            <CornerDownLeft class="size-4" aria-hidden="true" />
          </Button>
        </TooltipTrigger>
        <TooltipContent>{{
          t(
            hasDraft
              ? "agent.workspace.keepDraft"
              : "agent.workspace.reuseQuestion",
          )
        }}</TooltipContent>
      </Tooltip>
    </TooltipProvider>
    <span
      v-if="copied || copyFailed"
      role="status"
      class="text-xs text-muted-foreground"
      >{{
        t(copyFailed ? "agent.workspace.copyFailed" : "agent.workspace.copied")
      }}</span
    >
  </MessageFooter>
</template>
