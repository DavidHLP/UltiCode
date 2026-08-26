<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from "vue";
import { useI18n } from "vue-i18n";
import { RouterLink } from "vue-router";

const { t, locale } = useI18n();
const isScrolled = ref(false);
const isMobileMenuOpen = ref(false);
const menuBtnRef = ref<HTMLButtonElement | null>(null);
const firstDrawerItemRef = ref<HTMLAnchorElement | null>(null);

const handleScroll = () => {
  isScrolled.value = window.scrollY > 20;
};

const toggleLocale = () => {
  locale.value = locale.value === "zh-CN" ? "en-US" : "zh-CN";
};

const toggleMobileMenu = () => {
  isMobileMenuOpen.value = !isMobileMenuOpen.value;
  if (isMobileMenuOpen.value) {
    nextTick(() => {
      firstDrawerItemRef.value?.focus();
    });
  } else {
    menuBtnRef.value?.focus();
  }
};

const closeMobileMenu = () => {
  if (isMobileMenuOpen.value) {
    isMobileMenuOpen.value = false;
    menuBtnRef.value?.focus();
  }
};

const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === "Escape" && isMobileMenuOpen.value) {
    closeMobileMenu();
  }
};

onMounted(() => {
  handleScroll();
  window.addEventListener("scroll", handleScroll, { passive: true });
  window.addEventListener("keydown", handleKeydown);
});

onUnmounted(() => {
  window.removeEventListener("scroll", handleScroll);
  window.removeEventListener("keydown", handleKeydown);
});
</script>

<template>
  <header
    class="editorial-header"
    :class="{ 'is-elevated': isScrolled || isMobileMenuOpen }"
  >
    <div class="header-container">
      <RouterLink to="/" class="brand-logotype" @click="closeMobileMenu">
        <span class="brand-emblem">⌘</span>
        <span class="brand-name">UltiCode</span>
        <span class="brand-tag">ARCHIVE</span>
      </RouterLink>

      <!-- 桌面端导航 -->
      <nav class="nav-links" aria-label="Main Navigation">
        <a href="#proof" class="nav-item">{{ t("landing.nav.proof") }}</a>
        <a href="#workflow" class="nav-item">{{ t("landing.nav.workflow") }}</a>
        <a href="#governance" class="nav-item">{{
          t("landing.nav.governance")
        }}</a>
      </nav>

      <!-- 桌面端操作区 -->
      <div class="desktop-actions">
        <button type="button" class="locale-toggle-btn" @click="toggleLocale">
          {{ locale === "zh-CN" ? "EN" : "中" }}
        </button>
        <RouterLink to="/login" class="action-link">{{
          t("landing.nav.login")
        }}</RouterLink>
        <RouterLink to="/problemset" class="action-btn-primary">{{
          t("landing.nav.enter")
        }}</RouterLink>
      </div>

      <!-- 移动端操作栏与汉堡按钮 -->
      <div class="mobile-actions">
        <RouterLink
          to="/problemset"
          class="action-btn-primary action-btn-compact"
          >{{ t("landing.nav.enter") }}</RouterLink
        >
        <button
          ref="menuBtnRef"
          type="button"
          class="mobile-menu-btn"
          aria-controls="mobile-nav-drawer"
          :aria-expanded="isMobileMenuOpen"
          :aria-label="isMobileMenuOpen ? 'Close Menu' : 'Open Menu'"
          @click="toggleMobileMenu"
        >
          <span class="menu-icon">{{ isMobileMenuOpen ? "✕" : "☰" }}</span>
        </button>
      </div>
    </div>

    <!-- 移动端下拉/抽屉菜单 -->
    <div
      id="mobile-nav-drawer"
      v-show="isMobileMenuOpen"
      class="mobile-drawer"
      @click="closeMobileMenu"
    >
      <nav class="mobile-nav-list" aria-label="Mobile Navigation">
        <a ref="firstDrawerItemRef" href="#proof" class="mobile-nav-item">{{
          t("landing.nav.proof")
        }}</a>
        <a href="#workflow" class="mobile-nav-item">{{
          t("landing.nav.workflow")
        }}</a>
        <a href="#governance" class="mobile-nav-item">{{
          t("landing.nav.governance")
        }}</a>
      </nav>
      <div class="mobile-drawer-footer">
        <RouterLink to="/login" class="mobile-login-link">{{
          t("landing.nav.login")
        }}</RouterLink>
        <button
          type="button"
          class="locale-toggle-btn mobile-locale-btn"
          @click.stop="toggleLocale"
        >
          {{ locale === "zh-CN" ? "English (US)" : "简体中文 (CN)" }}
        </button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.editorial-header {
  position: sticky;
  top: 0;
  z-index: 50;
  display: flow-root;
  width: 100%;
  min-height: 72px;
  background-color: transparent;
}

