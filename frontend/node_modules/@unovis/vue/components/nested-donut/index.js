import { defineComponent as i, inject as m, computed as g, ref as v, onMounted as y, nextTick as f, onUnmounted as b, watch as c, createElementBlock as B, openBlock as S } from "vue";
import { NestedDonut as u } from "@unovis/ts";
import { useForwardProps as _, arePropsEqual as h } from "../../utils/props.js";
import { componentAccessorKey as w } from "../../utils/context.js";
const L = { "data-vis-component": "" }, E = u.selectors, P = /* @__PURE__ */ i({
  __name: "index",
  props: {
    angleRange: {},
    direction: {},
    value: {},
    centralLabel: {},
    centralSubLabel: {},
    centralSubLabelWrap: { type: Boolean },
    showBackground: { type: Boolean },
    sort: { type: Function },
    layers: {},
    layerSettings: {},
    layerPadding: {},
    cornerRadius: {},
    emptySegmentAngle: {},
    hideOverflowingSegmentLabels: { type: Boolean },
    segmentColor: {},
    segmentLabel: {},
    segmentLabelColor: {},
    showEmptySegments: { type: Boolean },
    showSegmentLabels: { type: Boolean },
    duration: {},
    events: {},
    attributes: {},
    data: {}
  },
  setup(p, { expose: d }) {
    const o = m(w), s = p, a = g(() => o.data.value ?? s.data), n = _(s), t = v();
    return y(() => {
      f(() => {
        var e;
        t.value = new u(n.value), (e = t.value) == null || e.setData(a.value), o.update(t.value);
      });
    }), b(() => {
      var e;
      (e = t.value) == null || e.destroy(), o.destroy();
    }), c(n, (e, r) => {
      var l;
      h(e, r) || (l = t.value) == null || l.setConfig(n.value);
    }), c(a, () => {
      var e;
      (e = t.value) == null || e.setData(a.value);
    }), d({
      component: t
    }), (e, r) => (S(), B("div", L));
  }
});
export {
  E as VisNestedDonutSelectors,
  P as default
};
//# sourceMappingURL=index.js.map
