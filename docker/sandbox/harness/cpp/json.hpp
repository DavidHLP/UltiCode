// UltiCode sandbox harness — minimal JSON (C++).
//
// Self-contained recursive-descent JSON parser + serializer. No external
// dependency (the sandbox base image ships no nlohmann/json), keeping the
// harness portable across image rebuilds.
//
// Mirrors the wire contract enforced by the Java harness (Harness.java
// parseJson/toJson/normalizeJson) so the backend's envelope parser
// (EnvelopeDTO / PerCaseResultDTO) accepts C++ output verbatim.
#ifndef ULTICODE_JSON_HPP
#define ULTICODE_JSON_HPP

#include <string>
#include <utility>
#include <vector>

namespace ulticode {

struct Json {
    enum class Type { Null, Bool, Number, String, Array, Object };

    Type type = Type::Null;
    bool boolVal = false;
    double numVal = 0.0;
    // Exact 64-bit integer payload, populated when the source token had no
    // fractional/exponent part. Keeps values beyond 2^53 precise across
    // parse → asInteger → serialize round-trips (e.g. 9007199254740993),
    // which a double-only representation would silently round.
    long long intVal = 0;
    bool isInt = false;
    std::string strVal;
    std::vector<Json> arr;
    std::vector<std::pair<std::string, Json>> obj;

    static Json makeNull() { Json j; j.type = Type::Null; return j; }
    static Json makeBool(bool b) { Json j; j.type = Type::Bool; j.boolVal = b; return j; }
    static Json makeNumber(double d) { Json j; j.type = Type::Number; j.numVal = d; j.isInt = false; return j; }
    static Json makeInt(long long i) {
        Json j; j.type = Type::Number; j.intVal = i; j.numVal = static_cast<double>(i); j.isInt = true; return j;
    }
    static Json makeString(std::string s) { Json j; j.type = Type::String; j.strVal = std::move(s); return j; }
    static Json makeArray() { Json j; j.type = Type::Array; return j; }
    static Json makeObject() { Json j; j.type = Type::Object; return j; }

    bool isNull() const { return type == Type::Null; }
    bool isBool() const { return type == Type::Bool; }
    bool isNumber() const { return type == Type::Number; }
    bool isString() const { return type == Type::String; }
    bool isArray() const { return type == Type::Array; }
    bool isObject() const { return type == Type::Object; }

    bool asBool() const { return boolVal; }
    double asNumber() const { return numVal; }
    long long asInteger() const { return isInt ? intVal : static_cast<long long>(numVal); }
    const std::string& asString() const { return strVal; }
    const std::vector<Json>& asArray() const { return arr; }

    // Bounds-checked array access; throws std::out_of_range.
    const Json& at(std::size_t i) const;
    // Object lookup; returns nullptr if absent.
    const Json* find(const std::string& key) const;
    bool has(const std::string& key) const { return find(key) != nullptr; }
    // Object lookup; throws std::out_of_range if absent.
    const Json& operator[](const std::string& key) const;
};

// Parse a JSON document. Throws std::runtime_error on malformed input.
Json parseJson(const std::string& text);

// Serialize a Json value to a compact JSON string.
std::string toJson(const Json& v);

// Parse then re-serialize — canonical form for equality comparison
// (aligned with Java Harness.normalizeJson, used for expected-vs-actual).
std::string normalizeJson(const std::string& text);

}  // namespace ulticode

#endif  // ULTICODE_JSON_HPP
