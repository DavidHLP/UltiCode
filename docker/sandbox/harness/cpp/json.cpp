// UltiCode sandbox harness — minimal JSON implementation (C++).
#include "json.hpp"

#include <cmath>
#include <cstdio>
#include <stdexcept>

namespace ulticode {

const Json& Json::at(std::size_t i) const {
    if (type != Type::Array || i >= arr.size()) {
        throw std::out_of_range("Json::at: index out of range");
    }
    return arr[i];
}

const Json* Json::find(const std::string& key) const {
    if (type != Type::Object) return nullptr;
    for (const auto& kv : obj) {
        if (kv.first == key) return &kv.second;
    }
    return nullptr;
}

const Json& Json::operator[](const std::string& key) const {
    const Json* p = find(key);
    if (p == nullptr) throw std::out_of_range("Json::operator[]: key absent: " + key);
    return *p;
}

// ─── parser ────────────────────────────────────────────────────────────────
namespace {

struct Parser {
    const std::string& s;
    std::size_t pos = 0;
    explicit Parser(const std::string& src) : s(src) {}

    [[noreturn]] void fail(const std::string& msg) const {
        throw std::runtime_error("JSON parse error at " + std::to_string(pos) + ": " + msg);
    }

    void skipWs() {
        while (pos < s.size()) {
            char c = s[pos];
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                ++pos;
            } else {
                break;
            }
        }
    }

    Json parseValue() {
        skipWs();
        if (pos >= s.size()) fail("unexpected end of input");
        char c = s[pos];
        if (c == '{') return parseObject();
        if (c == '[') return parseArray();
        if (c == '"') return parseString();
        if (c == 't' || c == 'f') return parseBool();
        if (c == 'n') return parseNull();
        if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();
        fail(std::string("unexpected character '") + c + "'");
    }

    Json parseObject() {
        Json j = Json::makeObject();
        ++pos;  // consume '{'
        skipWs();
        if (pos < s.size() && s[pos] == '}') { ++pos; return j; }
        while (true) {
            skipWs();
            if (pos >= s.size() || s[pos] != '"') fail("expected string key");
            Json key = parseString();
            skipWs();
            if (pos >= s.size() || s[pos] != ':') fail("expected ':' after key");
            ++pos;
            Json val = parseValue();
            j.obj.emplace_back(key.strVal, std::move(val));
            skipWs();
            if (pos >= s.size()) fail("unterminated object");
            char nc = s[pos];
            if (nc == ',') { ++pos; continue; }
            if (nc == '}') { ++pos; return j; }
            fail(std::string("expected ',' or '}' in object, got '") + nc + "'");
        }
    }

    Json parseArray() {
        Json j = Json::makeArray();
        ++pos;  // consume '['
        skipWs();
        if (pos < s.size() && s[pos] == ']') { ++pos; return j; }
        while (true) {
            Json val = parseValue();
            j.arr.push_back(std::move(val));
            skipWs();
            if (pos >= s.size()) fail("unterminated array");
            char nc = s[pos];
            if (nc == ',') { ++pos; continue; }
            if (nc == ']') { ++pos; return j; }
            fail(std::string("expected ',' or ']' in array, got '") + nc + "'");
        }
    }

    Json parseString() {
        Json j = Json::makeString("");
        ++pos;  // consume opening quote
        std::string out;
        while (pos < s.size()) {
            char c = s[pos++];
            if (c == '"') { j.strVal = std::move(out); return j; }
            if (c == '\\') {
                if (pos >= s.size()) fail("trailing escape");
                char e = s[pos++];
                switch (e) {
                    case '"': out.push_back('"'); break;
                    case '\\': out.push_back('\\'); break;
                    case '/': out.push_back('/'); break;
                    case 'b': out.push_back('\b'); break;
                    case 'f': out.push_back('\f'); break;
                    case 'n': out.push_back('\n'); break;
                    case 'r': out.push_back('\r'); break;
                    case 't': out.push_back('\t'); break;
                    case 'u': {
                        if (pos + 4 > s.size()) fail("bad \\u escape");
                        unsigned cp = 0;
                        for (int k = 0; k < 4; ++k) {
                            char h = s[pos++];
                            cp <<= 4;
                            if (h >= '0' && h <= '9') cp |= (h - '0');
                            else if (h >= 'a' && h <= 'f') cp |= (h - 'a' + 10);
                            else if (h >= 'A' && h <= 'F') cp |= (h - 'A' + 10);
                            else fail("bad hex digit in \\u escape");
                        }
                        // UTF-8 encode (BMP only; surrogate pairs not merged for MVP).
                        if (cp < 0x80) {
                            out.push_back(static_cast<char>(cp));
                        } else if (cp < 0x800) {
                            out.push_back(static_cast<char>(0xC0 | (cp >> 6)));
                            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
                        } else {
                            out.push_back(static_cast<char>(0xE0 | (cp >> 12)));
                            out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
                            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
                        }
                        break;
                    }
                    default: fail(std::string("bad escape '\\") + e + "'");
                }
            } else {
                out.push_back(c);
            }
        }
        fail("unterminated string");
    }

