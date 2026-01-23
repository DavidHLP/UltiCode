"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.infiniteStream = infiniteStream;
const StreamArbitrary_1 = require("./_internals/StreamArbitrary");
/**@__NO_SIDE_EFFECTS__*/function infiniteStream(arb, constraints) {
    const history = constraints !== undefined && typeof constraints === 'object' && 'noHistory' in constraints
        ? !constraints.noHistory
        : true;
    return new StreamArbitrary_1.StreamArbitrary(arb, history);
}
