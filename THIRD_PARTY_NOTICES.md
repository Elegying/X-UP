# 第三方依赖

- llama.cpp，MIT。固定到 1e411d8f5a1e23525fa3265dfb4bd76265465397；本项目修改 GGUF 旧编号兼容和 ARM STQ 运行时分发。内核复制部分同样适用 MIT，完整许可见 assets/licenses/NOTICE.txt。
- 腾讯 HY-MT2 模型权重从腾讯官方渠道独立下载，不包含在 APK 或源码仓库中。Apache-2.0 许可见 licenses/HY-MT-License.txt。
- libxposed API / service / interface 102。出处和原始依赖说明见 libs/README.md。
- DexKit 2.3.0，Apache-2.0。用于识别目标 APK 中的功能结构；来源：https://github.com/LuckyPray/DexKit。完整许可见 licenses/DexKit-Apache-2.0.txt 及 APK 内 assets/licenses/NOTICE.txt。
- Android Gradle Plugin、Gradle wrapper、JUnit：构建及测试工具，沿用各自许可。
