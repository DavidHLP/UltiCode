<script setup lang="ts">
import { useI18n } from "vue-i18n";
import engineeringStillLifeUrl from "@/assets/landing/engineering-still-life.webp";

const { t } = useI18n();
</script>

<template>
  <section id="workflow" class="proof-section" aria-labelledby="proof-title">
    <div class="proof-container">
      <header class="proof-header reveal-on-scroll">
        <h2 id="proof-title" class="section-title">
          {{ t("landing.workflow.title") }}
          <em>{{ t("landing.workflow.titleItalic") }}</em>
        </h2>
        <p>{{ t("landing.workflow.subtitle") }}</p>
      </header>

      <div class="proof-body">
        <ol class="proof-steps reveal-on-scroll">
          <li>
            <span>01</span>
            <div>
              <h3>{{ t("landing.workflow.step1Title") }}</h3>
              <p>{{ t("landing.workflow.step1Desc") }}</p>
              <ul>
                <li>{{ t("landing.workflow.step1Bullet1") }}</li>
                <li>{{ t("landing.workflow.step1Bullet2") }}</li>
              </ul>
            </div>
          </li>
          <li>
            <span>02</span>
            <div>
              <h3>{{ t("landing.workflow.step2Title") }}</h3>
              <p>{{ t("landing.workflow.step2Desc") }}</p>
              <ul>
                <li>{{ t("landing.workflow.step2Bullet1") }}</li>
                <li>{{ t("landing.workflow.step2Bullet2") }}</li>
              </ul>
            </div>
          </li>
          <li>
            <span>03</span>
            <div>
              <h3>{{ t("landing.workflow.step3Title") }}</h3>
              <p>{{ t("landing.workflow.step3Desc") }}</p>
              <ul>
                <li>{{ t("landing.workflow.step3Bullet1") }}</li>
                <li>{{ t("landing.workflow.step3Bullet2") }}</li>
              </ul>
            </div>
          </li>
          <li>
            <span>04</span>
            <div>
              <h3>{{ t("landing.workflow.step4Title") }}</h3>
              <p>{{ t("landing.workflow.step4Desc") }}</p>
              <ul>
                <li>{{ t("landing.workflow.step4Bullet1") }}</li>
                <li>{{ t("landing.workflow.step4Bullet2") }}</li>
              </ul>
            </div>
          </li>
        </ol>

        <figure class="proof-visual reveal-on-scroll">
          <img
            :src="engineeringStillLifeUrl"
            alt=""
            loading="lazy"
            decoding="async"
          />
          <figcaption>
            <span>LIVE</span>
            {{ t("landing.proof.disclosure") }}
          </figcaption>
        </figure>
      </div>
    </div>
  </section>
</template>

<style scoped>
.proof-section {
  padding: 7.5rem 1.5rem;
}

.proof-container {
  max-width: 1220px;
  margin: 0 auto;
}

.proof-header {
  max-width: 650px;
  margin: 0 auto 5rem;
  text-align: center;
}

.section-title {
  margin: 0 0 1.2rem;
  font-family: var(--font-serif);
  font-size: clamp(3rem, 6vw, 5.2rem);
  font-weight: 400;
  line-height: 0.98;
  letter-spacing: -0.035em;
}

.section-title em {
  display: block;
  font-weight: 400;
}

.proof-header p {
  color: var(--text-muted);
  font-size: 1rem;
  line-height: 1.55;
}

.proof-body {
  display: grid;
  grid-template-columns: 0.78fr 1.22fr;
  gap: 4rem;
  align-items: center;
}

.proof-steps {
  list-style: none;
  padding: 0;
  margin: 0;
}

.proof-steps > li {
  display: grid;
  grid-template-columns: 2.4rem 1fr;
  gap: 1rem;
  padding: 1.5rem 0;
  border-top: 1px solid var(--border-delicate);
}

.proof-steps > li:last-child {
  border-bottom: 1px solid var(--border-delicate);
}

.proof-steps > li > span {
  color: var(--brand-olive);
  font-family: var(--font-mono);
  font-size: 0.72rem;
}

.proof-steps h3 {
  margin: 0 0 0.6rem;
  font-family: var(--font-serif);
  font-size: 1.4rem;
  font-weight: 500;
}

.proof-steps p {
  margin: 0 0 0.7rem;
  color: var(--text-muted);
  font-size: 0.9rem;
  line-height: 1.55;
}

.proof-steps ul {
  margin: 0;
  padding-left: 1.1rem;
  color: var(--text-dim);
  font-size: 0.77rem;
  line-height: 1.65;
}

.proof-visual {
  position: relative;
  min-height: 520px;
  margin: 0;
  overflow: hidden;
  border-radius: var(--radius-panel);
  background: var(--bg-sky);
}

.proof-visual img {
  width: 100%;
  height: 100%;
  min-height: 520px;
  object-fit: cover;
  filter: saturate(0.82) blur(0.25px);
}

.proof-visual figcaption {
  position: absolute;
  top: 1.25rem;
  right: 1.25rem;
  max-width: 260px;
  padding: 0.65rem 0.8rem;
  background: rgba(247, 246, 240, 0.86);
  border-radius: var(--radius-control);
  color: var(--text-muted);
  font-size: 0.7rem;
  line-height: 1.45;
  backdrop-filter: blur(8px);
}

.proof-visual figcaption span {
  display: block;
  color: var(--brand-olive);
  font-family: var(--font-mono);
  font-size: 0.62rem;
  letter-spacing: 0.08em;
}

@media (max-width: 900px) {
  .proof-body {
    grid-template-columns: 1fr;
  }
  .proof-visual {
    order: -1;
  }
}

@media (max-width: 768px) {
  .proof-section {
    padding: 4.5rem 1rem;
  }
  .proof-header {
    margin-bottom: 2.5rem;
  }
  .section-title {
    font-size: 3.1rem;
  }
  .proof-body {
    gap: 2rem;
  }
  .proof-visual,
  .proof-visual img {
    min-height: 310px;
  }
}
</style>
