<script setup lang="ts">
import { onUnmounted, watchEffect } from "vue";
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

onUnmounted(() => {
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
  <div class="ulticode-landing-root min-h-svh bg-background text-foreground">
    <a
      href="#main-content"
      class="sr-only focus:not-sr-only focus:absolute focus:z-50 focus:bg-background focus:p-4"
      >{{ t("landing.skipContent") }}</a
    >
    <LandingHeader />
    <main id="main-content">
      <HeroSection /><ProductProofSection /><UseCasesSection /><HumanControlSection /><FinalStorySection />
    </main>
    <LandingFooter />
  </div>
</template>
