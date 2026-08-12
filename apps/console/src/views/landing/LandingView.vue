<script setup lang="ts">
import { onMounted, onUnmounted } from "vue";
import { useI18n } from "vue-i18n";
import bundleCss from "./styles/bundle.css?inline";
import landingOverridesCss from "./styles/solarized-overrides.css?inline";

/**
 * LandingView — UltiCode landing built on the mirrored WebGL experience.
 *
 * The DOM skeleton and WebGL scene (Three.js, scroll orchestration, audio)
 * derive from the mirrored landing; the story/content was rewritten for
 * UltiCode. The compiled bundle.css and Solarized DOM overrides are injected
 * on mount and removed on unmount so their global rules never leak to the
 * rest of the console app. WebGL colors come from experience/theme.js; we
 * only READ the `dark` class and never write `data-theme` (reserved to
 * shared/theme).
 *
 * i18n: DOM chrome (loader / header / footer / aria labels) is translated
 * via the `landing.*` locale keys. The 3D MSDF story text stays English in
 * every locale because the font atlas only contains ASCII glyphs.
 */

const { t } = useI18n();

let destroyExperience: (() => void) | null = null;
let styleEl: HTMLStyleElement | null = null;

onMounted(async () => {
  styleEl = document.createElement("style");
  styleEl.dataset.landingBundle = "";
  styleEl.textContent = `${bundleCss}\n${landingOverridesCss}`;
  document.head.appendChild(styleEl);

  try {
    const mod = await import("./experience/main.js");
    destroyExperience = mod.initLandingExperience() as () => void;
  } catch (err) {
    console.error("[LandingView] experience init failed:", err);
  }
});

onUnmounted(() => {
  destroyExperience?.();
  destroyExperience = null;
  styleEl?.remove();
  styleEl = null;
});
</script>

<template>
  <div class="landing-replica">
    <div class="loader">
      <div class="wrap">
        <i>{{ t("landing.loader.loading") }}</i>
        <a href="javascript:void(0);">{{ t("landing.loader.enter") }}</a>
      </div>
      <span></span>
    </div>
    <div class="canvas"></div>
    <header class="header">
      <div class="wrapper">
        <div class="container-fluid">
          <div class="row">
            <div class="col-6">
              <a class="header__lnk" href="javascript:void(0);">{{ t("landing.header.brand") }}</a>
            </div>
            <div class="col-6 d-flex align-items-center justify-content-end">
              <router-link class="header__lnk--2 top-menu-lnk--3" to="/problemset">{{ t("landing.header.cta") }}</router-link>
            </div>
          </div>
        </div>
      </div>
    </header>
    <div id="scroller">
      <div id="content">
        <main data-barba="container" data-barba-namespace="home">
          <div class="steps">
            <div v-for="n in 24" :key="n" class="step">{{ n }}</div>
          </div>
          <div class="d-none" id="awards">
            <img :src="'/landing/static/images/awards/solve.png'" alt="" class="img-fluid" data-title="1" />
            <img :src="'/landing/static/images/awards/run.png'" alt="" class="img-fluid" data-title="2" />
            <img :src="'/landing/static/images/awards/compete.png'" alt="" class="img-fluid" data-title="3" />
            <img :src="'/landing/static/images/awards/rank.png'" alt="" class="img-fluid" data-title="4" />
            <img :src="'/landing/static/images/awards/share.png'" alt="" class="img-fluid" data-title="5" />
            <img :src="'/landing/static/images/awards/ulticode.png'" alt="" class="img-fluid" data-title="6" />
          </div>
          <footer class="footer">
            <div class="wrapper">
              <div class="container-fluid">
                <div class="row align-items-center justify-content-between">
                  <div class="col-8">
                    <ul class="footer__social">
                      <li><router-link class="footer__social-lnk" to="/problemset">{{ t("landing.footer.links.problems") }}</router-link></li>
                      <li><router-link class="footer__social-lnk" to="/contest">{{ t("landing.footer.links.contests") }}</router-link></li>
                      <li><router-link class="footer__social-lnk" to="/forum">{{ t("landing.footer.links.forum") }}</router-link></li>
                    </ul>
                  </div>
                  <div class="col-4 text-end">
                    <button class="footer__audio-toggle js-audio-toggle" type="button" :aria-label="t('landing.footer.audioOff')" aria-pressed="false">
                      <svg class="footer__audio-icon" width="24" height="24" viewBox="0 0 10 10" aria-hidden="true">
                        <polyline class="js-audio-wave" points="1,5 2.14,4.08 3.29,3.55 4.43,3.82 5.57,4.8 6.71,5.9 7.86,6.45 9,6.08"></polyline>
                      </svg>
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </footer>
        </main>
      </div>
    </div>
  </div>
</template>

<style>
/* 与镜像 <head> 内联样式一致:进入前隐藏 WebGL 画布 */
.landing-replica .canvas {
  opacity: 0;
}
</style>