.header-container {
  width: calc(100% - 4rem);
  max-width: 1200px;
  min-height: 70px;
  margin: 0 auto;
  padding: 0.75rem 1.25rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-sizing: border-box;
  background-color: transparent;
  border-radius: 0;
  transition:
    width 0.48s cubic-bezier(0.22, 1, 0.36, 1),
    min-height 0.48s cubic-bezier(0.22, 1, 0.36, 1),
    margin-top 0.48s cubic-bezier(0.22, 1, 0.36, 1),
    padding 0.48s cubic-bezier(0.22, 1, 0.36, 1),
    background-color 0.48s cubic-bezier(0.22, 1, 0.36, 1),
    border-radius 0.48s cubic-bezier(0.22, 1, 0.36, 1),
    backdrop-filter 0.48s cubic-bezier(0.22, 1, 0.36, 1);
}

.editorial-header.is-elevated .header-container {
  width: min(800px, calc(100% - 2rem));
  min-height: 62px;
  margin-top: 10px;
  padding: 0.5rem 0.5rem 0.5rem 1.25rem;
  background-color: rgba(255, 255, 255, 0.2);
  border-radius: 11px;
  -webkit-backdrop-filter: blur(20px);
  backdrop-filter: blur(20px);
}

.brand-logotype {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  text-decoration: none;
  color: var(--text-primary);
  animation: header-brand-in 0.45s cubic-bezier(0.22, 1, 0.36, 1) 0.18s both;
}

.brand-emblem {
  font-size: 1.1rem;
  color: var(--brand-olive);
}

.brand-name {
  font-family: var(--font-serif);
  font-size: 1.5rem;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.brand-tag {
  font-size: 0.65rem;
  font-weight: 600;
  padding: 0.15rem 0.4rem;
  border: 1px solid var(--border-delicate);
  border-radius: var(--radius-small);
  letter-spacing: 0.1em;
  color: var(--text-muted);
}

.nav-links {
  display: flex;
  gap: 2.25rem;
}

.nav-item {
  font-size: 0.9rem;
  color: var(--text-muted);
  text-decoration: none;
  position: relative;
  transition: color 0.2s ease;
}

.nav-item:hover {
  color: var(--text-primary);
}

.desktop-actions {
  display: flex;
  align-items: center;
  gap: 1.25rem;
}

.mobile-actions {
  display: none;
  align-items: center;
  gap: 0.75rem;
}

.mobile-menu-btn {
  background: transparent;
  border: 1px solid var(--border-delicate);
  color: var(--text-primary);
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-control);
  cursor: pointer;
  font-size: 1.1rem;
}

.action-link {
  font-size: 0.9rem;
  color: var(--text-muted);
  text-decoration: none;
  transition: color 0.2s ease;
}

.action-link:hover {
  color: var(--text-primary);
}

.action-btn-primary {
  background-color: var(--brand-olive);
  color: #ffffff;
  padding: 0.55rem 1.25rem;
  font-size: 0.875rem;
  text-decoration: none;
  border-radius: var(--radius-control);
  transition: background-color 0.2s ease;
  white-space: nowrap;
}

.action-btn-primary:hover {
  background-color: var(--brand-olive-hover);
}

.action-btn-compact {
  padding: 0.4rem 0.85rem;
  font-size: 0.8rem;
}

.locale-toggle-btn {
  background: transparent;
  border: 1px solid var(--border-delicate);
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 0.75rem;
  padding: 0.2rem 0.5rem;
  border-radius: var(--radius-small);
  cursor: pointer;
  transition: all 0.2s ease;
}

.locale-toggle-btn:hover {
  color: var(--text-primary);
  border-color: var(--brand-olive);
}

/* 移动端抽屉菜单 */
.mobile-drawer {
  display: flex;
  flex-direction: column;
  padding: 1.25rem 1.5rem 1.75rem;
  background-color: rgba(227, 225, 209, 0.98);
  border-bottom: 1px solid var(--border-delicate);
  border-radius: 0 0 var(--radius-panel) var(--radius-panel);
  gap: 1.25rem;
  animation: drawerSlide 0.2s ease-out;
}

.mobile-nav-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.mobile-nav-item {
  font-size: 1rem;
  color: var(--text-primary);
  text-decoration: none;
  padding: 0.35rem 0;
  border-bottom: 1px solid rgba(84, 92, 69, 0.08);
}

.mobile-drawer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 0.5rem;
}

.mobile-login-link {
  font-size: 0.95rem;
  color: var(--brand-olive);
  font-weight: 600;
  text-decoration: none;
}

@keyframes drawerSlide {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes header-brand-in {
  from {
    opacity: 0;
    transform: translateY(-4px);
    filter: blur(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
    filter: blur(0);
  }
}

@media (max-width: 768px) {
  .nav-links {
    display: none;
  }
  .desktop-actions {
    display: none;
  }
  .mobile-actions {
    display: flex;
  }
  .header-container {
    width: calc(100% - 1.5rem);
    padding: 0.85rem 1.25rem;
  }
  .editorial-header.is-elevated .header-container {
    width: calc(100% - 1rem);
    min-height: 54px;
    margin-top: 0.5rem;
    padding: 0.45rem 0.45rem 0.45rem 1rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .header-container,
  .brand-logotype,
  .mobile-drawer {
    opacity: 1 !important;
    transform: none !important;
    filter: none !important;
    animation: none !important;
  }
  .header-container {
    transition: none !important;
  }
}
</style>
