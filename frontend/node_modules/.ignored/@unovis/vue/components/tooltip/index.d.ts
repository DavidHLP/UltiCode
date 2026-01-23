import { Tooltip } from '@unovis/ts';
export declare const VisTooltipSelectors: typeof import("@unovis/ts/dist/components/tooltip/style");
declare const _default: import("vue").DefineComponent<import("vue").ExtractPropTypes<{
    components: {
        type: import("vue").PropType<import("@unovis/ts").ComponentCore<unknown, import("@unovis/ts").ComponentConfigInterface>[]>;
    };
    container: {
        type: import("vue").PropType<HTMLElement>;
    };
    followCursor: {
        type: import("vue").PropType<boolean>;
    };
    allowHover: {
        type: import("vue").PropType<boolean>;
    };
    horizontalPlacement: {
        type: import("vue").PropType<string>;
    };
    horizontalShift: {
        type: import("vue").PropType<number>;
    };
    verticalPlacement: {
        type: import("vue").PropType<string>;
    };
    verticalShift: {
        type: import("vue").PropType<number>;
    };
    triggers: {
        type: import("vue").PropType<{
            [selector: string]: (data: any, i: number, elements: (SVGElement | HTMLElement)[]) => string | void | HTMLElement;
        }>;
    };
    attributes: {
        type: import("vue").PropType<{
            [attr: string]: string | number | boolean;
        }>;
    };
    className: {
        type: import("vue").PropType<string>;
    };
    hideDelay: {
        type: import("vue").PropType<number>;
    };
    showDelay: {
        type: import("vue").PropType<number>;
    };
    data: {
        type: import("vue").PropType<never>;
    };
}>, {
    component: import("vue").Ref<Tooltip, Tooltip>;
}, {}, {}, {}, import("vue").ComponentOptionsMixin, import("vue").ComponentOptionsMixin, {}, string, import("vue").PublicProps, Readonly<import("vue").ExtractPropTypes<{
    components: {
        type: import("vue").PropType<import("@unovis/ts").ComponentCore<unknown, import("@unovis/ts").ComponentConfigInterface>[]>;
    };
    container: {
        type: import("vue").PropType<HTMLElement>;
    };
    followCursor: {
        type: import("vue").PropType<boolean>;
    };
    allowHover: {
        type: import("vue").PropType<boolean>;
    };
    horizontalPlacement: {
        type: import("vue").PropType<string>;
    };
    horizontalShift: {
        type: import("vue").PropType<number>;
    };
    verticalPlacement: {
        type: import("vue").PropType<string>;
    };
    verticalShift: {
        type: import("vue").PropType<number>;
    };
    triggers: {
        type: import("vue").PropType<{
            [selector: string]: (data: any, i: number, elements: (SVGElement | HTMLElement)[]) => string | void | HTMLElement;
        }>;
    };
    attributes: {
        type: import("vue").PropType<{
            [attr: string]: string | number | boolean;
        }>;
    };
    className: {
        type: import("vue").PropType<string>;
    };
    hideDelay: {
        type: import("vue").PropType<number>;
    };
    showDelay: {
        type: import("vue").PropType<number>;
    };
    data: {
        type: import("vue").PropType<never>;
    };
}>> & Readonly<{}>, {}, {}, {}, {}, string, import("vue").ComponentProvideOptions, true, {}, any>;
export default _default;
