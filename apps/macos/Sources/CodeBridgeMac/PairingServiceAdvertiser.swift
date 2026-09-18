import Foundation

/// Advertises this Mac as `_codebridge._tcp` on the local network so paired
/// phones can re-find it after an address change. Only the device name and
/// port are published — the token still travels exclusively via the QR code.
final class PairingServiceAdvertiser: NSObject, NetServiceDelegate {
    private var service: NetService?

    func start(name: String, port: UInt16) {
        stop()
        let service = NetService(
            domain: "local.",
            type: "_codebridge._tcp.",
            name: name,
            port: Int32(port)
        )
        service.delegate = self
        service.publish()
        self.service = service
    }

    func stop() {
        service?.stop()
        service = nil
    }
}
