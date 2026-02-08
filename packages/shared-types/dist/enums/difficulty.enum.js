"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.Difficulty = void 0;
exports.isDifficulty = isDifficulty;
exports.Difficulty = {
    EASY: 'Easy',
    MEDIUM: 'Medium',
    HARD: 'Hard',
};
function isDifficulty(value) {
    return ['Easy', 'Medium', 'Hard'].includes(value);
}
