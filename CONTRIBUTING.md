# Contributing to CodeBridge

Thanks for your interest in improving CodeBridge!

## Getting started

CodeBridge is a monorepo with two apps that talk over the protocol in
[docs/protocol.md](docs/protocol.md):

- `apps/macos` — macOS menu bar receiver (Swift, SwiftPM)
- `apps/android` — Android sender (Kotlin, Jetpack Compose, Gradle)

Before opening a PR, please make sure the relevant checks pass locally.

### macOS

```bash
cd apps/macos
swift build
swift test
```

### Android

Open `apps/android` in Android Studio, or from the command line:

```bash
cd apps/android
./gradlew :app:testDebugUnitTest
```

## Guidelines

- Keep the product principles in mind: fast, quiet, local-first, short-lived.
- Protocol changes must update [docs/protocol.md](docs/protocol.md) and both apps.
- New parsing/history logic belongs in the pure core modules (`CodeBridgeCore`,
  `OtpExtractor`) so it stays unit-testable.
- Add or update tests for the behavior you change.
- Keep the MVP scope lean — large features are best discussed in an issue first.

## Reporting bugs

Open an issue with: the app (macOS/Android), OS version, what you expected,
and what happened. Never paste real verification codes or tokens — redact them.
