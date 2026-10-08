#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
revision=1e411d8f5a1e23525fa3265dfb4bd76265465397
expected=e7b9788ae7d41bf388ce4f89d38d372ed17aab2c2da1169f7620acb8419c8e51
if [[ -f native/vendor/llama.cpp/.xup-source-ready ]]; then exit 0; fi
if [[ -d native/vendor/llama.cpp ]]; then
  echo 'Existing vendor directory preserved. Verify or move it before preparing dependencies.' >&2
  exit 1
fi
stage=$(mktemp -d)
trap 'rm -rf "$stage"' EXIT
curl --fail --location --retry 3 "https://codeload.github.com/ggml-org/llama.cpp/tar.gz/$revision" -o "$stage/source.tar.gz"
actual=$(shasum -a 256 "$stage/source.tar.gz" | cut -d ' ' -f 1)
[[ "$actual" == "$expected" ]] || { echo 'Native source checksum mismatch' >&2; exit 1; }
mkdir -p "$stage/source"
tar -xzf "$stage/source.tar.gz" --strip-components=1 -C "$stage/source"
patch -d "$stage/source" -p1 < native/patches/hy-mt-compat.patch
printf '%s\n' "$revision" > "$stage/source/.xup-source-ready"
mkdir -p native/vendor
mv "$stage/source" native/vendor/llama.cpp
