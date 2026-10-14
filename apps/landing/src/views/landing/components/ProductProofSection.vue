<script setup lang="ts">
import { useI18n } from "vue-i18n";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@ulticode/design-system/ui/card";
const { t } = useI18n();
</script>
<template>
  <section
    id="workflow"
    class="border-y bg-muted/30 px-4 py-16 sm:px-6"
    aria-labelledby="proof-title"
  >
    <div class="mx-auto flex max-w-7xl flex-col gap-8">
      <header class="flex max-w-3xl flex-col gap-3">
        <h2 id="proof-title" class="text-3xl font-semibold">
          {{ t("landing.workflow.title") }}
          {{ t("landing.workflow.titleItalic") }}
        </h2>
        <p class="text-muted-foreground">
          {{ t("landing.workflow.subtitle") }}
        </p>
      </header>
      <ol class="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <li v-for="step in 4" :key="step">
          <Card class="h-full"
            ><CardHeader
              ><CardDescription>0{{ step }}</CardDescription
              ><CardTitle>{{
                t(`landing.workflow.step${step}Title`)
              }}</CardTitle></CardHeader
            ><CardContent class="flex flex-col gap-3"
              ><p>{{ t(`landing.workflow.step${step}Desc`) }}</p>
              <ul class="list-disc ps-4 text-sm text-muted-foreground">
                <li>{{ t(`landing.workflow.step${step}Bullet1`) }}</li>
                <li>{{ t(`landing.workflow.step${step}Bullet2`) }}</li>
              </ul></CardContent
            ></Card
          >
        </li>
      </ol>
      <p class="text-sm text-muted-foreground">
        {{ t("landing.proof.disclosure") }}
      </p>
    </div>
  </section>
</template>
