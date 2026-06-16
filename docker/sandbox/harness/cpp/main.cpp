// UltiCode sandbox harness — orchestrator entry point (C++).
//
// This binary (cpp-sandbox) is what the backend launches inside the sandbox
// container (see CppLanguageProfile.dockerCommand). It does NOT run user code
// directly — C++ has no reflection, so we:
//   1. read /job/input.json + /job/solution.cpp
//   2. extract the Solution method name (solution_parser) + arg type hints
//      from input.json
//   3. generate /tmp/runner.cpp with a typed call site
//      (deserialize args by type → sol.<method>(args) → serialize result)
//   4. g++-compile runner.cpp + json.cpp + serializer.cpp
//   5. fork+exec the runner; it runs each case in an isolated child process
//      (SEGV/ABRT in user code kills the child, not the harness) and writes
//      the D-form envelope to stdout
//   6. forward the runner's envelope verbatim
//
// Contract (matches Java Main / backend EnvelopeDTO): stdout = single JSON
// envelope even on error; exit 0 = well-formed envelope, exit 2 = harness
// panic (backend surfaces a Runtime Error for the whole batch).
#include "json.hpp"
#include "solution_parser.hpp"

#include <chrono>
#include <csignal>
#include <cstring>
#include <errno.h>
#include <fcntl.h>
#include <iostream>
#include <poll.h>
#include <stdexcept>
#include <stdlib.h>
#include <string>
#include <sys/wait.h>
#include <unistd.h>
#include <vector>

namespace {

using ulticode::Json;

constexpr long kCompileBudgetMs = 30000;   // g++ compile budget
constexpr long kRunnerBudgetMs = 60000;    // overall runner budget (per-case TLE handled inside)
constexpr std::size_t kCompileErrorExcerptBytes = 4096;

std::string readFile(const std::string& path) {
    FILE* f = std::fopen(path.c_str(), "rb");
    if (!f) throw std::runtime_error("cannot open " + path);
    std::string out;
    char buf[8192];
    std::size_t n;
    while ((n = std::fread(buf, 1, sizeof(buf), f)) > 0) out.append(buf, n);
    std::fclose(f);
    return out;
}

void writeFile(const std::string& path, const std::string& content) {
    FILE* f = std::fopen(path.c_str(), "wb");
    if (!f) throw std::runtime_error("cannot write " + path);
    std::size_t w = std::fwrite(content.data(), 1, content.size(), f);
    std::fclose(f);
    if (w != content.size()) throw std::runtime_error("short write to " + path);
}

std::string drainChild(int fd, pid_t pid, long timeoutMs, bool& timedOut, int& status) {
    std::string out;
    timedOut = false;
    status = 0;
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags >= 0) fcntl(fd, F_SETFL, flags | O_NONBLOCK);
    auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(timeoutMs);
    char buf[8192];
    for (;;) {
        auto rem = std::chrono::duration_cast<std::chrono::milliseconds>(
                       deadline - std::chrono::steady_clock::now()).count();
        if (rem <= 0) { timedOut = true; break; }
        int wait = static_cast<int>(rem < 100 ? rem : 100);
        struct pollfd pfd;
        pfd.fd = fd;
        pfd.events = POLLIN;
        pfd.revents = 0;
        int pr = poll(&pfd, 1, wait);
        if (pr < 0) {
            if (errno == EINTR) continue;
            break;
        }
        if (pr == 0) continue;  // timeout slice, loop re-checks deadline
        if (pfd.revents & (POLLIN | POLLHUP | POLLERR)) {
            ssize_t n = read(fd, buf, sizeof(buf));
            if (n > 0) {
                out.append(buf, static_cast<std::size_t>(n));
            } else if (n == 0) {
                break;  // EOF
            } else {
                if (errno == EAGAIN || errno == EWOULDBLOCK) {
                    // nothing right now
                } else {
                    break;
                }
            }
            if (pfd.revents & POLLHUP) {
                while ((n = read(fd, buf, sizeof(buf))) > 0) out.append(buf, static_cast<std::size_t>(n));
                break;
            }
        }
    }
    if (timedOut) kill(pid, SIGKILL);
    while (waitpid(pid, &status, 0) < 0 && errno == EINTR) {
    }
    return out;
}

