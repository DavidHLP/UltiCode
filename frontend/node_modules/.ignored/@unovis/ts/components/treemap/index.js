import { select } from 'd3-selection';
import { hierarchy, treemap } from 'd3-hierarchy';
import { extent, group, max } from 'd3-array';
import { scaleLinear, scaleThreshold } from 'd3-scale';
import { hsl } from 'd3-color';
import { ComponentCore } from '../../core/component/index.js';
import { SeriesDataModel } from '../../data-models/series.js';
import { getColor, getHexValue, brighter, isColorDark } from '../../utils/color.js';
import { isNumber, getString, getNumber } from '../../utils/data.js';
import { smartTransition } from '../../utils/d3.js';
import { wrapSVGText, trimSVGText } from '../../utils/text.js';
import { cssvar } from '../../utils/style.js';
import { FitMode } from '../../types/text.js';
import { TreemapDefaultConfig } from './config.js';
import * as style from './style.js';
import { tiles, tileGroup, tile, clickableTile, labelGroup, label, variables, internalLabel } from './style.js';

class Treemap extends ComponentCore {
    constructor(config) {
        super();
        this._defaultConfig = TreemapDefaultConfig;
        this.config = this._defaultConfig;
        this.datamodel = new SeriesDataModel();
        if (config)
            this.setConfig(config);
        this.tiles = this.g.append('g').attr('class', tiles);
    }
    _isTileLargeEnough(d) {
        const w = d.x1 - d.x0;
        const h = d.y1 - d.y0;
        return (w >= this.config.minTileSizeForLabel) && (h >= this.config.minTileSizeForLabel);
    }
    _getTileLightness(node, siblings) {
        // Get the value extent of the sibling group
        const [minValue, maxValue] = extent(siblings, d => d.value);
        // If there's no range or no value, return default lightness
        if (minValue === maxValue || !node.value)
            return 0;
        // Calculate relative position in the range (0 to 1)
        // Larger values will be closer to 0 (darker)
        return this.config.lightnessVariationAmount * ((maxValue - node.value) / (maxValue - minValue));
    }
    _render(customDuration) {
        var _a;
        super._render(customDuration);
        const { config, datamodel: { data }, _width, _height } = this;
        const duration = isNumber(customDuration) ? customDuration : config.duration;
        if (!((_a = config.layers) === null || _a === void 0 ? void 0 : _a.length)) {
            console.warn('Unovis | Treemap: No layers defined');
            return;
        }
        // Map each layer accessor function to get string values from the data array
        const layerAccessors = config.layers.map(layerAccessor => {
            return (i) => getString(data[i], layerAccessor, i);
        });
        // Group the data indices by the layer accessors to create a hierarchical structure
        const nestedData = group(data.keys(), ...layerAccessors);
        // Create the hierarchy from the grouped data,
        // which by itself is not quite right because there is an extra
        // level of nesting that we don't want, just above the leaf nodes.
        const rootNode = hierarchy(nestedData);
        // Compute the aggregation
        if (config.value) {
            rootNode.sum(index => isNumber(index) && getNumber(data[index], config.value, index));
        }
        else {
            rootNode.count();
        }
        // Fix the hierarchy by removing the extra level of nesting
        rootNode.each(node => {
            if (!node.children && node.parent) {
                node.parent.children = null;
            }
        });
        const treemapLayout = treemap()
            .size([_width, _height])
            .round(true)
            .padding(config.tilePadding);
        // Apply padding to the top of each tile,
        // but not for the root node.
        if (this.config.tilePaddingTop !== undefined) {
            treemapLayout.paddingTop(d => d.parent ? config.tilePaddingTop : 0);
        }
        // Compute the treemap layout
        const treemapData = treemapLayout(rootNode);
        // Process the resulting hierarchy into the type we need
        let nodeId = 0;
        treemapData.each(node => {
            const n = node;
            // Generate unique IDs for each node
            node._id = `node-${nodeId++}`;
            const treemapDatum = {
                key: n.data[0],
            };
            // Populate the index and datum for leaf nodes
            const isLeafNode = !n.children;
            if (isLeafNode) {
                treemapDatum.index = n.data[1][0];
                treemapDatum.datum = data[treemapDatum.index];
            }
            node.data = treemapDatum;
        });
        const descendants = treemapData.descendants();
        // Set up the brightness increase scale based on depth
        const maxDepth = max(descendants, d => d.depth);
        const brightnessIncrease = scaleLinear()
            .domain([1, maxDepth])
            .range([0, 1]);
        // Get all leaf node values and calculate their square roots
        // (since area is proportional to value)
        const leafValues = descendants.filter(d => !d.children).map(d => d.value);
        const maxLeafValue = Math.sqrt(max(leafValues)) || 0;
        // Divide the range into three equal intervals based on the square root of values
        // This accounts for the fact that area is proportional to value
        const fontSizeScale = scaleThreshold()
            .domain([
            maxLeafValue / 3,
            (maxLeafValue * 2) / 3, // Second third of the max value
        ])
            .range([
            config.tileLabelSmallFontSize,
            config.tileLabelMediumFontSize,
            config.tileLabelLargeFontSize,
        ]);
        const visibleTiles = descendants.filter(d => d.depth > 0);
        const firstLevelTiles = visibleTiles.filter(d => d.depth === 1);
        const nonFirstLevelTiles = visibleTiles.filter(d => d.depth > 1);
        // Set the fill color for the first level tiles
        firstLevelTiles.forEach((d, i) => {
            d._fill = getColor(d, config.tileColor, i);
        });
        // Set the fill color for the non-first level tiles
        nonFirstLevelTiles.forEach((d, i) => {
            var _a;
            const providedColor = getColor(d, config.tileColor, i, true);
            if (providedColor) {
                d._fill = providedColor;
                return;
            }
            const hslColor = hsl(getHexValue((_a = d.parent) === null || _a === void 0 ? void 0 : _a._fill, this.element));
            if (config.enableLightnessVariance && !d.children && d.parent) {
                const siblings = d.parent.children;
                const lightnessAdjustment = this._getTileLightness(d, siblings);
                hslColor.l = Math.min(1, hslColor.l + lightnessAdjustment);
            }
            d._fill = brighter(hslColor.toString(), brightnessIncrease(d.depth));
        });
        // Render tiles
        const tiles = this.tiles
            .selectAll(`g.${tileGroup}`)
            .data(visibleTiles, d => `${d.data.key}-${d.depth}`);
        const tilesEnter = tiles
            .enter()
            .append('g')
            .attr('class', tileGroup);
        // Computes the rect border radius for a given tile.
        // The rx and ry values are the minimum of the tile
        // border radius and some fraction the width of the tile,
        // based on the tileBorderRadiusFactor config.
        // This ensures that the tile border radius is not
        // larger than the tile size, which makes small tiles
        // look better.
        const getTileBorderRadius = (d) => Math.min(config.tileBorderRadius, (d.x1 - d.x0) * config.tileBorderRadiusFactor);
        // Add clipPath elements
        tilesEnter
            .append('clipPath')
            .attr('id', d => `clip-${this.uid}-${d._id}`)
            .append('rect')
            .attr('rx', getTileBorderRadius)
            .attr('ry', getTileBorderRadius);
        // Tile rectangles
        const tileRects = tilesEnter
            .append('rect')
            .classed(tile, true)
            .classed(clickableTile, d => config.showTileClickAffordance && !d.children)
            .attr('rx', getTileBorderRadius)
            .attr('ry', getTileBorderRadius)
            .attr('x', d => d.x0)
            .attr('y', d => d.y0)
            .attr('width', d => d.x1 - d.x0)
            .attr('height', d => d.y1 - d.y0)
            .style('fill', d => d._fill)
            .style('opacity', 0)
            .style('cursor', config.showTileClickAffordance ? d => !d.children ? 'pointer' : null : null);
        tileRects.append('title');
        tilesEnter
            .append('g')
            .attr('class', labelGroup)
            .attr('clip-path', d => `url(#clip-${this.uid}-${d._id})`)
            .attr('transform', d => `translate(${d.x0 + config.labelOffsetX},${d.y0 + config.labelOffsetY})`)
            .style('opacity', 0)
            .append('text')
            .attr('class', label)
            .attr('x', 0)
            .attr('y', 0);
        const mergedTiles = tiles.merge(tilesEnter);
        const tileRectsMerged = mergedTiles.select(`rect.${tile}`);
        smartTransition(tileRectsMerged, duration)
            .style('fill', d => d._fill)
            .style('opacity', 1)
            .attr('x', d => d.x0)
            .attr('y', d => d.y0)
            .attr('width', d => d.x1 - d.x0)
            .attr('height', d => d.y1 - d.y0);
        tileRectsMerged.select('title')
            .text(d => config.tileLabel(d));
        // Update clipPath rects
        mergedTiles.select('clipPath rect')
            .attr('width', d => d.x1 - d.x0 - 2 * config.labelOffsetX)
            .attr('height', d => d.y1 - d.y0 - 2 * config.labelOffsetY);
        const textSelection = mergedTiles.selectAll(`g.${labelGroup} text`);
        textSelection
            .text(d => config.tileLabel(d))
            .attr('title', d => config.tileLabel(d))
            .property('font-size-px', d => {
            var _a;
            const sqrtVal = Math.sqrt((_a = d.value) !== null && _a !== void 0 ? _a : 0);
            return config.enableTileLabelFontSizeVariation && !d.children
                ? fontSizeScale(sqrtVal)
                : null; // Use the default css variable value
        })
            .style('font-size', (_, i, els) => `${select(els[i]).property('font-size-px')}px`)
            .style('fill', d => cssvar(isColorDark(d._fill) ? variables.treemapLabelTextColorLight : variables.treemapLabelTextColor));
        // Fit label (wrap or trim)
        textSelection.each((d, i, els) => {
            var _a;
            const isLeafNode = !d.children;
            const el = els[i];
            const text = select(el);
            const tileWidth = d.x1 - d.x0 - ((_a = config.labelOffsetX) !== null && _a !== void 0 ? _a : 0) * 2;
            const fontSize = parseFloat(text.property('font-size-px')) || parseFloat(window.getComputedStyle(el).fontSize);
            if (config.labelFit === FitMode.Wrap && isLeafNode) {
                wrapSVGText(text, tileWidth);
            }
            else {
                trimSVGText(text, tileWidth, config.labelTrimMode, true, fontSize);
            }
        });
        // Transition group position
        smartTransition(mergedTiles.select(`g.${labelGroup}`), duration)
            .attr('transform', d => `translate(${d.x0 + config.labelOffsetX},${d.y0 + config.labelOffsetY})`);
        // Transition text opacity only (fade-in)
        smartTransition(mergedTiles.select(`g.${labelGroup}`), duration)
            .style('opacity', 1);
        // Hide labels that don't meet criteria
        mergedTiles.select(`text.${label}`)
            .style('display', d => {
            const isAllowedNode = config.labelInternalNodes ? true : !d.children;
            return isAllowedNode && this._isTileLargeEnough(d) ? null : 'none';
        })
            // Make the internal labels semibold via class
            .attr('class', d => d.children ? `${label} ${internalLabel}` : label);
        smartTransition(tiles.exit(), duration)
            .style('opacity', 0)
            .remove();
    }
}
Treemap.selectors = style;

export { Treemap };
//# sourceMappingURL=index.js.map
