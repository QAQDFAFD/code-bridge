# CodeBridge

[English](README.md)

CodeBridge 是一个本地优先的验证码中继工具，面向 Android 手机和 macOS。

当 Android 手机收到短信验证码后，CodeBridge 会通过局域网把验证码发送到 Mac。Mac 菜单栏应用收到后会自动复制到剪贴板，并在菜单栏里保留最近验证码历史。

## 当前状态

可用的 MVP，第一条完整链路已经跑通：

1. 启动 macOS 应用。
2. 在 Android 应用里配置 Mac 的地址、端口和 token，完成配对。
3. 从 Android 发送测试验证码（或接收真实短信）。
4. 验证码出现在 Mac 剪贴板和菜单栏历史中。

- [`apps/macos`](apps/macos)：macOS 菜单栏接收端，使用 Swift 编写。
- [`apps/android`](apps/android)：Android 发送端，使用 Kotlin 和 Jetpack Compose 编写。
- [`docs/protocol.md`](docs/protocol.md)：两端共用的本地 HTTP 协议。

## 产品原则

- 快：在局域网内，尽量做到 1 秒左右完成接收、复制和提示。
- 静：平时不打扰，只在验证码到达时出现。
- 本地优先：MVP 不依赖云服务。
- 短暂保留：验证码只用于快速粘贴和短时间内再次复制。

## 快速开始

### macOS

要求：macOS 14 或更新版本，Swift 6 工具链。

```bash
cd apps/macos
swift run CodeBridgeMac
```

**首次启动会自动生成随机配对 token**。点菜单栏图标 → **Settings…** 会显示**配对二维码**（内容包含 Mac 名称、局域网地址、端口和 token），Android 端扫码即可配对；手动输入这些值也可以。

### Android

要求：Android Studio、较新的 Android SDK、JDK 17 或更新版本、一台 Android 真机。

用 Android Studio 打开 `apps/android`，运行 `app` 模块到真机，然后：

1. 按提示授予短信权限（部分手机还需要关闭电池优化，后台接收才稳定）。
2. 点 **Scan QR Code to Pair**，对准 Mac 设置弹窗里的二维码即可完成配对；"Manual setup" 里仍可手动填地址/端口/token。
3. 配对一次后，只要手机和 Mac 连到同一个 Wi-Fi，App 会自动重新连接。**后台同样生效**：短信转发走系统广播接收器，WorkManager 会在每次网络变化时自动探测已配对的 Mac，无需打开 App。在 OPPO/一加/小米等激进省电的机型上，请关闭电池优化并允许自启动，后台才稳定。
4. 点 **Send Test Code**，验证码应该出现在 Mac 剪贴板。

注意：`swift run` 直接运行时不是标准 `.app` bundle，开发态会跳过系统通知，但接收验证码、复制剪贴板和菜单栏历史都正常。

### 本地验证

Mac 应用启动后，从仓库根目录执行：

```bash
./scripts/send-test-code.sh 127.0.0.1 47821 <你的token>
```

返回 `{"ok":true}` 且剪贴板出现测试验证码，说明链路已跑通。

## 安全与隐私

- **流量是局域网内的明文 HTTP**，同一网络内的其他人可以读到转发的验证码。请只在可信网络中使用，详见 [SECURITY.md](SECURITY.md)。
- 请求必须携带配对 token；token 在首次启动时随机生成，显示在 Mac 菜单栏设置里。
- 验证码在 Mac 端只保存在内存中（不落盘）；Android 端除连接配置外不保存任何数据。
- Android 应用需要短信权限，适合个人自用侧载。如要公开发布或上架，请先仔细检查 Google Play 对短信权限的政策要求。

## 仓库结构

```text
apps/
  android/   Android 发送端（Kotlin + Compose）
  macos/     macOS 菜单栏接收端（Swift）
docs/
  protocol.md   本地 HTTP 协议
scripts/
  send-test-code.sh   针对接收端的命令行冒烟测试
.github/workflows/    CI（Swift 测试 + Android 单元测试）
```

## 开发

```bash
# macOS
cd apps/macos && swift test

# Android 单元测试
cd apps/android && ./gradlew :app:testDebugUnitTest
```

解析和历史逻辑放在可单元测试的核心模块里（macOS 的 `CodeBridgeCore`、Android 的 `OtpExtractor`）。改动协议时必须同步更新 `docs/protocol.md` 和两端实现，详见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 路线图

- [ ] 二维码配对
- [ ] Android 权限诊断
- [ ] Mac 锁屏暂停、剪贴板自动清理
- [ ] 打包正式 `.app`，启用通知和开机自启
- [ ] HTTPS / 本地证书锁定

## 参与贡献

欢迎提 issue 和 PR——请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md)，并且不要在工单里粘贴真实验证码或 token。

## 许可证

[MIT](LICENSE)
