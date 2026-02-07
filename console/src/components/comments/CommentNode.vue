<template>
  <div class="relative flex flex-row">
    <!-- Left Side: Avatar & Rail -->
    <div
      class="flex flex-col items-center mr-2 flex-shrink-0 w-8 relative"
      ref="containerRef"
    >
      <!-- Curve Connector (Child draws this to connect to parent's rail) -->
      <CommentConnector :depth="depth" :is-active="isParentRailHovered" />

      <!-- Parent Rail (I draw this for MY children) -->
      <CommentRail
        v-if="!isCollapsed && hasChildren"
        :height="railHeight"
        :is-active="isRailHovered"
        @click="toggleCollapse"
        @mouseenter="isRailHovered = true"
        @mouseleave="isRailHovered = false"
      />

      <!-- Avatar -->
      <div
        v-if="!isCollapsed"
        class="w-8 h-8 rounded-full overflow-hidden z-10 cursor-pointer border border-transparent hover:border-foreground/10 transition-all relative bg-background"
      >
        <div
          class="w-full h-full flex items-center justify-center text-white font-bold text-xs bg-muted"
        >
          <img
            v-if="comment.avatar"
            :src="comment.avatar"
            :alt="comment.author"
            class="w-full h-full object-cover"
          />
          <span v-else>{{ comment.author[0]?.toUpperCase() }}</span>
        </div>
      </div>

      <!-- Collapsed State Icon -->
      <div
        v-else
        class="mt-1 cursor-pointer hover:bg-accent p-1 rounded z-10"
        @click="toggleCollapse"
      >
        <Maximize2 class="h-3.5 w-3.5 text-muted-foreground" />
      </div>
    </div>

    <!-- Right Side: Content -->
    <div class="flex-1 min-w-0">
      <!-- Header -->
      <div class="flex items-center gap-2 mb-1 text-xs h-8">
        <span
          class="font-bold hover:underline cursor-pointer"
          :class="{ 'text-primary': comment.isOp }"
          @click="toggleCollapse"
        >
          {{ comment.author }}
        </span>
        <Badge
          v-if="comment.isOp"
          variant="secondary"
          class="text-[9px] font-black px-1 h-3.5 rounded-sm bg-primary/10 text-primary border-none uppercase"
        >
          {{ t("forum.author.op") }}
        </Badge>
        <span class="text-muted-foreground">•</span>
        <span
          class="text-muted-foreground hover:underline cursor-pointer"
          @click="toggleCollapse"
        >
          {{ comment.time }}
        </span>
        <span
          v-if="isCollapsed"
          class="text-muted-foreground text-xs italic ml-2 cursor-pointer select-none"
          @click="toggleCollapse"
        >
          {{ t("forum.comments.collapsed") }}
        </span>
      </div>

      <div v-if="!isCollapsed">
        <!-- Content Body -->
        <div v-if="!isEditing">
          <div
            class="text-sm leading-snug break-words pr-4 whitespace-pre-wrap mb-2"
          >
            {{ comment.content }}
          </div>

          <!-- Actions -->
          <div class="flex items-center gap-1 mt-2 -ml-2 select-none">
            <VoteControl
              :likes="likes"
              :dislikes="dislikes"
              :user-vote="userVote"
              class="origin-left"
              @vote="handleVote"
            />

            <ActionItem
              variant="button"
              :icon="MessageSquare"
              :label="t('common.actions.reply')"
              @click="toggleReply"
            />
            <ActionItem
              variant="button"
              :icon="Share2"
              :label="t('common.actions.share')"
            />

            <DropdownMenu v-if="comment.isOwn">
              <DropdownMenuTrigger as-child>
                <button
                  class="flex items-center gap-2 px-2 py-1 rounded hover:bg-accent text-muted-foreground hover:text-foreground transition-colors text-xs font-bold"
                >
                  <MoreHorizontal class="h-4.5 w-4.5" />
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="start" class="rounded-xl">
                <DropdownMenuItem
                  @click="toggleEdit"
                  class="gap-2 font-bold text-xs"
                >
                  <Pencil class="h-3.5 w-3.5" /> {{ t("common.actions.edit") }}
                </DropdownMenuItem>
                <DropdownMenuItem
                  class="text-destructive focus:text-destructive gap-2 font-bold text-xs"
                  @click="handleDeleteClick"
                >
                  <Trash2 class="h-3.5 w-3.5" />
                  {{ t("common.actions.delete") }}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
            <button
              v-else
              class="flex items-center gap-2 px-2 py-1 rounded hover:bg-accent text-muted-foreground hover:text-foreground transition-colors text-xs font-bold"
            >
              <Flag class="h-4.5 w-4.5" />
            </button>
          </div>
        </div>

        <!-- Edit Form -->
        <div v-if="isEditing" class="mb-3 mt-2">
          <CommentForm
            :parent-id="comment.id"
            :initial-content="comment.content"
            :on-cancel="() => (isEditing = false)"
            @submit="handleEditSubmit"
          />
        </div>

        <!-- Reply Input -->
        <div v-if="isReplying" class="mt-3 mb-2">
          <CommentForm
            :parent-id="comment.id"
            :on-cancel="() => (isReplying = false)"
            @submit="handleReplySubmit"
          />
        </div>

        <!-- Children Container -->
        <div v-if="hasChildren" class="mt-2" ref="childrenContainerRef">
          <div
            v-for="(child, index) in comment.children"
            :key="child.id"
            :ref="(el) => setLastChildRef(el, index)"
          >
            <CommentNode
              :comment="child"
              :depth="depth + 1"
              :is-parent-rail-hovered="isRailHovered"
              @reply="(id, content) => emit('reply', id, content)"
              @vote="(id, type) => emit('vote', id, type)"
              @edit="(id, content) => emit('edit', id, content)"
              @delete="(id) => emit('delete', id)"
            />
          </div>
        </div>
      </div>
    </div>

    <AlertDialog
      :open="showDeleteDialog"
      @update:open="showDeleteDialog = $event"
    >
      <AlertDialogContent class="rounded-3xl">
        <AlertDialogHeader>
          <AlertDialogTitle class="text-xl font-black tracking-tight">{{
            t("forum.comments.deleteDialog.title")
          }}</AlertDialogTitle>
          <AlertDialogDescription>
            {{ t("forum.comments.deleteDialog.description") }}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter class="gap-2 sm:gap-0">
          <AlertDialogCancel class="rounded-xl font-bold">{{
            t("forum.comments.deleteDialog.cancel")
          }}</AlertDialogCancel>
          <AlertDialogAction
            @click="confirmDelete"
            class="bg-destructive text-destructive-foreground hover:bg-destructive/90 rounded-xl font-black"
          >
            {{ t("forum.comments.deleteDialog.confirm") }}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  </div>
</template>

<script setup lang="ts">
import {
  ref,
  computed,
  onMounted,
  onBeforeUnmount,
  nextTick,
  watch,
  type ComponentPublicInstance,
} from "vue";
import type { Comment } from "@/types/comment";
import CommentConnector from "./CommentConnector.vue";
import CommentRail from "./CommentRail.vue";
import CommentForm from "./CommentForm.vue";
import { ActionItem, VoteControl } from "@/components/edge-operations";
import { Badge } from "@/components/ui/badge";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import {
  MessageSquare,
  Share2,
  MoreHorizontal,
  Maximize2,
  Flag,
  Pencil,
  Trash2,
} from "lucide-vue-next";
import { useI18n } from "vue-i18n";

defineOptions({
  name: "CommentNode",
});

const props = withDefaults(
  defineProps<{
    comment: Comment;
    depth?: number;
    isParentRailHovered?: boolean;
  }>(),
  {
    depth: 0,
    isParentRailHovered: false,
  },
);

const emit = defineEmits<{
  (e: "reply", commentId: number | string, content: string): void;
  (e: "vote", commentId: number | string, voteType: 1 | -1): void;
  (e: "edit", commentId: number | string, content: string): void;
  (e: "delete", commentId: number | string): void;
}>();

const { t } = useI18n();

const isCollapsed = ref(false);
const isRailHovered = ref(false);
const isReplying = ref(false);
const isEditing = ref(false);
const showDeleteDialog = ref(false);

const containerRef = ref<HTMLElement | null>(null);
const childrenContainerRef = ref<HTMLElement | null>(null);
const lastChildEl = ref<HTMLElement | null>(null);
const railHeight = ref(0);

const hasChildren = computed(() => (props.comment.children?.length ?? 0) > 0);
const userVote = ref(props.comment.userVote ?? 0);
const likes = ref(props.comment.likes ?? 0);
const dislikes = ref(props.comment.dislikes ?? 0);

// Update local state if props change (though usually we just rely on parent re-rendering)
watch(
  () => props.comment.userVote,
  (newVal) => {
    userVote.value = newVal ?? 0;
  },
);
watch(
  () => props.comment.likes,
  (newVal) => {
    likes.value = newVal ?? 0;
  },
);
watch(
  () => props.comment.dislikes,
  (newVal) => {
    dislikes.value = newVal ?? 0;
  },
);

const setLastChildRef = (
  el: ComponentPublicInstance | Element | null,
  index: number,
) => {
  if (index === (props.comment.children?.length ?? 0) - 1) {
    // If it's a component, get $el, otherwise use el directly
    lastChildEl.value = (el as ComponentPublicInstance)?.$el ?? el;
  }
};

const calculateRailHeight = () => {
  if (
    !isCollapsed.value &&
    hasChildren.value &&
    containerRef.value &&
    lastChildEl.value
  ) {
    const containerRect = containerRef.value.getBoundingClientRect();
    const lastChildRect = lastChildEl.value.getBoundingClientRect();

    // We want the line to end where the curve of the last child starts
    // In React code: lastChildCurveStartY = lastChildRect.top + 4;
    // The connector top is at child top. The curve starts at roughly 0 relative to child.
    // Let's match the React logic: `lastChildRect.top + 4 - containerTop`

    // Wait, the connector in React is `absolute -left-6 top-0`.
    // The curve M moves to `startY`.
    // The logic in React: `const lastChildCurveStartY = lastChildRect.top + 4;`

    const containerTop = containerRect.top;
    const lastChildTop = lastChildRect.top;

    // 4px seems to be an arbitrary offset used in the React example for visual alignment
    const distance = lastChildTop + 4 - containerTop;

    railHeight.value = distance;
  }
};

let resizeObserver: ResizeObserver | null = null;

onMounted(() => {
  calculateRailHeight();

  if (childrenContainerRef.value) {
    resizeObserver = new ResizeObserver(() => {
      calculateRailHeight();
    });
    resizeObserver.observe(childrenContainerRef.value);
  }
});

onBeforeUnmount(() => {
  if (resizeObserver) {
    resizeObserver.disconnect();
  }
});

watch([isCollapsed, hasChildren], () => {
  nextTick(calculateRailHeight);
});

const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value;
};

