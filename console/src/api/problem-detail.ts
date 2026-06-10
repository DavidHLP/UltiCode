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
  value: unknown;
  label?: string;
  fieldName?: string;
}

interface BackendExample {
  id: string;
  explanation: string;
  inputs?: BackendExampleInput[];
  inputText?: string;
  input_text?: string;
  input?: string;
  outputText?: string;
  output_text?: string;
  output?: string;
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
  // D-10: nested viewer object keyed by current user via SecurityContextHolder
  viewer?: { reaction?: string | null } | null;
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
  const isNumeric = typeof id === "number" || !isNaN(Number(id));
  const path = isNumeric ? `/problems/${id}` : `/problems/slug/${id}`;
  const endpoint = userId
    ? `${path}?userId=${encodeURIComponent(userId)}`
    : path;
  const response = await apiGet<BackendProblemResponse>(endpoint);
  return mapProblemDetail(response);
}

const stringifyInputValue = (value: unknown): string => {
  if (typeof value === "string") return value;
  if (value == null) return "";
  return JSON.stringify(value);
};

const splitTopLevelAssignments = (inputText: string): string[] => {
  const parts: string[] = [];
  let current = "";
  let depth = 0;
  let quote: string | null = null;

  for (const char of inputText) {
    if (quote) {
      current += char;
      if (char === quote) quote = null;
      continue;
    }

    if (char === '"' || char === "'") {
      quote = char;
      current += char;
      continue;
    }

    if (char === "[" || char === "{" || char === "(") {
      depth += 1;
    }
    if (char === "]" || char === "}" || char === ")") {
      depth = Math.max(0, depth - 1);
    }

    if (char === "," && depth === 0) {
      parts.push(current.trim());
      current = "";
      continue;
    }

    current += char;
  }

  if (current.trim()) parts.push(current.trim());
  return parts;
};

const parseInputText = (inputText?: string): BackendExampleInput[] => {
  if (!inputText?.trim()) return [];

  return splitTopLevelAssignments(inputText)
    .map((part) => {
      const separator = part.indexOf("=");
      if (separator === -1) {
        return {
          name: "input",
          value: part.trim(),
        };
      }

      return {
        name: part.slice(0, separator).trim() || "input",
        value: part.slice(separator + 1).trim(),
      };
    })
    .filter((input) => input.value !== "");
};

const mapExampleInputs = (
  example: BackendExample,
): ProblemTestCase["inputs"] => {
  const inputs =
    Array.isArray(example.inputs) && example.inputs.length > 0
      ? example.inputs
      : parseInputText(getExampleInput(example));

  return inputs.map((input, index) => {
    const name =
      input.name || input.label || input.fieldName || `input${index + 1}`;
    return {
      id: `${example.id || "case"}-input-${index}`,
      name,
      fieldName: input.fieldName ?? name,
      value: stringifyInputValue(input.value),
      label: input.label ?? name,
    };
  });
};

const getExampleInput = (example: BackendExample): string =>
  example.input ?? example.inputText ?? example.input_text ?? "";

const getExampleOutput = (example: BackendExample): string =>
  example.output ?? example.outputText ?? example.output_text ?? "";

const mapExamplesToTestCases = (
  examples: BackendExample[],
): ProblemTestCase[] =>
  examples.map((ex, index) => ({
    id: ex.id || `case-${index}`,
    label: `Case ${index + 1}`,
    explanation: ex.explanation,
    inputs: mapExampleInputs(ex),
    output: getExampleOutput(ex),
  }));

const mapExamplesToDescription = (examples: BackendExample[]) =>
  examples.map((ex) => ({
    input: getExampleInput(ex),
    output: getExampleOutput(ex),
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
          // D-10: backend now returns nested `interactions.viewer.reaction` (lowercase, e.g. "like")
          // keyed by current user via SecurityContextHolder. Anonymous / no reaction → undefined.
          viewer: response.interactions.viewer?.reaction
            ? {
                reaction: response.interactions.viewer
                  .reaction as ProblemReactionType,
              }
            : undefined,
        }
      : undefined,
  } as ProblemDetail;
}
