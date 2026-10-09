# X-UP 全项目审查 2026年10月10日

审查基线为 `main` 的 `14c102e38ff4639760760f5e6d8c1cd318034fc4`，版本 `1.8.0-beta7 / 26`。范围覆盖 80 个生产 Java 文件、11 个项目自有 C/C++ 及头文件，以及构建脚本、Manifest、资源配置、测试、依赖来源、GitHub 门禁和更新接口。第三方原生依赖检查了固定版本、下载和构建，不等同于逐行审计所有第三方源码。

初次审查确认 5 项问题（1 项 P1、4 项 P2），随后用户授权修复并继续审查。分支 `codex/audit-fixes-20261010` 修复了这 5 项，以及后续确认的 2 项手势问题、2 项模型状态问题和第三轮确认的 1 项总开关通知问题，累计 10 项生产缺陷。第二轮还修正了一处主机测试的异步等待条件。以下结果记录第三轮本地审查结束时的状态；用户随后授权将 Google 设为默认引擎并直接发版，交付说明见 [1.8.0-beta8 审查与发布说明](1.8.0-beta8-review.md)。原始失败记录保留在下方，涉及旧代码的行号均以初始提交为准。

## 修复结果与后续审查

| 问题 | 修复后的行为 | 回归证据 |
| --- | --- | --- |
| Java 配置导致 Debug D8 失败 | source/target 对齐 Java 17，Android 最低版本仍为 30 | 不再使用临时 init 脚本，完整 `assembleDebug` 成功 |
| 导出视频入口缺少调用者鉴权 | 通过现有 Provider 的 UID 校验签发短时、一次性凭据；Activity 只能消费绑定任务和 URL 的凭据 | 未授权 UID、裸 URL、伪造凭据、过期、重放均拒绝；真实生产入口可启动合法任务 |
| 下载回调失联导致永久忙碌 | 服务保存任务状态；X 前台主动查询，返回前台立即核对；模块进程重建后把未完成任务标为中断 | 失联后恢复、进程重建、成功状态保留、重复提交及取消期间不新建任务 |
| 横屏手势起点过窄 | 根据窗口横竖比例、系统 Insets 和视频可见区域限定起点 | 300/320/360dp 高窗口可用范围分别为 172/192/232dp；800dp 竖向窗口仍为 480dp |
| 通知关闭后无法取消 | 下载图标可查看与取消，模块下载设置页可取消，通知作为额外入口 | 取消 API 不依赖通知权限；服务测试确认取消后断开连接并清理 pending 行 |
| 后续发现：异常复位后泄漏触摸流 | X 收到 CANCEL 后，反射失败或外部 reset 都保留已接管状态，消费本次流的剩余事件 | 修复前后生产代码对照：`remainderConsumed=false` → `true` |
| 后续发现：窗口坐标系混用 | 视频可见矩形与触摸起点都在根视图坐标系比较 | 设置窗口屏幕偏移后，修复前后生产代码对照：`offsetWindowRecognized=false` → `true` |
| 第二轮 P2：暂停模型下载会丢失已下载分片 | 在开始请求、重定向、响应头返回和截断文件前检查暂停；保留已有分片 | 4 个旧代码失败用例修复后通过；响应头或正文打开期间暂停时，原来 6 字节变为 0，现在保持 6 字节 |
| 第二轮 P2：Google 语言包回调覆盖较新状态 | 查询带版本标记；开始下载使旧查询失效；下载中不再发库存查询 | 6 个旧代码乱序失败用例修复后通过；共 9 个场景、20 条断言覆盖查询、成功、失败和重试 |
| 第三轮 P2：关闭翻译总开关未通知本地调度 | 保存成功后复用既有设置通知，取消运行任务、清理排队任务并安排引擎释放 | 真实保存入口连接真实调度类；断开和连接两种桥接状态下，旧代码均未取消，修复后均取消并可重新开启 |

