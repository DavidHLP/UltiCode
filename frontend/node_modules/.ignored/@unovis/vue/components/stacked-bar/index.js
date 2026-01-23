import { defineComponent as p, inject as m, computed as v, ref as f, onMounted as y, nextTick as x, onUnmounted as b, watch as i, createElementBlock as _, openBlock as h } from "vue";
import { StackedBar as u } from "@unovis/ts";
import { useForwardProps as B, arePropsEqual as S } from "../../utils/props.js";
import { componentAccessorKey as g } from "../../utils/context.js";
const k = { "data-vis-component": "" }, j = u.selectors, w = /* @__PURE__ */ p({
  __name: "index",
  props: {
    color: {},
    barWidth: {},
    barMaxWidth: {},
    dataStep: {},
    barPadding: {},
    roundedCorners: { type: [Number, Boolean] },
    cursor: {},
    barMinHeight1Px: { type: Boolean },
    barMinHeightZeroValue: {},
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
  setup(d, { expose: l }) {
    const o = m(g), r = d, a = v(() => o.data.value ?? r.data), n = B(r), t = f();
    return y(() => {
      x(() => {
        var e;
        t.value = new u(n.value), (e = t.value) == null || e.setData(a.value), o.update(t.value);
      });
    }), b(() => {
      var e;
      (e = t.value) == null || e.destroy(), o.destroy();
    }), i(n, (e, c) => {
      var s;
      S(e, c) || (s = t.value) == null || s.setConfig(n.value);
    }), i(a, () => {
      var e;
      (e = t.value) == null || e.setData(a.value);
    }), l({
      component: t
    }), (e, c) => (h(), _("div", k));
  }
});
export {
  j as VisStackedBarSelectors,
  w as default
};
//# sourceMappingURL=index.js.map
