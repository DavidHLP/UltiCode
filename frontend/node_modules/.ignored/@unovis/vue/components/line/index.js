import { defineComponent as d, inject as m, computed as v, ref as y, onMounted as f, nextTick as h, onUnmounted as x, watch as i, createElementBlock as _, openBlock as g } from "vue";
import { Line as l } from "@unovis/ts";
import { useForwardProps as B, arePropsEqual as D } from "../../utils/props.js";
import { componentAccessorKey as F } from "../../utils/context.js";
const b = { "data-vis-component": "" }, O = l.selectors, S = /* @__PURE__ */ d({
  __name: "index",
  props: {
    color: {},
    curveType: {},
    lineWidth: {},
    lineDashArray: {},
    fallbackValue: {},
    highlightOnHover: { type: Boolean },
    cursor: {},
    interpolateMissingData: { type: Boolean },
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
  setup(u, { expose: p }) {
    const o = m(F), c = u, a = v(() => o.data.value ?? c.data), n = B(c), t = y();
    return f(() => {
      h(() => {
        var e;
        t.value = new l(n.value), (e = t.value) == null || e.setData(a.value), o.update(t.value);
      });
    }), x(() => {
      var e;
      (e = t.value) == null || e.destroy(), o.destroy();
    }), i(n, (e, r) => {
      var s;
      D(e, r) || (s = t.value) == null || s.setConfig(n.value);
    }), i(a, () => {
      var e;
      (e = t.value) == null || e.setData(a.value);
    }), p({
      component: t
    }), (e, r) => (g(), _("div", b));
  }
});
export {
  O as VisLineSelectors,
  S as default
};
//# sourceMappingURL=index.js.map
