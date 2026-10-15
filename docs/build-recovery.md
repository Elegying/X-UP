# X-UP 构建与签名恢复

适用源码基线：`1.8.0-beta10`，`versionCode=29`。公开代码仓库保存源码、依赖版本和补丁，不保存发布私钥、开发工具、下载模型或构建缓存。

## 从 GitHub 恢复开发与发布环境

维护者重装系统后，让 AI 从以下两个仓库恢复：

- 最新源码：[Elegying/X-UP](https://github.com/Elegying/X-UP)。先读取本文，按当前源码声明安装开发依赖。
- 原签名及恢复资料：[Elegying/X-UP-Recovery](https://github.com/Elegying/X-UP-Recovery)（私有）。使用拥有该仓库读取权限的 GitHub 账号登录，按其中的 `README.md` 校验备份、解密并恢复签名。

GitHub CLI 登录后可执行：

```sh
umask 077
mkdir -p "$HOME/Projects" "$HOME/.xup-recovery"
gh repo clone Elegying/X-UP "$HOME/Projects/X-UP"
gh repo clone Elegying/X-UP-Recovery "$HOME/.xup-recovery/repository"
```

源码目录须不存在且完整路径不含空白。已有项目应先检查本地改动再同步，不覆盖未提交工作。私有仓库保存加密包、解密口令文件、解密工具和验证记录；按用户指定，两者由同一私有仓库的访问权限保护。工具应从文件读取口令，不打印口令、私钥或凭据，也不把恢复资料写入公开代码仓库。

日常开发使用公开仓库的最新源码；备份中的 Git bundle 和补丁仅用于恢复当时的源码快照，不要把旧补丁覆盖到最新源码。开发环境配置完成后，使用备份中的 `build-restored.py` 对最新源码执行签名构建，并按本文末尾的覆盖升级要求验收。配置环境和构建不会自动创建 GitHub Release，实际发布时还需要写入权限及明确的发版指令。

## 重装前保存

- 原发布签名文件及其密钥库密码、密钥别名、密钥密码。将加密备份和解密口令分别保存到重装不会擦除的位置，并实际试解压一次。
- Git 仓库中尚未推送的修改；本次恢复包用 Git bundle 和补丁保存源码及构建修复。
- GitHub 账号的登录及二次验证恢复方式。应确认新系统的账号能读取私有恢复仓库；原发布签名仍需从备份恢复。

不能从 APK、公钥证书或 GitHub 账号重新生成原私钥。不要把签名文件、凭据或包含它们的备份上传到公开仓库、Release 或 CI 日志。

当前公开 beta10 的证书 SHA-256：

```text
3f3e9f2d2b12b19feb8265fb9610cb02a4414a73b9db9a0d1c48ed8c4351734b
```

该指纹是公开证书标识，不是密码。其他开发者使用自己的签名时应改用自己的预期指纹。

## 安装开发环境

macOS / Linux 需要 Bash、Git、Python 3、JDK 17 或 21，以及 `curl`、`tar`、`patch`、`shasum`。macOS 可先运行 `xcode-select --install` 安装命令行工具；Linux 可安装发行版对应软件包。Windows 可在 WSL2 中按 Linux 流程安装 Linux 版 JDK 与 SDK；该路线未在本次恢复演练中实测。

源码所在目录的完整路径不要包含空格或其他空白字符（包括上级目录）。当前 Android 原生构建工具会错误拆分带空格路径的 `-fmacro-prefix-map` 参数；`build.sh` 会在开始下载或构建之前提示。可使用 `$HOME/Projects/X-UP`，前提是用户目录路径也不含空格。签名文件和备份包可放在含空格的路径中。

从 [Android 官方下载页](https://developer.android.com/studio) 安装 Android Studio 或命令行工具。用 SDK Manager 安装：

```text
platforms;android-36
build-tools;36.0.0
ndk;28.2.13676358
cmake;3.22.1
platform-tools
```

终端示例（根据实际安装位置设置 `JAVA_HOME`）：

```sh
export JAVA_HOME="/实际路径/jdk"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/Library/Android/sdk"  # Linux 通常是 $HOME/Android/Sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
  "platforms;android-36" "build-tools;36.0.0" \
  "ndk;28.2.13676358" "cmake;3.22.1" "platform-tools"
java -version
javac -version
```

交互阅读并接受所需 SDK 许可。Gradle 9.1.0 由仓库中的 Wrapper 下载，不必安装系统 Gradle。依赖准备需要能访问 GitHub、Google Maven、Maven Central 和 Gradle 下载服务；恢复包没有包含整个 SDK、NDK、Gradle 缓存或原生依赖源码，不能用于完全离线构建。

## 恢复源码和签名

通常从 GitHub 克隆：

```sh
git clone https://github.com/Elegying/X-UP.git
cd X-UP
```

若使用本次恢复包，先按照包内《恢复说明.md》执行 `restore-source.sh`：它从随包 Git bundle 建立独立仓库并应用构建修复，无需依赖修复是否已推送到 GitHub。不要直接用旧补丁覆盖之后的新版源码；需要合并时先检查差异。

原私钥放在仓库以外，并设置：

```sh
export SIGNING_KEYSTORE="/安全位置/xlowerseek-signing.p12"
export EXPECTED_SIGNER_SHA256="3f3e9f2d2b12b19feb8265fb9610cb02a4414a73b9db9a0d1c48ed8c4351734b"
./build.sh
```

密钥密码和别名如与现有默认配置不同，使用 `SIGNING_STORE_PASSWORD`、`SIGNING_KEY_ALIAS`、`SIGNING_KEY_PASSWORD`；不要把真实密码写入会提交的文件。本次恢复包提供 `build-restored.py`，从包内凭据文件读取这些值并传给构建进程，无需把密码写进命令或打印到终端。

`build.sh` 会先检查签名文件和必要工具，再调用现有原生依赖准备脚本。构建完成后先验证 APK 签名；设置了预期指纹时，只有匹配才会导出。输出文件名取自 APK 的真实 `versionName`，默认保存在 `build/releases/v<版本>/`，不会再固定为 beta7。

可通过 `RELEASE_OUTPUT_DIR` 指定输出目录，通过 `ANDROID_BUILD_TOOLS_VERSION` 指定已安装的构建工具版本（默认 `36.0.0`）。Gradle 参数可附加给 `build.sh`，例如 `./build.sh --no-daemon`。

## 后续覆盖升级与发布

1. 保持包名 `io.github.jared.xlowerseek` 和原签名不变。
2. 在 `build.gradle` 中提高 `versionCode`，并将 `versionName` 升级到更新器可识别的更高版本；同步 `AndroidManifest.xml` 中的版本声明。例如以 beta10 / 29 为基线，下一版可使用 beta11 / 30。实际发版前先核对 GitHub 是否已有更高版本。
3. 执行主机回归、JUnit / Lint 和发布构建，并核对证书、包名、版本及 SHA-256：

   ```sh
   bash test.sh
   ./gradlew testDebugUnitTest lintRelease
   ./build.sh
   ```

4. 在保留数据的测试设备上进行旧版本覆盖安装验收，重启 X，检查设置与模型保留、LSPosed 加载和关键功能。不要先卸载旧应用，也不要清除应用数据。
5. 公开发布需要推送经审查的源码和对应 `v<版本>` 标签，在 `Elegying/X-UP` 创建 GitHub Release，并上传精确命名的 `X-UP-<版本>.apk`。例如标签 `v1.8.0-beta11` 对应 `X-UP-1.8.0-beta11.apk`。上传后核对 GitHub 附件的 `digest` 为正确的 `sha256:...`，并核对公开下载文件。测试版标记为 prerelease；用户需开启测试版渠道。

仅 `git push`、创建标签或构建成功不会自动发布可更新 APK。现有 GitHub Actions 执行测试、Lint 和 Debug 构建，没有签名发布流水线。更新器要求版本高于已安装版本、包名和签名相同、附件名称与版本一致、大小和 SHA-256 正确；同版本重建不构成在线更新。

## 验证范围

恢复包内的《验证记录.json》记录实际演练环境、源码与签名摘要、产物和检查结果。使用已有 SDK 配合新的源码目录及 Gradle 缓存构建，仍不等同于已实际重装操作系统；主机侧签名及版本检查也不代替手机覆盖安装和功能验收。

参考：[Android 应用签名](https://developer.android.com/studio/publish/app-signing)、[SDK Manager](https://developer.android.com/tools/sdkmanager)。
