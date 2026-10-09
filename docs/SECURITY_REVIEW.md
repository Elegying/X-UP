# 安全告警复核（2026-10-09）

## CodeQL #1、#2：第三方端点证书固定

UpdateClient 连接 GitHub 官方 API，ModelTransfer 连接固定的第三方模型地址及 HTTPS CDN 重定向。
代码没有自定义信任全部证书的 TrustManager 或跳过主机名验证的 HostnameVerifier。
保留 Android 平台的系统 CA、证书链和主机名验证；应用显式禁止明文连接，且不信任用户添加的 CA。
模型还需要通过代码固定的 SHA-256 和大小验证后才原子替换现有模型。

本项目不拥有 GitHub、ModelScope、Hugging Face 及 CDN 的证书私钥，也无法安排它们的备用密钥轮换。
因此不对这些第三方证书做硬编码固定。这是明确的设计取舍，仍信任系统 CA；它不能防御受信任 CA 误签。
若将来改为自有下载服务，应重新评估证书固定和独立签名元数据。
这两条以 `won't fix` 记录，不宣称已经实现证书固定，也不全局禁用扫描规则。

依据：[Android 官方 TLS 指南](https://developer.android.com/privacy-and-security/security-ssl#Pinning)。

## CodeQL #3：测试临时文件权限

将 File.createTempFile 改为 Files.createTempFile，使 POSIX 系统上的测试 ZIP 默认仅当前用户可读写。
回归检查无组/其他用户访问权限，并继续验证解包、目录穿越、损坏内容及取消时不发布模型。
