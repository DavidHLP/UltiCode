import type {
  ProblemDetail,
  ProblemLanguageOption,
  ProblemReactionType,
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
  input_text?: string;
  outputText?: string;
  output_text?: string;
}

interface BackendProblemDetail {
  summary: string;
  content?: string | null;
  companies: { id: string; name: string; logo?: string }[] | null;
  constraints_json: string[];
  follow_up: string;
  hints: string[] | null;
}

interface BackendInteractionData {
  likes?: number;
  dislikes?: number;
  favorites?: number;
  viewer_reaction?: string;
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
  interactions?: BackendInteractionData | null;
  [key: string]: unknown;
}

interface BackendLanguageOption {
  id?: unknown;
  label?: unknown;
  value?: unknown;
  style?: unknown;
  starterCode?: unknown;
  starter_code?: unknown;
}

export async function fetchProblemDetailById(
  id: number | string,
  userId?: string,
): Promise<ProblemDetail> {
  const query = userId ? `?userId=${userId}` : "";
  const isNumeric = typeof id === "number" || !isNaN(Number(id));
  const endpoint = isNumeric ? `/problems/${id}` : `/problems/slug/${id}`;
  const response = await apiGet<BackendProblemResponse>(`${endpoint}${query}`);
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
    output: ex.outputText ?? ex.output_text,
  }));

const mapExamplesToDescription = (examples: BackendExample[]) =>
  examples.map((ex) => ({
    input: ex.inputText ?? ex.input_text ?? "",
    output: ex.outputText ?? ex.output_text ?? "",
    explanation: ex.explanation,
  }));

const mapLanguages = (raw: unknown): ProblemLanguageOption[] => {
  if (!Array.isArray(raw)) return [];
  const mapped = raw
    .map((lang) => {
      const l = lang as BackendLanguageOption;
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
    content: detail.content ?? detail.summary ?? response.summary ?? "",
    summary: detail.summary ?? response.summary ?? "",
    constraints: detail.constraints_json ?? response.constraints ?? [],
    followUp: detail.follow_up ?? response.followUp ?? "",
    companies: detail.companies ?? response.companies ?? [],
    starterNotes: detail.hints ?? response.starterNotes ?? [],
    languages: mapLanguages(response.languages),
    testCases: examples.length > 0 ? mapExamplesToTestCases(examples) : [],
    examples: examples.length > 0 ? mapExamplesToDescription(examples) : [],
    interactions: response.interactions
      ? {
          counts: {
            likes: response.interactions.likes ?? 0,
            dislikes: response.interactions.dislikes ?? 0,
            favorites: response.interactions.favorites ?? 0,
          },
          reactions: [],
          viewer: response.interactions.viewer_reaction
            ? { reaction: response.interactions.viewer_reaction as ProblemReactionType }
            : undefined,
        }
      : undefined,
  } as ProblemDetail;
}
