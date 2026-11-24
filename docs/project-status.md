# X-UP 项目现状与开发交接

更新于 2026-11-23。X-UP 是面向 Android 版 X 的 LSPosed 模块，提供视频手势与保存、本地中文翻译、最新评论和时间线推广内容过滤。项目支持用户保留 X 自动更新，遇到不兼容结构时优先保留原有行为和其余功能。

## 当前基线

| 项目 | 已核对的状态 |
| --- | --- |
| 模块版本 | `1.8.0-beta13`（`versionCode 32`）。[GitHub 测试版](https://github.com/Elegying/X-UP/releases/tag/v1.8.0-beta13)发布于 2026-11-14，包含 APK 和 SHA-256 清单。beta12 仅用于本地测试。 |
| 源码 | 发布标签 `v1.8.0-beta13` 指向 `5b8724d`。本次文档更新前，本地与远端 `main` 均为 `10d4a5a`，该提交的 [Android checks](https://github.com/Elegying/X-UP/actions/runs/43901035122) 已通过。 |
| 模块与环境 | 包名 `io.github.jared.xlowerseek`，作用域 `com.twitter.android`。需要 Root、Android 11（API 30）及以上和支持现代 Xposed API 102 的 LSPosed。APK 架构为 `arm64-v8a` 和 `x86_64`。 |
| 实机基线 | Xiaomi 17 Pro Max / Android 17 / X `12.42.0-prod.01`（`312420001`）。同签名覆盖安装后 11/11 项兼容检测通过。检测项反映 Hook 加载状态，具体交互结果见 [beta13 验收记录](1.8.0-beta13-review.md)。 |

## 从哪里改

Java 代码位于 [`src/io/github/jared/xlowerseek/`](../src/io/github/jared/xlowerseek/)，工程直接使用仓库根目录下的 [`build.gradle`](../build.gradle) 和 [`AndroidManifest.xml`](../AndroidManifest.xml)。LSPosed 从 [`java_init.list`](../resources/META-INF/xposed/java_init.list) 读取入口，`XLowerSeek` 在 X 主进程的 `Application.attach` 后按功能安装 Hook。`ModuleApplication` 和 `SettingsActivity` 是 X-UP 自身进程的入口。

| 维护场景 | 先读的代码 |
| --- | --- |
| 兼容扫描、回滚和诊断 | `XLowerSeek` 注册安装顺序。`TargetResolver` 与 `CompatibilitySelection` 负责 DexKit 匹配和缓存。`Hooks`、`CompatibilityFailure` 负责事务和回退。`CompatibilityRuntime`、`CompatibilityStatus`、`CompatibilityProvider` 和 `ModuleStatusActivity` 负责报告与状态页。 |
| 双击、倍速、进度和下载 | `LowerSeekHook`、`ContinuationTapHook`、`PlaybackStepHook` 处理双击和步长。`VideoTargets`、`VideoDownloadHook`、`FullScreenGestureHook` 处理全屏入口。下载经 `VideoDownloadClient` 和 `LocalTranslationProvider` 进入 `VideoDownloads`、`DownloadService`，通过 `MediaStore` 保存。 |
| 推广过滤和最新评论 | `AdBlockHook` 定位时间线入口，`AdRenderSignature`、`AdTimelineFilter` 处理渲染参数和内容过滤；`ReplySortHook` 负责最新评论。 |
| 设置与同步 | `SettingsActivity`、`Feature`、`SettingsStore` 管理开关和本地设置，X 侧 `RemoteSettings` 读取 LSPosed 同步的偏好。`TranslationMode` 维护本地与 X 原生翻译互斥。 |
| 翻译和模型 | `GlobalTextTranslationHook`、`IdentityTextHook` 和 `PostContextHook` 处理显示文字、账号名保护和上下文。本地请求经 `LocalTranslationClient`、`LocalTranslationProvider` 到 `LocalTranslationService`。`AutoTranslateHook` 和 `PostTranslationBridge` 负责 X 原生翻译相关入口。`LocalEngine`、`LocalModels`、`ModelDownloadService` 管理 Google ML Kit（默认）、腾讯 HY-MT2 和 OPUS-MT。原生实现位于 [`native/`](../native/)，固定依赖见 [`scripts/`](../scripts/)。 |
| 应用内更新 | `UpdateActivity`、`UpdateClient`、`UpdateRelease` 筛选 GitHub Releases；`UpdateDownload` 校验附件大小、SHA-256、包名、版本和签名后，交系统安装器确认。更新器使用 GitHub 的 APK 附件 `digest` 字段。 |

## 处理 X 兼容问题

1. 从“状态与帮助”复制兼容报告，记录模块版本、X 的 `versionName` / `versionCode`、出问题的功能和复现步骤。可请求完整重扫，然后完全退出并重开 X，对照报告中的缓存、扫描、备用路径及异常原因。
2. 按报告功能键到 `XLowerSeek` 找安装入口。匹配优先使用业务特征、类型和调用关系，用 `TargetResolver` 校验唯一候选。增加已知结构变体时保留旧路径，只有零候选时才尝试备用匹配。遇到歧义应保留 X 原行为。
3. 缓存身份包含模块版本、X 版本、基础及拆分 APK 的路径和文件信息，以及手动重扫代次。缓存参与匹配且发生结构错误时，仅清理受影响功能并重试一次。安装回滚失败时停止重试。单入口累计三次 Hook 回调异常后，本进程放行原调用。
4. 回归时分别检查新旧结构、功能开关和实际行为。`continuation_seek` 依赖 `lower_seek`，`local_text` 依赖 `identity_text`，`video` 当前仍共享全屏下载和手势入口。状态标记为已加载后，仍需到相应页面验证结果。

版本适配的代码变更与证据分别见 [beta12 的 X 12.42 适配](1.8.0-beta12-review.md) 和 [beta13 的自动恢复](1.8.0-beta13-review.md)。兼容报告只保留功能、版本、状态及异常类型，排查时沿用这个边界，避免记录帖子、私信或账号。

## 构建与验证

在仓库根目录执行。CI 使用 JDK 17、Android SDK 36、Build Tools 36.0.0、NDK 28.2.13676358 和 CMake 3.22.1。Gradle 9.1.0 由 Wrapper 提供。原生构建使用不含空白字符的源码路径。

```sh
bash test.sh
./scripts/prepare-native.sh
python3 scripts/prepare-opus-native.py
./gradlew testDebugUnitTest lintRelease assembleDebug --no-daemon --console=plain
```

- [`tests/`](../tests/) 由 `test.sh` 运行，使用 SDK 及替身覆盖 Hook 回滚、手势、下载、翻译队列和设置同步。可先运行这一组，使用已安装的 `platforms/android-36`，无需准备原生依赖。
- [`unit-tests/`](../unit-tests/) 由 Gradle 运行。兼容、匹配和更新解析可从 `CompatibilitySelectionTest`、`ReplySortContractTest`、`UpdateReleaseTest` 等测试定位。Debug APK 为 `build/outputs/apk/debug/X-UP-debug.apk`。CI 配置见 [Android 工作流](../.github/workflows/android.yml)。
- 普通开发验证使用 Debug 构建。正式发布和覆盖已发布的版本使用原发行签名，版本字段须同步更新 `build.gradle` 和 `AndroidManifest.xml`。本地 Debug 包与正式包使用相同包名，签名不同时 Android 会拒绝覆盖。环境设置、签名恢复和发布步骤见 [构建与签名恢复](build-recovery.md)。

2026-11-23 在 macOS / JDK 21 上复跑上述命令，主机回归和 148 项 JUnit 通过，Release Lint 为 0 错误、46 条警告，Debug APK 构建成功。本次只复核主机侧。

beta13 发布时实机验证了单项缓存恢复、手动全量重扫、下半区双击和连续点按，并确认视频列表显示中文翻译和下载入口。完整视频保存见 beta12 的实机记录。

## 下一步

1. 补齐 beta13 实机回归。做真实推广内容开关对照，验证 Google ML Kit、腾讯 HY-MT2、OPUS-MT 各自的模型下载及英日翻译，并检查私信排版、完整视频保存和长时间播放。记录设备、X 版本和具体结果。
2. 有下一版 X 样本时，记录升级前后的兼容报告及实际行为，验证自动重扫和失配回退。真实回调异常仍缺手机验证，当前运行回退证据来自主机故障注入。
3. 根据新增失配评估视频下载与手势的共享依赖，按影响处理既有 Lint 警告。修复及实机证据写入对应版本验收记录，再更新本页的版本、验证范围与待办。
