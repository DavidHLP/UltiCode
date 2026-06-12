/*
 * Phase 1 smoke: confirms the harness directory builds with g++.
 * Prints a minimal envelope to stdout and exits 0.
 */
#include "harness.hpp"

int main(int argc, char** argv) {
    (void)argc;
    (void)argv;
    std::printf("{\"harness_version\":\"%s\",\"language\":\"%s\","
                "\"exit_code\":0,\"total_elapsed_ms\":0,"
                "\"results\":[],"
                "\"phase\":\"1-smoke\"}",
                ulticode::HARNESS_VERSION, ulticode::HARNESS_LANGUAGE);
    return 0;
}
