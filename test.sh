#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
TEMP_TEST="$(mktemp -d)"
trap 'rm -rf "$TEMP_TEST"' EXIT
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
CP="libs/libxposed-api-102.jar:$SDK/platforms/android-36/android.jar"
javac -classpath "$CP" -d "$TEMP_TEST" src/io/github/jared/xlowerseek/{GesturePolicy,DownloadPolicy,SwipePolicy,TranslationGate,PostTranslationPolicy,TextTranslationPlan,WeakIdentityMap,Mp4Transfer,DownloadProgress,Hooks,HookCallback,Reflect}.java tests/*.java
java -cp "$TEMP_TEST" GesturePolicyTest
java -cp "$TEMP_TEST" DownloadPolicyTest
java -cp "$TEMP_TEST" io.github.jared.xlowerseek.SwipePolicyTest
java -cp "$TEMP_TEST" io.github.jared.xlowerseek.TranslationGateTest
java -cp "$TEMP_TEST" io.github.jared.xlowerseek.PostTranslationPolicyTest
java -cp "$TEMP_TEST" io.github.jared.xlowerseek.TextTranslationPlanTest
java -cp "$TEMP_TEST" io.github.jared.xlowerseek.WeakIdentityMapTest
java -cp "$TEMP_TEST" io.github.jared.xlowerseek.Mp4TransferTest
java -cp "$TEMP_TEST" io.github.jared.xlowerseek.DownloadProgressTest
java -cp "$TEMP_TEST:$CP" io.github.jared.xlowerseek.HookAdapterTest
java -cp "$TEMP_TEST:$CP" io.github.jared.xlowerseek.ReflectTest

mkdir -p "$TEMP_TEST/client"
javac -encoding UTF-8 -d "$TEMP_TEST/client" $(find tests/client-stubs -name '*.java') src/io/github/jared/xlowerseek/{LocalTranslationClient,ResultReceiverTransport,PostTranslationPolicy,Feature}.java tests/client/*.java
java -cp "$TEMP_TEST/client" io.github.jared.xlowerseek.LocalTranslationClientTest
