<script setup lang="ts">
import { computed, ref } from "vue";
import { useI18n } from "vue-i18n";
import { useFollowStatus } from "@/composables/useFollowStatus";
import { Button } from "@/components/ui/button";

const props = defineProps<{
  targetUserId: string;
  initialIsFollowing?: boolean;
  hidden?: boolean;
}>();

const { t } = useI18n();
const { isFollowing, loading, toggleFollow } = useFollowStatus(
  props.targetUserId,
  props.initialIsFollowing ?? false,
);

const isHovered = ref(false);

const buttonText = computed(() => {
  if (!isFollowing.value) return t("personal.social.follow");
  if (isHovered.value) return t("personal.social.unfollow");
  return t("personal.social.following");
});

const buttonVariant = computed(() => {
  if (!isFollowing.value) return "outline";
  if (isHovered.value) return "destructive";
  return "default";
});

async function handleClick() {
  try {
    await toggleFollow();
  } catch {
    // Error handled by composable rollback
  }
}
</script>

<template>
  <Button
    v-if="!hidden"
    :variant="buttonVariant"
    size="sm"
    :disabled="loading"
    class="min-w-[100px] rounded-none transition-all duration-200"
    @click="handleClick"
    @mouseenter="isHovered = true"
    @mouseleave="isHovered = false"
  >
    <span
      v-if="loading"
      class="mr-1 h-3 w-3 animate-spin rounded-full border-2 border-current border-t-transparent"
    />
    {{ buttonText }}
  </Button>
</template>
