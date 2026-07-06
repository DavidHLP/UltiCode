import { describe, it, expect } from "vitest";
import {
  CODE_TEMPLATES,
  SUPPORTED_LANGUAGES,
  getTemplateCategories,
  getTemplatesByLanguage,
  getTemplatesByCategory,
  getTemplateById,
  normalizeLanguage,
  getTemplatesForLanguage,
  hasTemplatesForLanguage,
} from "../codeTemplates";

describe("codeTemplates constants", () => {
  describe("CODE_TEMPLATES", () => {
    it("should have templates for all supported languages", () => {
      expect(CODE_TEMPLATES.length).toBeGreaterThan(0);

      for (const lang of SUPPORTED_LANGUAGES) {
        const langTemplates = CODE_TEMPLATES.filter(
          (t) => t.language === lang,
        );
        expect(langTemplates.length).toBeGreaterThan(0);
      }
    });

    it("should have valid template structure", () => {
      for (const template of CODE_TEMPLATES) {
        expect(template.id).toBeDefined();
        expect(template.name).toBeDefined();
        expect(template.description).toBeDefined();
        expect(template.language).toBeDefined();
        expect(template.code).toBeDefined();
        expect(template.category).toBeDefined();
        expect(["basic", "algorithm", "data-structure"]).toContain(
          template.category,
        );
      }
    });
  });

  describe("getTemplateCategories", () => {
    it("should return templates grouped by category", () => {
      const categories = getTemplateCategories();

      expect(categories.length).toBe(3);

      const categoryIds = categories.map((c) => c.id);
      expect(categoryIds).toContain("basic");
      expect(categoryIds).toContain("algorithm");
      expect(categoryIds).toContain("data-structure");
    });

    it("should have correct category labels", () => {
      const categories = getTemplateCategories();

      const basicCategory = categories.find((c) => c.id === "basic");
      expect(basicCategory?.label).toBe("Basic");

      const algoCategory = categories.find((c) => c.id === "algorithm");
      expect(algoCategory?.label).toBe("Algorithms");

      const dsCategory = categories.find((c) => c.id === "data-structure");
      expect(dsCategory?.label).toBe("Data Structures");
    });
  });

  describe("getTemplatesByLanguage", () => {
    it.each(SUPPORTED_LANGUAGES)("should return templates for %s", (lang) => {
      const templates = getTemplatesByLanguage(lang);
      expect(templates.length).toBeGreaterThan(0);

      for (const t of templates) {
        expect(t.language).toBe(lang);
      }
    });
  });

  describe("getTemplatesByCategory", () => {
    it("should return basic templates", () => {
      const templates = getTemplatesByCategory("basic");
      expect(templates.length).toBeGreaterThan(0);

      for (const t of templates) {
        expect(t.category).toBe("basic");
      }
    });

    it("should return algorithm templates", () => {
      const templates = getTemplatesByCategory("algorithm");
      expect(templates.length).toBeGreaterThan(0);

      for (const t of templates) {
        expect(t.category).toBe("algorithm");
      }
    });
  });

  describe("getTemplateById", () => {
    it("should return template for valid id", () => {
      const template = getTemplateById("js-main");
      expect(template).toBeDefined();
      expect(template?.name).toBe("Main Function");
      expect(template?.language).toBe("javascript");
    });

    it("should return undefined for invalid id", () => {
      const template = getTemplateById("nonexistent");
      expect(template).toBeUndefined();
    });
  });

  describe("normalizeLanguage", () => {
    it("should normalize JavaScript variants", () => {
      expect(normalizeLanguage("javascript")).toBe("javascript");
      expect(normalizeLanguage("js")).toBe("javascript");
      expect(normalizeLanguage("JAVASCRIPT")).toBe("javascript");
      expect(normalizeLanguage("JS")).toBe("javascript");
    });

    it("should normalize TypeScript variants", () => {
      expect(normalizeLanguage("typescript")).toBe("typescript");
      expect(normalizeLanguage("ts")).toBe("typescript");
      expect(normalizeLanguage("TYPESCRIPT")).toBe("typescript");
    });

    it("should normalize Python variants", () => {
      expect(normalizeLanguage("python")).toBe("python");
      expect(normalizeLanguage("py")).toBe("python");
      expect(normalizeLanguage("PYTHON")).toBe("python");
    });

    it("should normalize Java variants", () => {
      expect(normalizeLanguage("java")).toBe("java");
      expect(normalizeLanguage("JAVA")).toBe("java");
    });

    it("should normalize C++ variants", () => {
      expect(normalizeLanguage("cpp")).toBe("cpp");
      expect(normalizeLanguage("c++")).toBe("cpp");
      expect(normalizeLanguage("CPP")).toBe("cpp");
      expect(normalizeLanguage("C++")).toBe("cpp");
    });

    it("should normalize Go variants", () => {
      expect(normalizeLanguage("go")).toBe("go");
      expect(normalizeLanguage("golang")).toBe("go");
      expect(normalizeLanguage("GO")).toBe("go");
    });

    it("should return javascript for unknown languages", () => {
      expect(normalizeLanguage("unknown")).toBe("javascript");
      expect(normalizeLanguage("")).toBe("javascript");
    });
  });

  describe("getTemplatesForLanguage (normalized)", () => {
    it("should return templates for normalized language variants", () => {
      const jsTemplates = getTemplatesForLanguage("js");
      expect(jsTemplates.length).toBeGreaterThan(0);

      const tsTemplates = getTemplatesForLanguage("ts");
      expect(tsTemplates.length).toBeGreaterThan(0);

      const pyTemplates = getTemplatesForLanguage("py");
      expect(pyTemplates.length).toBeGreaterThan(0);
    });
  });

  describe("hasTemplatesForLanguage", () => {
    it("should return true for supported languages", () => {
      expect(hasTemplatesForLanguage("javascript")).toBe(true);
      expect(hasTemplatesForLanguage("typescript")).toBe(true);
      expect(hasTemplatesForLanguage("python")).toBe(true);
      expect(hasTemplatesForLanguage("java")).toBe(true);
      expect(hasTemplatesForLanguage("cpp")).toBe(true);
      expect(hasTemplatesForLanguage("go")).toBe(true);
      expect(hasTemplatesForLanguage("c")).toBe(true);
    });

    it("should return true for normalized language variants", () => {
      expect(hasTemplatesForLanguage("js")).toBe(true);
      expect(hasTemplatesForLanguage("ts")).toBe(true);
      expect(hasTemplatesForLanguage("py")).toBe(true);
    });
  });

  describe("template content", () => {
    it("should have valid code in all templates", () => {
      for (const template of CODE_TEMPLATES) {
        expect(template.code.length).toBeGreaterThan(0);
        expect(template.code.trim()).not.toBe("");
      }
    });

    it("should have JavaScript main function template", () => {
      const template = getTemplateById("js-main");
      expect(template?.code).toContain("function main");
      expect(template?.code).toContain("// Example usage");
    });

    it("should have Python main function template", () => {
      const template = getTemplateById("py-main");
      expect(template?.code).toContain("def main");
      expect(template?.code).toContain("if __name__");
    });

    it("should have Java main class template", () => {
      const template = getTemplateById("java-main");
      expect(template?.code).toContain("public class");
      expect(template?.code).toContain("public static void main");
    });

    it("should have C++ main function template", () => {
      const template = getTemplateById("cpp-main");
      expect(template?.code).toContain("int main");
      expect(template?.code).toContain("#include");
    });

    it("should have Go main function template", () => {
      const template = getTemplateById("go-main");
      expect(template?.code).toContain("func main");
      expect(template?.code).toContain("package main");
    });
  });
});
