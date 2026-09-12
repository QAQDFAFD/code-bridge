import Foundation

public struct ParsedCodeRequest: Equatable, Sendable {
    public let payload: IncomingCodePayload
}

public enum HTTPCodeRequestParser {
    /// Returns true when `data` holds a full HTTP request: the blank line that
    /// ends the header block is present, and the body reaches Content-Length bytes.
    public static func isRequestComplete(_ data: Data) -> Bool {
        guard let headerEnd = data.firstRange(of: Data("\r\n\r\n".utf8)) else {
            return false
        }

        let headerBlock = String(decoding: data[..<headerEnd.lowerBound], as: UTF8.self)
        let contentLength = headerBlock
            .components(separatedBy: "\r\n")
            .dropFirst()
            .compactMap { line -> Int? in
                guard let separator = line.firstIndex(of: ":") else { return nil }
                let key = line[..<separator].trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                guard key == "content-length" else { return nil }
                return Int(line[line.index(after: separator)...].trimmingCharacters(in: .whitespacesAndNewlines))
            }
            .first

        guard let contentLength else {
            return true
        }
        return data.count - headerEnd.upperBound >= contentLength
    }

    public static func parse(_ data: Data, expectedToken: String) throws -> ParsedCodeRequest {
        guard let raw = String(data: data, encoding: .utf8) else {
            throw CodeBridgeError.invalidRequest
        }

        let sections = raw.components(separatedBy: "\r\n\r\n")
        guard sections.count >= 2 else {
            throw CodeBridgeError.invalidRequest
        }

        let headerLines = sections[0].components(separatedBy: "\r\n")
        guard let requestLine = headerLines.first,
              requestLine.hasPrefix("POST /v1/codes ") else {
            throw CodeBridgeError.invalidRequest
        }

        let headers = Dictionary(
            uniqueKeysWithValues: headerLines.dropFirst().compactMap { line -> (String, String)? in
                guard let separator = line.firstIndex(of: ":") else { return nil }
                let key = line[..<separator].trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                let value = line[line.index(after: separator)...].trimmingCharacters(in: .whitespacesAndNewlines)
                return (key, value)
            }
        )

        guard headers["authorization"] == "Bearer \(expectedToken)" else {
            throw CodeBridgeError.unauthorized
        }

        let body = sections.dropFirst().joined(separator: "\r\n\r\n")
        guard let bodyData = body.data(using: .utf8) else {
            throw CodeBridgeError.invalidJSON
        }

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601

        do {
            let payload = try decoder.decode(IncomingCodePayload.self, from: bodyData)
            _ = try payload.toEvent()
            return ParsedCodeRequest(payload: payload)
        } catch CodeBridgeError.invalidCode {
            throw CodeBridgeError.invalidCode
        } catch {
            throw CodeBridgeError.invalidJSON
        }
    }
}

