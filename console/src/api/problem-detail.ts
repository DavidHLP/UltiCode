import type {
  ProblemDetail,
  ProblemLanguageOption,
  ProblemTestCase,
} from "@/types/problem-detail";
import { apiGet } from "@/utils/request";
import { mapProblem } from "@/api/problem";

interface BackendExampleInput {
  name: string;
  value: string;
}

interface BackendExample {
  id: string;
  explanation: string;
  inputs?: BackendExampleInput[];
  inputText?: string;
  outputText?: string;
}

interface BackendProblemDetail {
  summary: string;
  companies: { id: string; name: string; logo?: string }[] | null;
  constraints_json: string[];
  follow_up: string;
  hints: string[] | null;
}

interface BackendProblemResponse {
  detail?: BackendProblemDetail | null;
  examples?: BackendExample[] | null;
  languages?: unknown[] | null;
  summary?: string | null;
  constraints?: string[] | null;
  followUp?: string | null;
  companies?: BackendProblemDetail["companies"] | null;
  starterNotes?: string[] | null;
  [key: string]: unknown;
}

export async function fetchProblemDetailById(
  id: number | string,
  userId?: string,
): Promise<ProblemDetail> {
  const query = userId ? `?userId=${userId}` : "";
  const response = await apiGet<BackendProblemResponse>(
    `/problems/${id}${query}`,
  );
  return mapProblemDetail(response);
}

const mapExamplesToTestCases = (
  examples: BackendExample[],
): ProblemTestCase[] =>
  examples.map((ex, index) => ({
    id: ex.id || `case-${index}`,
    label: `Case ${index + 1}`,
    explanation: ex.explanation,
    inputs: ex.inputs
      ? ex.inputs.map((input: BackendExampleInput) => ({
          name: input.name,
          value: input.value,
          label: input.name,
        }))
      : [],
    output: ex.outputText,
  }));

const mapExamplesToDescription = (examples: BackendExample[]) =>
  examples.map((ex) => ({
    input: ex.inputText || "",
    output: ex.outputText || "",
    explanation: ex.explanation,
  }));

const mapLanguages = (raw: unknown): ProblemLanguageOption[] => {
  if (!Array.isArray(raw)) return [];
  const mapped = raw
    .map((lang) => {
      const l = lang as Record<string, unknown>;
      const value = typeof l.value === "string" ? l.value : "";
      return {
        id: l.id as ProblemLanguageOption["id"],
        label: (typeof l.label === "string" && l.label) || value || "Unknown",
        value,
        style: typeof l.style === "string" ? l.style : undefined,
        starterCode:
          (typeof l.starterCode === "string" && l.starterCode) ||
          (typeof l.starter_code === "string" && l.starter_code) ||
          "",
      } as ProblemLanguageOption;
    })
    .filter((lang) => lang.value);

  return mapped;
};

export function mapProblemDetail(
  response: BackendProblemResponse,
): ProblemDetail {
  const base = mapProblem(response);
  const detail: BackendProblemDetail = response.detail ?? {
    summary: "",
    companies: null,
    constraints_json: [],
    follow_up: "",
    hints: null,
  };
  const examples = (response.examples ?? []).filter(
    Boolean,
  ) as BackendExample[];

  return {
    ...base,
    content: detail.summary ?? response.summary ?? "",
    summary: detail.summary ?? response.summary ?? "",
    constraints: detail.constraints_json ?? response.constraints ?? [],
    followUp: detail.follow_up ?? response.followUp ?? "",
    companies: detail.companies ?? response.companies ?? [],
    starterNotes: detail.hints ?? response.starterNotes ?? [],
    languages: mapLanguages(response.languages),
    testCases: examples.length > 0 ? mapExamplesToTestCases(examples) : [],
    examples: examples.length > 0 ? mapExamplesToDescription(examples) : [],
  } as ProblemDetail;
}
