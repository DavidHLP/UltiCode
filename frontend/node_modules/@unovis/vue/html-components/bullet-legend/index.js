import { defineComponent as c, computed as p, ref as d, onMounted as m, nextTick as f, onUnmounted as v, watch as b, createElementBlock as _, openBlock as g } from "vue";
import { BulletLegend as u } from "@unovis/ts";
import { useForwardProps as x, arePropsEqual as k } from "../../utils/props.js";
const y = u.selectors, P = /* @__PURE__ */ c({
  __name: "index",
  props: {
    items: {},
    labelClassName: {},
    onLegendItemClick: { type: Function },
    labelFontSize: {},
    labelMaxWidth: {},
    bulletSize: {},
    bulletSpacing: {},
    bulletShape: {},
    orientation: {},
    renderIntoProvidedDomNode: { type: Boolean },
    data: {}
  },
  setup(i, { expose: s }) {
    const l = i;
    p(() => l.data);
    const t = x(l), o = d(), n = d();
    return m(() => {
      f(() => {
        n.value && (o.value = new u(n.value, { ...t.value, renderIntoProvidedDomNode: !0 }));
      });
    }), v(() => {
      var e;
      (e = o.value) == null || e.destroy();
    }), b(t, (e, a) => {
      var r;
      k(e, a) || (r = o.value) == null || r.update(t.value);
    }), s({
      component: o
    }), (e, a) => (g(), _("div", {
      "data-vis-bullet-legend": "",
      ref_key: "elRef",
      ref: n
    }, null, 512));
  }
});
export {
  y as VisBulletLegendSelectors,
  P as default
};
//# sourceMappingURL=index.js.map
