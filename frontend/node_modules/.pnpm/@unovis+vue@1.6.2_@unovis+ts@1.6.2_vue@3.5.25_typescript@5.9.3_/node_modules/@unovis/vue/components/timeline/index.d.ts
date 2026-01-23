import { Timeline, NumericAccessor } from '@unovis/ts';
import { StringAccessor } from "@unovis/ts";
export declare const VisTimelineSelectors: typeof import("@unovis/ts/dist/components/timeline/style");
declare const _default: <Datum>(__VLS_props: any, __VLS_ctx?: {
    attrs: any;
    emit: any;
    slots: any;
}, __VLS_expose?: any, __VLS_setup?: Promise<{
    props: {
        duration?: number;
        events?: {
            [selector: string]: {
                touchstart?: import("@unovis/ts").VisEventCallback<any>;
                touchmove?: import("@unovis/ts").VisEventCallback<any>;
                touchend?: import("@unovis/ts").VisEventCallback<any>;
                mouseover?: import("@unovis/ts").VisEventCallback<any>;
                mousemove?: import("@unovis/ts").VisEventCallback<any>;
                mousedown?: import("@unovis/ts").VisEventCallback<any>;
                mouseup?: import("@unovis/ts").VisEventCallback<any>;
                click?: import("@unovis/ts").VisEventCallback<any>;
                auxclick?: import("@unovis/ts").VisEventCallback<any>;
                contextmenu?: import("@unovis/ts").VisEventCallback<any>;
                dblclick?: import("@unovis/ts").VisEventCallback<any>;
                mouseenter?: import("@unovis/ts").VisEventCallback<any>;
                mouseleave?: import("@unovis/ts").VisEventCallback<any>;
                mouseout?: import("@unovis/ts").VisEventCallback<any>;
                scroll?: import("@unovis/ts").VisEventCallback<any>;
                select?: import("@unovis/ts").VisEventCallback<any>;
                wheel?: import("@unovis/ts").VisEventCallback<any>;
                pointerdown?: import("@unovis/ts").VisEventCallback<any>;
                pointerup?: import("@unovis/ts").VisEventCallback<any>;
                pointerout?: import("@unovis/ts").VisEventCallback<any>;
                pointermove?: import("@unovis/ts").VisEventCallback<any>;
            };
        };
        attributes?: {
            [selector: string]: {
                [attr: string]: string | number | boolean | ((datum: any) => string | number | boolean);
            };
        };
        data?: Datum[];
        x: NumericAccessor<Datum>;
        y?: NumericAccessor<Datum> | NumericAccessor<Datum>[];
        length?: NumericAccessor<Datum>;
        onScroll?: (scrollTop: number) => void;
        color?: import("@unovis/ts").ColorAccessor<Datum>;
        cursor?: StringAccessor<Datum>;
        id?: (d: Datum, i: number, ...any: any[]) => string;
        xScale?: import("@unovis/ts").ContinuousScale;
        yScale?: import("@unovis/ts").ContinuousScale;
        excludeFromDomainCalculation?: boolean;
        type?: StringAccessor<Datum>;
        showEmptySegments?: boolean;
        lineWidth?: NumericAccessor<Datum>;
        lineRow?: StringAccessor<Datum>;
        lineDuration?: NumericAccessor<Datum>;
        lineCap?: boolean;
        lineStartIcon?: StringAccessor<Datum>;
        lineStartIconColor?: StringAccessor<Datum>;
        lineStartIconSize?: NumericAccessor<Datum>;
        lineStartIconArrangement?: import("@unovis/ts").GenericAccessor<import("@unovis/ts").Arrangement | "inside" | "outside" | "center", Datum>;
        lineEndIcon?: StringAccessor<Datum>;
        lineEndIconColor?: StringAccessor<Datum>;
        lineEndIconSize?: NumericAccessor<Datum>;
        lineEndIconArrangement?: import("@unovis/ts").GenericAccessor<import("@unovis/ts").Arrangement | "inside" | "outside" | "center", Datum>;
        lineCursor?: StringAccessor<Datum>;
        showEmptySegmentsCorrectPosition?: boolean;
        rowHeight?: number;
        alternatingRowColors?: boolean;
        showLabels?: boolean;
        labelWidth?: number;
        maxLabelWidth?: number;
        showRowLabels?: boolean;
        rowLabelStyle?: import("@unovis/ts").GenericAccessor<Record<string, string>, import("@unovis/ts").TimelineRowLabel<Datum>>;
        rowLabelFormatter?: (key: string, items: Datum[], i: number) => string;
        rowIcon?: (key: string, items: Datum[], i: number) => import("@unovis/ts").TimelineRowIcon;
        rowLabelWidth?: number;
        rowMaxLabelWidth?: number;
        rowLabelTextAlign?: import("@unovis/ts").TextAlign | "center" | "left" | "right";
        arrows?: import("@unovis/ts").TimelineArrow[];
        animationLineEnterPosition?: [number, number] | ((d: Datum & import("@unovis/ts").TimelineLineRenderState, i: number, data: (Datum & import("@unovis/ts").TimelineLineRenderState)[]) => [number, number]);
        animationLineExitPosition?: [number, number] | ((d: Datum & import("@unovis/ts").TimelineLineRenderState, i: number, data: (Datum & import("@unovis/ts").TimelineLineRenderState)[]) => [number, number]);
    } & import("vue").VNodeProps & import("vue").AllowedComponentProps & import("vue").ComponentCustomProps;
    expose(exposed: import("vue").ShallowUnwrapRef<{
        component: import("vue").Ref<Timeline<Datum>, Timeline<Datum>>;
    }>): void;
    attrs: any;
    slots: {};
    emit: any;
}>) => import("vue").VNode<import("vue").RendererNode, import("vue").RendererElement, {
    [key: string]: any;
}> & {
    __ctx?: any;
};
export default _default;
