import { defineComponent as d, inject as m, computed as v, ref as y, onMounted as f, nextTick as b, onUnmounted as x, watch as l, createElementBlock as _, openBlock as F } from "vue";
import { Scatter as i } from "@unovis/ts";
import { useForwardProps as S, arePropsEqual as g } from "../../utils/props.js";
import { componentAccessorKey as h } from "../../utils/context.js";
const k = { "data-vis-component": "" }, w = i.selectors, z = /* @__PURE__ */ d({
  __name: "index",
  props: {
    color: {},
    size: {},
    sizeScale: { type: [Object, Function] },
    sizeRange: {},
    shape: { type: [Function, String] },
    label: {},
    labelColor: {},
    labelHideOverlapping: { type: Boolean },
    cursor: {},
    labelTextBrightnessRatio: {},
    labelPosition: {},
    strokeColor: {},
    strokeWidth: {},
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
    const o = m(h), c = u, a = v(() => o.data.value ?? c.data), n = S(c), t = y();
    return f(() => {
      b(() => {
        var e;
        t.value = new i(n.value), (e = t.value) == null || e.setData(a.value), o.update(t.value);
      });
    }), x(() => {
      var e;
      (e = t.value) == null || e.destroy(), o.destroy();
    }), l(n, (e, s) => {
      var r;
      g(e, s) || (r = t.value) == null || r.setConfig(n.value);
    }), l(a, () => {
      var e;
      (e = t.value) == null || e.setData(a.value);
    }), p({
      component: t
    }), (e, s) => (F(), _("div", k));
  }
});
export {
  w as VisScatterSelectors,
  z as default
};
//# sourceMappingURL=index.js.map
