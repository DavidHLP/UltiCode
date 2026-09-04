package com.ulticode.modules.problem.port;

import java.util.Set;

/** App-private advertised language catalog for problem projections. */
public interface ProblemLanguageCatalog {

    Set<String> advertisedLanguages();
}
