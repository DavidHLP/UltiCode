"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.SubmissionStatus = void 0;
exports.isSubmissionStatus = isSubmissionStatus;
exports.SubmissionStatus = {
    PENDING: 'PENDING',
    JUDGING: 'JUDGING',
    ACCEPTED: 'ACCEPTED',
    WRONG_ANSWER: 'WRONG_ANSWER',
    TIME_LIMIT_EXCEEDED: 'TIME_LIMIT_EXCEEDED',
    MEMORY_LIMIT_EXCEEDED: 'MEMORY_LIMIT_EXCEEDED',
    RUNTIME_ERROR: 'RUNTIME_ERROR',
    COMPILATION_ERROR: 'COMPILATION_ERROR',
};
function isSubmissionStatus(value) {
    return Object.values(exports.SubmissionStatus).includes(value);
}
