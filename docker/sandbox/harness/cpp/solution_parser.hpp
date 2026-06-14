// UltiCode sandbox harness — Solution signature extractor (C++).
//
// C++ has no runtime reflection, so the harness cannot dispatch to
// Solution::mergeKLists by name + param types like the Java harness does
// via Method.invoke. Instead the orchestrator statically extracts the public
// method name AND its parameter C++ types from the user's solution.cpp and
// bakes them into the generated runner. The frontend does not send type
// hints (input.json inputs[].type is usually absent), so parsing the source
// is the only reliable signal. LeetCode-style solutions are regular enough
// that a brace-depth-aware scan is reliable.
#ifndef ULTICODE_SOLUTION_PARSER_HPP
#define ULTICODE_SOLUTION_PARSER_HPP

#include <string>
#include <vector>

namespace ulticode {

struct MethodSignature {
    std::string name;
    // C++ source parameter types (variable names stripped), in declaration
    // order, e.g. {"vector<ListNode*>&", "int"}. Used to pick the right
    // deserializer per argument when generating the runner.
    std::vector<std::string> paramTypes;
};

// Extract the first public instance method's signature declared inside
// `class Solution { ... }`. Throws std::runtime_error if no class Solution
// or no callable public method is found.
MethodSignature extractMethodSignature(const std::string& code);

// Convenience: just the method name.
std::string extractMethodName(const std::string& code);

}  // namespace ulticode

#endif  // ULTICODE_SOLUTION_PARSER_HPP