// Fork+exec args; merge child stdout+stderr into capture; apply a soft kill
// timeout. Returns the child's exit code, or -2 on timeout, -1/-3 on errors.
int runProcess(const std::vector<std::string>& args, long timeoutMs, std::string& capture) {
    int pipefd[2];
    if (pipe(pipefd) != 0) return -1;
    pid_t pid = fork();
    if (pid < 0) {
        close(pipefd[0]);
        close(pipefd[1]);
        return -1;
    }
    if (pid == 0) {
        close(pipefd[0]);
        dup2(pipefd[1], STDOUT_FILENO);
        dup2(pipefd[1], STDERR_FILENO);
        close(pipefd[1]);
        std::vector<char*> argv;
        argv.reserve(args.size() + 1);
        for (const auto& a : args) argv.push_back(const_cast<char*>(a.c_str()));
        argv.push_back(nullptr);
        execvp(argv[0], argv.data());
        std::fprintf(stderr, "execvp failed: %s: %s\n", argv[0], std::strerror(errno));
        _exit(127);
    }
    close(pipefd[1]);
    bool timedOut = false;
    int status = 0;
    capture = drainChild(pipefd[0], pid, timeoutMs, timedOut, status);
    close(pipefd[0]);
    if (timedOut) return -2;
    return WIFEXITED(status) ? WEXITSTATUS(status) : -3;
}

// Map a C++ source parameter type → the deserializer function name. Substring
// match (order matters: more-specific patterns first). The type comes from
// parsing the user's solution.cpp signature, NOT from input.json — the
// frontend does not send type hints, so the source is the only reliable
// signal. Covers the DFORM type set (int/long/double/bool/string + arrays +
// ListNode/TreeNode and their vectors).
std::string parserForCppType(const std::string& t) {
    if (t.find("vector<vector<int") != std::string::npos) return "parse_int_2d_array";
    if (t.find("vector<int") != std::string::npos) return "parse_int_array";
    if (t.find("vector<long") != std::string::npos) return "parse_long_array";
    if (t.find("vector<string") != std::string::npos) return "parse_string_array";
    if (t.find("vector<ListNode") != std::string::npos) return "parse_listnode_array";
    if (t.find("vector<TreeNode") != std::string::npos) return "parse_treenode_array";
    if (t.find("ListNode") != std::string::npos) return "parse_listnode";
    if (t.find("TreeNode") != std::string::npos) return "parse_treenode";
    if (t.find("string") != std::string::npos) return "parse_string";
    if (t.find("double") != std::string::npos) return "parse_double";
    if (t.find("bool") != std::string::npos) return "parse_bool";
    if (t.find("long") != std::string::npos) return "parse_long";
    if (t.find("int") != std::string::npos) return "parse_int";
    throw std::runtime_error("unsupported parameter type: " + (t.empty() ? std::string("<empty>") : t));
}

// Per-case JSON escape helper for envelope assembly (independent of json.cpp so
// the orchestrator does not depend on linking it).
std::string esc(const std::string& in) {
    std::string out;
    out.reserve(in.size() + 2);
    for (unsigned char c : in) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (c < 0x20) {
                    char b[8];
                    std::snprintf(b, sizeof(b), "\\u%04x", c);
                    out += b;
                } else {
                    out.push_back(static_cast<char>(c));
                }
        }
    }
    return out;
}

std::string truncateErr(const std::string& e) {
    if (e.size() <= kCompileErrorExcerptBytes) return e;
    return e.substr(0, kCompileErrorExcerptBytes) + "\n... [truncated]";
}

