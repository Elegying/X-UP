#pragma once
#include <memory>
#include <string>
class TranslationEngine {
 struct Impl;std::unique_ptr<Impl> impl;
public:
 explicit TranslationEngine(const std::string &path);
 ~TranslationEngine();
 std::string translate(const std::string &prompt);
};
