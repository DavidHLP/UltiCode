export default {
  loading: "Initializing",
  enter: "Enter",
  skipLoader: "Skip intro",
  portalHint: "Verdict · Contest · Memory",
  portalStatus: "Initializing the architecture",
  nav: {
    primaryNav: "Primary navigation",
    mobileNav: "Mobile navigation",
    skipToContent: "Skip to content",
    home: "Index",
    problems: "Problems",
    contests: "Contests",
    community: "Community",
    talk: "Enter",
    openMenu: "Open menu",
    closeMenu: "Close menu",
  },
  hero: {
    // Phase 1 — the macro artifact. The word stack foreshadows the whole
    // descent: form → lattice → anatomy → horizon.
    eyebrow: "THE ARCHITECTURE OF UNCOMPROMISED CODE",
    brand: "ULTICODE",
    tagline: "One form. Infinite execution.",
    roleLine:
      "A monolithic judging architecture — every submission measured against one absolute standard.",
    cta: "Enter",
    ctaSecondary: "Inspect a problem",
    words: {
      code: "FORM",
      judge: "LATTICE",
      compete: "ANATOMY",
      learn: "HORIZON",
    },
  },
  manifesto: {
    eyebrow: "Principle",
    lead: "Precision over persuasion.",
    body1: "One submission. One verdict. Failure has a layer; passing has a reason.",
    body2: "Read, write, judge, review — one continuous thread.",
    signal: "INPUT → VERDICT → MEMORY",
  },
  problem: {
    // Phase 2 entry — the surface tension before the camera plunges in.
    eyebrow: "Surface",
    title: "Friction at the surface.",
    body: "Most platforms return a verdict and withhold the structure. No lattice. No grain. No way to see where the failure lives.",
    fragments: {
      one: "Opaque",
      two: "Disjointed",
      three: "Silent",
      four: "Unindexed",
    },
  },
  solution: {
    // Phase 2 — interiority. The camera is inside the mesh; copy goes micro.
    eyebrow: "Interiority",
    title: "Beneath the interface, a frictionless core.",
    body: "Read, write, judge, review share one continuous lattice. A submission threads the editorial, the discussion, and your next iteration.",
    core: "Frictionless core",
    orbits: {
      editor: { label: "Input layer", desc: "Write at the point of contact" },
      judge: { label: "Verdict core", desc: "Isolated, staged, sub-millisecond" },
      community: { label: "Memory lattice", desc: "Every verdict becomes persistent knowledge" },
    },
  },
  experience: {
    eyebrow: "Granular path",
    title: "A single thread, end to end.",
    body: "Five granular stages, from first read to final review, inside one frame.",
    cta: "Trace the thread",
    steps: {
      read: { label: "Read", desc: "Statement, constraints, samples — one frame." },
      code: { label: "Code", desc: "Write in context — C++ / Python / JavaScript." },
      submit: { label: "Submit", desc: "One entry into the isolated queue." },
      judge: { label: "Judge", desc: "Compile, run, return a staged verdict." },
      review: { label: "Review", desc: "Resolve against editorials and discussion." },
    },
  },
  about: {
    eyebrow: "Intent",
    statement: "A quiet machine.",
    body1: "UltiCode does not announce itself. It joins reading, writing, judging and review into one repeatable structure, so precision compounds one verdict at a time.",
    body2: "Problems, contests, editorials and discussion orbit a single feedback chain — not separate instruments.",
    principles: {
      one: "Results with evidence",
      two: "Feedback that persists",
      three: "Surfaces that do not fragment",
    },
  },
  work: {
    // Phase 2 → 3 bridge: the apertures into the machine.
    eyebrow: "Apertures",
    title: "Points of entry.",
    subtitle: "Each surface below is reachable from the primary frame.",
    viewAll: "Inspect the set",
    items: {
      twosum: {
        tag: "Sample",
        title: "Two Sum",
        desc: "Arrays and hash tables. Begin from a classic and run the full verdict loop.",
        glyph: "01",
      },
      editor: {
        tag: "Edit",
        title: "Inline editor",
        desc: "Write and revise in context — C++, Python, JavaScript.",
        glyph: "‹/›",
      },
      judge: {
        tag: "Judge",
        title: "Isolated judge",
        desc: "Compile, run, return a precise verdict; isolate the failing stage.",
        glyph: "✓/✗",
      },
      contest: {
        tag: "Contest",
        title: "Timed contests",
        desc: "Submit under one ruleset and time window; read the standings.",
        glyph: "◷",
      },
    },
  },
  capabilities: {
    // Phase 3 — exploded view. Components decoupled and exposed.
    eyebrow: "Anatomy",
    title: "Anatomy of control.",
    pillars: {
      editor: { label: "Editor", desc: "Decoupled write layer" },
      judge: { label: "Judge", desc: "Isolated verdict runtime" },
      contest: { label: "Contest", desc: "Time-boxed execution window" },
      community: { label: "Community", desc: "Persistent memory graph" },
    },
  },
  awards: {
    // Phase 3 — transparency: the record exposed as a spec log.
    eyebrow: "Evidence",
    title: "The record, exposed.",
    label: "Specification log",
    items: {
      siteOfDay2024: "Site of the day 2024",
      siteOfDay2023: "Site of the day 2023",
      honorable2023a: "Honorable mention 2023",
      honorable2023b: "Honorable mention 2023",
      innovation2022: "Innovation award 2022",
      kudos2022: "Special kudos 2022",
    },
  },
  contact: {
    // Phase 4 — convergence. Camera pulls back; the object self-assembles.
    eyebrow: "Horizon",
    title: "The new standard is asymmetric.",
    desc: "Deploy the future. Create a free account, enter contests, and let every verdict compound into skill.",
    cta: "Begin today",
    ctaSecondary: "Inspect the set",
  },
  social: {
    label: "External links",
    github: "GitHub",
    docs: "Documentation",
    community: "Developer community",
  },
  footer: {
    builtWith: "Built with Vue 3 and Spring Boot",
    copyright: "© 2026 UltiCode Project · Apache License 2.0",
    rights: "ICP filing pending · 备案申请中",
  },
} as const;
