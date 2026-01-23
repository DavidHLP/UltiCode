import { defineComponent as d, inject as m, computed as v, ref as b, onMounted as f, nextTick as y, onUnmounted as x, watch as l, createElementBlock as g, openBlock as _ } from "vue";
import { XYLabels as u } from "@unovis/ts";
import { useForwardProps as C, arePropsEqual as F } from "../../utils/props.js";
import { componentAccessorKey as B } from "../../utils/context.js";
const k = { "data-vis-component": "" }, j = u.selectors, w = /* @__PURE__ */ d({
  __name: "index",
  props: {
    color: {},
    y: {},
    xPositioning: {},
    yPositioning: {},
    labelFontSize: {},
    label: {},
    backgroundColor: {},
    cursor: {},
    labelTextBrightnessRatio: {},
    clustering: { type: Boolean },
    clusterLabel: {},
    clusterFontSize: {},
    clusterBackgroundColor: {},
    clusterCursor: {},
    clusterLabelColor: {},
    x: {},
    id: { type: Function },
    xScale: { type: [Object, Function] },
    yScale: { type: [Object, Function] },
    excludeFromDomainCalculation: { type: Boolean },
    duration: {},
    events: {},
    attributes: {},
    data: {}
  },
  setup(i, { expose: p }) {
    const t = m(B), c = i, a = v(() => t.data.value ?? c.data), n = C(c), o = b();
    return f(() => {
      y(() => {
        var e;
        o.value = new u(n.value), (e = o.value) == null || e.setData(a.value), t.update(o.value);
      });
    }), x(() => {
      var e;
      (e = o.value) == null || e.destroy(), t.destroy();
    }), l(n, (e, r) => {
      var s;
      F(e, r) || (s = o.value) == null || s.setConfig(n.value);
    }), l(a, () => {
      var e;
      (e = o.value) == null || e.setData(a.value);
    }), p({
      component: o
    }), (e, r) => (_(), g("div", k));
  }
});
export {
  j as VisXYLabelsSelectors,
  w as default
};
//# sourceMappingURL=index.js.map
