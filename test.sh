#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
TEMP_TEST="$(mktemp -d)"
trap 'rm -rf "$TEMP_TEST"' EXIT
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
CP="libs/libxposed-api-102.jar:$SDK/platforms/android-36/android.jar"
javac -classpath "$CP" -d "$TEMP_TEST" src/io/github/jared/xlowerseek/{SeekStepAdapter,ContinuationTapHook,GesturePolicy,DownloadPolicy,SwipePolicy,TranslationGate,PostTranslationPolicy,TextTranslationPlan,WeakIdentityMap,Mp4Transfer,DownloadProgress,Hooks,HookCallback,Reflect}.java tests/*.java
java -cp "$TEMP_TEST" GesturePolicyTest
java -cp "$TEMP_TEST" DownloadPolicyTest
java -cp "$TEMP_TEST" io.github.jared.xlowerseek.SwipePolicyTest
java -cp "$TEMP_TEST" io.github.jared.xlowerseek.TranslationGateTest
java -cp "$TEMP_TEST" io.github.jared.xlowerseek.PostTranslationPolicyTest
java -cp "$TEMP_TEST" io.github.jared.xlowerseek.TextTranslationPlanTest
java -cp "$TEMP_TEST" io.github.jared.xlowerseek.WeakIdentityMapTest
java -cp "$TEMP_TEST" io.github.jared.xlowerseek.Mp4TransferTest
java -cp "$TEMP_TEST" io.github.jared.xlowerseek.DownloadProgressTest
java -cp "$TEMP_TEST:$CP" io.github.jared.xlowerseek.SeekStepAdapterTest
java -cp "$TEMP_TEST:$CP" io.github.jared.xlowerseek.ContinuationTapHookTest
java -cp "$TEMP_TEST:$CP" io.github.jared.xlowerseek.HookAdapterTest
java -cp "$TEMP_TEST:$CP" io.github.jared.xlowerseek.ReflectTest

mkdir -p "$TEMP_TEST/client"
javac -encoding UTF-8 -d "$TEMP_TEST/client" $(find tests/client-stubs -name '*.java') src/io/github/jared/xlowerseek/{LocalTranslationClient,ResultReceiverTransport,PostTranslationPolicy,Feature}.java tests/client/*.java
java -cp "$TEMP_TEST/client" io.github.jared.xlowerseek.LocalTranslationClientTest

mkdir -p "$TEMP_TEST/service"
javac -encoding UTF-8 -d "$TEMP_TEST/service" $(find tests/client-stubs/android -name '*.java') $(find tests/service-stubs -name '*.java') src/io/github/jared/xlowerseek/{LocalTranslationService,LocalEngine,OfflineEngine,TranslationCancellation,TranslationOutput,Feature}.java tests/service/*.java
java -cp "$TEMP_TEST/service" io.github.jared.xlowerseek.LocalTranslationServiceTest

mkdir -p "$TEMP_TEST/gestures"
javac -encoding UTF-8 -classpath "$CP" -d "$TEMP_TEST/gestures" $(find tests/gesture-stubs -name '*.java') tests/client-stubs/android/os/{Handler,Looper,SystemClock}.java src/io/github/jared/xlowerseek/{FullScreenGestureHook,GestureArea,SwipePolicy,Reflect,HookCallback}.java tests/gestures/*.java
java -cp "$TEMP_TEST/gestures:$CP" io.github.jared.xlowerseek.FullScreenGestureTest

mkdir -p "$TEMP_TEST/video"
javac -encoding UTF-8 -d "$TEMP_TEST/video" $(find tests/client-stubs/android -name '*.java') src/io/github/jared/xlowerseek/{VideoDownloadClient,VideoDownloadState,DownloadPolicy}.java tests/video/*.java
java -cp "$TEMP_TEST/video" io.github.jared.xlowerseek.VideoDownloadClientTest

mkdir -p "$TEMP_TEST/download"
javac -encoding UTF-8 -classpath "$CP" -d "$TEMP_TEST/download" $(find tests/download-stubs -name '*.java') tests/client-stubs/android/os/{Bundle,Handler,Looper,SystemClock,ResultReceiver,Parcel}.java tests/client-stubs/android/content/ContentProviderClient.java src/io/github/jared/xlowerseek/{DownloadActivity,DownloadService,LocalTranslationProvider,VideoDownloads,VideoDownloadState,DownloadPolicy,DownloadProgress,Mp4Transfer}.java tests/download/*.java
java -cp "$TEMP_TEST/download:$CP" io.github.jared.xlowerseek.DownloadIntegrationTest

mkdir -p "$TEMP_TEST/mlkit"
javac -encoding UTF-8 -d "$TEMP_TEST/mlkit" $(find tests/client-stubs/android -name '*.java') $(find tests/mlkit-stubs -name '*.java') src/io/github/jared/xlowerseek/MlKitModels.java tests/mlkit/*.java
java -cp "$TEMP_TEST/mlkit" io.github.jared.xlowerseek.MlKitModelsTest

mkdir -p "$TEMP_TEST/settings"
javac -encoding UTF-8 -d "$TEMP_TEST/settings" $(find tests/client-stubs/android -name '*.java' ! -path '*/Context.java') $(find tests/settings-stubs -name '*.java') tests/service-stubs/android/os/Process.java tests/service-stubs/io/github/jared/xlowerseek/LocalModels.java src/io/github/jared/xlowerseek/{SettingsStore,RemoteSettings,LocalTranslationService,LocalEngine,OfflineEngine,TranslationCancellation,TranslationOutput,TranslationMode,Feature}.java tests/settings/*.java
java -cp "$TEMP_TEST/settings" io.github.jared.xlowerseek.SettingsTranslationTest offline
java -cp "$TEMP_TEST/settings" io.github.jared.xlowerseek.SettingsTranslationTest connected
java -cp "$TEMP_TEST/settings" io.github.jared.xlowerseek.SettingsSyncTest
for mode in fresh invalid local-tencent local-opus local-mlkit remote-tencent remote-opus remote-mlkit; do
 java -cp "$TEMP_TEST/settings" io.github.jared.xlowerseek.SettingsEngineDefaultsTest "$mode"
done
