#pragma once
#include <memory>
#include <cstdint>
#include <string>
class TranslationEngine {
 struct Impl;std::unique_ptr<Impl> impl;
public:
 explicit TranslationEngine(const std::string &path);
 ~TranslationEngine();
 uint64_t ticket() const;
 void cancel();
 std::string translate(const std::string &prompt);
 std::string translate(const std::string &prompt,uint64_t ticket);
};
