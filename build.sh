#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

fail() { printf '%s\n' "$*" >&2; exit 1; }

[[ "$(pwd -P)" != *[[:space:]]* ]] || fail '当前 Android 原生构建工具要求源码完整路径不含空白字符，请将项目移到不含空格的目录。'

default_sdk="$HOME/Library/Android/sdk"
if [[ "$(uname -s)" != Darwin ]]; then default_sdk="$HOME/Android/Sdk"; fi
export ANDROID_HOME="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$default_sdk}}"
build_tools="$ANDROID_HOME/build-tools/${ANDROID_BUILD_TOOLS_VERSION:-36.0.0}"
export SIGNING_KEYSTORE="${SIGNING_KEYSTORE:-../../work/xlowerseek-signing.p12}"

[[ -f "$SIGNING_KEYSTORE" ]] || fail '找不到发布签名文件，请先恢复原私钥，并设置 SIGNING_KEYSTORE 为文件路径。'
for tool in python3 java javac git curl tar patch shasum; do
    command -v "$tool" >/dev/null || fail "缺少构建工具：$tool"
done
for tool in aapt apksigner; do
    [[ -x "$build_tools/$tool" ]] || fail "缺少 Android 构建工具：$build_tools/$tool"
done

./scripts/prepare-native.sh
python3 scripts/prepare-opus-native.py
./gradlew assembleRelease --console=plain "$@"

apk='build/outputs/apk/release/X-UP-release.apk'
signature_report="$("$build_tools/apksigner" verify --verbose --print-certs "$apk")"
signer="$(printf '%s\n' "$signature_report" | sed -n 's/^Signer #[0-9][0-9]* certificate SHA-256 digest: //p')"
if [[ -n "${EXPECTED_SIGNER_SHA256:-}" ]]; then
    expected="$(printf '%s' "$EXPECTED_SIGNER_SHA256" | tr '[:upper:]' '[:lower:]' | tr -d ':')"
    [[ "$signer" == "$expected" ]] || fail '安装包签名与预期发布证书不一致，已停止导出。'
fi

badging="$("$build_tools/aapt" dump badging "$apk")"
version="$(printf '%s\n' "$badging" | sed -n "s/^package: .*versionName='\([^']*\)'.*/\1/p")"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-(alpha|beta|rc)[0-9]+)?$ ]] || fail "安装包版本不符合更新器规则：$version"
output_dir="${RELEASE_OUTPUT_DIR:-build/releases/v$version}"
mkdir -p "$output_dir"
output_apk="X-UP-$version.apk"
cp "$apk" "$output_dir/$output_apk"
(cd "$output_dir" && shasum -a 256 "$output_apk" > "X-UP-$version-SHA256SUMS.txt")
printf '%s\n' "$signature_report"
printf '\n已生成：%s/%s\n校验清单：%s/X-UP-%s-SHA256SUMS.txt\n' "$output_dir" "$output_apk" "$output_dir" "$version"