// Build an envelope where every case is a Compile Error.
std::string emitCompileErrorEnvelope(const Json& input, const std::string& gxxErr) {
    std::string detail = truncateErr(gxxErr);
    std::string s = "{\"harness_version\":\"1.0\",\"language\":\"cpp\",\"exit_code\":0,"
                    "\"total_elapsed_ms\":0,\"results\":[";
    const Json* cases = input.find("cases");
    bool first = true;
    if (cases && cases->isArray()) {
        for (const auto& tc : cases->asArray()) {
            if (!first) s += ",";
            first = false;
            std::string cid = tc.has("case_id") ? tc["case_id"].asString() : std::string();
            std::string lbl = tc.has("label") ? tc["label"].asString() : cid;
            s += "{\"case_id\":\"" + esc(cid) + "\",\"label\":\"" + esc(lbl) + "\",";
            s += "\"elapsed_ms\":0,\"peak_memory_bytes\":0,\"status\":\"Compile Error\",";
            s += "\"result\":null,\"interrupted\":false,";
            s += "\"error\":{\"type\":\"compile\",\"message\":\"" + esc(detail) + "\",\"stack\":[]},";
            s += "\"user_stdout\":\"\",\"user_stderr\":\"\"}";
        }
    }
    s += "]}";
    return s;
}

// Build a panic envelope (exit_code 2 → backend surfaces a Runtime Error for
// the whole batch, aligned with Java harness panic semantics).
std::string emitPanicEnvelope(const std::string& detail) {
    std::string s = "{\"harness_version\":\"1.0\",\"language\":\"cpp\",\"exit_code\":2,"
                    "\"total_elapsed_ms\":0,\"results\":[],\"error\":\"" + esc(detail) + "\"}";
    return s;
}

// ─── generated runner source ────────────────────────────────────────────────
// The runner includes the user solution, a typed call site, and a fixed body
// (per-case fork isolation + envelope assembly). Only run_user_method is
// codegen'd; the body is constant.

const char* kRunnerBody = R"RUNNEREOF(
#include <chrono>
#include <csignal>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <poll.h>
#include <cstdio>
#include <string>
#include <sys/resource.h>
#include <sys/wait.h>
#include <unistd.h>
#include <vector>

using namespace ulticode;
using namespace std::chrono;

struct CaseResult {
    std::string caseId, label;
    long long elapsedMs = 0;
    long long elapsedUs = 0;
    long long cpuMs = 0;
    long long peakBytes = 0;
    std::string status, resultJson, errorMsg, errorType;
    bool interrupted = false;
};

static std::string runnerReadFile(const std::string& path) {
    FILE* f = fopen(path.c_str(), "rb");
    if (!f) throw std::runtime_error("runner: cannot open " + path);
    std::string out;
    char buf[8192];
    size_t n;
    while ((n = fread(buf, 1, sizeof(buf), f)) > 0) out.append(buf, n);
    fclose(f);
    return out;
}

static std::string drainCaseChild(int fd, pid_t pid, long timeoutMs, bool& timedOut, int& status) {
    std::string out;
    timedOut = false;
    status = 0;
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags >= 0) fcntl(fd, F_SETFL, flags | O_NONBLOCK);
    auto deadline = steady_clock::now() + milliseconds(timeoutMs);
    char buf[8192];
    for (;;) {
        auto rem = duration_cast<milliseconds>(deadline - steady_clock::now()).count();
        if (rem <= 0) { timedOut = true; break; }
        int wait = static_cast<int>(rem < 50 ? rem : 50);
        struct pollfd pfd; pfd.fd = fd; pfd.events = POLLIN; pfd.revents = 0;
        int pr = poll(&pfd, 1, wait);
        if (pr < 0) { if (errno == EINTR) continue; break; }
        if (pr == 0) continue;
        if (pfd.revents & (POLLIN | POLLHUP | POLLERR)) {
            ssize_t n = read(fd, buf, sizeof(buf));
            if (n > 0) out.append(buf, static_cast<size_t>(n));
            else if (n == 0) break;
            else { if (!(errno == EAGAIN || errno == EWOULDBLOCK)) break; }
            if (pfd.revents & POLLHUP) {
                while ((n = read(fd, buf, sizeof(buf))) > 0) out.append(buf, static_cast<size_t>(n));
                break;
            }
        }
    }
    if (timedOut) kill(pid, SIGKILL);
    while (waitpid(pid, &status, 0) < 0 && errno == EINTR) {}
    return out;
}

