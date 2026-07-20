<script setup lang="ts">
/**
 * ParseSection — code becomes structure. Pairs the narrative copy with a
 * trustworthy editor fragment (a real Python snippet, one of the seven
 * supported languages) so the 2D text and the 3D lattice tell one story.
 */
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { DEMO_CODE_SNIPPET } from "../data/demoMatrix";

const { t } = useI18n();

// Explicit literal keys: the i18n static checker cannot resolve dynamic
// key interpolation in templates.
const points = computed(() => [
  t("landing.parse.points.run"),
  t("landing.parse.points.languages"),
  t("landing.parse.points.debug"),
]);

defineOptions({ name: "ParseSection" });
</script>

<template>
  <section class="landing-section" aria-labelledby="landing-parse-title">
    <div class="landing-container">
      <div class="grid items-center gap-10 lg:grid-cols-2">
        <div class="landing-block landing-reveal">
          <p class="landing-eyebrow">{{ t("landing.parse.eyebrow") }}</p>
          <h2 id="landing-parse-title" class="landing-heading">
            {{ t("landing.parse.title") }}
          </h2>
          <p class="landing-body">{{ t("landing.parse.body") }}</p>
          <ul class="landing-points">
            <li v-for="point in points" :key="point">
              {{ point }}
            </li>
          </ul>
        </div>

        <div class="landing-editor landing-reveal" data-testid="landing-editor">
          <div class="landing-editor-bar">
            <span class="landing-editor-dot" />
            <span class="landing-editor-dot" />
            <span class="landing-editor-dot" />
            <span class="landing-editor-file">solution.py</span>
          </div>
          <pre class="landing-editor-code"><code>{{ DEMO_CODE_SNIPPET }}</code></pre>
        </div>
      </div>
    </div>
  </section>
</template>
