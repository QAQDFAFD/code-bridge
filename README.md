# CodeBridge

[中文说明](README.zh-CN.md)

CodeBridge is a tiny local-first OTP relay for Android and macOS.

When your Android phone receives a verification code, CodeBridge sends it to your Mac over the local network. The Mac menu bar app copies the code to the clipboard, shows a notification, and keeps a short recent-code history.

## Status

Working MVP. The first useful loop is done:

1. Run the macOS app.
2. Pair the Android app with the Mac host, port, and token.
3. Send a test code from Android (or receive a real SMS).
4. The code appears on the Mac clipboard and in the menu bar history.

- [`apps/macos`](apps/macos) — macOS menu bar receiver, written in Swift.
- [`apps/android`](apps/android) — Android sender, written in Kotlin and Jetpack Compose.
- [`docs/protocol.md`](docs/protocol.md) — the local HTTP protocol shared by both apps.

## Product Principles

- Fast: receive, copy, and notify in about one second on a local network.
- Quiet: stay out of the way until a code arrives.
- Local-first: no cloud service in the MVP.
- Short-lived: recent codes are kept only for quick reuse.

## Quick Start

### macOS

Requirements: macOS 14 or newer, Swift 6 toolchain.

```bash
cd apps/macos
swift run CodeBridgeMac
```

A **random pairing token is generated on first launch**. Open the menu bar item → **Settings…** and a **pairing QR code** is shown (encoding the Mac's name, LAN address, port, and token). The Android app can scan it to pair — manual entry of the same values also works.

### Android

Requirements: Android Studio, a recent Android SDK, JDK 17+, and a physical Android phone.

Open `apps/android` in Android Studio and run the `app` module on the phone. Then:

1. Grant SMS permissions when asked (some phones also need battery optimization disabled for reliable background delivery).
2. Tap **Scan QR Code to Pair** and point the camera at the QR code in the Mac's Settings — manual host/port/token entry is still available under "Manual setup".
3. Once paired, the app re-connects automatically whenever the phone and Mac are on the same Wi-Fi. This works in the background too: SMS forwarding comes from a system broadcast receiver, and a WorkManager job re-probes paired Macs on every network change — you don't need to open the app. On aggressive OEM ROMs (OPPO/OnePlus/Xiaomi…), disable battery optimization and allow Auto-start for reliable background delivery.
4. Tap **Send Test Code** — the code should land on your Mac clipboard.

Note: running the Mac app via `swift run` is not a proper `.app` bundle, so system notifications are skipped in dev mode; clipboard, menu bar history, and the server all work.

### Verify locally

With the Mac app running:

```bash
./scripts/send-test-code.sh 127.0.0.1 47821 <your-token>
```

A `{"ok":true}` response and a clipboard containing the test code means the pipeline works.

## Security & Privacy

- **Traffic is plain HTTP on your LAN.** Anyone on the same network can read relayed codes. Only use CodeBridge on networks you trust — see [SECURITY.md](SECURITY.md).
- Requests require the pairing token; it is generated randomly on first launch and shown in the Mac menu bar.
- Codes live only in memory on the Mac (no persistence); the Android app stores nothing beyond connection settings.
- The Android app needs SMS permissions; it is intended for personal side-loading. Review Google Play's SMS permission policy before any public distribution.

## Repository Structure

```text
apps/
  android/   Android sender (Kotlin + Compose)
  macos/     macOS menu bar receiver (Swift)
docs/
  protocol.md   Local HTTP protocol
scripts/
  send-test-code.sh   CLI smoke test against the Mac receiver
.github/workflows/    CI (Swift tests + Android unit tests)
```

## Development

```bash
# macOS
cd apps/macos && swift test

# Android unit tests
cd apps/android && ./gradlew :app:testDebugUnitTest
```

Parsing and history logic live in testable core modules (`CodeBridgeCore` on macOS, `OtpExtractor` on Android). Protocol changes must update `docs/protocol.md` and both apps — see [CONTRIBUTING.md](CONTRIBUTING.md).

## Roadmap

- [ ] QR-code pairing
- [ ] Permission diagnostics on Android
- [ ] Pause while the Mac is locked; auto-clear clipboard
- [ ] Proper `.app` bundle with notifications and auto-start
- [ ] HTTPS / local certificate pinning

## Contributing

Issues and pull requests are welcome — read [CONTRIBUTING.md](CONTRIBUTING.md) first, and never paste real verification codes or tokens into tickets.

## License

[MIT](LICENSE)