static std::string caseResultToJson(const CaseResult& r) {
    std::string s = "{\"case_id\":" + toJson(Json::makeString(r.caseId)) +
        ",\"label\":" + toJson(Json::makeString(r.label)) +
        ",\"elapsed_ms\":" + std::to_string(r.elapsedMs) +
        ",\"peak_memory_bytes\":" + std::to_string(r.peakBytes) +
        ",\"elapsed_us\":" + std::to_string(r.elapsedUs) +
        ",\"cpu_ms\":" + std::to_string(r.cpuMs) +
        ",\"status\":" + toJson(Json::makeString(r.status)) + ",";
    if (r.resultJson.empty()) {
        s += "\"result\":null,";
    } else {
        std::string norm;
        try { norm = normalizeJson(r.resultJson); } catch (...) { norm = "null"; }
        s += "\"result\":" + norm + ",";
    }
    s += "\"interrupted\":";
    s += r.interrupted ? "true" : "false";
    if (!r.errorType.empty()) {
        s += ",\"error\":{\"type\":" + toJson(Json::makeString(r.errorType));
        s += ",\"message\":" + toJson(Json::makeString(r.errorMsg));
        s += ",\"stack\":[]}";
    }
    s += ",\"user_stdout\":\"\",\"user_stderr\":\"\"}";
    return s;
}

static CaseResult runCase(const Json& tc, long timeoutMs, long long memoryLimitBytes) {
    CaseResult r;
    r.caseId = tc.has("case_id") ? tc["case_id"].asString() : std::string();
    r.label = tc.has("label") ? tc["label"].asString() : r.caseId;

    int pipefd[2];
    if (pipe(pipefd) != 0) {
        r.status = "Runtime Error";
        r.errorMsg = "pipe() failed";
        return r;
    }
    auto start = steady_clock::now();
    pid_t pid = fork();
    if (pid < 0) {
        // fork() failed under PID/memory pressure — infrastructure error,
        // NOT a verdict on user code. Report it explicitly instead of letting
        // the parent's invalid-pid path surface it as a Wrong Answer.
        close(pipefd[0]);
        close(pipefd[1]);
        r.status = "Runtime Error";
        r.errorType = "infrastructure";
        r.errorMsg = "fork() failed";
        return r;
    }
    if (pid == 0) {
        // child: run the user method in isolation. A SEGV/ABRT/stack-overflow
        // here kills only this child; the parent reports Runtime Error.
        close(pipefd[0]);
        // Redirect user-facing stdout/stderr to /dev/null so user cout/cerr
        // (debug prints, exception traces) cannot contaminate the result
        // channel. The serialized result is written to pipefd[1] explicitly
        // below — it never goes through STDOUT, so user output stays separate.
        int devnull = open("/dev/null", O_WRONLY);
        if (devnull >= 0) {
            dup2(devnull, STDOUT_FILENO);
            dup2(devnull, STDERR_FILENO);
            close(devnull);
        }
        try {
            Solution sol;
            std::string out = run_user_method(sol, tc);
            // ADR-002 §8: report this child's peak RSS + CPU time back to the
            // parent via a two-line header preceding the result JSON.
            struct rusage ru;
            long long peakKb = (getrusage(RUSAGE_SELF, &ru) == 0) ? ru.ru_maxrss : 0;
            long long cpuMs = (long long)((ru.ru_utime.tv_sec * 1000.0 + ru.ru_utime.tv_usec / 1000.0)
                                          + (ru.ru_stime.tv_sec * 1000.0 + ru.ru_stime.tv_usec / 1000.0));
            std::string header = std::to_string(peakKb) + "\n" + std::to_string(cpuMs) + "\n";
            ssize_t w1 = write(pipefd[1], header.data(), header.size());
            ssize_t w2 = write(pipefd[1], out.data(), out.size());
            (void)w1; (void)w2;
            close(pipefd[1]);
            _Exit(0);
        } catch (const std::exception& e) {
            std::string m = std::string("EXC:") + e.what();
            ssize_t w = write(pipefd[1], m.data(), m.size());
            (void)w;
            close(pipefd[1]);
            _Exit(1);
        } catch (...) {
            const char* m = "EXC:unknown exception";
            ssize_t w = write(pipefd[1], m, std::strlen(m));
            (void)w;
            close(pipefd[1]);
            _Exit(2);
        }
    }
    close(pipefd[1]);
    bool timedOut = false;
    int status = 0;
    std::string childOut = drainCaseChild(pipefd[0], pid, timeoutMs, timedOut, status);
    close(pipefd[0]);
    r.elapsedUs = duration_cast<microseconds>(steady_clock::now() - start).count();
    r.elapsedMs = r.elapsedUs / 1000;

    if (timedOut) {
        r.status = "Time Limit Exceeded";
        r.interrupted = true;
        return r;
    }
    if (WIFSIGNALED(status)) {
        r.status = "Runtime Error";
        r.errorType = "signal";
        r.errorMsg = std::string("terminated by signal ") + strsignal(WTERMSIG(status));
        return r;
    }
    if (childOut.rfind("EXC:", 0) == 0) {
        r.status = "Runtime Error";
        r.errorMsg = childOut.substr(4);
        return r;
    }
    // ADR-002 §8: child wrote "peakKb\ncpuMs\n<resultJson>".
    size_t nl1 = childOut.find('\n');
    size_t nl2 = (nl1 != std::string::npos) ? childOut.find('\n', nl1 + 1) : std::string::npos;
    std::string resultJson = childOut;
    if (nl1 != std::string::npos && nl2 != std::string::npos) {
        try { r.peakBytes = std::stoll(childOut.substr(0, nl1)) * 1024; } catch (...) {}
        try { r.cpuMs = std::stoll(childOut.substr(nl1 + 1, nl2 - nl1 - 1)); } catch (...) {}
        resultJson = childOut.substr(nl2 + 1);
    }
    r.resultJson = resultJson;
    // ADR-002 §8 (P0-2): over the per-run memory ceiling → MLE.
    if (memoryLimitBytes > 0 && r.peakBytes > memoryLimitBytes) {
        r.status = "Memory Limit Exceeded";
        return r;
    }
    std::string expected = tc.has("expected_output") ? tc["expected_output"].asString() : std::string();
    bool passed = false;
    if (!expected.empty()) {
        try {
            passed = (normalizeJson(expected) == normalizeJson(resultJson));
        } catch (...) {
            passed = false;
        }
    }
    r.status = passed ? "Accepted" : "Wrong Answer";
    return r;
}

