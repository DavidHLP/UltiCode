<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { platformUrl } from "@/utils/platformUrl";
import { Badge } from "@ulticode/design-system/ui/badge";
import { Button } from "@ulticode/design-system/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@ulticode/design-system/ui/card";
const { t } = useI18n();
const links = ["/problemset", "/contest", "/problemset"];
</script>
<template>
  <section
    id="use-cases"
    class="mx-auto flex max-w-7xl flex-col gap-8 px-4 py-16 sm:px-6"
    aria-labelledby="use-cases-title"
  >
    <header class="flex max-w-3xl flex-col items-start gap-3">
      <Badge variant="outline">{{ t("landing.useCases.badge") }}</Badge>
      <h2 id="use-cases-title" class="text-3xl font-semibold">
        {{ t("landing.useCases.title") }}
        {{ t("landing.useCases.titleItalic") }}
      </h2>
      <p class="text-muted-foreground">{{ t("landing.useCases.subtitle") }}</p>
    </header>
    <div class="grid gap-4 md:grid-cols-3">
      <Card v-for="(link, index) in links" :key="link + index"
        ><CardHeader
          ><CardDescription>0{{ index + 1 }}</CardDescription
          ><CardTitle>{{
            t(`landing.useCases.case${index + 1}Title`)
          }}</CardTitle></CardHeader
        ><CardContent class="flex flex-col gap-4"
          ><p>{{ t(`landing.useCases.case${index + 1}Desc`) }}</p>
          <ul class="flex flex-col gap-2 text-sm text-muted-foreground">
            <li>{{ t(`landing.useCases.case${index + 1}Metric1`) }}</li>
            <li>{{ t(`landing.useCases.case${index + 1}Metric2`) }}</li>
          </ul></CardContent
        ><CardFooter
          ><Button variant="outline" as-child
            ><a :href="platformUrl(link)">{{
              t(`landing.useCases.case${index + 1}Action`)
            }}</a></Button
          ></CardFooter
        ></Card
      >
    </div>
  </section>
</template>
