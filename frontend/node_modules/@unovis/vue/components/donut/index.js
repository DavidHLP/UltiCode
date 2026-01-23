import { defineComponent as i, inject as m, computed as f, ref as v, onMounted as g, nextTick as y, onUnmounted as b, watch as l, createElementBlock as _, openBlock as h } from "vue";
import { Donut as u } from "@unovis/ts";
import { useForwardProps as B, arePropsEqual as k } from "../../utils/props.js";
import { componentAccessorKey as w } from "../../utils/context.js";
const x = { "data-vis-component": "" }, F = u.selectors, E = /* @__PURE__ */ i({
  __name: "index",
  props: {
    id: { type: Function },
    value: {},
    angleRange: {},
    padAngle: {},
    sortFunction: { type: Function },
    cornerRadius: {},
    color: {},
    radius: {},
    arcWidth: {},
    centralLabel: {},
    centralSubLabel: {},
    centralSubLabelWrap: { type: Boolean },
    showEmptySegments: { type: Boolean },
    emptySegmentAngle: {},
    showBackground: { type: Boolean },
    backgroundAngleRange: {},
    centralLabelOffsetX: {},
    centralLabelOffsetY: {},
    duration: {},
    events: {},
    attributes: {},
    data: {}
  },
  setup(p, { expose: d }) {
    const o = m(w), r = p, a = f(() => o.data.value ?? r.data), n = B(r), t = v();
    return g(() => {
      y(() => {
        var e;
        t.value = new u(n.value), (e = t.value) == null || e.setData(a.value), o.update(t.value);
      });
    }), b(() => {
      var e;
      (e = t.value) == null || e.destroy(), o.destroy();
    }), l(n, (e, c) => {
      var s;
      k(e, c) || (s = t.value) == null || s.setConfig(n.value);
    }), l(a, () => {
      var e;
      (e = t.value) == null || e.setData(a.value);
    }), d({
      component: t
    }), (e, c) => (h(), _("div", x));
  }
});
export {
  F as VisDonutSelectors,
  E as default
};
//# sourceMappingURL=index.js.map
