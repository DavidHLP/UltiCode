import { defineComponent as m, inject as d, computed as v, ref as y, onMounted as f, nextTick as x, onUnmounted as _, watch as i, createElementBlock as h, openBlock as F } from "vue";
import { Area as u } from "@unovis/ts";
import { useForwardProps as b, arePropsEqual as g } from "../../utils/props.js";
import { componentAccessorKey as B } from "../../utils/context.js";
const j = { "data-vis-component": "" }, D = u.selectors, P = /* @__PURE__ */ m({
  __name: "index",
  props: {
    color: {},
    curveType: {},
    baseline: {},
    opacity: {},
    cursor: {},
    minHeight1Px: { type: Boolean },
    minHeight: {},
    x: {},
    y: {},
    id: { type: Function },
    xScale: { type: [Object, Function] },
    yScale: { type: [Object, Function] },
    excludeFromDomainCalculation: { type: Boolean },
    duration: {},
    events: {},
    attributes: {},
    data: {}
  },
  setup(l, { expose: p }) {
    const o = d(B), c = l, a = v(() => o.data.value ?? c.data), n = b(c), t = y();
    return f(() => {
      x(() => {
        var e;
        t.value = new u(n.value), (e = t.value) == null || e.setData(a.value), o.update(t.value);
      });
    }), _(() => {
      var e;
      (e = t.value) == null || e.destroy(), o.destroy();
    }), i(n, (e, r) => {
      var s;
      g(e, r) || (s = t.value) == null || s.setConfig(n.value);
    }), i(a, () => {
      var e;
      (e = t.value) == null || e.setData(a.value);
    }), p({
      component: t
    }), (e, r) => (F(), h("div", j));
  }
});
export {
  D as VisAreaSelectors,
  P as default
};
//# sourceMappingURL=index.js.map
