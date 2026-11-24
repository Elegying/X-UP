# X-UP 开发交接

接手先读 [项目现状与开发交接](docs/project-status.md)，再按任务查对应源码和版本验收记录。[README](README.md) 说明用户功能和安装方式，[构建与签名恢复](docs/build-recovery.md) 说明依赖和发布流程。

- 开始前用 `git status --short --branch` 查看工作区，保留已有修改。涉及发布或 X 适配时，分别核对 GitHub Release 与实际测试设备上的 X 版本。
- 模块入口为 `src/io/github/jared/xlowerseek/XLowerSeek.java`，Xposed 元数据位于 `resources/META-INF/xposed/`。修改兼容匹配时从已定位的功能入口着手，保留旧结构的支持，并沿用 `TargetResolver` 的唯一匹配和 `Hooks` 的事务安装、异常回退。
- 快速回归用 `bash test.sh`。完整的本地构建与检查命令见 [构建与验证](docs/project-status.md#构建与验证)。报告主机测试、Debug 构建、签名核对和实机验收各自实际完成的范围。
- 发布包名为 `io.github.jared.xlowerseek`。在有旧版和模型的测试机上，用同一签名覆盖安装以保留数据。私钥和凭据从维护者的恢复资料获取，留在仓库外。
