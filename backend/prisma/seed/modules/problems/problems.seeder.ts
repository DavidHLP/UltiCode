import type { PrismaClient, Difficulty } from '@prisma/client';
import { Prisma } from '@prisma/client';
import type {
  SeederMetadata,
  SeedModuleResult,
  TransactionClient,
} from '../../core/interfaces';
import { CONTEXT_KEYS } from '../../core/seed-context';
import { BaseSeeder, createSeederExport } from '../base/base.seeder';
import problemsData, { PROBLEM_IDS } from '../../data/problems.data';
import problemDetailsData from '../../data/problem-details.data';

type ProblemCompanySeed = string | { id: string; name: string; logo?: string };
type ProblemCompanyNormalized = { id: string; name: string; logo?: string };

/**
 * Problems seeder - creates problems with details, examples, and languages.
 *
 * Layer: L2 (depends on ProblemTags)
 *
 * Stores in context:
 * - PROBLEM_IDS: Array of created problem IDs
 * - PROBLEM_MAP: Map of problem slug -> problemId
 */
export class ProblemsSeeder extends BaseSeeder {
  readonly metadata: SeederMetadata = {
    name: 'Problems',
    version: '1.0.0',
    dependencies: ['ProblemTags'],
    priority: 0,
    description: 'Seed problems with details, examples, and languages',
  };

  async clear(tx?: TransactionClient): Promise<void> {
    const client = this.getClient(tx) as PrismaClient;
    // Clear in dependency order (child tables first)
    await client.problemExample.deleteMany();
    await client.problemLanguage.deleteMany();
    await client.problemTagRelation.deleteMany();
    await client.problemDetail.deleteMany();
    await client.problem.deleteMany();
  }

  async seed(tx?: TransactionClient): Promise<SeedModuleResult> {
    const startTime = Date.now();
    const client = this.getClient(tx) as PrismaClient;
    const details: Record<string, number> = {};

    // 1. Seed problems using batch insert
    const problemData = problemsData.problems.map((p) => ({
      id: BigInt(p.id),
      slug: p.slug,
      title: p.title,
      difficulty: p.difficulty as Difficulty,
      acceptance_rate: p.acceptance_rate,
      is_premium: p.is_premium,
      has_solution: p.has_solution,
    }));

    const problemResult = await client.problem.createMany({
      data: problemData,
      skipDuplicates: true,
    });
    details.problems = problemResult.count;

    // 2. Seed tag relations using batch insert
    const tagRelationData = problemsData.problem_tag_relations.map((rel) => ({
      problem_id: BigInt(rel.problem_id),
      tag_id: rel.tag_id,
    }));

    const tagRelResult = await client.problemTagRelation.createMany({
      data: tagRelationData,
      skipDuplicates: true,
    });
    details.tagRelations = tagRelResult.count;

    // 3. Seed problem details
    const detailData = problemDetailsData.problem_details.map((pd) => {
      const companies = Array.isArray(pd.companies)
        ? (pd.companies as ProblemCompanySeed[]).map<ProblemCompanyNormalized>(
            (company) =>
              typeof company === 'string'
                ? {
                    id: company.toLowerCase().replace(/\s+/g, '-'),
                    name: company,
                  }
                : { ...company },
          )
        : null;

      return {
        id: pd.id,
        problem_id: BigInt(pd.problem_id),
        slug: pd.slug,
        summary: pd.summary,
        companies: companies ?? Prisma.DbNull,
        likes: pd.id === 'pd-two-sum' ? 54300 : 0,
        dislikes: pd.id === 'pd-two-sum' ? 1800 : 0,
        difficulty_rating: pd.difficulty_rating,
        updated_at: new Date(pd.updated_at),
        follow_up: pd.follow_up ?? null,
        constraints_json: pd.constraints_json,
        hints:
          pd.id === 'pd-two-sum'
            ? [
                'A brute force approach is simple. Loop through each element x and find if there is another value that equals to target – x.',
                'So, if we fix one of the numbers, say x, we have to scan the entire array to find the next number y which is value - x where value is the input parameter. Can we change our array somehow so that this search becomes faster?',
                'The second train of thought is, without changing the array, can we use additional space to somehow make the search faster? This is where a hash map comes in handy.',
              ]
            : Prisma.DbNull,
      };
    });

    const detailResult = await client.problemDetail.createMany({
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      data: detailData as any,
      skipDuplicates: true,
    });
    details.details = detailResult.count;

    // 4. Seed problem examples
    const exampleData = problemDetailsData.problem_examples.map((ex) => {
      // Parse inputs
      const inputText = ex.input_text;
      const assignRegex = /([a-zA-Z0-9_]+)\s*=\s*/g;
      let match: RegExpExecArray | null;
      const pairs: { name: string; valueStart: number; valueEnd?: number }[] = [];

      while ((match = assignRegex.exec(inputText)) !== null) {
        if (pairs.length > 0) {
          pairs[pairs.length - 1].valueEnd = match.index;
        }
        pairs.push({ name: match[1], valueStart: assignRegex.lastIndex });
      }

      const inputs: { name: string; value: string }[] = [];
      if (pairs.length > 0) {
        pairs[pairs.length - 1].valueEnd = inputText.length;
        for (const pair of pairs) {
          let val = inputText.slice(pair.valueStart, pair.valueEnd).trim();
          if (val.endsWith(',')) {
            val = val.slice(0, -1).trim();
          }
          inputs.push({ name: pair.name, value: val });
        }
      }

      return {
        id: ex.id,
        problem_id: BigInt(ex.problem_id),
        example_order: ex.example_order,
        input_text: ex.input_text,
        output_text: ex.output_text,
        explanation: ex.explanation ?? null,
        inputs: inputs.length ? inputs : Prisma.DbNull,
      };
    });

    const exampleResult = await client.problemExample.createMany({
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      data: exampleData as any,
      skipDuplicates: true,
    });
    details.examples = exampleResult.count;

    // 5. Seed problem languages
    const languageData = problemDetailsData.problem_languages.map((lang) => ({
      id: lang.id,
      problem_id: BigInt(lang.problem_id),
      label: lang.label,
      value: lang.value,
      style: lang.style,
      starter_code: lang.starterCode,
    }));

    const languageResult = await client.problemLanguage.createMany({
      data: languageData,
      skipDuplicates: true,
    });
    details.languages = languageResult.count;

    // Store in context for dependent seeders
    const problemIds = problemsData.problems.map((p) => p.id);
    this.set(CONTEXT_KEYS.PROBLEM_IDS, problemIds);

    const problemMap = new Map<string, number>();
    for (const p of problemsData.problems) {
      problemMap.set(p.slug, p.id);
    }
    this.set(CONTEXT_KEYS.PROBLEM_MAP, problemMap);

    const totalCount = Object.values(details).reduce((sum, n) => sum + n, 0);
    return this.createResult(totalCount, startTime, details);
  }
}

export const createProblemsSeeder = createSeederExport(ProblemsSeeder);
export { PROBLEM_IDS };
