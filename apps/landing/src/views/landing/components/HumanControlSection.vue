<script setup lang="ts">
import { useI18n } from "vue-i18n";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@ulticode/design-system/ui/card";
import { CircleCheck } from "@lucide/vue";
const { t } = useI18n();
</script>
<template>
  <section
    id="governance"
    class="border-y bg-muted/30 px-4 py-16 sm:px-6"
    aria-labelledby="governance-title"
  >
    <div class="mx-auto flex max-w-7xl flex-col gap-8">
      <header class="flex max-w-3xl flex-col gap-3">
        <h2 id="governance-title" class="text-3xl font-semibold">
          {{ t("landing.governance.title") }}
          {{ t("landing.governance.titleItalic") }}
        </h2>
        <p class="text-muted-foreground">
          {{ t("landing.governance.subtitle") }}
        </p>
      </header>
      <div class="grid gap-4 md:grid-cols-2">
        <Card v-for="item in 2" :key="item"
          ><CardHeader
            ><CardTitle>{{
              t(`landing.governance.item${item}Title`)
            }}</CardTitle></CardHeader
          ><CardContent class="flex flex-col gap-4"
            ><p>{{ t(`landing.governance.item${item}Text`) }}</p>
            <p class="flex items-center gap-2 text-sm">
              <CircleCheck class="size-4" aria-hidden="true" />{{
                item === 2 ? "100% " : ""
              }}{{ t("landing.governance.statCaption") }}
            </p></CardContent
          ></Card
        >
      </div>
    </div>
  </section>
</template>
