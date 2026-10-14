<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { platformUrl } from "@/utils/platformUrl";
import { Button } from "@ulticode/design-system/ui/button";
const { t } = useI18n();
</script>
<template>
  <section
    class="mx-auto flex max-w-3xl flex-col items-center gap-6 px-4 py-20 text-center"
    aria-labelledby="final-title"
  >
    <h2 id="final-title" class="text-3xl font-semibold sm:text-4xl">
      {{ t("landing.finalStory.headline") }}
      {{ t("landing.finalStory.headlineItalic") }}
    </h2>
    <p class="text-lg text-muted-foreground">
      {{ t("landing.finalStory.subtext") }}
    </p>
    <div class="flex flex-wrap justify-center gap-3">
      <Button size="lg" as-child
        ><a :href="platformUrl('/problemset')">{{
          t("landing.finalStory.ctaPrimary")
        }}</a></Button
      ><Button size="lg" variant="outline" as-child
        ><a :href="platformUrl('/login')">{{
          t("landing.finalStory.ctaSecondary")
        }}</a></Button
      >
    </div>
  </section>
</template>
