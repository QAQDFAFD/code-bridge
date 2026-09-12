# CodeBridge

[English](README.md)

CodeBridge 是一个本地优先的验证码中继工具，面向 Android 手机和 macOS。

当 Android 手机收到短信验证码后，CodeBridge 会通过局域网把验证码发送到 Mac。Mac 菜单栏应用收到后会自动复制到剪贴板，并在菜单栏里保留最近验证码历史。

## 功能特性

- **短信到剪贴板约 1 秒**：Android 端解析短信中的验证码并通过局域网发送，Mac 端自动复制并通知。
- **Mac 端复制 Toast**：每次复制都会在屏幕顶部弹出带绿色对勾的“已复制 &lt;code&gt;”浮动提示——即时可见的确认，开发模式（无系统通知）下同样生效。
- **扫码配对**：Mac 菜单栏显示二维码（包含设备名、局域网地址、端口和 token），手机扫码即完成配对；也支持手动输入。
- **多 Mac 管理**：可配对多台 Mac，当前使用的设备高亮标记，点击即切换。
- **自动连接**：手机加入网络（比如回到家连上 Wi-Fi）时自动探测已配对的 Mac 并切换，**后台同样生效，无需打开 App**。
- **后台转发**：短信转发由系统广播接收器驱动，App 不打开也能工作。
- **随机 token**：首次启动自动生成，所有请求必须携带。

## 工作原理

1. Mac 菜单栏应用在局域网内运行一个需要 token 鉴权的小型 HTTP 服务（默认端口 `47821`）。
2. Android 扫码（或手动输入地址/端口/token），用 `GET /v1/ping` 验证连通后保存这台 Mac。
3. 收到短信时，手机解析出验证码（[OtpExtractor](apps/android/app/src/main/java/dev/codebridge/app/sms/OtpExtractor.kt)）并 POST 到当前活跃的 Mac。
4. Mac 校验请求、复制验证码到剪贴板、弹出“已复制 &lt;code&gt;”浮动提示、发通知（打包为正式 App 时）、写入菜单栏历史（每次打开菜单都会刷新相对时间）。

协议细节见 [`docs/protocol.md`](docs/protocol.md)。

## 快速开始

### macOS

要求：macOS 14 或更新版本，Swift 6 工具链。

```bash
cd apps/macos
swift run CodeBridgeMac
```

**首次启动会自动生成随机配对 token**。点菜单栏图标 → **Settings…** 可以看到**配对二维码**，以及手动输入用的地址和 token。

注意：`swift run` 直接运行不是标准 `.app` bundle，开发态会跳过系统通知，但接收验证码、复制剪贴板和菜单栏历史都正常。

### Android

要求：Android Studio、较新的 Android SDK、JDK 17 或更新版本、一台 Android 真机（模拟器收不了短信）。

用 Android Studio 打开 `apps/android` 运行 `app` 模块，或直接打 APK：

```bash
cd apps/android
./gradlew :app:assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk（调试签名，自用侧载足够）
```

然后：

1. 打开 CodeBridge → **Add Mac** → **Scan QR**，对准 Mac 设置弹窗里的二维码（Manual Setup 页可手动输入）。
2. 授予首页权限列表中的权限。**SMS access** 和 **Ignore battery optimization** 是后台转发必需的；OPPO/一加/小米等机型还需开启 **Auto-start**（点该行可直接跳到对应设置区域——状态本身无法查询）。
3. 在 Manual Setup 页点 **Send Test Code**，验证码应该出现在 Mac 剪贴板。

### 本地验证

Mac 应用启动后，从仓库根目录执行：

```bash
./scripts/send-test-code.sh 127.0.0.1 47821 <你的token>
```

返回 `{"ok":true}` 且剪贴板出现测试验证码，说明链路已跑通。

## 后台行为

- 短信转发由 manifest 广播接收器驱动——App 关闭也能工作，前提是系统没有杀掉进程（激进省电的机型请授予电池优化豁免/自启动）。
- WorkManager 任务在每次网络变化时重新探测已配对的 Mac 并激活可达的设备，无需打开 App。
- 前台界面对每台已配对的 Mac 显示实时连接状态。

## 安全与隐私

- **流量是局域网内的明文 HTTP**，同一网络内的其他人可以读到转发的验证码。请只在可信网络中使用，详见 [SECURITY.md](SECURITY.md)。
- 请求必须携带配对 token；token 在首次启动时随机生成，显示在 Mac 菜单栏设置里。
- 验证码在 Mac 端只保存在内存中（不落盘）；Android 端除已配对设备的连接配置外不保存任何数据。
- Android 应用需要短信权限，适合个人自用侧载。如要公开发布或上架，请先仔细检查 Google Play 对短信权限的政策要求。

## 仓库结构

```text
apps/
  android/   Android 发送端（Kotlin + Compose，CameraX + ZXing 扫码配对）
  macos/     macOS 菜单栏接收端（Swift，Network.framework HTTP 服务）
docs/
  protocol.md   本地 HTTP 协议（/v1/codes、/v1/ping）
scripts/
  send-test-code.sh   针对接收端的命令行冒烟测试
.github/workflows/    CI（Swift 测试 + Android 单元测试）
```

## 开发

```bash
# macOS 测试（单元 + 真 TCP 端到端）
cd apps/macos && swift test

# Android 单元测试（解析、RelayClient 对 MockWebServer、设置）
cd apps/android && ./gradlew :app:testDebugUnitTest
```

解析和历史逻辑放在可单元测试的核心模块里（macOS 的 `CodeBridgeCore`、Android 的 `OtpExtractor`）。改动协议时必须同步更新 `docs/protocol.md` 和两端实现，详见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 路线图

- [x] macOS 菜单栏接收端（token 鉴权）
- [x] Android 短信解析与转发
- [x] 二维码配对
- [x] 多 Mac 管理与自动连接
- [x] Android 权限诊断
- [ ] Mac 锁屏暂停、剪贴板自动清理
- [ ] 打包正式 `.app`，启用通知和开机自启
- [ ] HTTPS / 本地证书锁定

## 参与贡献

欢迎提 issue 和 PR——请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md)，并且不要在工单里粘贴真实验证码或 token。

## 许可证

[MIT](LICENSE)
