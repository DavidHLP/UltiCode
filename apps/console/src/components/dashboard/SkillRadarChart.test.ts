import { mount } from "@vue/test-utils";
import { nextTick } from "vue";
import { createI18n } from "vue-i18n";
import { expect, it, vi } from "vitest";
import enPersonal from "@/i18n/locales/en-US/personal";
import enProblem from "@/i18n/locales/en-US/problem";
import zhPersonal from "@/i18n/locales/zh-CN/personal";
import zhProblem from "@/i18n/locales/zh-CN/problem";
import SkillRadarChart from "./SkillRadarChart.vue";

const createTestI18n = () =>
  createI18n({
    legacy: false,
    locale: "en-US",
    messages: {
      "en-US": { personal: enPersonal, problem: enProblem },
      "zh-CN": { personal: zhPersonal, problem: zhProblem },
    },
  });

it("resizes the SVG when its container shrinks and grows, and disconnects on unmount", async () => {
  const observers: {
    callback: ResizeObserverCallback;
    disconnect: ReturnType<typeof vi.fn>;
  }[] = [];
  class Observer {
    disconnect = vi.fn();
    observe = vi.fn();
    unobserve = vi.fn();
    constructor(public callback: ResizeObserverCallback) {
      observers.push(this);
    }
  }
  vi.stubGlobal("ResizeObserver", Observer);
  const width = vi
    .spyOn(Element.prototype, "clientWidth", "get")
    .mockReturnValue(600);
  const i18n = createTestI18n();
  const wrapper = mount(SkillRadarChart, {
    props: {
      skills: [
        { tagName: "分治", tagSlug: "divide-and-conquer", count: 1 },
        { tagName: "堆", tagSlug: "heap", count: 1 },
        { tagName: "链表", tagSlug: "linked-list", count: 1 },
        {
          tagName: "算法",
          tagSlug: "problem-category-algorithms",
          count: 1,
        },
      ],
    },
    global: { plugins: [i18n] },
  });
  try {
    await nextTick();
    expect(wrapper.get("svg").attributes("width")).toBe("300");
    expect(observers.length).toBeGreaterThan(0);
    const observer = observers.at(-1)!;
    width.mockReturnValue(223);
    observer.callback([], observer as unknown as ResizeObserver);
    await nextTick();
    expect(wrapper.get("svg").attributes("width")).toBe("159");
    expect(wrapper.get("svg").attributes("height")).toBe("159");
    expect(wrapper.findAll("text")).toHaveLength(4);
    expect(wrapper.findAll("text").map((label) => label.text())).toEqual([
      "Divide and Conquer",
      "Heap",
      "Linked List",
      "Algorithms",
    ]);
    i18n.global.locale.value = "zh-CN";
    await nextTick();
    expect(wrapper.findAll("text").map((label) => label.text())).toEqual([
      "分治",
      "堆",
      "链表",
      "算法",
    ]);
    width.mockReturnValue(600);
    observer.callback([], observer as unknown as ResizeObserver);
    await nextTick();
    expect(wrapper.get("svg").attributes("width")).toBe("300");
  } finally {
    wrapper.unmount();
    width.mockRestore();
    vi.unstubAllGlobals();
  }
  expect(observers.at(-1)?.disconnect).toHaveBeenCalled();
});
