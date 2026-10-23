<script setup lang="ts">
import { ref, computed } from "vue";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@ulticode/design-system/ui/card";
import { Button } from "@ulticode/design-system/ui/button";
import { Textarea } from "@ulticode/design-system/ui/textarea";
import { Input } from "@ulticode/design-system/ui/input";
import { Label } from "@ulticode/design-system/ui/label";
import { Separator } from "@ulticode/design-system/ui/separator";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  SelectGroup,
} from "@ulticode/design-system/ui/select";
import { toast } from "vue-sonner";
import { useI18n } from "vue-i18n";

const { t } = useI18n();

const feedbackType = ref<string>("bug");
const subject = ref("");
const description = ref("");

const feedbackTypes = computed(() => [
  { value: "bug", label: t("forum.feedback.types.bug") },
  { value: "feature", label: t("forum.feedback.types.feature") },
  { value: "improvement", label: t("forum.feedback.types.improvement") },
  { value: "content", label: t("forum.feedback.types.content") },
  { value: "other", label: t("forum.feedback.types.other") },
]);
const selectedFeedbackTypeLabel = computed(
  () =>
    feedbackTypes.value.find((type) => type.value === feedbackType.value)
      ?.label,
);

function submitFeedback() {
  if (!subject.value.trim() || !description.value.trim()) {
    toast.error(t("forum.feedback.fillRequired"));
    return;
  }

  toast.info(t("forum.feedback.unavailable"));
}
</script>

<template>
  <div
    class="max-w-7xl mx-auto w-full space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-500 pb-10 px-4 py-8"
  >
    <div class="space-y-2">
      <h1 class="text-3xl font-bold tracking-tight">
        {{ t("forum.feedback.title") }}
      </h1>
      <p class="text-muted-foreground text-lg">
        {{ t("forum.feedback.description") }}
      </p>
    </div>

    <Separator />

    <div class="grid gap-8 lg:grid-cols-3">
      <Card class="lg:col-span-2">
        <CardHeader>
          <CardTitle>{{ t("forum.feedback.submitTitle") }}</CardTitle>
        </CardHeader>
        <CardContent>
          <form @submit.prevent="submitFeedback" class="space-y-6">
            <div class="space-y-2">
              <Label for="feedback-type">{{ t("forum.feedback.type") }}</Label>
              <Select v-model="feedbackType">
                <SelectTrigger id="feedback-type">
                  <SelectValue
                    :placeholder="t('forum.feedback.typePlaceholder')"
                    >{{ selectedFeedbackTypeLabel }}</SelectValue
                  >
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup
                    ><SelectItem
                      v-for="type in feedbackTypes"
                      :key="type.value"
                      :value="type.value"
                    >
                      {{ type.label }}
                    </SelectItem></SelectGroup
                  >
                </SelectContent>
              </Select>
            </div>

            <div class="space-y-2">
              <Label for="subject">{{ t("forum.feedback.subject") }}</Label>
              <Input
                id="subject"
                v-model="subject"
                :placeholder="t('forum.feedback.subjectPlaceholder')"
                required
              />
            </div>

            <div class="space-y-2">
              <Label for="description">{{ t("forum.feedback.content") }}</Label>
              <Textarea
                id="description"
                v-model="description"
                :placeholder="t('forum.feedback.contentPlaceholder')"
                rows="8"
                required
                class="resize-none"
              />
              <p class="text-xs text-muted-foreground">
                {{ t("forum.feedback.bugHint") }}
              </p>
            </div>

            <Button type="submit" class="w-full">
              {{ t("forum.feedback.submitButton") }}
            </Button>
          </form>
        </CardContent>
      </Card>

      <div class="space-y-6">
        <Card>
          <CardHeader>
            <CardTitle>{{ t("forum.feedback.quickLinks") }}</CardTitle>
          </CardHeader>
          <CardContent class="space-y-4">
            <div class="group">
              <router-link
                to="/forum/guidelines"
                class="text-primary hover:underline font-bold"
              >
                {{ t("forum.feedback.communityGuidelines") }}
              </router-link>
              <p class="mt-1 text-xs text-muted-foreground">
                {{ t("forum.feedback.readRules") }}
              </p>
            </div>
            <div class="group">
              <router-link
                to="/forum"
                class="text-primary hover:underline font-bold"
              >
                {{ t("forum.feedback.forumHome") }}
              </router-link>
              <p class="mt-1 text-xs text-muted-foreground">
                {{ t("forum.feedback.backToDiscussions") }}
              </p>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{{ t("forum.feedback.commonIssues") }}</CardTitle>
          </CardHeader>
          <CardContent class="space-y-4">
            <div>
              <p class="font-bold text-foreground/80">
                {{ t("forum.feedback.issue1Title") }}
              </p>
              <p class="text-muted-foreground leading-relaxed">
                {{ t("forum.feedback.issue1Desc") }}
              </p>
            </div>
            <div>
              <p class="font-bold text-foreground/80">
                {{ t("forum.feedback.issue2Title") }}
              </p>
              <p class="text-muted-foreground leading-relaxed">
                {{ t("forum.feedback.issue2Desc") }}
              </p>
            </div>
            <div>
              <p class="font-bold text-foreground/80">
                {{ t("forum.feedback.issue3Title") }}
              </p>
              <p class="text-muted-foreground leading-relaxed">
                {{ t("forum.feedback.issue3Desc") }}
              </p>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  </div>
</template>
