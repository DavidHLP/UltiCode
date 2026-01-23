import { defineComponent as l, inject as m, computed as v, ref as f, onMounted as y, nextTick as x, onUnmounted as g, watch as u, createElementBlock as _, openBlock as b } from "vue";
import { GroupedBar as i } from "@unovis/ts";
import { useForwardProps as h, arePropsEqual as B } from "../../utils/props.js";
import { componentAccessorKey as F } from "../../utils/context.js";
const C = { "data-vis-component": "" }, w = i.selectors, D = /* @__PURE__ */ l({
  __name: "index",
  props: {
    color: {},
    groupWidth: {},
    groupMaxWidth: {},
    dataStep: {},
    groupPadding: {},
    barPadding: {},
    roundedCorners: { type: [Number, Boolean] },
    barMinHeight: {},
    cursor: {},
    orientation: {},
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
  setup(d, { expose: p }) {
    const t = m(F), r = d, a = v(() => t.data.value ?? r.data), n = h(r), o = f();
    return y(() => {
      x(() => {
        var e;
        o.value = new i(n.value), (e = o.value) == null || e.setData(a.value), t.update(o.value);
      });
    }), g(() => {
      var e;
      (e = o.value) == null || e.destroy(), t.destroy();
    }), u(n, (e, c) => {
      var s;
      B(e, c) || (s = o.value) == null || s.setConfig(n.value);
    }), u(a, () => {
      var e;
      (e = o.value) == null || e.setData(a.value);
    }), p({
      component: o
    }), (e, c) => (b(), _("div", C));
  }
});
export {
  w as VisGroupedBarSelectors,
  D as default
};
//# sourceMappingURL=index.js.map
