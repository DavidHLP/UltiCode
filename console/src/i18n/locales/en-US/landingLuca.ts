export default {
  loading: "Initializing",
  enter: "Enter",
  skipLoader: "Skip intro",
  portalHint: "Verdict · Contest · Memory",
  portalStatus: "Initializing the architecture",
  brand: "ULTICODE",
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
  // Nine-beat narrative — the polyhedron literally acts out each beat's copy.
  // The Chinese headline is locale-stable (the approved brand copy is not
  // translated, per spec); the English sub-line is the optional small caption.
  beats: {
    squashed: {
      eyebrow: "Surface · 01",
      title: "表层之上的阻力。",
      subline: "Friction above the surface",
    },
    cracked: {
      eyebrow: "Interiority · 02",
      title: "界面之下,一颗无阻力的核心。",
      subline: "Beneath the interface, a frictionless core",
    },
    snapped: {
      eyebrow: "Principle · 03",
      title: "以精度,代替游说。",
      subline: "Precision over rhetoric",
    },
    axed: {
      eyebrow: "Granular path · 04",
      title: "一条线,贯穿始终。",
      subline: "One line, through and through",
    },
    opened: {
      eyebrow: "Aperture · 05",
      title: "入口所在。",
      subline: "Where the entrance is",
      cta: "Write your first line of code",
    },
    quarteted: {
      eyebrow: "Anatomy · 06",
      title: "控制的解剖。",
      subline: "Anatomy of control",
      pillars: {
        editor: { label: "Code", desc: "Decoupled write layer" },
        judge: { label: "Judge", desc: "Isolated verdict runtime" },
        contest: { label: "Contest", desc: "Time-boxed execution window" },
        community: { label: "Community", desc: "Persistent memory graph" },
      },
    },
    timed: {
      eyebrow: "Evidence · 07",
      title: "记录,公开。",
      subline: "Records, public",
      logLabel: "Specification log",
      years: {
        "2021": "Public beta",
        "2022": "Isolated judge runtime",
        "2023": "Editorials and discussion in one frame",
        "2024": "Time-boxed contest windows",
        "2025": "Persistent memory graph",
        "2026": "The asymmetric new standard",
      },
    },
    still: {
      eyebrow: "Silence · 08",
      title: "一台安静的机器。",
      subline: "A quiet machine",
    },
    broken: {
      eyebrow: "Horizon · 09",
      title: "新标准,是非对称的。",
      subline: "The new standard is asymmetric",
      ctaPrimary: "Create the future",
      ctaSecondary: "Then and now, coexist",
    },
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
