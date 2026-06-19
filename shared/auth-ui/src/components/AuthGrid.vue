<script setup lang="ts">
/**
 * AuthGrid - Precision geometric grid background
 */
defineOptions({
  name: "AuthGrid",
});
</script>

<template>
  <div class="auth-grid">
    <div class="auth-grid__lines"></div>
    <div class="auth-grid__glow"></div>
    <slot />
  </div>
</template>

<style scoped>
.auth-grid {
  position: relative;
  display: none;
  overflow: hidden;
  background: var(--background);
}

@media (min-width: 1024px) {
  .auth-grid {
    display: flex;
    align-items: center;
    justify-content: center;
  }
}

.auth-grid__lines {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(to right, var(--silver-200) 1px, transparent 1px),
    linear-gradient(to bottom, var(--silver-200) 1px, transparent 1px);
  background-size: 40px 40px;
  opacity: 0.15;
}

.dark .auth-grid__lines {
  background-image:
    linear-gradient(to right, var(--silver-300) 1px, transparent 1px),
    linear-gradient(to bottom, var(--silver-300) 1px, transparent 1px);
  opacity: 0.08;
}

.auth-grid__glow {
  position: absolute;
  inset: 0;
  background: radial-gradient(
    ellipse at center,
    var(--accent-glow) 0%,
    transparent 70%
  );
  opacity: 0.12;
}

.dark .auth-grid__glow {
  opacity: 0.2;
}
</style>