import { globalIgnores } from "eslint/config";
import {
  defineConfigWithVueTs,
  vueTsConfigs,
} from "@vue/eslint-config-typescript";
import pluginVue from "eslint-plugin-vue";
import skipFormatting from "@vue/eslint-config-prettier/skip-formatting";

export default defineConfigWithVueTs(
  { files: ["**/*.{ts,vue}"] },
  globalIgnores(["dist/**"]),
  pluginVue.configs["flat/essential"],
  vueTsConfigs.recommended,
  skipFormatting,
);
