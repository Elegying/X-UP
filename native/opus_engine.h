#pragma once
#include <memory>
#include <string>
#include <cstdint>
class OpusEngine {
 struct Impl;std::unique_ptr<Impl> impl;
public:
 explicit OpusEngine(const std::string &path);
 ~OpusEngine();
 uint64_t ticket() const;
 void cancel();
 std::string translate(const std::string &text,bool japanese,uint64_t ticket);
};