    Json parseNumber() {
        std::size_t start = pos;
        if (pos < s.size() && s[pos] == '-') ++pos;
        while (pos < s.size() && (s[pos] >= '0' && s[pos] <= '9')) ++pos;
        bool hasFrac = false;
        if (pos < s.size() && s[pos] == '.') {
            hasFrac = true;
            ++pos;
            while (pos < s.size() && (s[pos] >= '0' && s[pos] <= '9')) ++pos;
        }
        if (pos < s.size() && (s[pos] == 'e' || s[pos] == 'E')) {
            hasFrac = true;
            ++pos;
            if (pos < s.size() && (s[pos] == '+' || s[pos] == '-')) ++pos;
            while (pos < s.size() && (s[pos] >= '0' && s[pos] <= '9')) ++pos;
        }
        std::string num = s.substr(start, pos - start);
        // Prefer an exact 64-bit integer so values beyond 2^53 survive the
        // parse → asInteger → serialize round-trip.
        if (!hasFrac) {
            try { return Json::makeInt(std::stoll(num)); }
            catch (...) { /* fall through to double */ }
        }
        try {
            return Json::makeNumber(std::stod(num));
        } catch (...) {
            fail("bad number: " + num);
        }
    }

    Json parseBool() {
        if (s.compare(pos, 4, "true") == 0) { pos += 4; return Json::makeBool(true); }
        if (s.compare(pos, 5, "false") == 0) { pos += 5; return Json::makeBool(false); }
        fail("invalid literal");
    }

    Json parseNull() {
        if (s.compare(pos, 4, "null") == 0) { pos += 4; return Json::makeNull(); }
        fail("invalid literal");
    }
};

}  // namespace

Json parseJson(const std::string& text) {
    Parser p(text);
    Json v = p.parseValue();
    p.skipWs();
    if (p.pos != text.size()) {
        // Trailing garbage — fail loudly (aligned with strict backend parse).
        p.fail("trailing characters after JSON value");
    }
    return v;
}

// ─── serializer ────────────────────────────────────────────────────────────
namespace {

void writeEscaped(std::string& out, const std::string& in) {
    for (unsigned char c : in) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\b': out += "\\b"; break;
            case '\f': out += "\\f"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (c < 0x20) {
                    char buf[8];
                    std::snprintf(buf, sizeof(buf), "\\u%04x", c);
                    out += buf;
                } else {
                    out.push_back(static_cast<char>(c));
                }
        }
    }
}

void writeNumber(std::string& out, const Json& v) {
    if (v.isInt) {
        // Exact integer payload — emit verbatim, no double rounding.
        char buf[32];
        std::snprintf(buf, sizeof(buf), "%lld", v.intVal);
        out += buf;
        return;
    }
    double d = v.numVal;
    if (!std::isfinite(d)) {
        throw std::runtime_error("Cannot serialize non-finite number");
    }
    // Integer-valued doubles render without a decimal part so wire output
    // matches LeetCode-style expected_output (e.g. [1,2,3] not [1.0,2.0,3.0]).
    if (d == std::floor(d) && std::fabs(d) < 1e15) {
        char buf[32];
        std::snprintf(buf, sizeof(buf), "%lld", static_cast<long long>(d));
        out += buf;
    } else {
        char buf[64];
        std::snprintf(buf, sizeof(buf), "%.17g", d);
        out += buf;
    }
}

void writeValue(std::string& out, const Json& v) {
    switch (v.type) {
        case Json::Type::Null: out += "null"; break;
        case Json::Type::Bool: out += v.boolVal ? "true" : "false"; break;
        case Json::Type::Number: writeNumber(out, v); break;
        case Json::Type::String: {
            out += '"';
            writeEscaped(out, v.strVal);
            out += '"';
            break;
        }
        case Json::Type::Array: {
            out += '[';
            for (std::size_t i = 0; i < v.arr.size(); ++i) {
                if (i) out += ',';
                writeValue(out, v.arr[i]);
            }
            out += ']';
            break;
        }
        case Json::Type::Object: {
            out += '{';
            for (std::size_t i = 0; i < v.obj.size(); ++i) {
                if (i) out += ',';
                out += '"';
                writeEscaped(out, v.obj[i].first);
                out += "\":";
                writeValue(out, v.obj[i].second);
            }
            out += '}';
            break;
        }
    }
}

}  // namespace

std::string toJson(const Json& v) {
    std::string out;
    writeValue(out, v);
    return out;
}

std::string normalizeJson(const std::string& text) {
    return toJson(parseJson(text));
}

}  // namespace ulticode
