<script setup lang="ts">
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { useI18n } from "vue-i18n";

const { t } = useI18n();
</script>

<template>
  <div
    class="max-w-7xl mx-auto w-full space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-500 pb-10 px-4 py-8"
  >
    <div class="max-w-4xl space-y-6">
      <div class="space-y-2">
        <h1 class="text-3xl font-bold tracking-tight">
          {{ t("forum.guidelines.title") }}
        </h1>
        <p class="text-muted-foreground text-lg">
          {{ t("forum.guidelines.subtitle") }}
        </p>
      </div>

      <Separator />

      <div class="grid gap-6">
        <Card class="border-none shadow-sm bg-muted/20">
          <CardHeader>
            <CardTitle>{{ t("forum.guidelines.rules.r1.title") }}</CardTitle>
          </CardHeader>
          <CardContent class="space-y-2 text-base">
            <p>{{ t("forum.guidelines.rules.r1.content") }}</p>
          </CardContent>
        </Card>

        <Card class="border-none shadow-sm bg-muted/20">
          <CardHeader>
            <CardTitle>{{ t("forum.guidelines.rules.r2.title") }}</CardTitle>
          </CardHeader>
          <CardContent class="space-y-2 text-base">
            <p>{{ t("forum.guidelines.rules.r2.content") }}</p>
          </CardContent>
        </Card>

        <Card class="border-none shadow-sm bg-muted/20">
          <CardHeader>
            <CardTitle>{{ t("forum.guidelines.rules.r3.title") }}</CardTitle>
          </CardHeader>
          <CardContent class="space-y-2 text-base">
            <p>{{ t("forum.guidelines.rules.r3.content") }}</p>
          </CardContent>
        </Card>

        <Card class="border-none shadow-sm bg-muted/20">
          <CardHeader>
            <CardTitle>{{ t("forum.guidelines.rules.r4.title") }}</CardTitle>
          </CardHeader>
          <CardContent class="space-y-2 text-base">
            <p>{{ t("forum.guidelines.rules.r4.content") }}</p>
          </CardContent>
        </Card>

        <Card class="border-none shadow-sm bg-muted/20">
          <CardHeader>
            <CardTitle>{{ t("forum.guidelines.rules.r5.title") }}</CardTitle>
          </CardHeader>
          <CardContent class="space-y-2 text-base">
            <p>{{ t("forum.guidelines.rules.r5.content") }}</p>
          </CardContent>
        </Card>

        <Card class="border-none shadow-sm bg-muted/20">
          <CardHeader>
            <CardTitle>{{ t("forum.guidelines.rules.r6.title") }}</CardTitle>
          </CardHeader>
          <CardContent class="space-y-2 text-base">
            <p>{{ t("forum.guidelines.rules.r6.content") }}</p>
          </CardContent>
        </Card>

        <Card class="border-none shadow-sm bg-muted/20">
          <CardHeader>
            <CardTitle>{{ t("forum.guidelines.rules.r7.title") }}</CardTitle>
          </CardHeader>
          <CardContent class="space-y-2 text-base">
            <p>{{ t("forum.guidelines.rules.r7.content") }}</p>
          </CardContent>
        </Card>

        <Card class="border-none shadow-sm bg-muted/20">
          <CardHeader>
            <CardTitle>{{ t("forum.guidelines.rules.r8.title") }}</CardTitle>
          </CardHeader>
          <CardContent class="space-y-2 text-base">
            <p>{{ t("forum.guidelines.rules.r8.content") }}</p>
          </CardContent>
        </Card>

        <Card class="border-none shadow-sm bg-muted/20">
          <CardHeader>
            <CardTitle>{{
              t("forum.guidelines.rules.enforcement.title")
            }}</CardTitle>
          </CardHeader>
          <CardContent class="space-y-2 text-base">
            <p>{{ t("forum.guidelines.rules.enforcement.content") }}</p>
          </CardContent>
        </Card>

        <div
          class="rounded-2xl border border-primary/20 bg-primary/5 p-6 text-base"
        >
          <p class="font-bold text-primary">
            {{ t("forum.guidelines.questionsTitle") }}
          </p>
          <p class="mt-2 text-muted-foreground">
            {{ t("forum.guidelines.questionsDesc") }}
            <router-link
              to="/forum/feedback"
              class="text-primary hover:underline font-medium"
            >
              {{ t("forum.feedback.title") }}
            </router-link>
            ...
          </p>
        </div>
      </div>
    </div>
  </div>
</template>
