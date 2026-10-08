# API 102 依赖

本项目使用 Maven Central 发布的 libxposed 102.0.0，许可证 Apache-2.0，见 resources/META-INF/LICENSE-libxposed.txt。API 仅参与编译，不打入 APK；service 和 interface 参与打包。

原始来源：
- https://repo.maven.apache.org/maven2/io/github/libxposed/api/102.0.0/api-102.0.0.aar
- https://repo.maven.apache.org/maven2/io/github/libxposed/service/102.0.0/service-102.0.0.aar
- https://repo.maven.apache.org/maven2/io/github/libxposed/interface/102.0.0/interface-102.0.0.aar

各 jar 为对应 AAR 的 classes.jar，未经修改。模块不再引用或打包 legacy API 82。

- libxposed-api-102.jar: SHA-256 `a515dd7a53cd7a47c05e101dff77d61acb3091a97b20a885b9ea3494412db985`
- libxposed-interface-102.jar: SHA-256 `08711eceb0ffb64cbade3536fa7bb48a5fe3420dfdb1e3edde12cea006ae207f`
- libxposed-service-102.jar: SHA-256 `b07bc86b9da1e9adc4fd967d5f27a37f1907e8234594a20e6928c78981f0f37f`

## 1.8.0-beta1 本地翻译

移除 ML Kit 翻译和语言识别依赖。使用源码构建的 llama.cpp CPU 推理内核，模型由用户从腾讯官方渠道独立下载。原生内核版本、校验和及补丁见 `scripts/prepare-native.sh` 和 `native/patches`。
