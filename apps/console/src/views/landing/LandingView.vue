<script setup lang="ts">
import { ref, onMounted, onUnmounted, watchEffect, nextTick } from "vue";
import { useI18n } from "vue-i18n";
import LandingHeader from "./components/LandingHeader.vue";
import HeroSection from "./components/HeroSection.vue";
import ProductProofSection from "./components/ProductProofSection.vue";
import UseCasesSection from "./components/UseCasesSection.vue";
import HumanControlSection from "./components/HumanControlSection.vue";
import FinalStorySection from "./components/FinalStorySection.vue";
import LandingFooter from "./components/LandingFooter.vue";
import algorithmicGardenUrl from "@/assets/landing/algorithmic-garden.webp";

const { t } = useI18n();
const isLoaded = ref(false);

let originalTitle = "";
let originalDescription: string | null = null;
let originalCanonical: string | null = null;
let originalOgTitle: string | null = null;
let originalOgDescription: string | null = null;
let originalOgImage: string | null = null;

let metaDescEl: HTMLMetaElement | null = null;
let metaDescCreated = false;

let canonicalEl: HTMLLinkElement | null = null;
let canonicalCreated = false;

let ogTitleEl: HTMLMetaElement | null = null;
let ogTitleCreated = false;

let ogDescEl: HTMLMetaElement | null = null;
let ogDescCreated = false;

let ogImageEl: HTMLMetaElement | null = null;
let ogImageCreated = false;

let initialized = false;
let scrollObserver: IntersectionObserver | null = null;

function initSeoTags() {
  if (typeof document === "undefined" || initialized) return;
  initialized = true;

  originalTitle = document.title;

  metaDescEl = document.querySelector('meta[name="description"]');
  if (!metaDescEl) {
    metaDescEl = document.createElement("meta");
    metaDescEl.setAttribute("name", "description");
    document.head.appendChild(metaDescEl);
    metaDescCreated = true;
  } else {
    originalDescription = metaDescEl.getAttribute("content");
  }

  canonicalEl = document.querySelector('link[rel="canonical"]');
  if (!canonicalEl) {
    canonicalEl = document.createElement("link");
    canonicalEl.setAttribute("rel", "canonical");
    document.head.appendChild(canonicalEl);
    canonicalCreated = true;
  } else {
    originalCanonical = canonicalEl.getAttribute("href");
  }

  ogTitleEl = document.querySelector('meta[property="og:title"]');
  if (!ogTitleEl) {
    ogTitleEl = document.createElement("meta");
    ogTitleEl.setAttribute("property", "og:title");
    document.head.appendChild(ogTitleEl);
    ogTitleCreated = true;
  } else {
    originalOgTitle = ogTitleEl.getAttribute("content");
  }

  ogDescEl = document.querySelector('meta[property="og:description"]');
  if (!ogDescEl) {
    ogDescEl = document.createElement("meta");
    ogDescEl.setAttribute("property", "og:description");
    document.head.appendChild(ogDescEl);
    ogDescCreated = true;
  } else {
    originalOgDescription = ogDescEl.getAttribute("content");
  }

  ogImageEl = document.querySelector('meta[property="og:image"]');
  if (!ogImageEl) {
    ogImageEl = document.createElement("meta");
    ogImageEl.setAttribute("property", "og:image");
    document.head.appendChild(ogImageEl);
    ogImageCreated = true;
  } else {
    originalOgImage = ogImageEl.getAttribute("content");
  }
}

watchEffect(() => {
  if (typeof document === "undefined") return;
  initSeoTags();

  document.title = t("landing.seoTitle");

  if (metaDescEl) {
    metaDescEl.setAttribute("content", t("landing.seoDescription"));
  }
  if (canonicalEl && typeof window !== "undefined") {
    canonicalEl.setAttribute(
      "href",
      `${window.location.origin}${window.location.pathname}`,
    );
  }
  if (ogTitleEl) {
    ogTitleEl.setAttribute("content", t("landing.seoTitle"));
  }
  if (ogDescEl) {
    ogDescEl.setAttribute("content", t("landing.seoDescription"));
  }
  if (ogImageEl && typeof window !== "undefined") {
    ogImageEl.setAttribute(
      "content",
      new URL(algorithmicGardenUrl, window.location.origin).href,
    );
  }
});

