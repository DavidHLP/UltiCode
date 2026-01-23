import { defineComponent as d, inject as m, computed as v, ref as y, onMounted as h, nextTick as f, onUnmounted as B, watch as u, createElementBlock as F, openBlock as x } from "vue";
import { Brush as i } from "@unovis/ts";
import { useForwardProps as _, arePropsEqual as g } from "../../utils/props.js";
import { componentAccessorKey as b } from "../../utils/context.js";
const S = { "data-vis-component": "" }, D = i.selectors, E = /* @__PURE__ */ d({
  __name: "index",
  props: {
    onBrush: { type: Function },
    onBrushStart: { type: Function },
    onBrushMove: { type: Function },
    onBrushEnd: { type: Function },
    handleWidth: {},
    selection: {},
    draggable: { type: Boolean },
    handlePosition: {},
    selectionMinLength: {},
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
  setup(l, { expose: p }) {
    const o = m(b), c = l, n = v(() => o.data.value ?? c.data), a = _(c), t = y();
    return h(() => {
      f(() => {
        var e;
        t.value = new i(a.value), (e = t.value) == null || e.setData(n.value), o.update(t.value);
      });
    }), B(() => {
      var e;
      (e = t.value) == null || e.destroy(), o.destroy();
    }), u(a, (e, s) => {
      var r;
      g(e, s) || (r = t.value) == null || r.setConfig(a.value);
    }), u(n, () => {
      var e;
      (e = t.value) == null || e.setData(n.value);
    }), p({
      component: t
    }), (e, s) => (x(), F("div", S));
  }
});
export {
  D as VisBrushSelectors,
  E as default
};
//# sourceMappingURL=index.js.map
