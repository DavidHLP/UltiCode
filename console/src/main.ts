import { createApp } from "vue";
import { createPinia } from "pinia";

import App from "./App.vue";
import router from "./router";
import i18n from "./i18n";
import "./style.css";
import "./assets/markdown.css";
import VueDnDKitPlugin from "@vue-dnd-kit/core";

async function bootstrap() {
  const app = createApp(App);

  app.use(createPinia());
  app.use(i18n);
  app.use(router);
  app.use(VueDnDKitPlugin);

  // Set initial document language
  document.documentElement.lang = (
    i18n.global.locale as unknown as { value: string }
  ).value;

  app.mount("#app");
}

bootstrap();
