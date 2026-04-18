import { describe, it, expect } from "vitest";
import { useCodeTemplates } from "../useCodeTemplates";

describe("useCodeTemplates", () => {
  describe("all templates", () => {
    it("should have templates for all supported languages", () => {
      const { allTemplates, supportedLanguages } = useCodeTemplates();

      expect(allTemplates.value.length).toBeGreaterThan(0);

      // Check we have templates for each supported language
      for (const lang of supportedLanguages) {
        const langTemplates = allTemplates.value.filter(
          (t) => t.language === lang,
        );
        expect(langTemplates.length).toBeGreaterThan(0);
      }
    });

    it("should have valid template structure", () => {
      const { allTemplates } = useCodeTemplates();

      for (const template of allTemplates.value) {
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

  describe("categories", () => {
    it("should return templates grouped by category", () => {
      const { categories } = useCodeTemplates();

      expect(categories.value.length).toBe(3);

      const categoryIds = categories.value.map((c) => c.id);
      expect(categoryIds).toContain("basic");
      expect(categoryIds).toContain("algorithm");
      expect(categoryIds).toContain("data-structure");
    });

    it("should have correct category labels", () => {
      const { categories } = useCodeTemplates();

      const basicCategory = categories.value.find((c) => c.id === "basic");
      expect(basicCategory?.label).toBe("Basic");

      const algoCategory = categories.value.find((c) => c.id === "algorithm");
      expect(algoCategory?.label).toBe("Algorithms");

      const dsCategory = categories.value.find(
        (c) => c.id === "data-structure",
      );
      expect(dsCategory?.label).toBe("Data Structures");
    });
  });

  describe("get templates by language", () => {
    it("should return templates for JavaScript", () => {
      const { getTemplatesByLanguage } = useCodeTemplates();

      const templates = getTemplatesByLanguage("javascript");
      expect(templates.length).toBeGreaterThan(0);

      for (const t of templates) {
        expect(t.language).toBe("javascript");
      }
    });

    it("should return templates for TypeScript", () => {
      const { getTemplatesByLanguage } = useCodeTemplates();

      const templates = getTemplatesByLanguage("typescript");
      expect(templates.length).toBeGreaterThan(0);

      for (const t of templates) {
        expect(t.language).toBe("typescript");
      }
    });

    it("should return templates for Python", () => {
      const { getTemplatesByLanguage } = useCodeTemplates();

      const templates = getTemplatesByLanguage("python");
      expect(templates.length).toBeGreaterThan(0);

      for (const t of templates) {
        expect(t.language).toBe("python");
      }
    });

    it("should return templates for Java", () => {
      const { getTemplatesByLanguage } = useCodeTemplates();

      const templates = getTemplatesByLanguage("java");
      expect(templates.length).toBeGreaterThan(0);

      for (const t of templates) {
        expect(t.language).toBe("java");
      }
    });

    it("should return templates for C++", () => {
      const { getTemplatesByLanguage } = useCodeTemplates();

      const templates = getTemplatesByLanguage("cpp");
      expect(templates.length).toBeGreaterThan(0);

      for (const t of templates) {
        expect(t.language).toBe("cpp");
      }
    });

    it("should return templates for Go", () => {
      const { getTemplatesByLanguage } = useCodeTemplates();

      const templates = getTemplatesByLanguage("go");
      expect(templates.length).toBeGreaterThan(0);

      for (const t of templates) {
        expect(t.language).toBe("go");
      }
    });

    it("should return templates for C", () => {
      const { getTemplatesByLanguage } = useCodeTemplates();

      const templates = getTemplatesByLanguage("c");
      expect(templates.length).toBeGreaterThan(0);

      for (const t of templates) {
        expect(t.language).toBe("c");
      }
    });
  });

  describe("get templates by category", () => {
    it("should return basic templates", () => {
      const { getTemplatesByCategory } = useCodeTemplates();

      const templates = getTemplatesByCategory("basic");
      expect(templates.length).toBeGreaterThan(0);

      for (const t of templates) {
        expect(t.category).toBe("basic");
      }
    });

    it("should return algorithm templates", () => {
      const { getTemplatesByCategory } = useCodeTemplates();

      const templates = getTemplatesByCategory("algorithm");
      expect(templates.length).toBeGreaterThan(0);

      for (const t of templates) {
        expect(t.category).toBe("algorithm");
      }
    });
  });

  describe("get template by id", () => {
    it("should return template for valid id", () => {
      const { getTemplateById } = useCodeTemplates();

      const template = getTemplateById("js-main");
      expect(template).toBeDefined();
      expect(template?.name).toBe("Main Function");
      expect(template?.language).toBe("javascript");
    });

    it("should return undefined for invalid id", () => {
      const { getTemplateById } = useCodeTemplates();

      const template = getTemplateById("nonexistent");
      expect(template).toBeUndefined();
    });
  });

  describe("normalize language", () => {
    it("should normalize JavaScript variants", () => {
      const { normalizeLanguage } = useCodeTemplates();

      expect(normalizeLanguage("javascript")).toBe("javascript");
      expect(normalizeLanguage("js")).toBe("javascript");
      expect(normalizeLanguage("JAVASCRIPT")).toBe("javascript");
      expect(normalizeLanguage("JS")).toBe("javascript");
    });

    it("should normalize TypeScript variants", () => {
      const { normalizeLanguage } = useCodeTemplates();

      expect(normalizeLanguage("typescript")).toBe("typescript");
      expect(normalizeLanguage("ts")).toBe("typescript");
      expect(normalizeLanguage("TYPESCRIPT")).toBe("typescript");
    });

    it("should normalize Python variants", () => {
      const { normalizeLanguage } = useCodeTemplates();

      expect(normalizeLanguage("python")).toBe("python");
      expect(normalizeLanguage("py")).toBe("python");
      expect(normalizeLanguage("PYTHON")).toBe("python");
    });

    it("should normalize Java variants", () => {
      const { normalizeLanguage } = useCodeTemplates();

      expect(normalizeLanguage("java")).toBe("java");
      expect(normalizeLanguage("JAVA")).toBe("java");
    });

    it("should normalize C++ variants", () => {
      const { normalizeLanguage } = useCodeTemplates();

      expect(normalizeLanguage("cpp")).toBe("cpp");
      expect(normalizeLanguage("c++")).toBe("cpp");
      expect(normalizeLanguage("CPP")).toBe("cpp");
      expect(normalizeLanguage("C++")).toBe("cpp");
    });

    it("should normalize Go variants", () => {
      const { normalizeLanguage } = useCodeTemplates();

      expect(normalizeLanguage("go")).toBe("go");
      expect(normalizeLanguage("golang")).toBe("go");
      expect(normalizeLanguage("GO")).toBe("go");
    });

    it("should return javascript for unknown languages", () => {
      const { normalizeLanguage } = useCodeTemplates();

      expect(normalizeLanguage("unknown")).toBe("javascript");
      expect(normalizeLanguage("")).toBe("javascript");
    });
  });

  describe("get templates for language (normalized)", () => {
    it("should return templates for normalized language", () => {
      const { getTemplatesForLanguage } = useCodeTemplates();

      // Test with different variations
      const jsTemplates = getTemplatesForLanguage("js");
      expect(jsTemplates.length).toBeGreaterThan(0);

      const tsTemplates = getTemplatesForLanguage("ts");
      expect(tsTemplates.length).toBeGreaterThan(0);

      const pyTemplates = getTemplatesForLanguage("py");
      expect(pyTemplates.length).toBeGreaterThan(0);
    });
  });

  describe("has templates for language", () => {
    it("should return true for supported languages", () => {
      const { hasTemplatesForLanguage } = useCodeTemplates();

      expect(hasTemplatesForLanguage("javascript")).toBe(true);
      expect(hasTemplatesForLanguage("typescript")).toBe(true);
      expect(hasTemplatesForLanguage("python")).toBe(true);
      expect(hasTemplatesForLanguage("java")).toBe(true);
      expect(hasTemplatesForLanguage("cpp")).toBe(true);
      expect(hasTemplatesForLanguage("go")).toBe(true);
      expect(hasTemplatesForLanguage("c")).toBe(true);
    });

    it("should return true for normalized language variants", () => {
      const { hasTemplatesForLanguage } = useCodeTemplates();

      expect(hasTemplatesForLanguage("js")).toBe(true);
      expect(hasTemplatesForLanguage("ts")).toBe(true);
      expect(hasTemplatesForLanguage("py")).toBe(true);
    });
  });

  describe("template content", () => {
    it("should have valid code in templates", () => {
      const { allTemplates } = useCodeTemplates();

      for (const template of allTemplates.value) {
        expect(template.code.length).toBeGreaterThan(0);
        // Code should not be empty
        expect(template.code.trim()).not.toBe("");
      }
    });

    it("should have JavaScript main function template", () => {
      const { getTemplateById } = useCodeTemplates();

      const template = getTemplateById("js-main");
      expect(template?.code).toContain("function main");
      expect(template?.code).toContain("// Example usage");
    });

    it("should have Python main function template", () => {
      const { getTemplateById } = useCodeTemplates();

      const template = getTemplateById("py-main");
      expect(template?.code).toContain("def main");
      expect(template?.code).toContain("if __name__");
    });

    it("should have Java main class template", () => {
      const { getTemplateById } = useCodeTemplates();

      const template = getTemplateById("java-main");
      expect(template?.code).toContain("public class");
      expect(template?.code).toContain("public static void main");
    });

    it("should have C++ main function template", () => {
      const { getTemplateById } = useCodeTemplates();

      const template = getTemplateById("cpp-main");
      expect(template?.code).toContain("int main");
      expect(template?.code).toContain("#include");
    });

    it("should have Go main function template", () => {
      const { getTemplateById } = useCodeTemplates();

      const template = getTemplateById("go-main");
      expect(template?.code).toContain("func main");
      expect(template?.code).toContain("package main");
    });
  });
});
