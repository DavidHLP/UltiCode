import { defineComponent as p, inject as b, computed as m, ref as h, onMounted as y, nextTick as g, onUnmounted as v, watch as s, createElementBlock as f, openBlock as k } from "vue";
import { Sankey as d } from "@unovis/ts";
import { useForwardProps as x, arePropsEqual as C } from "../../utils/props.js";
import { componentAccessorKey as S } from "../../utils/context.js";
const F = { "data-vis-component": "" }, H = d.selectors, M = /* @__PURE__ */ p({
  __name: "index",
  props: {
    id: { type: Function },
    heightNormalizationCoeff: {},
    exitTransitionType: {},
    enterTransitionType: {},
    highlightSubtreeOnHover: { type: Boolean },
    highlightDuration: {},
    highlightDelay: {},
    iterations: {},
    nodeSort: { type: [Function, null] },
    linkSort: { type: [Function, null] },
    nodeWidth: {},
    nodeAlign: {},
    nodeHorizontalSpacing: {},
    nodeMinHeight: {},
    nodeMaxHeight: {},
    nodePadding: {},
    showSingleNode: { type: Boolean },
    nodeCursor: {},
    nodeIcon: {},
    nodeColor: {},
    nodeFixedValue: {},
    nodeIconColor: {},
    linkColor: {},
    linkValue: {},
    linkCursor: {},
    label: {},
    subLabel: {},
    labelPosition: {},
    labelVerticalAlign: {},
    labelBackground: { type: Boolean },
    labelFit: {},
    labelMaxWidth: {},
    labelExpandTrimmedOnHover: { type: Boolean },
    labelTrimMode: {},
    labelFontSize: {},
    labelTextSeparator: {},
    labelForceWordBreak: { type: Boolean },
    labelColor: {},
    labelCursor: {},
    labelVisibility: { type: Function },
    subLabelFontSize: {},
    subLabelColor: {},
    subLabelPlacement: {},
    subLabelToLabelInlineWidthRatio: {},
    duration: {},
    events: {},
    attributes: {},
    data: {}
  },
  setup(u, { expose: c }) {
    const n = b(S), l = u, t = m(() => n.data.value ?? l.data), a = x(l), o = h();
    return y(() => {
      g(() => {
        var e;
        o.value = new d(a.value), (e = o.value) == null || e.setData(t.value), n.update(o.value);
      });
    }), v(() => {
      var e;
      (e = o.value) == null || e.destroy(), n.destroy();
    }), s(a, (e, i) => {
      var r;
      C(e, i) || (r = o.value) == null || r.setConfig(a.value);
    }), s(t, () => {
      var e;
      (e = o.value) == null || e.setData(t.value);
    }), c({
      component: o
    }), (e, i) => (k(), f("div", F));
  }
});
export {
  H as VisSankeySelectors,
  M as default
};
//# sourceMappingURL=index.js.map