int runner_main(int argc, char** argv) {
    std::string inputPath = (argc > 1) ? argv[1] : "/job/input.json";
    Json input;
    try {
        input = parseJson(runnerReadFile(inputPath));
    } catch (const std::exception& e) {
        std::cout << "{\"harness_version\":\"1.0\",\"language\":\"cpp\",\"exit_code\":2,"
                     "\"total_elapsed_ms\":0,\"results\":[]}";
        std::cerr << "runner: cannot read input: " << e.what() << "\n";
        return 2;
    }
    long timeoutMs = input.has("per_case_timeout_ms")
                         ? static_cast<long>(input["per_case_timeout_ms"].asNumber())
                         : 1000;
    long long memoryLimitBytes = input.has("memory_limit_bytes")
                         ? static_cast<long long>(input["memory_limit_bytes"].asNumber())
                         : 0;
    const Json* cases = input.find("cases");
    std::vector<CaseResult> results;
    auto totalStart = steady_clock::now();
    if (cases && cases->isArray()) {
        for (const auto& tc : cases->asArray()) results.push_back(runCase(tc, timeoutMs, memoryLimitBytes));
    }
    auto totalMs = duration_cast<milliseconds>(steady_clock::now() - totalStart).count();

    std::string env = "{\"harness_version\":\"1.0\",\"language\":\"cpp\",\"exit_code\":0,"
                      "\"total_elapsed_ms\":";
    env += std::to_string(totalMs);
    env += ",\"results\":[";
    for (size_t i = 0; i < results.size(); ++i) {
        if (i) env += ",";
        env += caseResultToJson(results[i]);
    }
    env += "]}";
    std::cout << env << std::flush;
    return 0;
}

int main(int argc, char** argv) {
    return runner_main(argc, argv);
}
)RUNNEREOF";

