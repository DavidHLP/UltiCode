import { PrismaClient } from '@prisma/client';

const SUBMISSION_STATUS_DEFINITIONS = [
  {
    key: 'Accepted',
    code: 'AC',
    label: 'Accepted',
    description: 'All test cases passed.',
    category: 'success',
    severity: 'success',
    is_terminal: true,
    sort_order: 10,
  },
  {
    key: 'Wrong Answer',
    code: 'WA',
    label: 'Wrong Answer',
    description: 'Output does not match the expected result.',
    suggestion: 'Review edge cases, input parsing, and output formatting.',
    category: 'error',
    severity: 'error',
    is_terminal: true,
    sort_order: 20,
  },
  {
    key: 'Time Limit Exceeded',
    code: 'TLE',
    label: 'Time Limit Exceeded',
    description: 'Execution exceeded the time limit.',
    suggestion: 'Optimize the algorithm or reduce per-test overhead.',
    category: 'error',
    severity: 'warning',
    is_terminal: true,
    sort_order: 30,
  },
  {
    key: 'Memory Limit Exceeded',
    code: 'MLE',
    label: 'Memory Limit Exceeded',
    description: 'Memory usage exceeded the limit.',
    suggestion: 'Reduce allocations or use more memory-efficient structures.',
    category: 'error',
    severity: 'warning',
    is_terminal: true,
    sort_order: 40,
  },
  {
    key: 'Output Limit Exceeded',
    code: 'OLE',
    label: 'Output Limit Exceeded',
    description: 'Program produced too much output.',
    suggestion: 'Remove debug logs and avoid large prints.',
    category: 'error',
    severity: 'warning',
    is_terminal: true,
    sort_order: 50,
  },
  {
    key: 'Runtime Error',
    code: 'RE',
    label: 'Runtime Error',
    description: 'Program crashed or threw an exception.',
    suggestion: 'Check bounds, null values, and type conversions.',
    category: 'error',
    severity: 'error',
    is_terminal: true,
    sort_order: 60,
  },
  {
    key: 'Compile Error',
    code: 'CE',
    label: 'Compile Error',
    description: 'Code failed to compile or load.',
    suggestion: 'Fix syntax errors and missing definitions.',
    category: 'error',
    severity: 'error',
    is_terminal: true,
    sort_order: 70,
  },
  {
    key: 'Presentation Error',
    code: 'PE',
    label: 'Presentation Error',
    description: 'Output format differs from expected.',
    suggestion: 'Match spacing, line breaks, and formatting exactly.',
    category: 'error',
    severity: 'warning',
    is_terminal: true,
    sort_order: 80,
  },
  {
    key: 'System Error',
    code: 'SE',
    label: 'System Error',
    description: 'Judging system encountered an internal error.',
    suggestion: 'Retry later or contact support.',
    category: 'system',
    severity: 'error',
    is_terminal: true,
    sort_order: 90,
  },
  {
    key: 'Judging',
    code: 'JDG',
    label: 'Judging',
    description: 'Submission is being evaluated.',
    suggestion: 'Please wait.',
    category: 'pending',
    severity: 'info',
    is_terminal: false,
    sort_order: 100,
  },
  {
    key: 'Pending',
    code: 'PD',
    label: 'Pending',
    description: 'Submission is waiting in the queue.',
    suggestion: 'Please wait.',
    category: 'pending',
    severity: 'info',
    is_terminal: false,
    sort_order: 110,
  },
] as const;

export async function clearSubmissionStatuses(prisma: PrismaClient) {
  console.log('  Clearing submission statuses...');
  try {
    await prisma.submissionStatus.deleteMany();
  } catch (error) {
    const err = error as { code?: string; message?: string };
    if (err?.code === 'P2021') {
      console.warn('  Skipping submission statuses (table missing).');
      return;
    }
    throw error;
  }
}

export async function seedSubmissionStatuses(prisma: PrismaClient) {
  console.log('  Seeding submission statuses...');
  const data = SUBMISSION_STATUS_DEFINITIONS.map((definition) => ({
    ...definition,
  }));
  try {
    await prisma.submissionStatus.createMany({
      data,
      skipDuplicates: true,
    });
    return { count: data.length };
  } catch (error) {
    const err = error as { code?: string; message?: string };
    if (err?.code === 'P2021') {
      console.warn('  Skipping submission statuses (table missing).');
      return { count: 0 };
    }
    throw error;
  }
}
