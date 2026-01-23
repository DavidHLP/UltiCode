<script setup lang="ts">
import { ref, computed } from "vue";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { toast } from "vue-sonner";
import { useI18n } from "vue-i18n";

const { t } = useI18n();

const feedbackType = ref<string>("bug");
const subject = ref("");
const description = ref("");
const isSubmitting = ref(false);

const feedbackTypes = computed(() => [
  { value: "bug", label: t("forum.feedback.types.bug") },
  { value: "feature", label: t("forum.feedback.types.feature") },
  { value: "improvement", label: t("forum.feedback.types.improvement") },
  { value: "content", label: t("forum.feedback.types.content") },
  { value: "other", label: t("forum.feedback.types.other") },
]);

async function submitFeedback() {
  if (!subject.value.trim() || !description.value.trim()) {
    toast.error(t("forum.feedback.fillRequired"));
    return;
  }

  isSubmitting.value = true;

  try {
    // Simulate API call
    await new Promise((resolve) => setTimeout(resolve, 1000));

    toast.success(t("forum.feedback.success"));

    // Reset form
    subject.value = "";
    description.value = "";
    feedbackType.value = "bug";
  } catch {
    toast.error(t("forum.feedback.error"));
  } finally {
    isSubmitting.value = false;
  }
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

    <div class="grid gap-8 md:grid-cols-3">
      <Card class="md:col-span-2 border-none shadow-sm bg-muted/20">
        <CardHeader>
          <CardTitle>{{ t("forum.feedback.submitTitle") }}</CardTitle>
        </CardHeader>
        <CardContent>
          <form @submit.prevent="submitFeedback" class="space-y-6">
            <div class="space-y-2">
              <Label for="feedback-type">{{ t("forum.feedback.type") }}</Label>
              <Select v-model="feedbackType">
                <SelectTrigger id="feedback-type" class="bg-background">
                  <SelectValue
                    :placeholder="t('forum.feedback.typePlaceholder')"
                  />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem
                    v-for="type in feedbackTypes"
                    :key="type.value"
                    :value="type.value"
                  >
                    {{ type.label }}
                  </SelectItem>
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
                class="bg-background"
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
                class="bg-background resize-none"
              />
              <p class="text-xs text-muted-foreground">
                {{ t("forum.feedback.bugHint") }}
              </p>
            </div>

            <Button
              type="submit"
              :disabled="isSubmitting"
              class="w-full h-11 rounded-full text-base font-bold"
            >
              <span v-if="isSubmitting">{{
                t("forum.feedback.submitting")
              }}</span>
              <span v-else>{{ t("forum.feedback.submitButton") }}</span>
            </Button>
          </form>
        </CardContent>
      </Card>

      <div class="space-y-6">
        <Card class="border-none shadow-sm bg-muted/20">
          <CardHeader>
            <CardTitle class="text-base font-black uppercase tracking-widest">{{
              t("forum.feedback.quickLinks")
            }}</CardTitle>
          </CardHeader>
          <CardContent class="space-y-4 text-sm">
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

        <Card class="border-none shadow-sm bg-muted/20">
          <CardHeader>
            <CardTitle class="text-base font-black uppercase tracking-widest">{{
              t("forum.feedback.commonIssues")
            }}</CardTitle>
          </CardHeader>
          <CardContent class="space-y-4 text-xs">
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
