/*
 * UltiCode sandbox harness — C smoke skeleton.
 *
 * Phase 1 scope: prove the build path works (gcc compiles harness object
 * then links with user solution). Full data-structure support (ListNode,
 * TreeNode, JSON parser) lands in a later phase along with a real
 * envelope contract.
 *
 * Until then, the C/C++ paths use the legacy raw-stdin behavior in the
 * backend (sandbox.mode=legacy). When sandbox.mode=v2 is enabled but the
 * language is C/C++, the backend will refuse and surface "C/C++ not yet
 * supported in v2" until this file is fleshed out.
 */
#ifndef ULTICODE_HARNESS_H
#define ULTICODE_HARNESS_H

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* Harness contract version. Bump on incompatible envelope changes. */
#define ULTICODE_HARNESS_VERSION "1.0"
#define ULTICODE_HARNESS_LANGUAGE "c"

#endif /* ULTICODE_HARNESS_H */
