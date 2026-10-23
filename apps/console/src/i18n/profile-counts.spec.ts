import { createI18n } from "vue-i18n";
import { expect, it } from "vitest";
import enPersonal from "./locales/en-US/personal";
import zhPersonal from "./locales/zh-CN/personal";
import enProfile from "./locales/en-US/profile";
import zhProfile from "./locales/zh-CN/profile";

it.each(["personal.social.submissionsSubtitle", "profile.stats.submissions"])(
  "pluralizes %s through the existing named count contract",
  (key) => {
    const i18n = createI18n({
      legacy: false,
      locale: "en-US",
      messages: {
        "en-US": { personal: enPersonal, profile: enProfile },
        "zh-CN": { personal: zhPersonal, profile: zhProfile },
      },
    });
    for (const count of [0, 1, 2]) {
      expect(i18n.global.t(key, { count })).toBe(
        `${count} submission${count === 1 ? "" : "s"}`,
      );
    }
    i18n.global.locale.value = "zh-CN";
    for (const count of [0, 1, 2]) {
      expect(i18n.global.t(key, { count })).toBe(
        key.startsWith("personal.") ? `共 ${count} 次提交` : `${count} 次提交`,
      );
    }
  },
);
