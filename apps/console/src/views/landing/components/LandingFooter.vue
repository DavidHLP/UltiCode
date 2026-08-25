<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { RouterLink } from "vue-router";

const { t } = useI18n();
</script>

<template>
  <footer class="editorial-footer">
    <div class="footer-main-grid">
      <div class="footer-brand-col">
        <div class="brand-logotype">
          <span class="brand-emblem">⌘</span>
          <span class="brand-name">UltiCode</span>
        </div>
        <p class="brand-manifesto">
          {{ t("landing.footer.manifesto") }}
        </p>
      </div>

      <div class="footer-links-col">
        <h4 class="col-title">{{ t("landing.footer.archiveColTitle") }}</h4>
        <ul class="col-list">
          <li>
            <RouterLink to="/problemset">{{
              t("landing.footer.archiveLink1")
            }}</RouterLink>
          </li>
          <li>
            <RouterLink to="/contest">{{
              t("landing.footer.archiveLink2")
            }}</RouterLink>
          </li>
          <li>
            <RouterLink to="/forum">{{
              t("landing.footer.archiveLink3")
            }}</RouterLink>
          </li>
          <li>
            <RouterLink to="/login">{{
              t("landing.footer.archiveLink4")
            }}</RouterLink>
          </li>
        </ul>
      </div>

      <div class="footer-links-col">
        <h4 class="col-title">{{ t("landing.footer.specColTitle") }}</h4>
        <ul class="col-list">
          <li>
            <a href="#proof">{{ t("landing.footer.specLink1") }}</a>
          </li>
          <li>
            <a href="#workflow">{{ t("landing.footer.specLink2") }}</a>
          </li>
          <li>
            <a href="#use-cases">{{ t("landing.footer.specLink3") }}</a>
          </li>
          <li>
            <a href="#governance">{{ t("landing.footer.specLink4") }}</a>
          </li>
        </ul>
      </div>

      <div class="footer-links-col">
        <h4 class="col-title">{{ t("landing.footer.instColTitle") }}</h4>
        <ul class="col-list">
          <li>
            <a href="#governance">{{ t("landing.footer.instLink1") }}</a>
          </li>
          <li>
            <a href="#governance">{{ t("landing.footer.instLink2") }}</a>
          </li>
          <li>
            <a href="#proof">{{ t("landing.footer.instLink3") }}</a>
          </li>
          <li>
            <a href="#main-content">{{ t("landing.footer.instLink4") }}</a>
          </li>
        </ul>
      </div>
    </div>

    <div class="footer-bottom-bar">
      <div class="copyright">
        {{ t("landing.footer.copyright") }}
      </div>
      <div class="system-nominal">
        <span class="nominal-dot" aria-hidden="true" />
        {{ t("landing.footer.systemNominal") }}
      </div>
    </div>
  </footer>
</template>

<style scoped>
.editorial-footer {
  background:
    linear-gradient(rgba(32, 51, 37, 0.9), rgba(32, 51, 37, 0.98)),
    url("../../../assets/landing/algorithmic-horizon.webp") center bottom /
      cover;
  color: #8c9985;
  padding: 4.5rem 1.5rem 3rem;
  border-top: 1px solid rgba(227, 225, 209, 0.1);
  font-size: 0.85rem;
}

.footer-main-grid {
  max-width: 1080px;
  margin: 0 auto 4rem;
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 1fr;
  gap: 3rem;
}

.brand-logotype {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: #e3e1d1;
  margin-bottom: 1rem;
}

.brand-name {
  font-family: var(--font-serif);
  font-size: 1.35rem;
  font-weight: 700;
}

.brand-manifesto {
  line-height: 1.6;
  max-width: 320px;
  color: #e3e1d1;
  opacity: 0.7;
}

.col-title {
  font-family: var(--font-serif);
  font-size: 1rem;
  color: #e3e1d1;
  margin-bottom: 1rem;
}

.col-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.col-list a {
  color: #8c9985;
  text-decoration: none;
  transition: color 0.2s;
}

.col-list a:hover {
  color: #e3e1d1;
}

.footer-bottom-bar {
  max-width: 1080px;
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  padding-top: 2rem;
  border-top: 1px solid rgba(227, 225, 209, 0.08);
  font-size: 0.75rem;
  font-family: var(--font-mono);
}

.system-nominal {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.nominal-dot {
  width: 6px;
  height: 6px;
  background-color: #588e67;
  border-radius: 50%;
}

@media (max-width: 768px) {
  .editorial-footer {
    padding: 2.5rem 1rem 2rem;
  }
  .footer-main-grid {
    grid-template-columns: 1fr;
    gap: 1.5rem;
    margin-bottom: 2rem;
  }
  .footer-bottom-bar {
    flex-direction: column;
    gap: 0.75rem;
    padding-top: 1.25rem;
  }
}
</style>
