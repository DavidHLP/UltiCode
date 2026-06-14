// UltiCode sandbox harness — Solution signature extractor implementation.
#include "solution_parser.hpp"

#include <cctype>
#include <set>
#include <stdexcept>

namespace ulticode {

namespace {

// Strip // line comments, /* */ block comments, and blank out string/char
// literals so identifiers inside them are never mistaken for code.
std::string stripCommentsAndStrings(const std::string& code) {
    std::string out;
    out.reserve(code.size());
    enum State { CODE, LINE_CMT, BLOCK_CMT, STR, CHR } state = CODE;
    for (std::size_t i = 0; i < code.size(); ++i) {
        char c = code[i];
        char n = (i + 1 < code.size()) ? code[i + 1] : '\0';
        switch (state) {
            case CODE:
                if (c == '/' && n == '/') { state = LINE_CMT; ++i; }
                else if (c == '/' && n == '*') { state = BLOCK_CMT; ++i; }
                else if (c == '"') { state = STR; out += ' '; }
                else if (c == '\'') { state = CHR; out += ' '; }
                else { out += c; }
                break;
            case LINE_CMT:
                if (c == '\n') { state = CODE; out += '\n'; }
                break;
            case BLOCK_CMT:
                if (c == '*' && n == '/') { state = CODE; ++i; }
                break;
            case STR:
                if (c == '\\') { ++i; }
                else if (c == '"') { state = CODE; }
                break;
            case CHR:
                if (c == '\\') { ++i; }
                else if (c == '\'') { state = CODE; }
                break;
        }
    }
    return out;
}

bool isIdentStart(char c) { return std::isalpha(static_cast<unsigned char>(c)) || c == '_'; }
bool isIdentChar(char c) { return std::isalnum(static_cast<unsigned char>(c)) || c == '_'; }

std::string trim(const std::string& s) {
    std::size_t a = s.find_first_not_of(" \t\r\n");
    if (a == std::string::npos) return std::string();
    std::size_t b = s.find_last_not_of(" \t\r\n");
    return s.substr(a, b - a + 1);
}

const std::set<std::string>& disallowedNames() {
    static const std::set<std::string> kw = {
        "if", "for", "while", "switch", "catch", "return", "sizeof", "throw",
        "new", "delete", "static_cast", "dynamic_cast", "reinterpret_cast",
        "const_cast", "operator", "Solution", "noexcept", "constexpr",
        "decltype", "typeid", "alignof"
    };
    return kw;
}

bool matchAt(const std::string& s, std::size_t pos, const char* lit) {
    std::size_t k = 0;
    while (lit[k] != '\0') {
        if (pos + k >= s.size() || s[pos + k] != lit[k]) return false;
        ++k;
    }
    return true;
}

// Remove the trailing variable name from a parameter token, leaving the type.
// e.g. "vector<ListNode*>& lists" -> "vector<ListNode*>&", "int target" -> "int".
std::string stripVarName(const std::string& param) {
    std::string t = trim(param);
    // Drop a default value (everything after '=').
    std::size_t eq = t.find('=');
    if (eq != std::string::npos) t = t.substr(0, eq);
    int end = static_cast<int>(t.size());
    // Skip trailing non-identifier chars (e.g. '&', '*', spaces).
    while (end > 0 && !isIdentChar(t[static_cast<std::size_t>(end - 1)])) --end;
    if (end == 0) return trim(t);
    int start = end;
    while (start > 0 && isIdentChar(t[static_cast<std::size_t>(start - 1)])) --start;
    // [start, end) is the trailing identifier = variable name; drop it.
    std::string type = t.substr(0, static_cast<std::size_t>(start));
    return trim(type);
}

// Split a parameter list on top-level commas, ignoring commas inside '<...>'
// (template args like vector<vector<int>>). Default values are handled in
// stripVarName.
std::vector<std::string> splitParams(const std::string& paramsStr) {
    std::vector<std::string> out;
    std::string cur;
    int angle = 0;
    for (char c : paramsStr) {
        if (c == '<') ++angle;
        else if (c == '>') --angle;
        if (c == ',' && angle == 0) {
            std::string type = stripVarName(cur);
            if (!type.empty()) out.push_back(type);
            cur.clear();
        } else {
            cur += c;
        }
    }
    std::string type = stripVarName(cur);
    if (!type.empty()) out.push_back(type);
    return out;
}

}  // namespace

MethodSignature extractMethodSignature(const std::string& code) {
    const std::string s = stripCommentsAndStrings(code);

    // Locate "class Solution" (word-boundary safe).
    std::size_t afterSolution = std::string::npos;
    for (std::size_t i = 0; i + 5 <= s.size(); ++i) {
        if (s.compare(i, 5, "class") != 0) continue;
        bool leftOk = (i == 0) || !isIdentChar(s[i - 1]);
        if (!leftOk) continue;
        std::size_t j = i + 5;
        while (j < s.size() && std::isspace(static_cast<unsigned char>(s[j]))) ++j;
        if (j + 8 <= s.size() && s.compare(j, 8, "Solution") == 0
            && (j + 8 == s.size() || !isIdentChar(s[j + 8]))) {
            afterSolution = j + 8;
            break;
        }
    }
    if (afterSolution == std::string::npos) {
        throw std::runtime_error("No 'class Solution' found in user code");
    }

    std::size_t brace = s.find('{', afterSolution);
    if (brace == std::string::npos) {
        throw std::runtime_error("'class Solution' has no body");
    }

    // Scan the class body tracking brace depth. At depth 1 + public access,
    // the identifier immediately preceding the first '(' is the method name;
    // the text up to the matching ')' is the parameter list.
    int depth = 1;
    std::size_t i = brace + 1;
    bool inPublic = false;
    std::string lastIdent;

    while (i < s.size() && depth > 0) {
        char c = s[i];

        if (c == '{') { ++depth; ++i; lastIdent.clear(); continue; }
        if (c == '}') { --depth; ++i; lastIdent.clear(); continue; }

        if (depth == 1) {
            if (matchAt(s, i, "public:") && (i == 0 || !isIdentChar(s[i - 1]))) {
                inPublic = true; i += 7; lastIdent.clear(); continue;
            }
            if (matchAt(s, i, "private:") && (i == 0 || !isIdentChar(s[i - 1]))) {
                inPublic = false; i += 8; lastIdent.clear(); continue;
            }
            if (matchAt(s, i, "protected:") && (i == 0 || !isIdentChar(s[i - 1]))) {
                inPublic = false; i += 10; lastIdent.clear(); continue;
            }
            if (inPublic && isIdentStart(c)) {
                std::size_t start = i;
                while (i < s.size() && isIdentChar(s[i])) ++i;
                lastIdent = s.substr(start, i - start);
                continue;
            }
            if (inPublic && c == '(') {
                if (!lastIdent.empty() && disallowedNames().count(lastIdent) == 0) {
                    std::size_t parenEnd = s.find(')', i);
                    if (parenEnd == std::string::npos) {
                        throw std::runtime_error("unterminated parameter list");
                    }
                    std::string paramsStr = s.substr(i + 1, parenEnd - i - 1);
                    MethodSignature sig;
                    sig.name = lastIdent;
                    sig.paramTypes = splitParams(paramsStr);
                    return sig;
                }
                lastIdent.clear();
                ++i;
                continue;
            }
        }
        ++i;
    }

    throw std::runtime_error(
        "No public instance method found in 'class Solution'; "
        "declare 'class Solution { public: ReturnType methodName(...) ... }'.");
}

std::string extractMethodName(const std::string& code) {
    return extractMethodSignature(code).name;
}

}  // namespace ulticode
