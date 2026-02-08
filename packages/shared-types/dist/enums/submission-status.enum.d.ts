/**
 * Submission status codes
 */
export type SubmissionStatus = 'PENDING' | 'JUDGING' | 'ACCEPTED' | 'WRONG_ANSWER' | 'TIME_LIMIT_EXCEEDED' | 'MEMORY_LIMIT_EXCEEDED' | 'RUNTIME_ERROR' | 'COMPILATION_ERROR';
export declare const SubmissionStatus: {
    readonly PENDING: SubmissionStatus;
    readonly JUDGING: SubmissionStatus;
    readonly ACCEPTED: SubmissionStatus;
    readonly WRONG_ANSWER: SubmissionStatus;
    readonly TIME_LIMIT_EXCEEDED: SubmissionStatus;
    readonly MEMORY_LIMIT_EXCEEDED: SubmissionStatus;
    readonly RUNTIME_ERROR: SubmissionStatus;
    readonly COMPILATION_ERROR: SubmissionStatus;
};
export declare function isSubmissionStatus(value: string): value is SubmissionStatus;
//# sourceMappingURL=submission-status.enum.d.ts.map