手势后续问题的位置为 `FullScreenGestureHook.touch()`、`boost()` 和 `reset()`。前者原来将屏幕坐标的 raw touch 与根视图坐标的 `getGlobalVisibleRect()` 比较；后者在已向 X 发送 CANCEL 后清掉接管标记。窗口坐标的判断依据见 [Android View 官方文档](https://developer.android.com/reference/android/view/View.html?is-external=true#getGlobalVisibleRect(android.graphics.Rect))。两项都用初始提交和修复后生产类运行了相同探针，日志为 `build/reports/project-audit-2026-10-10/gesture-followup-before-after.log`。

### 第三轮设置与任务取消复查

`SettingsActivity` 的“自动中文阅读”总开关通过 `SettingsStore.save()` 保存。该方法以前只写本地和远端偏好，没有像 `setFeature()` 和 `setEngine()` 那样调用 `changed()`。因此模块内的 `LocalTranslationService.settingsChanged()` 不会执行，已运行的推理不收到取消信号，排队项也不立即移除。X 客户端正常收到远端设置时可以额外发取消请求，但在设置桥接不可用或客户端取消通知延迟时，服务端会继续旧工作，直到推理自行结束或其他取消路径触发。虽然最终发布结果前仍检查总开关，这并不能阻止多余计算和模型驻留。

新增回归直接调用生产 `SettingsStore.save()`，串联生产 `LocalTranslationService`，以可控引擎保持一项推理进行中，再排入另一项任务。桥接断开与桥接写入成功两种情况下，旧代码均得到 `cancelled=0 closed=0 activeResult=-1 queuedResult=-1`；不会依赖真实 X 客户端替测试发取消通知。修复仅在成功保存后补上既有 `changed()` 调用。相同输入得到 `cancelled=1 closed=1 activeResult=2 queuedResult=2`，并确认排队项没有进入推理、重新开启后可正常翻译。取消使用各引擎现有机制；项目中的 ML Kit 已提交任务仍需返回或超时，取消后的结果不再发布，不据此承诺所有引擎即时中断。

本轮共补 33 条主机断言：断开桥接的关闭/恢复 10 条、连接桥接的关闭/恢复 11 条、离线设置与远端失败后重连同步 12 条。后者同时检查引擎、独立功能开关及待同步标记，确认新通知没有破坏已有本地保存与重连重放逻辑。偏好存储、框架桥接和推理引擎为替身，设置保存和任务调度为实际生产类。

第三轮日志位于 `build/reports/project-audit-2026-10-10/round3/`：`SettingsTranslationTest-offline-before.log`、`SettingsTranslationTest-connected-before.log` 记录修复前失败；`core-tests.log` 记录修复后完整回归；`gradle.log`、`apk-verification.log` 和 `validation-summary.json` 记录当前构建验证。同时复查了更新的下载 ID、校验与 URI 授权、系统安装器选择、权限返回及翻译前后台清理，本轮未确认其他新增生产缺陷。

### 第二轮模型与异步状态复查

`ModelTransfer.download()` 以前先接受服务器的 HTTP 200，再将 `.part` 截断到零，最后才在读取循环中检查暂停。如果用户在等待响应头或正文期间暂停，而服务器忽略了续传 Range，已下载进度仍会被删除。请求开始前已经暂停也会继续联网，重定向途中暂停还会发起下一次请求。本轮先增加 4 个回归，旧生产代码全部失败；两个数据保留用例明确记录 `expected.length=6 actual.length=0`。修复复用现有 `Connections` 测试接口，在网络与文件操作边界检查暂停，并确保暂停和异常路径关闭当前连接。另补 2 项正常重定向续传和 HTTPS 降级拒绝测试，原有校验、续传和最终原子发布规则保留。暂停仍为协作式，已经进入的阻塞网络调用需要返回或超时。

`MlKitModels.refresh()` 以前不区分请求先后：下载已经成功后，较早的库存查询仍可将 `ready` 改回 false；过期失败也会覆盖下载中或下载完成的提示。页面只在 `onResume()` 查询库存，500ms 的界面刷新不会重新查询 SDK，因此该状态可能持续到重新打开页面。本轮为库存查询添加版本标记，下载开始时废弃旧查询，下载期间跳过新库存查询，下载结束后允许重新检查模型。生产调用入口和默认 Task 回调均在主线程，版本字段用于处理异步结果乱序。依据见 [RemoteModelManager 官方接口](https://developers.google.com/android/reference/com/google/mlkit/common/model/RemoteModelManager)及 [Task 默认监听线程说明](https://developers.google.com/android/reference/com/google/android/gms/tasks/Task)。回归运行真实 `MlKitModels`，仅用可控替身安排 SDK 的独立任务完成顺序；并未在主机上运行 Android ML Kit SDK。

本轮还捕获 `LocalTranslationClientTest` 的间歇性 `client case 71` 失败。生产清理先关闭 Provider 再释放服务绑定，测试只等待 Provider 数归零就立即断言绑定数为零。将替身的关闭与返回之间延长 50ms 后，旧断言稳定失败；修正为同时等待两类资源释放后，同一探针和完整脚本通过。生产客户端没有因此修改，原资源泄漏断言仍然保留。

本轮证据保存于 `build/reports/project-audit-2026-10-10/round2/`：`model-transfer-before.log` 与 `.xml`、`model-transfer-after.log`、`mlkit-before.log`、`core-tests-final.log`、`gradle-final.log`、`apk-verification.log`。首次全量脚本的间歇性失败保存在 `core-tests-intermittent-failure.log`，测试时序对照保存在 `client-cleanup-probe/before.log` 和 `after.log`。

### 下载任务边界

| 触发 | 前置条件 | 处理中 | 完成证据 | 失败或取消 |
| --- | --- | --- | --- | --- |
| X 中点击保存 | Provider 按真实 Binder UID 只允许 X 和本模块；URL 白名单校验 | 单任务；Activity 消费凭据并启动前台服务 | MP4 完整性验证与 MediaStore 发布成功 | 状态可查询；仅未启动任务可超时失效 |
| 返回 X 或打开下载设置 | 已知任务 ID，或查询当前任务 | 运行任务不会因缺少进度而自动重试 | 读取服务任务状态 | 进程重建恢复为中断，允许用户重试 |
| 图标、设置页或通知取消 | 取消操作绑定具体任务 ID | 等待实际传输退出及清理 | 清理完成后退出活动状态 | 旧通知、旧对话框不能取消新任务 |

实现沿现有 Provider 扩展三个带 `video.` 前缀的方法，未增加新的导出组件。状态机 `VideoDownloadState`、Android 桥接 `VideoDownloads` 和 X 侧 `VideoDownloadClient` 分离；下载策略与既有传输检查继续复用。URL 和能力凭据不写入任务状态持久化。前台使用短暂的 [unstable ContentProviderClient](https://developer.android.com/reference/android/content/ContentProviderClient) 查询，离开前台或确认任务结束后停止定时查询，空闲时不反复唤醒模块。

服务继续限制 HTTPS、主机、重定向、2 GB 上限及 MP4 完整性。补充验证覆盖截断内容、超限响应、跳转外部域名、写入失败、取消、服务销毁，以及不删除已发布的相册文件。取消回调绑定独立传输对象，终态在 worker 清理后产生；通知和对话框都携带任务身份。原右侧双击、下半区双击及右下交叠区域的既有回归全部保留。

### 第三轮本地审查结束时的验证

| 检查 | 结果 | 范围 |
| --- | --- | --- |
| `bash test.sh` | 796 条断言通过 | 原有 629 + 手势入口 25 + 视频客户端 35 + 下载整合 54 + Google 模型状态 20 + 设置与调度整合 33 |
| `testDebugUnitTest` | 92 项通过，0 失败、0 错误、0 跳过 | 相较初始基线新增 11 项下载状态机测试、第二轮 6 项模型传输测试 |
| `lintRelease` | 0 错误、41 条警告 | 数量与初始基线一致；既有兼容性、国际化和工程建议仍需按场景处理 |
| `assembleDebug` | 完整 APK 构建成功 | JDK 21、SDK 36，arm64-v8a 和 x86_64 原生库进入 APK |
| APK 检查 | Debug APK 的 v2 签名校验及 `zipalign -c -P 16 4` 均通过 | Debug 签名，不是正式发布或实机安装证明 |
| 后续手势问题对照 | 2 项均复现旧缺陷并验证修复 | 同样的替身和输入，分别运行初始提交与当前生产类 |
| Git 差异 | `git diff --check` 通过 | 包括新增测试与工作流 |

生产 Provider、Activity、服务、客户端、Google 模型状态类和设置存储类实际参与主机测试，Android 消息队列、网络连接、MediaStore、SDK 任务及框架桥接使用受控替身。这些结果不等于实机 Android、LSPosed 或 X 页面验收。早期日志 `fix-core-tests.log`、`fix-gradle.log`、`fix-apk-verification.log` 及 `round2/` 输出保留为历史记录；当前验证以 `round3/` 的最终日志为准。当前 Debug APK 的 SHA-256 为 `7c3da62676fa5b48277c37fcbcd89f31f81d80a7a86efbc67bc952de34d6c43c`。

新增 `.github/workflows/android.yml`，包含核心/生命周期回归、JUnit、Lint 和完整 Debug APK 构建，并固定 GitHub Actions 到经远端核对的提交。配置使用只读仓库权限，原生源码继续按项目固定摘要下载。第三轮本地审查结束时，该工作流尚未推送和在 GitHub 执行，也尚未加入远端必需状态检查；不能将这一阶段的本地通过视为远端门禁已生效。

后续复查还覆盖更新下载的 ID 与签名/摘要校验、安装权限返回、模型下载服务的暂停/销毁、模型解包、翻译前后台租约、任务清理和设置持久化。第三轮新确认并修复的是总开关通知问题；未将其他未经复现的风险记为已确认缺陷。审查时无 ADB 设备，通知拒绝后的真实 UI、X/LSPosed 跨进程表现、旋转/分屏实际控件位置、三引擎翻译质量与内存压力，以及系统安装器流程仍属于实机验收缺口。第三轮审查没有变更版本名，其 Debug 产物不作为新版本发布。

## 初次审查记录（修复前）

以下记录描述 `14c102e` 原始代码。初次确认的 5 项问题已在上文说明修复状态。

### 初次确认的问题

### P1 Java 兼容级别使 Debug 构建失败

位置：`build.gradle:36`，关联 `build.gradle:44` 的 `libxposed-service-102.jar`。

准备 README 指定的原生依赖后，使用 JDK 21、SDK 36、NDK 28.2.13676358、CMake 3.22.1 执行 `./gradlew testDebugUnitTest lintRelease assembleDebug`，在 `:desugarDebugFileDependencies` 失败：

```text
Invalid build configuration. Attempt to create a global synthetic for 'Record desugaring' without a global-synthetics consumer.
```

`javap` 确认所打包的 `io.github.libxposed.service.HotReloadResult` 继承 `java.lang.Record`，但项目将 Java source/target 设为 11。通过仓库外的 Gradle init 脚本仅把这两个值临时设为 17，同一个 D8 任务通过。该实验没有修改 `build.gradle`。随后恢复原配置再检查，Debug D8 同样失败，而 Release D8 任务通过；本问题明确针对 Debug 路径，不据此声称发布版无法构建。这是配置与所用依赖不一致造成的开发构建阻断，不能用 JUnit 通过或历史 Release 构建记录替代。

修复方向：将项目所需的 Java 字节码级别与依赖对齐，补上干净环境的 Debug 构建门禁，继续保留现有 Android 最低版本。Android 官方说明了 [Records 对 Java 17 配置和 desugaring 的要求](https://android-developers.googleblog.com/2023/06/records-in-android-studio-flamingo.html)。

### P2 视频下载入口没有调用方身份校验

位置：`AndroidManifest.xml:21`、`src/io/github/jared/xlowerseek/DownloadActivity.java:13`。

`DownloadActivity` 对外导出且没有权限要求。入口只检查 URL 是否属于 HTTPS `video.twimg.com` 的 MP4，随即启动不导出的下载服务；没有验证调用应用，也不要求由 X-UP 签发的任务凭据。其他前台应用可以通过显式 Intent 请求 X-UP 下载一个允许域名的视频，消耗 X-UP 的网络和存储资源并写入相册。目标地址白名单不能代替调用方鉴权。

最小 JVM 探针直接调用生产 `DownloadActivity`，只提供合法形式的 URL、不给 receiver 或任何授权凭据，观察到下载服务被启动；外部域名被正确拒绝。该探针只记录服务调度，不发送视频请求。跨应用可达性由 Manifest 与 [Android 导出组件规则](https://developer.android.com/privacy-and-security/risks/android-exported)共同确认；本轮没有在手机上启动第三方应用做利用验证。

修复方向：复用现有按 UID 检查调用者的 IPC 思路，通过可信 Provider/Binder 为 X 签发短时、一次性的下载能力，再由入口消费。需要保留 X 到模块的正常通信；不能直接把下载 Activity 改为不导出后就认为功能完整。

### P2 下载进度回调中断后按钮无法恢复

位置：`src/io/github/jared/xlowerseek/VideoDownloadHook.java:177`、`:181`，关联 `:34`、`:171`、`:196`。

启动时只有一个 30 秒等待首次回调的超时。收到任意进度回调后，代码设置 `downloadAcknowledged=true` 并移除这个超时；以后只有成功或失败的终态回调会清除 `downloading`。如果模块下载进程被结束、崩溃或后续 Binder 回调丢失，X 进程内的按钮会一直禁用，重新进入视频页也不查询服务的真实任务状态。

最小探针使用实际编译的 `VideoDownloadHook` 与其匿名 ResultReceiver，仅替换 Android 时钟和消息队列：无首次回调时 30 秒能恢复；收到一次进度后模拟失联 10 分钟仍为 `downloading=true`；补发终态回调才恢复。手机进程终止场景尚未实测。

修复方向：为任务提供可查询的 ID 和状态，在回到前台、回调超时或服务死亡时核对真实状态，恢复重试入口，并避免在旧任务仍运行时重复下载。

### P2 横屏固定留白挤掉滑动和长按手势区域

位置：`src/io/github/jared/xlowerseek/FullScreenGestureHook.java:43` 至 `:47`。

触摸起点无条件排除顶部 140dp 和底部 180dp，未按横竖屏和实际控件位置调整。假设视频表面铺满窗口、密度为 1，直接调用生产 `touch()`，逐行在中心 X 坐标注入 ACTION_DOWN，结果如下：

| 窗口高度 | 可用纵向范围 | 占高度比例 |
| --- | --- | --- |
| 300dp | 0dp | 0% |
| 320dp | 0dp，仅边界精确坐标可能通过 | 0% |
| 360dp | 40dp | 11.1% |
| 800dp | 480dp | 60% |

360dp 高的横屏窗口只剩 140–180dp 之间的一条窄带，其他大部分视频区域无法开始横向拖动或长按。该探针验证的是实际入口逻辑，不只是 `SwipePolicy` 的进度计算；具体手机的窗口尺寸仍需实测。

修复方向：根据系统 Insets、视频可见区域及实际工具栏/进度条占用区域划定手势范围，至少对横屏单独约束上下留白，并补充入口层横竖屏测试。

### P2 关闭通知时缺少视频下载取消入口

位置：`src/io/github/jared/xlowerseek/DownloadService.java:28` 至 `:31`，关联 `VideoDownloadHook.java:171`、`DownloadSettingsActivity.java:17`。

视频取消操作只放在前台服务通知中；下载时视频图标被禁用，下载设置页也没有当前任务的取消按钮。Android 13 及以上的新安装应用默认没有通知权限，用户关闭应用通知或下载通知渠道也会触发同一问题。下载仍然能够开始，但通知中的取消按钮不可见，用户只能另行打开通知权限或到系统停止整个应用，不能通过 X-UP 正常取消该任务。

这是源码控制流与 [Android 通知权限行为](https://developer.android.com/develop/ui/compose/notifications/notification-permission)共同确认的缺口。前台服务在无通知权限时可以启动，系统任务管理器中的应用停止操作不能替代应用自己的任务取消及清理流程。

修复方向：在下载图标或设置页提供可用的任务状态和取消操作，通知作为第二入口；用户拒绝通知权限时仍能取消下载。

## 初次自动检查与现场验证

| 检查 | 本轮结果 | 证据边界 |
| --- | --- | --- |
| `bash test.sh` | 629 条断言通过 | 包含 Android 替身，不能代替设备测试 |
| `testDebugUnitTest` | 75 项通过，0 失败、0 错误、0 跳过 | 读取 Gradle JUnit XML 统计 |
| 原配置 `assembleDebug` | 失败 | D8 Record desugaring，见 P1 |
| 临时 Java 17 对照 | `desugarDebugFileDependencies` 通过 | 只改变实验配置，不是原配置构建通过 |
| `lintRelease` | 0 错误、41 警告 | 临时 Java 17 与恢复后的原配置均完成；最终报告来自原配置 |
| Android 原生库 | arm64-v8a 与 x86_64 均通过，6 个 SO 的 LOAD 段均为 16KB 对齐，栈不可执行 | 原生编译不代表手机运行或完整 APK 构建 |
| 主机 STQ 内核 | 32 组随机比较通过 | 比较标量、运行时分发和反量化参考值 |
| 下载状态、入口、手势探针 | 复现上述 3 项问题 | 使用生产类及必要 Android 替身 |
| 真实更新接口 | beta6 可发现 beta7；beta7 无更新；正式渠道排除测试版 | 实际执行生产 `UpdateClient`，未下载或安装 APK |
| libxposed 依赖来源 | 3 个 JAR 与 Maven Central 对应 AAR 内 `classes.jar` 逐字节一致 | 版本为 102.0.0 |
| 凭据检查 | 当前跟踪文件中常见令牌/私钥模式无命中，无跟踪的签名密钥文件 | 模式扫描不等同于证明不存在任何秘密 |
| GitHub 检查 | 当前提交 CodeQL 成功；Dependabot 告警 API 返回空列表 | 不等同于全依赖、全原生源码无漏洞 |

原配置构建日志、临时配置、探针源码和探针结果保存在 `build/reports/project-audit-2026-10-10/`；该目录属于可清理的本地构建输出。初次审查阶段没有修改业务代码、发布、安装 APK 或更改 GitHub 设置；后续本地修复与 Debug 构建结果见上文。

## 初次记录的保护与工程风险

模型下载限定 HTTPS，固定大小及 SHA-256；OPUS 解包使用文件白名单和逐文件校验。视频下载逐次检查重定向地址，限制为指定主机，并通过 MediaStore 的 pending 状态发布文件。更新路径检查包名、版本、签名及附件摘要，使用只读内部 Provider，并显式选择系统安装器。这些边界在代码中存在，未发现把旧更新路由问题重新引入的证据。

翻译 Provider 检查调用 UID 对应包，客户端和服务端有引擎/模型版本隔离、并发上限、取消与过期结果处理；去广告在展示列表上做可逆复制，并保留未知条目。Lint 的导出 Provider 告警需要结合这些实际边界判断，不能把所有导出组件一概视为漏洞。GitHub 两项证书固定告警仍是有记录的设计取舍；系统 CA/主机名验证和完整性校验不应被弱化。

主分支由 ruleset 保护，要求 PR 和 `Analyze (java-kotlin)` 成功，但初次审查时没有构建、核心回归、JUnit 或 Native 检查门禁（本地工作流配置已在修复阶段补充，远端仍待执行）。本次 Debug 构建失败而 CodeQL 成功就是具体的漏检例子。建议先加入核心脚本、JUnit、Lint 和 Debug 构建，再根据运行成本补充原生内核检查；不应把 CodeQL 成功当作可交付证明。

架构已有 `OfflineEngine`、翻译策略和版本适配层等扩展点，适合保留。新增引擎时，`LocalModels`、下载服务和界面的引擎分支仍需多处修改；后续可把引擎工厂、就绪检查与模型下载描述收拢为稳定接口，按开闭原则扩展。若只修复本轮问题，不需要先做全项目重写。大量压缩到单行的生命周期和异常代码也应在相关修复时展开，便于核对锁、清理顺序和失败分支。

初次测试没有覆盖完整 `DownloadService`、更新安装生命周期和手势事件流；本轮已补充下载与手势主机整合回归，更新安装生命周期仍需设备验证。新增回归应覆盖回调失联后恢复、合法和非法调用者、通知拒绝、横屏起点，以及保留原右侧双击且右下角不重复快进。设备测试还应覆盖三引擎冷启动/切换/内存压力、真实断网续传与空间不足、去广告分页/恢复和系统安装器流程。

本轮 `adb devices -l` 没有设备。没有据此宣称手机翻译质量、性能耗电、完整手势行为、全部 X 页面或安装流程验收通过。README 中对小模型语义能力、支持脚本和特定 X 版本的限制仍然有效。修复顺序建议为构建与下载入口鉴权，然后是任务恢复、横屏区域和取消入口，最后补齐 CI 与实机回归。
