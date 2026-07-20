<script setup lang="ts">
/**
 * MatrixSection — pass through the judge. Test cells light up in sequence
 * when the section enters the viewport (IntersectionObserver, CSS-driven
 * stagger), mirroring the cause chain: code in → cells run → verdict out.
 * The demo model is static and labelled as illustrative.
 */
import { onBeforeUnmount, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { DEMO_TEST_CELLS } from "../data/demoMatrix";

const { t } = useI18n();

const rootRef = ref<HTMLElement | null>(null);
const active = ref(false);
let observer: IntersectionObserver | null = null;

onMounted(() => {
  const root = rootRef.value;
  if (!root || typeof IntersectionObserver === "undefined") {
    active.value = true;
    return;
  }
  observer = new IntersectionObserver(
    (entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        active.value = true;
        observer?.disconnect();
        observer = null;
      }
    },
    { threshold: 0.35 },
  );
  observer.observe(root);
});

onBeforeUnmount(() => {
  observer?.disconnect();
  observer = null;
});

defineOptions({ name: "MatrixSection" });
</script>

<template>
  <section
    ref="rootRef"
    class="landing-section"
    aria-labelledby="landing-matrix-title"
  >
    <div class="landing-container">
      <div class="grid items-center gap-10 lg:grid-cols-2">
        <div class="landing-block landing-reveal order-2 lg:order-1">
          <p class="landing-eyebrow">{{ t("landing.matrix.eyebrow") }}</p>
          <h2 id="landing-matrix-title" class="landing-heading">
            {{ t("landing.matrix.title") }}
          </h2>
          <p class="landing-body">{{ t("landing.matrix.body") }}</p>
        </div>

        <div
          class="landing-matrix order-1 lg:order-2"
          :class="{ 'is-active': active }"
          data-testid="landing-matrix"
        >
          <ul class="landing-matrix-cells">
            <li
              v-for="(cell, index) in DEMO_TEST_CELLS"
              :key="cell.id"
              class="landing-matrix-cell"
              :style="{ transitionDelay: `${index * 160}ms` }"
            >
              <span class="font-data text-xs text-muted-foreground">
                {{ cell.id }}
              </span>
              <span class="landing-matrix-status">
                {{ t("landing.matrix.status.passed") }}
              </span>
              <span class="font-data text-xs text-muted-foreground">
                {{ cell.timeMs }} ms · {{ cell.memoryMb }} MB
              </span>
            </li>
          </ul>
          <p
            class="landing-matrix-verdict"
            :style="{ transitionDelay: `${DEMO_TEST_CELLS.length * 160}ms` }"
          >
            {{ t("landing.matrix.verdict") }}
          </p>
          <p class="landing-matrix-note">{{ t("landing.matrix.demoNote") }}</p>
        </div>
      </div>
    </div>
  </section>
</template>
