import { defineComponent as r, inject as p, ref as u, onMounted as m, nextTick as d, onUnmounted as f, watch as b, createElementBlock as v, openBlock as x } from "vue";
import { Plotline as c } from "@unovis/ts";
import { useForwardProps as y, arePropsEqual as _ } from "../../utils/props.js";
import { componentAccessorKey as F } from "../../utils/context.js";
const O = { "data-vis-component": "" }, k = c.selectors, w = /* @__PURE__ */ r({
  __name: "index",
  props: {
    color: {},
    lineWidth: {},
    axis: {},
    value: {},
    duration: {},
    lineStyle: {},
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
  setup(s, { expose: i }) {
    const n = p(F), t = y(s), e = u();
    return m(() => {
      d(() => {
        e.value = new c(t.value), n.update(e.value);
      });
    }), f(() => {
      var o;
      (o = e.value) == null || o.destroy(), n.destroy();
    }), b(t, (o, l) => {
      var a;
      _(o, l) || (a = e.value) == null || a.setConfig(t.value);
    }), i({
      component: e
    }), (o, l) => (x(), v("div", O));
  }
});
export {
  k as VisPlotlineSelectors,
  w as default
};
//# sourceMappingURL=index.js.map
