import { defineComponent as i, inject as p, ref as u, onMounted as m, nextTick as d, onUnmounted as f, watch as b, createElementBlock as x, openBlock as v } from "vue";
import { Plotband as l } from "@unovis/ts";
import { useForwardProps as y, arePropsEqual as _ } from "../../utils/props.js";
import { componentAccessorKey as F } from "../../utils/context.js";
const O = { "data-vis-component": "" }, k = l.selectors, w = /* @__PURE__ */ i({
  __name: "index",
  props: {
    color: {},
    axis: {},
    from: {},
    to: {},
    duration: {},
    labelText: {},
    labelPosition: {},
    labelOffsetX: {},
    labelOffsetY: {},
    labelOrientation: {},
    labelColor: {},
    labelSize: {},
    x: {},
    y: {},
    id: { type: Function },
    xScale: { type: [Object, Function] },
    yScale: { type: [Object, Function] },
    excludeFromDomainCalculation: { type: Boolean },
    events: {},
    attributes: {},
    data: {}
  },
  setup(r, { expose: s }) {
    const n = p(F), t = y(r), e = u();
    return m(() => {
      d(() => {
        e.value = new l(t.value), n.update(e.value);
      });
    }), f(() => {
      var o;
      (o = e.value) == null || o.destroy(), n.destroy();
    }), b(t, (o, a) => {
      var c;
      _(o, a) || (c = e.value) == null || c.setConfig(t.value);
    }), s({
      component: e
    }), (o, a) => (v(), x("div", O));
  }
});
export {
  k as VisPlotbandSelectors,
  w as default
};
//# sourceMappingURL=index.js.map
