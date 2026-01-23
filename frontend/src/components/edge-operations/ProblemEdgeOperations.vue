<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { toast } from "vue-sonner";
import type {
  ProblemDetail,
  ProblemInteractionCounts,
  ProblemReactionType,
} from "@/types/problem-detail";
import {
  operateEdgeOperation,
  fetchEdgeOperationStatus,
  EdgeOperationTargetType,
  EdgeOperationType,
} from "@/api/interaction";
import { BookmarkType } from "@/types/bookmark";
import ProblemSaveButton from "./ProblemSaveButton.vue";
import { VoteControl } from "./vote-control";
import { fetchCurrentUserId, isAuthenticated } from "@/utils/auth";
import { useI18n } from "vue-i18n";

interface Props {
  problem?: ProblemDetail | null;
}

const props = defineProps<Props>();

const { t } = useI18n();

const interactionCounts = ref<ProblemInteractionCounts>({
  likes: 0,
  dislikes: 0,
  favorites: 0,
});

const viewerInteraction = ref<{
  reaction: ProblemReactionType | undefined;
}>({
  reaction: undefined,
});

const isLoadingInteractions = ref(false);

const loadInteractions = async (problemId: number | string) => {
  const userId = fetchCurrentUserId();
  isLoadingInteractions.value = true;
  try {
    const opsRes = await fetchEdgeOperationStatus(
      EdgeOperationTargetType.PROBLEM,
      problemId.toString(),
      userId ?? undefined,
    );

    interactionCounts.value = {
      likes: opsRes.likes,
      dislikes: opsRes.dislikes,
      favorites: opsRes.favorites,
    };
    viewerInteraction.value = {
      reaction:
        opsRes.viewer.vote === 1
          ? "like"
          : opsRes.viewer.vote === -1
            ? "dislike"
            : undefined,
    };
  } catch (e) {
    console.error("Failed to load interactions", e);
  } finally {
    isLoadingInteractions.value = false;
  }
};

watch(
  () => props.problem?.id,
  (problemId) => {
    if (problemId) {
      // First try to use interactions from props if available
      if (props.problem?.interactions) {
        interactionCounts.value = { ...props.problem.interactions.counts };
        viewerInteraction.value = {
          reaction: props.problem.interactions.viewer?.reaction,
        };
      } else {
        // Otherwise fetch from API
        loadInteractions(problemId);
      }
    }
  },
  { immediate: true },
);

const reactionCounts = computed(() => interactionCounts.value);

const toggleReaction = async (reaction: "like" | "dislike") => {
  if (!props.problem) return;
  if (!isAuthenticated()) {
    toast.error(t("problem.save.toast.loginToVote"));
    return;
  }
  const operationType =
    reaction === "like"
      ? EdgeOperationType.VOTE_UP
      : EdgeOperationType.VOTE_DOWN;
  try {
    const res = await operateEdgeOperation(
      operationType,
      EdgeOperationTargetType.PROBLEM,
      props.problem.id.toString(),
    );
    interactionCounts.value.likes = res.likes;
    interactionCounts.value.dislikes = res.dislikes;
    interactionCounts.value.favorites = res.favorites;
    viewerInteraction.value.reaction =
      res.viewer.vote === 1
        ? "like"
        : res.viewer.vote === -1
          ? "dislike"
          : undefined;
  } catch (e) {
    console.error("Failed to toggle reaction", e);
  }
};

const handleSaveChange = () => {
  if (props.problem) {
    loadInteractions(props.problem.id);
  }
};
</script>

<template>
  <div v-if="problem" class="flex items-center gap-3">
    <VoteControl
      :likes="reactionCounts.likes"
      :dislikes="reactionCounts.dislikes"
      :user-vote="
        viewerInteraction.reaction === 'like'
          ? 1
          : viewerInteraction.reaction === 'dislike'
            ? -1
            : 0
      "
      @vote="
        (v) => (v === 1 ? toggleReaction('like') : toggleReaction('dislike'))
      "
    />

    <ProblemSaveButton
      :problem-id="problem.id"
      :target-type="BookmarkType.PROBLEM"
      :count="reactionCounts.favorites"
      @change="handleSaveChange"
    />
  </div>
</template>