onMounted(async () => {
  document.documentElement.dataset.landingRoute = "";
  isLoaded.value = true;
  await nextTick();

  if (typeof window !== "undefined") {
    const prefersReducedMotion =
      typeof window.matchMedia === "function" &&
      window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    if (prefersReducedMotion) {
      document.querySelectorAll(".reveal-on-scroll").forEach((el) => {
        el.classList.add("is-visible");
      });
      return;
    }

    if ("IntersectionObserver" in window) {
      scrollObserver = new IntersectionObserver(
        (entries) => {
          entries.forEach((entry) => {
            if (entry.isIntersecting) {
              entry.target.classList.add("is-visible");
              scrollObserver?.unobserve(entry.target);
            }
          });
        },
        { threshold: 0.08, rootMargin: "0px 0px -20px 0px" },
      );

      document.querySelectorAll(".reveal-on-scroll").forEach((el) => {
        scrollObserver?.observe(el);
      });
    } else {
      document.querySelectorAll(".reveal-on-scroll").forEach((el) => {
        el.classList.add("is-visible");
      });
    }
  }
});

onUnmounted(() => {
  delete document.documentElement.dataset.landingRoute;
  if (scrollObserver) {
    scrollObserver.disconnect();
    scrollObserver = null;
  }
  if (typeof document !== "undefined") {
    document.title = originalTitle;

    if (metaDescCreated) {
      metaDescEl?.remove();
    } else if (metaDescEl) {
      if (originalDescription !== null) {
        metaDescEl.setAttribute("content", originalDescription);
      } else {
        metaDescEl.removeAttribute("content");
      }
    }

    if (canonicalCreated) {
      canonicalEl?.remove();
    } else if (canonicalEl) {
      if (originalCanonical !== null) {
        canonicalEl.setAttribute("href", originalCanonical);
      } else {
        canonicalEl.removeAttribute("href");
      }
    }

    if (ogTitleCreated) {
      ogTitleEl?.remove();
    } else if (ogTitleEl) {
      if (originalOgTitle !== null) {
        ogTitleEl.setAttribute("content", originalOgTitle);
      } else {
        ogTitleEl.removeAttribute("content");
      }
    }

    if (ogDescCreated) {
      ogDescEl?.remove();
    } else if (ogDescEl) {
      if (originalOgDescription !== null) {
        ogDescEl.setAttribute("content", originalOgDescription);
      } else {
        ogDescEl.removeAttribute("content");
      }
    }

    if (ogImageCreated) {
      ogImageEl?.remove();
    } else if (ogImageEl) {
      if (originalOgImage !== null) {
        ogImageEl.setAttribute("content", originalOgImage);
      } else {
        ogImageEl.removeAttribute("content");
      }
    }
  }
});
</script>

