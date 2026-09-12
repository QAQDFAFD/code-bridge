# CodeBridge

[中文说明](README.zh-CN.md)

CodeBridge is a tiny local-first OTP relay for Android and macOS.

When your Android phone receives a verification code, CodeBridge sends it to your Mac over the local network. The Mac menu bar app copies the code to the clipboard, shows a notification, and keeps a short recent-code history.

## Features

- **SMS → clipboard in about a second** on a local network: the Android app parses the OTP from incoming SMS and POSTs it to the Mac, which copies it and notifies you.
- **QR pairing**: the Mac menu bar shows a QR code encoding its name, LAN address, port, and token; scan it with the phone to pair. Manual entry also works.
- **Multi-Mac support**: pair several Macs; the active receiver is highlighted and one tap switches between them.
- **Auto-connect**: whenever the phone joins a network (e.g. you get home), paired Macs are probed and the reachable one becomes active — in the background too, no app launch needed.
- **Background forwarding**: SMS forwarding runs from a system broadcast receiver; it does not require the app to be open.
- **Random per-install token**: generated on first launch, required for every request.

## How It Works

1. The Mac menu bar app runs a small token-authenticated HTTP server (default port `47821`) on your LAN.
2. The Android app scans the pairing QR (or enters host/port/token), verifies reachability with `GET /v1/ping`, and stores the Mac.
3. On incoming SMS, the phone extracts the OTP ([OtpExtractor](apps/android/app/src/main/java/dev/codebridge/app/sms/OtpExtractor.kt)) and POSTs it to the active Mac.
4. The Mac validates the request, copies the code to the clipboard, posts a notification, and prepends it to the menu bar history (relative timestamps refresh every time you open the menu).

The wire protocol is documented in [`docs/protocol.md`](docs/protocol.md).

## Quick Start

### macOS

Requirements: macOS 14 or newer, Swift 6 toolchain.

```bash
cd apps/macos
swift run CodeBridgeMac
```

A **random pairing token is generated on first launch**. Open the menu bar item → **Settings…** to see the **pairing QR code** plus the address and token for manual entry.

Note: `swift run` is not a proper `.app` bundle, so system notifications are skipped in dev mode; clipboard, menu bar history, and the server all work.

### Android

Requirements: Android Studio, a recent Android SDK, JDK 17+, and a physical Android phone (emulators cannot receive SMS).

Open `apps/android` in Android Studio and run the `app` module on the phone, or build an APK:

```bash
cd apps/android
./gradlew :app:assembleDebug
# app/build/outputs/apk/debug/app-debug.apk (debug-signed, fine for side-loading)
```

Then:

1. Open CodeBridge → **Add Mac** → **Scan QR**, and point the camera at the QR code in the Mac's Settings (Manual Setup also available).
2. Grant the permissions listed on the home screen. **SMS access** and **Ignore battery optimization** are required for background forwarding; on OPPO/OnePlus/Xiaomi-style ROMs, also enable **Auto-start** (the app can jump you into the right settings area — the status itself is not queryable).
3. Tap **Send Test Code** (Manual Setup tab) — the code should land on your Mac clipboard.

### Verify locally

With the Mac app running:

```bash
./scripts/send-test-code.sh 127.0.0.1 47821 <your-token>
```

A `{"ok":true}` response and a clipboard containing the test code means the pipeline works.

## Background Behavior

- SMS forwarding is driven by a manifest broadcast receiver — it works with the app closed, as long as the OS hasn't killed the app (grant battery-optimization exemption / Auto-start on aggressive ROMs).
- A WorkManager job re-probes paired Macs on every network change and activates the reachable one, without opening the app.
- The foreground UI shows live connection status per paired Mac.

## Security & Privacy

- **Traffic is plain HTTP on your LAN.** Anyone on the same network can read relayed codes. Only use CodeBridge on networks you trust — see [SECURITY.md](SECURITY.md).
- Requests require the pairing token; it is generated randomly on first launch and shown in the Mac menu bar.
- Codes live only in memory on the Mac (no persistence); the Android app stores nothing beyond paired-device settings.
- The Android app needs SMS permissions; it is intended for personal side-loading. Review Google Play's SMS permission policy before any public distribution.

## Repository Structure

```text
apps/
  android/   Android sender (Kotlin + Compose, CameraX + ZXing for QR pairing)
  macos/     macOS menu bar receiver (Swift, Network.framework HTTP server)
docs/
  protocol.md   Local HTTP protocol (/v1/codes, /v1/ping)
scripts/
  send-test-code.sh   CLI smoke test against the Mac receiver
.github/workflows/    CI (Swift tests + Android unit tests)
```

## Development

```bash
# macOS tests (unit + real-TCP end-to-end)
cd apps/macos && swift test

# Android unit tests (parser, relay client against MockWebServer, settings)
cd apps/android && ./gradlew :app:testDebugUnitTest
```

Parsing and history logic live in testable core modules (`CodeBridgeCore` on macOS, `OtpExtractor` on Android). Protocol changes must update `docs/protocol.md` and both apps — see [CONTRIBUTING.md](CONTRIBUTING.md).

## Roadmap

- [x] macOS menu bar receiver with token auth
- [x] Android SMS parsing and relay
- [x] QR-code pairing
- [x] Multi-Mac management with auto-connect
- [x] Permission diagnostics on Android
- [ ] Pause while the Mac is locked; auto-clear clipboard
- [ ] Proper `.app` bundle with notifications and auto-start
- [ ] HTTPS / local certificate pinning

## Contributing

Issues and pull requests are welcome — read [CONTRIBUTING.md](CONTRIBUTING.md) first, and never paste real verification codes or tokens into tickets.

## License

[MIT](LICENSE)