const toggleReply = () => {
  isReplying.value = !isReplying.value;
};

const toggleEdit = () => {
  isEditing.value = !isEditing.value;
};

const handleReplySubmit = (content: string) => {
  emit("reply", props.comment.id, content);
  isReplying.value = false;
  isCollapsed.value = false;
};

const handleEditSubmit = (content: string) => {
  emit("edit", props.comment.id, content);
  isEditing.value = false;
};

const handleDeleteClick = () => {
  showDeleteDialog.value = true;
};

const confirmDelete = () => {
  emit("delete", props.comment.id);
  showDeleteDialog.value = false;
};

const handleVote = (type: 1 | -1) => {
  // Optimistic update
  if (userVote.value === type) {
    // toggle off
    userVote.value = 0;
    if (type === 1) {
      likes.value = Math.max(0, likes.value - 1);
    } else {
      dislikes.value = Math.max(0, dislikes.value - 1);
    }
    emit("vote", props.comment.id, type); // Backend handles toggle logic usually
  } else {
    // switch or new vote
    if (userVote.value === 1) {
      likes.value = Math.max(0, likes.value - 1);
    } else if (userVote.value === -1) {
      dislikes.value = Math.max(0, dislikes.value - 1);
    }
    if (type === 1) {
      likes.value += 1;
    } else {
      dislikes.value += 1;
    }
    userVote.value = type;
    emit("vote", props.comment.id, type);
  }
};
</script>
