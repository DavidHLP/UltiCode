import { Injectable, Logger } from '@nestjs/common';
import { PrismaService } from '../../prisma.service';

export interface SimilarityPair {
  user1_id: string;
  user2_id: string;
  problem_index: string;
  similarity: number;
  submission1_id: string;
  submission2_id: string;
}

export interface TimeAnomaly {
  user_id: string;
  problem_index: string;
  submission_id: string;
  time_from_start: number;
  code_length: number;
  anomaly_type: 'too_fast' | 'suspicious_pattern';
}

export interface AntiCheatReport {
  contest_id: string;
  contest_title: string;
  generated_at: Date;
  similarity_pairs: SimilarityPair[];
  time_anomalies: TimeAnomaly[];
  summary: {
    total_submissions: number;
    total_participants: number;
    suspicious_pairs_count: number;
    time_anomalies_count: number;
    risk_level: 'low' | 'medium' | 'high';
  };
}

export interface TimeAnomalyOptions {
  minTime?: number; // Minimum time in seconds for first submission
  codeLengthFactor?: number; // Factor to multiply with code length for expected time
}

@Injectable()
export class AntiCheatService {
  private readonly logger = new Logger(AntiCheatService.name);
  private readonly DEFAULT_SIMILARITY_THRESHOLD = 0.8;
  private readonly DEFAULT_MIN_TIME = 60; // 60 seconds
  private readonly N_GRAM_SIZE = 3;

  constructor(private readonly prisma: PrismaService) {}

  /**
   * Calculate code similarity using Jaccard similarity on n-grams
   * @param code1 First code snippet
   * @param code2 Second code snippet
   * @returns Similarity score between 0 and 1
   */
  calculateSimilarity(code1: string, code2: string): number {
    // Handle empty code
    if (!code1 || !code2 || code1.trim() === '' || code2.trim() === '') {
      return 0;
    }

    // Normalize code: collapse whitespace but preserve structure
    const normalized1 = this.normalizeCode(code1);
    const normalized2 = this.normalizeCode(code2);

    // If normalized codes are identical, return 1.0
    if (normalized1 === normalized2) {
      return 1.0;
    }

    // Generate n-grams for both codes
    const ngrams1 = this.generateNgrams(normalized1, this.N_GRAM_SIZE);
    const ngrams2 = this.generateNgrams(normalized2, this.N_GRAM_SIZE);

    // Handle empty n-grams
    if (ngrams1.size === 0 && ngrams2.size === 0) {
      return 1.0;
    }
    if (ngrams1.size === 0 || ngrams2.size === 0) {
      return 0;
    }

    // Calculate Jaccard similarity
    const intersection = new Set(
      [...ngrams1].filter((ngram) => ngrams2.has(ngram)),
    );
    const union = new Set([...ngrams1, ...ngrams2]);

    return intersection.size / union.size;
  }

