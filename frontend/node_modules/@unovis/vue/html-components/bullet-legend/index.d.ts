import { BulletLegend, BulletLegendItemInterface } from '@unovis/ts';
export declare const VisBulletLegendSelectors: typeof import("@unovis/ts/dist/components/bullet-legend/style");
declare const _default: import("vue").DefineComponent<import("vue").ExtractPropTypes<{
    items: {
        type: import("vue").PropType<BulletLegendItemInterface[]>;
        required: true;
    };
    labelClassName: {
        type: import("vue").PropType<string>;
    };
    onLegendItemClick: {
        type: import("vue").PropType<(d: BulletLegendItemInterface, i: number) => void>;
    };
    labelFontSize: {
        type: import("vue").PropType<string>;
    };
    labelMaxWidth: {
        type: import("vue").PropType<string>;
    };
    bulletSize: {
        type: import("vue").PropType<string>;
    };
    bulletSpacing: {
        type: import("vue").PropType<number>;
    };
    bulletShape: {
        type: import("vue").PropType<import("@unovis/ts").GenericAccessor<import("@unovis/ts").BulletShape, BulletLegendItemInterface>>;
    };
    orientation: {
        type: import("vue").PropType<string>;
    };
    renderIntoProvidedDomNode: {
        type: import("vue").PropType<boolean>;
    };
    data: {
        type: import("vue").PropType<never>;
    };
}>, {
    component: import("vue").Ref<BulletLegend, BulletLegend>;
}, {}, {}, {}, import("vue").ComponentOptionsMixin, import("vue").ComponentOptionsMixin, {}, string, import("vue").PublicProps, Readonly<import("vue").ExtractPropTypes<{
    items: {
        type: import("vue").PropType<BulletLegendItemInterface[]>;
        required: true;
    };
    labelClassName: {
        type: import("vue").PropType<string>;
    };
    onLegendItemClick: {
        type: import("vue").PropType<(d: BulletLegendItemInterface, i: number) => void>;
    };
    labelFontSize: {
        type: import("vue").PropType<string>;
    };
    labelMaxWidth: {
        type: import("vue").PropType<string>;
    };
    bulletSize: {
        type: import("vue").PropType<string>;
    };
    bulletSpacing: {
        type: import("vue").PropType<number>;
    };
    bulletShape: {
        type: import("vue").PropType<import("@unovis/ts").GenericAccessor<import("@unovis/ts").BulletShape, BulletLegendItemInterface>>;
    };
    orientation: {
        type: import("vue").PropType<string>;
    };
    renderIntoProvidedDomNode: {
        type: import("vue").PropType<boolean>;
    };
    data: {
        type: import("vue").PropType<never>;
    };
}>> & Readonly<{}>, {}, {}, {}, {}, string, import("vue").ComponentProvideOptions, true, {}, any>;
export default _default;
