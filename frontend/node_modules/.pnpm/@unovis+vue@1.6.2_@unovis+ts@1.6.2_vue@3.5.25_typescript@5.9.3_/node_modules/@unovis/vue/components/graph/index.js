import { defineComponent as s, inject as y, computed as m, ref as S, onMounted as F, nextTick as g, onUnmounted as k, watch as u, createElementBlock as b, openBlock as h } from "vue";
import { Graph as r } from "@unovis/ts";
import { useForwardProps as f, arePropsEqual as P } from "../../utils/props.js";
import { componentAccessorKey as v } from "../../utils/context.js";
const L = { "data-vis-component": "" }, B = r.selectors, D = /* @__PURE__ */ s({
  __name: "index",
  props: {
    zoomScaleExtent: {},
    disableZoom: { type: Boolean },
    zoomEventFilter: { type: Function },
    disableDrag: { type: Boolean },
    disableBrush: { type: Boolean },
    zoomThrottledUpdateNodeThreshold: {},
    fitViewPadding: {},
    fitViewAlign: {},
    layoutType: {},
    layoutAutofit: { type: Boolean },
    layoutAutofitTolerance: {},
    layoutNonConnectedAside: { type: Boolean },
    layoutNodeGroup: {},
    layoutGroupOrder: {},
    layoutParallelNodesPerColumn: {},
    layoutParallelNodeSubGroup: {},
    layoutParallelSubGroupsPerRow: {},
    layoutParallelNodeSpacing: {},
    layoutParallelSubGroupSpacing: {},
    layoutParallelGroupSpacing: {},
    layoutParallelSortConnectionsByGroup: {},
    forceLayoutSettings: {},
    dagreLayoutSettings: {},
    layoutElkSettings: {},
    layoutElkNodeGroups: {},
    layoutElkGetNodeShape: { type: Function },
    linkWidth: {},
    linkStyle: {},
    linkBandWidth: {},
    linkArrow: {},
    linkStroke: {},
    linkDisabled: {},
    linkFlow: {},
    linkFlowAnimDuration: {},
    linkFlowParticleSize: {},
    linkFlowParticleSpeed: {},
    linkLabel: {},
    linkLabelShiftFromCenter: {},
    linkNeighborSpacing: {},
    linkCurvature: {},
    linkHighlightOnHover: { type: Boolean },
    linkSourcePointOffset: {},
    linkTargetPointOffset: {},
    selectedLinkId: {},
    nodeSize: {},
    nodeStrokeWidth: {},
    nodeShape: {},
    nodeGaugeValue: {},
    nodeGaugeFill: {},
    nodeGaugeAnimDuration: {},
    nodeIcon: {},
    nodeIconSize: {},
    nodeLabel: {},
    nodeLabelTrim: {},
    nodeLabelTrimMode: {},
    nodeLabelTrimLength: {},
    nodeSubLabel: {},
    nodeSubLabelTrim: {},
    nodeSubLabelTrimMode: {},
    nodeSubLabelTrimLength: {},
    nodeSideLabels: {},
    nodeBottomIcon: {},
    nodeDisabled: {},
    nodeFill: {},
    nodeStroke: {},
    nodeSort: { type: Function },
    nodeEnterPosition: {},
    nodeEnterScale: {},
    nodeExitPosition: {},
    nodeExitScale: {},
    nodeEnterCustomRenderFunction: { type: Function },
    nodeUpdateCustomRenderFunction: { type: Function },
    nodePartialUpdateCustomRenderFunction: { type: Function },
    nodeExitCustomRenderFunction: { type: Function },
    nodeOnZoomCustomRenderFunction: { type: Function },
    nodeSelectionHighlightMode: {},
    selectedNodeId: {},
    selectedNodeIds: {},
    panels: {},
    onNodeDragStart: { type: Function },
    onNodeDrag: { type: Function },
    onNodeDragEnd: { type: Function },
    onZoom: { type: Function },
    onZoomStart: { type: Function },
    onZoomEnd: { type: Function },
    onLayoutCalculated: { type: Function },
    onNodeSelectionBrush: { type: Function },
    onNodeSelectionDrag: { type: Function },
    onRenderComplete: { type: Function },
    shouldDataUpdate: { type: Function },
    duration: {},
    events: {},
    attributes: {},
    data: {}
  },
  setup(c, { expose: p }) {
    const n = y(v), l = c, t = m(() => n.data.value ?? l.data), a = f(l), o = S();
    return F(() => {
      g(() => {
        var e;
        o.value = new r(a.value), (e = o.value) == null || e.setData(t.value), n.update(o.value);
      });
    }), k(() => {
      var e;
      (e = o.value) == null || e.destroy(), n.destroy();
    }), u(a, (e, i) => {
      var d;
      P(e, i) || (d = o.value) == null || d.setConfig(a.value);
    }), u(t, () => {
      var e;
      (e = o.value) == null || e.setData(t.value);
    }), p({
      component: o
    }), (e, i) => (h(), b("div", L));
  }
});
export {
  B as VisGraphSelectors,
  D as default
};
//# sourceMappingURL=index.js.map
