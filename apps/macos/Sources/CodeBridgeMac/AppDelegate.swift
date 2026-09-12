import AppKit
import CodeBridgeCore
import Foundation
import UserNotifications

@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate {
    private let statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
    private let settings = SettingsStore()
    private var history = CodeHistory()
    private var server: CodeHTTPServer?
    private var isReceiving = true
    private var serverStatusLine = "Starting…"
    private var titleResetTask: Task<Void, Never>?

    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory)
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
        server = CodeHTTPServer(port: settings.port, tokenProvider: { token })
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
    }

    private func handle(_ event: CodeEvent) {
        guard isReceiving else { return }

        history.add(event)
        ClipboardWriter.copy(event.code)
        showNotification(for: event)
        flashStatusItem()
        rebuildMenu()
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

        let clear = NSMenuItem(title: "Clear History", action: #selector(clearHistory), keyEquivalent: "")
        clear.target = self
        menu.addItem(clear)

        menu.addItem(.separator())
        menu.addItem(NSMenuItem(title: "Quit", action: #selector(quit), keyEquivalent: "q"))

        statusItem.menu = menu
    }

    @objc private func copyHistoryItem(_ sender: NSMenuItem) {
        guard let event = sender.representedObject as? CodeEvent else { return }
        ClipboardWriter.copy(event.code)
        flashStatusItem(with: "Copied")
    }

    @objc private func toggleReceiving() {
        isReceiving.toggle()
        rebuildMenu()
    }

    @objc private func clearHistory() {
        history.clear()
        rebuildMenu()
    }

    @objc private func openSettings() {
        let alert = NSAlert()
        alert.messageText = "CodeBridge Settings"
        alert.informativeText = """
        Host: this Mac on your local network
        Port: \(settings.port)
        Token: \(settings.token)

        Use these values in the Android app. The token is stored locally for the MVP.
        """
        alert.addButton(withTitle: "OK")
        alert.runModal()
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
