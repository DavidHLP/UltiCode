<template>
  <form @submit.prevent="submit" class="w-full">
    <div
      class="rounded-xl border border-muted-foreground/10 bg-background focus-within:ring-1 focus-within:ring-primary/30 transition-all overflow-hidden shadow-sm"
    >
      <Textarea
        v-model="content"
        :placeholder="t('forum.comments.placeholder')"
        class="min-h-[100px] w-full resize-none border-0 bg-transparent px-4 py-3 text-sm placeholder:text-muted-foreground/50 focus-visible:ring-0 focus-visible:ring-offset-0 disabled:cursor-not-allowed disabled:opacity-50 font-medium"
      />
      <div
        class="flex items-center justify-between px-3 py-2 bg-muted/20 border-t border-muted-foreground/5"
      >
        <div class="flex items-center gap-0.5">
          <Button
            type="button"
            variant="ghost"
            size="icon"
            class="h-8 w-8 rounded-full text-muted-foreground hover:text-primary hover:bg-primary/5 transition-colors"
          >
            <ImageIcon class="h-4 w-4" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            class="h-8 w-8 rounded-full text-muted-foreground hover:text-primary hover:bg-primary/5 transition-colors"
          >
            <Type class="h-4 w-4" />
          </Button>
        </div>
        <div class="flex items-center gap-2">
          <Button
            v-if="onCancel"
            type="button"
            variant="ghost"
            size="sm"
            class="rounded-full px-4 h-8 text-[12px] font-bold text-muted-foreground hover:bg-muted"
            @click="onCancel"
          >
            {{ t("common.actions.cancel") }}
          </Button>
          <Button
            type="submit"
            size="sm"
            class="rounded-full px-5 h-8 text-[12px] font-bold shadow-sm"
            :disabled="!content.trim()"
          >
            {{ t("forum.comments.submit") }}
          </Button>
        </div>
      </div>
    </div>
  </form>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { Image as ImageIcon, Type } from "lucide-vue-next";
import { useI18n } from "vue-i18n";

defineOptions({
  name: "CommentForm",
});

const props = defineProps<{
  parentId?: number | string;
  initialContent?: string;
  onCancel?: () => void;
}>();

const emit = defineEmits<{
  (e: "submit", content: string, parentId?: number | string): void;
}>();

const { t } = useI18n();

const content = ref(props.initialContent || "");

const submit = () => {
  if (!content.value.trim()) return;
  emit("submit", content.value, props.parentId);
  content.value = "";
};
</script>
