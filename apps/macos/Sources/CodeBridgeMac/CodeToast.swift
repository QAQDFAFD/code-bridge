import AppKit

/// Floating "已复制 <code>" toast shown briefly when a code is copied.
/// Borderless, non-activating, click-through — it never steals focus.
@MainActor
final class CodeToast {
    private var panel: NSPanel?
    private var codeLabel: NSTextField?
    private var hideTask: Task<Void, Never>?

    func show(code: String) {
        let panel = self.panel ?? makePanel()
        self.panel = panel
        codeLabel?.stringValue = code
        layout(panel: panel)

        panel.alphaValue = 0
        panel.orderFrontRegardless()
        NSAnimationContext.runAnimationGroup { context in
            context.duration = 0.18
            panel.animator().alphaValue = 1
        }

        hideTask?.cancel()
        hideTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(2.2))
            guard !Task.isCancelled, let self else { return }
            self.dismiss()
        }
    }

    private func dismiss() {
        guard let panel else { return }
        NSAnimationContext.runAnimationGroup { context in
            context.duration = 0.25
            panel.animator().alphaValue = 0
        }
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(300))
            if panel.alphaValue == 0 {
                panel.orderOut(nil)
            }
        }
    }

    private func layout(panel: NSPanel) {
        let size = panel.contentView?.fittingSize ?? NSSize(width: 180, height: 44)
        panel.setContentSize(size)

        if let visibleFrame = NSScreen.main?.visibleFrame {
            let origin = NSPoint(
                x: visibleFrame.midX - size.width / 2,
                y: visibleFrame.maxY - size.height - 8
            )
            panel.setFrameOrigin(origin)
        }
    }

    private func makePanel() -> NSPanel {
        let panel = NSPanel(
            contentRect: .zero,
            styleMask: [.borderless, .nonactivatingPanel],
            backing: .buffered,
            defer: false
        )
        panel.isOpaque = false
        panel.backgroundColor = .clear
        panel.hasShadow = true
        panel.level = .statusBar
        panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
        panel.ignoresMouseEvents = true
        panel.isReleasedWhenClosed = false
        panel.hidesOnDeactivate = false

        let effect = NSVisualEffectView()
        effect.material = .hudWindow
        effect.blendingMode = .behindWindow
        effect.state = .active
        effect.wantsLayer = true
        effect.layer?.cornerRadius = 14

        let icon = NSImageView()
        if let symbol = NSImage(
            systemSymbolName: "doc.on.doc.fill",
            accessibilityDescription: "Copied"
        ) {
            icon.symbolConfiguration = NSImage.SymbolConfiguration(
                pointSize: 15,
                weight: .medium
            )
            icon.image = symbol
        }
        icon.contentTintColor = .white

        let prefix = NSTextField(labelWithString: "已复制")
        prefix.textColor = .white.withAlphaComponent(0.85)
        prefix.font = .systemFont(ofSize: 14)

        let codeLabel = NSTextField(labelWithString: "")
        codeLabel.textColor = .white
        codeLabel.font = .monospacedDigitSystemFont(ofSize: 16, weight: .semibold)
        self.codeLabel = codeLabel

        let stack = NSStackView(views: [icon, prefix, codeLabel])
        stack.orientation = .horizontal
        stack.spacing = 8
        stack.edgeInsets = NSEdgeInsets(top: 10, left: 16, bottom: 10, right: 16)

        effect.addSubview(stack)
        stack.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: effect.topAnchor),
            stack.bottomAnchor.constraint(equalTo: effect.bottomAnchor),
            stack.leadingAnchor.constraint(equalTo: effect.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: effect.trailingAnchor)
        ])
        panel.contentView = effect
        return panel
    }
}
