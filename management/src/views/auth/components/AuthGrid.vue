<script setup lang="ts">
/**
 * AuthGrid - 精密仪器风格几何网格背景
 *
 * 使用 CSS linear-gradient 创建网格线，营造精密仪器的精确感
 */
defineOptions({
  name: 'AuthGrid',
})
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
  background: var(--silver-50);
}

@media (min-width: 1024px) {
  .auth-grid {
    display: flex;
    align-items: center;
    justify-content: center;
  }
}

/* Grid lines layer */
.auth-grid__lines {
  position: absolute;
  inset: 0;
  background-image:
    /* Vertical lines */
    linear-gradient(to right, var(--silver-200) 1px, transparent 1px),
    /* Horizontal lines */ linear-gradient(to bottom, var(--silver-200) 1px, transparent 1px);
  background-size: 40px 40px;
  opacity: 0.5;
}

.dark .auth-grid__lines {
  background-image:
    linear-gradient(to right, var(--silver-300) 1px, transparent 1px),
    linear-gradient(to bottom, var(--silver-300) 1px, transparent 1px);
  opacity: 0.3;
}

/* Subtle glow effect */
.auth-grid__glow {
  position: absolute;
  inset: 0;
  background: radial-gradient(ellipse at center, var(--accent-glow) 0%, transparent 70%);
  opacity: 0.3;
}

.dark .auth-grid__glow {
  opacity: 0.5;
}
</style>
