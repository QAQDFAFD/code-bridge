import AppKit
import CoreImage
import CoreImage.CIFilterBuiltins

enum QRCodeRenderer {
    /// Renders `string` as an NSImage QR code using the built-in CoreImage
    /// generator — no third-party dependency needed.
    static func image(for string: String, dimension: CGFloat = 220) -> NSImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(string.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage, output.extent.width > 0 else {
            return nil
        }

        let scale = dimension / output.extent.width
        let scaled = output.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        let representation = NSCIImageRep(ciImage: scaled)
        let image = NSImage(size: NSSize(width: dimension, height: dimension))
        image.addRepresentation(representation)
        return image
    }
}
