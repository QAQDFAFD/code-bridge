import AppKit

enum ClipboardWriter {
    static func copy(_ value: String) {
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.setString(value, forType: .string)
    }

    /// Clears the clipboard only if it still holds [value] — never wipes
    /// something the user copied in the meantime.
    static func clearIfStill(_ value: String) {
        let pasteboard = NSPasteboard.general
        guard pasteboard.string(forType: .string) == value else { return }
        pasteboard.clearContents()
    }
}

