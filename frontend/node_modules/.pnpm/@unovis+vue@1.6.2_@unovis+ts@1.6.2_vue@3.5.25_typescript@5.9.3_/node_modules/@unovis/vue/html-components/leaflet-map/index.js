import { defineComponent as f, computed as m, ref as r, onMounted as y, nextTick as v, onUnmounted as M, watch as u, createElementBlock as L, openBlock as B } from "vue";
import { LeafletMap as s } from "@unovis/ts";
import { useForwardProps as b, arePropsEqual as g } from "../../utils/props.js";
const F = s.selectors, w = /* @__PURE__ */ f({
  __name: "index",
  props: {
    width: {},
    height: {},
    flyToDuration: {},
    fitViewPadding: {},
    zoomDuration: {},
    initialBounds: {},
    fitBoundsOnUpdate: {},
    fitViewOnInit: { type: Boolean },
    fitViewOnUpdate: { type: Boolean },
    style: {},
    styleDarkTheme: {},
    accessToken: {},
    attribution: {},
    renderer: {},
    onMapInitialized: { type: Function },
    onMapMoveZoom: { type: Function },
    onMapMoveStart: { type: Function },
    onMapMoveEnd: { type: Function },
    onMapZoomStart: { type: Function },
    onMapZoomEnd: { type: Function },
    onMapClick: { type: Function },
    pointLongitude: {},
    pointLatitude: {},
    pointId: {},
    pointShape: {},
    pointColor: {},
    pointRadius: {},
    pointLabel: {},
    pointLabelColor: {},
    pointBottomLabel: {},
    pointCursor: {},
    pointRingWidth: {},
    selectedPointId: {},
    clusterColor: {},
    clusterRadius: {},
    clusterLabel: {},
    clusterLabelColor: {},
    clusterBottomLabel: {},
    clusterRingWidth: {},
    clusterBackground: { type: Boolean },
    clusterExpandOnClick: { type: Boolean },
    clusteringDistance: {},
    colorMap: {},
    topoJSONLayer: {},
    tooltip: {},
    ariaLabel: {},
    duration: {},
    events: {},
    attributes: {},
    data: {}
  },
  setup(c, { expose: d }) {
    const i = c, o = m(() => i.data), n = b(i), t = r(), a = r();
    return y(() => {
      v(() => {
        a.value && (t.value = new s(a.value, n.value, o.value));
      });
    }), M(() => {
      var e;
      (e = t.value) == null || e.destroy();
    }), u(n, (e, l) => {
      var p;
      g(e, l) || (p = t.value) == null || p.setConfig(n.value);
    }), u(o, () => {
      var e;
      (e = t.value) == null || e.setData(o.value);
    }), d({
      component: t
    }), (e, l) => (B(), L("div", {
      "data-vis-leaflet-map": "",
      ref_key: "elRef",
      ref: a
    }, null, 512));
  }
});
export {
  F as VisLeafletMapSelectors,
  w as default
};
//# sourceMappingURL=index.js.map
