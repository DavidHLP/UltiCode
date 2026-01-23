import { defineComponent as d, inject as m, computed as v, ref as y, onMounted as h, nextTick as f, onUnmounted as F, watch as u, createElementBlock as B, openBlock as x } from "vue";
import { FreeBrush as i } from "@unovis/ts";
import { useForwardProps as _, arePropsEqual as S } from "../../utils/props.js";
import { componentAccessorKey as b } from "../../utils/context.js";
const g = { "data-vis-component": "" }, D = i.selectors, E = /* @__PURE__ */ d({
  __name: "index",
  props: {
    mode: {},
    onBrush: { type: Function },
    onBrushStart: { type: Function },
    onBrushMove: { type: Function },
    onBrushEnd: { type: Function },
    handleWidth: {},
    selection: {},
    selectionMinLength: {},
    autoHide: { type: Boolean },
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
    }), F(() => {
      var e;
      (e = t.value) == null || e.destroy(), o.destroy();
    }), u(a, (e, s) => {
      var r;
      S(e, s) || (r = t.value) == null || r.setConfig(a.value);
    }), u(n, () => {
      var e;
      (e = t.value) == null || e.setData(n.value);
    }), p({
      component: t
    }), (e, s) => (x(), B("div", g));
  }
});
export {
  D as VisFreeBrushSelectors,
  E as default
};
//# sourceMappingURL=index.js.map
