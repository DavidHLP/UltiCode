/*
 * Phase 1 smoke: confirms the harness directory builds with gcc.
 * Prints a minimal envelope to stdout and exits 0. Not yet wired to
 * input.json. Full C executor logic lands in a later phase.
 */
#include "harness.h"

int main(int argc, char** argv) {
    (void)argc;
    (void)argv;
    printf("{\"harness_version\":\"%s\",\"language\":\"%s\","
           "\"exit_code\":0,\"total_elapsed_ms\":0,"
           "\"results\":[],"
           "\"phase\":\"1-smoke\"}",
           ULTICODE_HARNESS_VERSION, ULTICODE_HARNESS_LANGUAGE);
    return 0;
}
