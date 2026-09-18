import AppKit
import CodeBridgeCore
import Foundation
import ServiceManagement
import UserNotifications

@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate, NSMenuDelegate {
    private let statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
    private let settings = SettingsStore()
    private var history = CodeHistory()
    private var server: CodeHTTPServer?
    private var isReceiving = true
    private var serverStatusLine = "Starting…"
    private var titleResetTask: Task<Void, Never>?
    private let toast = CodeToast()
    private let advertiser = PairingServiceAdvertiser()
    private let historyStore = HistoryStore()

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory)
        history = CodeHistory(events: historyStore.load())
        configureStatusItem()
        requestNotificationPermission()
        startServer()
        rebuildMenu()
    }

    func applicationWillTerminate(_ notification: Notification) {
        server?.stop()
    }

    private func configureStatusItem() {
        statusItem.button?.title = "CB"
        statusItem.button?.toolTip = "CodeBridge"
    }

    private func startServer() {
        let token = settings.token
        let name = SettingsStore.deviceName()
        server = CodeHTTPServer(port: settings.port, tokenProvider: { token }, nameProvider: { name })
        server?.onReceive = { [weak self] event in
            Task { @MainActor in
                self?.handle(event)
            }
        }
        server?.onStateChange = { [weak self] state in
            Task { @MainActor in
                guard let self else { return }
                switch state {
                case .listening(let port):
                    self.serverStatusLine = "Listening on :\(port)"
                case .failed(let reason):
                    self.serverStatusLine = "Server error: \(reason)"
                case .stopped:
                    self.serverStatusLine = "Server stopped"
                }
                self.rebuildMenu()
            }
        }
        server?.start()
        advertiser.start(name: SettingsStore.deviceName(), port: settings.port)
    }

    private func handle(_ event: CodeEvent) {
        guard isReceiving else { return }

        history.add(event)
        historyStore.save(history.events)
        copyToClipboard(event.code)
        toast.show(code: event.code)
        showNotification(for: event)
        flashStatusItem()
        rebuildMenu()
    }

    /// Copies the code; when auto-clear is enabled, wipes it again after
    /// 60s — but only if the user hasn't copied something else in between.
    private func copyToClipboard(_ code: String) {
        ClipboardWriter.copy(code)
        guard settings.autoClearsClipboard else { return }
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(60))
            ClipboardWriter.clearIfStill(code)
        }
    }

    private func rebuildMenu() {
        let menu = NSMenu()

        let title = NSMenuItem(title: "CodeBridge", action: nil, keyEquivalent: "")
        title.isEnabled = false
        menu.addItem(title)

        let stateTitle = isReceiving ? serverStatusLine : "Paused"
        let state = NSMenuItem(title: stateTitle, action: nil, keyEquivalent: "")
        state.isEnabled = false
        menu.addItem(state)
        menu.addItem(.separator())

        if history.events.isEmpty {
            let empty = NSMenuItem(title: "No codes yet", action: nil, keyEquivalent: "")
            empty.isEnabled = false
            menu.addItem(empty)
        } else {
            for event in history.events {
                let item = NSMenuItem(
                    title: "\(event.sender)    \(event.code)    \(relativeTime(event.receivedAt))",
                    action: #selector(copyHistoryItem(_:)),
                    keyEquivalent: ""
                )
                item.target = self
                item.representedObject = event
                menu.addItem(item)
            }
        }

        menu.addItem(.separator())

        let pauseTitle = isReceiving ? "Pause Receiving" : "Resume Receiving"
        let pause = NSMenuItem(title: pauseTitle, action: #selector(toggleReceiving), keyEquivalent: "")
        pause.target = self
        menu.addItem(pause)

        let settingsItem = NSMenuItem(title: "Settings...", action: #selector(openSettings), keyEquivalent: ",")
        settingsItem.target = self
        menu.addItem(settingsItem)

        let autoClear = NSMenuItem(
            title: "Auto-clear clipboard (60s)",
            action: #selector(toggleAutoClear),
            keyEquivalent: ""
        )
        autoClear.target = self
        autoClear.state = settings.autoClearsClipboard ? .on : .off
        menu.addItem(autoClear)

        if Bundle.main.bundleIdentifier != nil {
            let loginItem = NSMenuItem(
                title: "Start at Login",
                action: #selector(toggleStartAtLogin),
                keyEquivalent: ""
            )
            loginItem.target = self
            loginItem.state = isRegisteredForLogin ? .on : .off
            menu.addItem(loginItem)
        }

        let clear = NSMenuItem(title: "Clear History", action: #selector(clearHistory), keyEquivalent: "")
        clear.target = self
        menu.addItem(clear)

        menu.addItem(.separator())
        menu.addItem(NSMenuItem(title: "Quit", action: #selector(quit), keyEquivalent: "q"))

        menu.delegate = self
        statusItem.menu = menu
    }

    /// Recomputes relative timestamps every time the menu is shown, instead of
    /// showing the time frozen at the last rebuild.
    func menuWillOpen(_ menu: NSMenu) {
        for item in menu.items {
            guard let event = item.representedObject as? CodeEvent else { continue }
            item.title = "\(event.sender)    \(event.code)    \(relativeTime(event.receivedAt))"
        }
    }

    @objc private func copyHistoryItem(_ sender: NSMenuItem) {
        guard let event = sender.representedObject as? CodeEvent else { return }
        copyToClipboard(event.code)
        toast.show(code: event.code)
        flashStatusItem(with: "Copied")
    }

    @objc private func toggleReceiving() {
        isReceiving.toggle()
        rebuildMenu()
    }

    @objc private func clearHistory() {
        history.clear()
        historyStore.clear()
        rebuildMenu()
    }

    @objc private func toggleAutoClear() {
        settings.autoClearsClipboard.toggle()
        rebuildMenu()
    }

    @objc private func openSettings() {
        let addresses = SettingsStore.lanIPv4Addresses()
        let host = addresses.first ?? "127.0.0.1"
        let payload = PairingPayload(
            name: SettingsStore.deviceName(),
            host: host,
            port: settings.port,
            token: settings.token
        )

        let alert = NSAlert()
        alert.messageText = "Pair with the Android app"
        alert.informativeText = """
        Scan this QR code in CodeBridge for Android, or enter the values manually.

        Device: \(payload.name)
        Address: \(addresses.isEmpty ? "\(host) (no LAN IPv4 found)" : addresses.joined(separator: ", ")):\(payload.port)
        Token: \(payload.token)

        The Android app auto-connects to a paired Mac whenever both are on the same Wi-Fi.
        """
        if let json = payload.jsonString, let qr = QRCodeRenderer.image(for: json) {
            let view = NSImageView(frame: NSRect(x: 0, y: 0, width: 220, height: 220))
            view.image = qr
            alert.accessoryView = view
        }
        alert.addButton(withTitle: "Done")
        alert.addButton(withTitle: "Regenerate Token")
        let response = alert.runModal()

        if response == .alertSecondButtonReturn {
            settings.storeToken(SettingsStore.generateToken())
            server?.stop()
            startServer()
            // Reopen so the new QR/token is visible; paired phones must re-scan.
            openSettings()
        }
    }

    private var isRegisteredForLogin: Bool {
        SMAppService.mainApp.status == .enabled
    }

    @objc private func toggleStartAtLogin() {
        do {
            if isRegisteredForLogin {
                try SMAppService.mainApp.unregister()
            } else {
                try SMAppService.mainApp.register()
            }
        } catch {
            NSLog("CodeBridge login item toggle failed: \(error.localizedDescription)")
        }
        rebuildMenu()
    }

    @objc private func quit() {
        NSApplication.shared.terminate(nil)
    }

    private func flashStatusItem(with title: String = "*") {
        statusItem.button?.title = title
        titleResetTask?.cancel()
        titleResetTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(1.2))
            guard !Task.isCancelled else { return }
            self.statusItem.button?.title = "CB"
        }
    }

    private func showNotification(for event: CodeEvent) {
        guard Bundle.main.bundleIdentifier != nil else {
            return
        }

        let content = UNMutableNotificationContent()
        content.title = "Code copied"
        content.body = "\(event.sender): \(event.code)"
        let request = UNNotificationRequest(identifier: event.id, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }

    private func requestNotificationPermission() {
        guard Bundle.main.bundleIdentifier != nil else {
            return
        }

        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
    }

    private func relativeTime(_ date: Date) -> String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .short
        return formatter.localizedString(for: date, relativeTo: Date())
    }
}
