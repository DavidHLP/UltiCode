import { defineComponent as d, inject as x, computed as m, ref as y, onMounted as k, nextTick as T, onUnmounted as v, watch as s, createElementBlock as f, openBlock as B } from "vue";
import { Axis as r } from "@unovis/ts";
import { useForwardProps as F, arePropsEqual as b } from "../../utils/props.js";
import { axisAccessorKey as g } from "../../utils/context.js";
const S = { "data-vis-axis": "" }, A = r.selectors, C = /* @__PURE__ */ d({
  __name: "index",
  props: {
    position: {},
    type: {},
    fullSize: { type: Boolean },
    label: {},
    labelFontSize: {},
    labelMargin: {},
    labelTextFitMode: {},
    labelTextTrimType: {},
    labelColor: {},
    gridLine: { type: Boolean },
    tickLine: { type: Boolean },
    domainLine: { type: Boolean },
    minMaxTicksOnly: { type: Boolean },
    minMaxTicksOnlyShowGridLines: { type: Boolean },
    minMaxTicksOnlyWhenWidthIsLess: {},
    tickFormat: { type: Function },
    tickValues: {},
    numTicks: {},
    tickTextFitMode: {},
    tickTextWidth: {},
    tickTextSeparator: {},
    tickTextForceWordBreak: { type: Boolean },
    tickTextTrimType: {},
    tickTextFontSize: {},
    tickTextAlign: {},
    tickTextColor: {},
    tickTextAngle: {},
    tickTextHideOverlapping: { type: Boolean },
    tickPadding: {},
    x: {},
    y: {},
    id: { type: Function },
    color: {},
    xScale: { type: [Object, Function] },
    yScale: { type: [Object, Function] },
    excludeFromDomainCalculation: { type: Boolean },
    duration: {},
    events: {},
    attributes: {},
    data: {}
  },
  setup(p, { expose: u }) {
    const o = x(g), a = p, i = m(() => o.data.value ?? a.data), n = F(a), t = y();
    return k(() => {
      T(() => {
        var e;
        t.value = new r(n.value), (e = t.value) == null || e.setData(i.value), o.update(t.value);
      });
    }), v(() => {
      var e;
      (e = t.value) == null || e.destroy(), o.destroy(a.type);
    }), s(n, (e, c) => {
      var l;
      b(e, c) || (l = t.value) == null || l.setConfig(n.value);
    }), s(i, () => {
      var e;
      (e = t.value) == null || e.setData(i.value);
    }), u({
      component: t
    }), (e, c) => (B(), f("div", S));
  }
});
export {
  A as VisAxisSelectors,
  C as default
};
//# sourceMappingURL=index.js.map
