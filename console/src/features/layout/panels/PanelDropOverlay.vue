<script setup lang="ts">
import { ref } from "vue";
import { useI18n } from "vue-i18n";

const { t } = useI18n();

const emit = defineEmits<{
  (
    e: "zone-change",
    zone: "top" | "bottom" | "left" | "right" | "center" | null,
  ): void;
  (e: "drop", zone: "top" | "bottom" | "left" | "right" | "center"): void;
}>();

const activeZone = ref<"top" | "bottom" | "left" | "right" | "center" | null>(
  null,
);

const handleMouseMove = (e: MouseEvent) => {
  const target = e.currentTarget as HTMLElement;
  const rect = target.getBoundingClientRect();
  const x = e.clientX - rect.left;
  const y = e.clientY - rect.top;
  const w = rect.width;
  const h = rect.height;

  // Thresholds (percent)
  const threshold = 0.25; // 25% for edge zones

  let zone: "top" | "bottom" | "left" | "right" | "center" = "center";

  if (y < h * threshold) {
    zone = "top";
  } else if (y > h * (1 - threshold)) {
    zone = "bottom";
  } else if (x < w * threshold) {
    zone = "left";
  } else if (x > w * (1 - threshold)) {
    zone = "right";
  }

  if (activeZone.value !== zone) {
    activeZone.value = zone;
    emit("zone-change", zone);
  }
};

const handleMouseLeave = () => {
  activeZone.value = null;
  emit("zone-change", null);
};

const handleMouseUp = () => {
  if (activeZone.value) {
    emit(
      "drop",
      activeZone.value as "top" | "bottom" | "left" | "right" | "center",
    );
  }
};
</script>

<template>
  <div
    class="absolute inset-0 z-50 flex items-center justify-center bg-black/5"
    @mousemove="handleMouseMove"
    @mouseleave="handleMouseLeave"
    @mouseup="handleMouseUp"
  >
    <!-- Visual Feedback Layers -->
    <div
      v-if="activeZone === 'top'"
      class="absolute top-0 left-0 right-0 h-1/4 bg-blue-500/20 border-b-2 border-blue-500 transition-all duration-150 pointer-events-none"
    >
      <div
        class="absolute inset-0 flex items-center justify-center text-blue-600 font-bold bg-white/50"
      >
        {{ t("problem.layout.splitTop") }}
      </div>
    </div>

    <div
      v-if="activeZone === 'bottom'"
      class="absolute bottom-0 left-0 right-0 h-1/4 bg-blue-500/20 border-t-2 border-blue-500 transition-all duration-150 pointer-events-none"
    >
      <div
        class="absolute inset-0 flex items-center justify-center text-blue-600 font-bold bg-white/50"
      >
        {{ t("problem.layout.splitBottom") }}
      </div>
    </div>

    <div
      v-if="activeZone === 'left'"
      class="absolute top-0 left-0 bottom-0 w-1/4 bg-blue-500/20 border-r-2 border-blue-500 transition-all duration-150 pointer-events-none"
    >
      <div
        class="absolute inset-0 flex items-center justify-center text-blue-600 font-bold bg-white/50 writing-mode-vertical"
      >
        {{ t("problem.layout.splitLeft") }}
      </div>
    </div>

    <div
      v-if="activeZone === 'right'"
      class="absolute top-0 right-0 bottom-0 w-1/4 bg-blue-500/20 border-l-2 border-blue-500 transition-all duration-150 pointer-events-none"
    >
      <div
        class="absolute inset-0 flex items-center justify-center text-blue-600 font-bold bg-white/50 writing-mode-vertical"
      >
        {{ t("problem.layout.splitRight") }}
      </div>
    </div>

    <div
      v-if="activeZone === 'center'"
      class="absolute inset-4 bg-blue-500/10 border-2 border-dashed border-blue-500 rounded-lg transition-all duration-150 pointer-events-none"
    >
      <div
        class="absolute inset-0 flex items-center justify-center text-blue-600 font-bold"
      >
        {{ t("problem.layout.addToGroup") }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.writing-mode-vertical {
  writing-mode: vertical-lr;
}
</style>
