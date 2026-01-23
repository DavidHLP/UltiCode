import { select } from 'd3-selection';
import toPx from 'to-px';
import { merge } from '../../utils/data.js';
import { BulletLegendDefaultConfig } from './config.js';
import { BulletLegendOrientation } from './types.js';
import { createBullets, getBulletsTotalWidth, updateBullets } from './modules/shape.js';
import * as style from './style.js';
import { root, item, itemVertical, clickable, bullet, label } from './style.js';

class BulletLegend {
    constructor(element, config) {
        this._defaultConfig = BulletLegendDefaultConfig;
        this.config = this._defaultConfig;
        this._colorAccessor = (d) => d.color;
        this._container = element;
        this.div = (config === null || config === void 0 ? void 0 : config.renderIntoProvidedDomNode) ? select(this._container) : select(this._container).append('div');
        this.div.classed(root, true);
        this.element = this.div.node();
        if (config)
            this.update(config);
    }
    update(config) {
        this.prevConfig = this.config;
        this.config = merge(this._defaultConfig, config);
        this.render();
    }
    render() {
        const { config } = this;
        const legendItems = this.div.selectAll(`.${item}`).data(config.items);
        const legendItemsEnter = legendItems.enter().append('div')
            .on('click', this._onItemClick.bind(this));
        const legendItemsMerged = legendItemsEnter.merge(legendItems);
        legendItemsMerged
            .attr('class', d => { var _a; return `${item} ${(_a = d.className) !== null && _a !== void 0 ? _a : ''}`; })
            .classed(itemVertical, config.orientation === BulletLegendOrientation.Vertical)
            .classed(clickable, d => !!config.onLegendItemClick && this._isItemClickable(d))
            .attr('title', d => d.name)
            .style('display', (d) => d.hidden ? 'none' : null);
        // Bullet
        legendItemsEnter.append('span')
            .attr('class', bullet)
            .call(createBullets);
        legendItemsMerged.select(`.${bullet}`)
            .style('width', function (d) {
            const colors = Array.isArray(d.color) ? d.color : [d.color];
            const numColors = colors.length;
            const defaultSize = toPx(getComputedStyle(this).getPropertyValue('--vis-legend-bullet-size')) || 9;
            const baseSize = config.bulletSize ? toPx(config.bulletSize) : defaultSize;
            const spacing = config.bulletSpacing;
            return `${getBulletsTotalWidth(baseSize, numColors, spacing)}px`;
        })
            .style('height', config.bulletSize)
            .style('box-sizing', 'content-box')
            .call(updateBullets, this.config, this._colorAccessor);
        // Labels
        legendItemsEnter.append('span')
            .attr('class', label)
            .classed(config.labelClassName, true)
            .style('max-width', config.labelMaxWidth)
            .style('font-size', config.labelFontSize);
        legendItemsMerged.select(`.${label}`)
            .text((d) => d.name);
        legendItems.exit().remove();
    }
    _isItemClickable(item) {
        return item.pointer === undefined ? true : item.pointer;
    }
    _onItemClick(event, d) {
        const { config: { onLegendItemClick } } = this;
        const legendItems = this.div.selectAll(`.${item}`).nodes();
        const index = legendItems.indexOf(event.currentTarget);
        if (onLegendItemClick)
            onLegendItemClick(d, index);
    }
    destroy() {
        if (this.element !== this._container)
            this.div.remove();
    }
}
BulletLegend.selectors = style;

export { BulletLegend };
//# sourceMappingURL=index.js.map