std::string generateRunner(const std::string& methodName, const std::vector<std::string>& types,
                           const std::string& solutionPath) {
    std::string s;
    s += "#include \"oj_types.hpp\"\n";
    s += "#include <bits/stdc++.h>\n";
    s += "#include \"json.hpp\"\n";
    s += "#include \"serializer.hpp\"\n";
    s += "#include <stdexcept>\n";
    s += "#include <string>\n";
    s += "#include <iostream>\n";
    s += "using namespace std;\n";
    s += "using namespace ulticode;\n";
    s += "#include \"" + solutionPath + "\"\n";
    s += "\n";
    // generated typed call site
    s += "static std::string run_user_method(Solution& sol, const ulticode::Json& caseObj) {\n";
    s += "    const ulticode::Json* inputs = caseObj.find(\"inputs\");\n";
    s += "    if (!inputs || !inputs->isArray()) throw std::runtime_error(\"case has no inputs\");\n";
    for (std::size_t i = 0; i < types.size(); ++i) {
        std::string parser = parserForCppType(types[i]);
        s += "    auto a" + std::to_string(i) + " = ulticode::" + parser +
             "(inputs->at(" + std::to_string(i) + ")[\"value\"].asString());\n";
    }
    s += "    auto result = sol." + methodName + "(";
    for (std::size_t i = 0; i < types.size(); ++i) {
        if (i) s += ", ";
        s += "a" + std::to_string(i);
    }
    s += ");\n";
    s += "    return ulticode::serialize(result);\n";
    s += "}\n\n";
    s += kRunnerBody;
    return s;
}

}  // namespace

int main(int argc, char** argv) {
    std::string inputPath = (argc > 1) ? argv[1] : "/job/input.json";
    const char* hr = std::getenv("ULTICODE_HARNESS_ROOT");
    std::string harnessRoot = hr ? hr : "/opt/harness/cpp";
    const char* sp = std::getenv("ULTICODE_SOLUTION_PATH");
    std::string solutionPath = sp ? sp : "/job/solution.cpp";

    Json input;
    std::string solution;
    try {
        input = ulticode::parseJson(readFile(inputPath));
        solution = readFile(solutionPath);
    } catch (const std::exception& e) {
        std::cerr << "cpp-sandbox: init failed: " << e.what() << "\n";
        return 2;  // empty stdout → backend harness-panic → Runtime Error
    }

    std::string methodName;
    std::vector<std::string> types;
    try {
        // Signature (name + C++ param types) comes from the user's source,
        // not input.json — the frontend omits type hints, so the source is
        // the only reliable signal for C++ (no reflection).
        ulticode::MethodSignature sig = ulticode::extractMethodSignature(solution);
        methodName = sig.name;
        types = sig.paramTypes;
    } catch (const std::exception& e) {
        std::cout << emitPanicEnvelope(std::string("method resolution failed: ") + e.what());
        return 0;
    }

    std::string runnerSrc;
    try {
        runnerSrc = generateRunner(methodName, types, solutionPath);
    } catch (const std::exception& e) {
        std::cout << emitPanicEnvelope(std::string("runner generation failed: ") + e.what());
        return 0;
    }

    try {
        writeFile("/tmp/runner.cpp", runnerSrc);
    } catch (const std::exception& e) {
        std::cout << emitPanicEnvelope(std::string("cannot write runner.cpp: ") + e.what());
        return 0;
    }

    // Compile the generated runner against the harness library sources.
    std::string ccErr;
    std::vector<std::string> cc = {
        "g++", "-std=c++17", "-O2", "-w",
        "-I" + harnessRoot,
        "/tmp/runner.cpp",
        harnessRoot + "/json.cpp",
        harnessRoot + "/serializer.cpp",
        "-o", "/tmp/runner"};
    int ccRc = runProcess(cc, kCompileBudgetMs, ccErr);
    if (ccRc != 0) {
        std::cout << emitCompileErrorEnvelope(input, ccErr);
        return 0;
    }

    // Run the runner; it emits the D-form envelope on stdout.
    std::string runnerOut;
    std::vector<std::string> runArgs = {"/tmp/runner", inputPath};
    int rc = runProcess(runArgs, kRunnerBudgetMs, runnerOut);
    if (runnerOut.empty() || runnerOut[0] != '{') {
        std::cout << emitPanicEnvelope("runner produced no envelope (exit " + std::to_string(rc) + ")");
        return 0;
    }
    std::cout << runnerOut;
    return 0;
}
