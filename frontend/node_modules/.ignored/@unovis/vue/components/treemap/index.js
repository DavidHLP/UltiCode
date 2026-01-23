import { defineComponent as p, inject as m, computed as f, ref as b, onMounted as v, nextTick as F, onUnmounted as y, watch as s, createElementBlock as B, openBlock as L } from "vue";
import { Treemap as c } from "@unovis/ts";
import { useForwardProps as T, arePropsEqual as _ } from "../../utils/props.js";
import { componentAccessorKey as g } from "../../utils/context.js";
const S = { "data-vis-component": "" }, w = c.selectors, C = /* @__PURE__ */ p({
  __name: "index",
  props: {
    id: { type: Function },
    value: {},
    layers: {},
    numberFormat: { type: Function },
    tileLabel: { type: Function },
    tileColor: {},
    tilePadding: {},
    tilePaddingTop: {},
    labelInternalNodes: { type: Boolean },
    labelOffsetX: {},
    labelOffsetY: {},
    labelFit: {},
    labelTrimMode: {},
    tileBorderRadius: {},
    tileBorderRadiusFactor: {},
    enableLightnessVariance: { type: Boolean },
    enableTileLabelFontSizeVariation: { type: Boolean },
    tileLabelSmallFontSize: {},
    tileLabelMediumFontSize: {},
    tileLabelLargeFontSize: {},
    showTileClickAffordance: { type: Boolean },
    lightnessVariationAmount: {},
    minTileSizeForLabel: {},
    duration: {},
    events: {},
    attributes: {},
    data: {}
  },
  setup(u, { expose: d }) {
    const o = m(g), l = u, a = f(() => o.data.value ?? l.data), n = T(l), t = b();
    return v(() => {
      F(() => {
        var e;
        t.value = new c(n.value), (e = t.value) == null || e.setData(a.value), o.update(t.value);
      });
    }), y(() => {
      var e;
      (e = t.value) == null || e.destroy(), o.destroy();
    }), s(n, (e, i) => {
      var r;
      _(e, i) || (r = t.value) == null || r.setConfig(n.value);
    }), s(a, () => {
      var e;
      (e = t.value) == null || e.setData(a.value);
    }), d({
      component: t
    }), (e, i) => (L(), B("div", S));
  }
});
export {
  w as VisTreemapSelectors,
  C as default
};
//# sourceMappingURL=index.js.map