  /**
   * Normalize code by collapsing whitespace and removing comments
   */
  private normalizeCode(code: string): string {
    return (
      code
        // Remove single-line comments
        .replace(/\/\/.*$/gm, '')
        // Remove multi-line comments
        .replace(/\/\*[\s\S]*?\*\//g, '')
        // Collapse multiple whitespace to single space
        .replace(/\s+/g, ' ')
        // Trim
        .trim()
    );
  }

  /**
   * Generate n-grams from a string
   */
  private generateNgrams(text: string, n: number): Set<string> {
    const ngrams = new Set<string>();
    const chars = text.split('');

    for (let i = 0; i <= chars.length - n; i++) {
      const ngram = chars.slice(i, i + n).join('');
      ngrams.add(ngram);
    }

    return ngrams;
  }

  /**
   * Detect suspicious pairs in contest submissions
   * @param contestId Contest ID to analyze
   * @param threshold Similarity threshold (0-1), default 0.8
   * @returns Array of suspicious pairs
   */
  async detectSimilarity(
    contestId: string,
    threshold: number = this.DEFAULT_SIMILARITY_THRESHOLD,
  ): Promise<SimilarityPair[]> {
    // Fetch all submissions for the contest
    const submissions = await this.prisma.contestSubmission.findMany({
      where: { contest_id: contestId },
      include: {
        submission: {
          select: {
            id: true,
            code: true,
            user_id: true,
            language: true,
          },
        },
        contestProblem: {
          select: {
            problem_index: true,
          },
        },
        participant: {
          select: {
            user_id: true,
          },
        },
      },
    });

    const suspiciousPairs: SimilarityPair[] = [];

    // Group submissions by problem
    const submissionsByProblem = new Map<string, typeof submissions>();
    for (const sub of submissions) {
      const problemId = sub.contest_problem_id;
      if (!submissionsByProblem.has(problemId)) {
        submissionsByProblem.set(problemId, []);
      }
      submissionsByProblem.get(problemId)!.push(sub);
    }

    // Compare submissions within each problem group
    for (const [, problemSubmissions] of submissionsByProblem) {
      for (let i = 0; i < problemSubmissions.length; i++) {
        for (let j = i + 1; j < problemSubmissions.length; j++) {
          const sub1 = problemSubmissions[i];
          const sub2 = problemSubmissions[j];

          // Skip if same user
          if (sub1.participant.user_id === sub2.participant.user_id) {
            continue;
          }

          // Only compare same language submissions
          if (sub1.submission.language !== sub2.submission.language) {
            continue;
          }

          const similarity = this.calculateSimilarity(
            sub1.submission.code,
            sub2.submission.code,
          );

          if (similarity >= threshold) {
            suspiciousPairs.push({
              user1_id: sub1.participant.user_id,
              user2_id: sub2.participant.user_id,
              problem_index: sub1.contestProblem.problem_index,
              similarity: Math.round(similarity * 1000) / 1000, // Round to 3 decimals
              submission1_id: sub1.submission_id,
              submission2_id: sub2.submission_id,
            });
          }
        }
      }
    }

    // Sort by similarity descending
    return suspiciousPairs.sort((a, b) => b.similarity - a.similarity);
  }

  /**
   * Check for time anomalies (too-fast submissions)
   * @param contestId Contest ID to analyze
   * @param options Options for time anomaly detection
   * @returns Array of time anomalies
   */
  async checkTimeAnomaly(
    contestId: string,
    options: TimeAnomalyOptions = {},
  ): Promise<TimeAnomaly[]> {
    const minTime = options.minTime ?? this.DEFAULT_MIN_TIME;
    const codeLengthFactor = options.codeLengthFactor ?? 0.05; // 50ms per character

    // Get contest info
    const contest = await this.prisma.contest.findUnique({
      where: { id: contestId },
      select: { start_time: true },
    });

    if (!contest) {
      return [];
    }

    // Get all accepted submissions with their timing
    const submissions = await this.prisma.contestSubmission.findMany({
      where: {
        contest_id: contestId,
        is_accepted: true,
      },
      include: {
        submission: {
          select: {
            id: true,
            code: true,
            user_id: true,
          },
        },
        contestProblem: {
          select: {
            problem_index: true,
          },
        },
        participant: {
          select: {
            user_id: true,
          },
        },
      },
      orderBy: {
        submitted_at: 'asc',
      },
    });

    const anomalies: TimeAnomaly[] = [];

    // Track first accepted submission per user per problem
    const firstSubmissionPerUserProblem = new Map<string, typeof submissions[0]>();

    for (const sub of submissions) {
      const key = `${sub.participant.user_id}-${sub.contest_problem_id}`;

      // Only consider first accepted submission per problem per user
      if (!firstSubmissionPerUserProblem.has(key)) {
        firstSubmissionPerUserProblem.set(key, sub);
      }
    }

    // Analyze first submissions
    for (const [, sub] of firstSubmissionPerUserProblem) {
      const codeLength = sub.submission.code.length;
      const expectedMinTime = Math.max(minTime, codeLength * codeLengthFactor);
      const actualTime = sub.time_from_start;

      // Check if submission is suspiciously fast
      if (actualTime < expectedMinTime) {
        anomalies.push({
          user_id: sub.participant.user_id,
          problem_index: sub.contestProblem.problem_index,
          submission_id: sub.submission_id,
          time_from_start: actualTime,
          code_length: codeLength,
          anomaly_type: actualTime < minTime / 2 ? 'suspicious_pattern' : 'too_fast',
        });
      }
    }

    // Sort by time_from_start ascending (most suspicious first)
    return anomalies.sort((a, b) => a.time_from_start - b.time_from_start);
  }

  /**
   * Generate full anti-cheat report for a contest
   * @param contestId Contest ID
   * @returns Complete anti-cheat report
   */
  async generateReport(contestId: string): Promise<AntiCheatReport | null> {
    // Get contest info
    const contest = await this.prisma.contest.findUnique({
      where: { id: contestId },
      select: {
        id: true,
        title: true,
        start_time: true,
        end_time: true,
      },
    });

    if (!contest) {
      return null;
    }

    // Run all checks in parallel
    const [similarityPairs, timeAnomalies, submissionCount, participantCount] =
      await Promise.all([
        this.detectSimilarity(contestId),
        this.checkTimeAnomaly(contestId),
        this.prisma.contestSubmission.count({
          where: { contest_id: contestId },
        }),
        this.prisma.contestParticipant.count({
          where: { contest_id: contestId },
        }),
      ]);

    // Calculate risk level
    const suspiciousRatio =
      (similarityPairs.length + timeAnomalies.length) / Math.max(participantCount, 1);
    let riskLevel: 'low' | 'medium' | 'high';
    if (suspiciousRatio > 0.3 || similarityPairs.some((p) => p.similarity > 0.95)) {
      riskLevel = 'high';
    } else if (suspiciousRatio > 0.1 || similarityPairs.length > 0) {
      riskLevel = 'medium';
    } else {
      riskLevel = 'low';
    }

    return {
      contest_id: contestId,
      contest_title: contest.title,
      generated_at: new Date(),
      similarity_pairs: similarityPairs,
      time_anomalies: timeAnomalies,
      summary: {
        total_submissions: submissionCount,
        total_participants: participantCount,
        suspicious_pairs_count: similarityPairs.length,
        time_anomalies_count: timeAnomalies.length,
        risk_level: riskLevel,
      },
    };
  }
}