<template>
  <div class="ulticode-landing-root" :class="{ 'is-ready': isLoaded }">
    <!-- 侧边算法工程与架构图纸装饰 (OJ Architectural Blueprint Grid) -->
    <div class="blueprint-frame blueprint-left" aria-hidden="true">
      <svg viewBox="0 0 160 600" fill="none" class="blueprint-svg">
        <path
          d="M20,0 L20,600 M60,0 L60,600 M100,0 L100,600"
          stroke="#545C45"
          stroke-width="0.75"
          stroke-dasharray="3 6"
          opacity="0.2"
        />
        <!-- 树结构与节点 -->
        <circle cx="60" cy="80" r="4" fill="#545C45" opacity="0.3" />
        <path
          d="M60,84 L40,120 M60,84 L80,120"
          stroke="#545C45"
          stroke-width="1"
          opacity="0.25"
        />
        <circle cx="40" cy="120" r="3" fill="#838F81" opacity="0.3" />
        <circle cx="80" cy="120" r="3" fill="#838F81" opacity="0.3" />
        <!-- 坐标与状态网格 -->
        <rect
          x="20"
          y="240"
          width="80"
          height="60"
          stroke="#545C45"
          stroke-width="0.75"
          stroke-dasharray="2 2"
          opacity="0.2"
        />
        <text
          x="30"
          y="260"
          font-family="monospace"
          font-size="8"
          fill="#545C45"
          opacity="0.35"
        >
          DAG #01
        </text>
        <text
          x="30"
          y="280"
          font-family="monospace"
          font-size="7"
          fill="#838F81"
          opacity="0.3"
        >
          SECTOR: 0x4F
        </text>
        <!-- 下部标尺 -->
        <path
          d="M20,440 L100,440 M20,435 L20,445 M60,437 L60,443 M100,435 L100,445"
          stroke="#545C45"
          stroke-width="1"
          opacity="0.25"
        />
      </svg>
    </div>

    <div class="blueprint-frame blueprint-right" aria-hidden="true">
      <svg viewBox="0 0 160 600" fill="none" class="blueprint-svg">
        <path
          d="M60,0 L60,600 M100,0 L100,600 M140,0 L140,600"
          stroke="#545C45"
          stroke-width="0.75"
          stroke-dasharray="3 6"
          opacity="0.2"
        />
        <!-- 执行时序刻度 -->
        <rect
          x="60"
          y="100"
          width="70"
          height="45"
          stroke="#545C45"
          stroke-width="0.75"
          stroke-dasharray="2 2"
          opacity="0.2"
        />
        <text
          x="70"
          y="120"
          font-family="monospace"
          font-size="8"
          fill="#545C45"
          opacity="0.35"
        >
          TIMELINE
        </text>
        <text
          x="70"
          y="135"
          font-family="monospace"
          font-size="7"
          fill="#838F81"
          opacity="0.3"
        >
          TRACE: AUTO
        </text>
        <!-- 节点连线 -->
        <circle cx="100" cy="280" r="4" fill="#545C45" opacity="0.3" />
        <path
          d="M100,284 L80,320 M100,284 L120,320"
          stroke="#545C45"
          stroke-width="1"
          opacity="0.25"
        />
        <circle cx="80" cy="320" r="3" fill="#838F81" opacity="0.3" />
        <circle cx="120" cy="320" r="3" fill="#838F81" opacity="0.3" />
      </svg>
    </div>

    <!-- 页面核心架构 -->
    <LandingHeader />
    <main id="main-content">
      <HeroSection />
      <ProductProofSection />
      <UseCasesSection />
      <HumanControlSection />
      <FinalStorySection />
    </main>
    <LandingFooter />
  </div>
</template>

<style scoped>
.ulticode-landing-root {
  --bg-parchment: #e3e1d1;
  --bg-card: #f7f6f0;
  --bg-sky: #92b3cf;
  --text-primary: #19220e;
  --text-muted: #545c45;
  --text-dim: #838f81;
  --brand-olive: #545c45;
  --brand-olive-hover: #3e4433;
  --brand-sage: #a2afa9;
  --border-delicate: rgba(84, 92, 69, 0.18);
  --font-serif:
    "Instrument Serif", "Newsreader", "Cormorant Garamond", Georgia, serif;
  --font-sans:
    "Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  --font-mono: "JetBrains Mono", "Fira Code", monospace;
  --shadow-subtle: 0 4px 20px -4px rgba(25, 34, 14, 0.06);
  --shadow-elevated: 0 12px 36px -8px rgba(25, 34, 14, 0.12);
  --radius-small: 4px;
  --radius-control: 8px;
  --radius-card: 20px;
  --radius-panel: 20px;

  position: relative;
  background-color: var(--bg-parchment);
  color: var(--text-primary);
  font-family: var(--font-sans);
  min-height: 100vh;
  overflow-x: clip;
  line-height: 1.6;
}

.blueprint-frame {
  position: absolute;
  top: 100px;
  width: 140px;
  height: 600px;
  pointer-events: none;
  z-index: 10;
}

.blueprint-left {
  left: 0;
}
.blueprint-right {
  right: 0;
}
.blueprint-svg {
  width: 100%;
  height: 100%;
}

:deep(.reveal-on-scroll) {
  opacity: 0;
  transform: translateY(12px);
  filter: blur(6px);
  transition:
    opacity 0.55s cubic-bezier(0.22, 1, 0.36, 1),
    transform 0.55s cubic-bezier(0.22, 1, 0.36, 1),
    filter 0.55s cubic-bezier(0.22, 1, 0.36, 1);
  transition-delay: var(--reveal-delay, 0ms);
  will-change: opacity, transform, filter;
}

:deep(.reveal-on-scroll.is-visible) {
  opacity: 1;
  transform: translateY(0);
  filter: blur(0);
  will-change: auto;
}

@media (max-width: 1100px) {
  .blueprint-frame {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  :deep(.reveal-on-scroll) {
    opacity: 1 !important;
    transform: none !important;
    filter: none !important;
    transition: none !important;
  }
}
</style>
