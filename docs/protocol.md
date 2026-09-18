# CodeBridge Local Protocol

CodeBridge uses a local HTTP endpoint for the MVP. The Android app sends OTP events to the macOS receiver.

## Transport

- Plain HTTP over TCP on the local network (default port `47821`).
- Clients send requests with a `Content-Length` body (chunked transfer encoding is not supported).
- Requests larger than 1 MiB are rejected with `400`.
- The receiver closes the connection after each response (`Connection: close`).

## Endpoint

```text
POST /v1/codes
Authorization: Bearer <token>
Content-Type: application/json
```

## Reachability probe

```text
GET /v1/ping
Authorization: Bearer <token>
```

The Android app uses this to detect a paired Mac on the current network
(auto-connect) and to validate a QR pairing before saving it.

```text
200 OK   {"ok":true,"name":"<device name>"}
```

Wrong or missing token returns `401` like `/v1/codes`.

## Payload

```json
{
  "id": "optional-client-id",
  "code": "106284",
  "sender": "GitHub",
  "messagePreview": "Your GitHub verification code is 106284.",
  "receivedAt": "2026-07-04T12:00:00+08:00",
  "source": "sms",
  "confidence": 0.98
}
```

## Field Notes

- `code`: Required. Usually 4 to 8 digits for the MVP.
- `sender`: Optional display name or SMS sender.
- `messagePreview`: Optional short preview used for debugging and UI context.
- `receivedAt`: Optional ISO-8601 timestamp. If omitted, macOS uses receive time.
- `source`: Optional. Expected values include `sms`, `notification`, and `manual`.
- `confidence`: Optional parser confidence from 0.0 to 1.0.

## Responses

Responses carry a short JSON body and `Connection: close`.

```text
202 Accepted   {"ok":true}
```

The Mac accepted and handled the code.

```text
401 Unauthorized   {"error":"unauthorized"}
```

The token is missing or invalid.

```text
400 Bad Request   {"error":"bad_request"}
```

The JSON payload is invalid, the code fails validation (4–10 alphanumeric characters), or the request is malformed.

```text
429 Too Many Requests   {"error":"too_many_requests"}
```

Too many failed authentication attempts from one host within a short window (5 failures / 60s by default). The block expires when the window slides past the failures.


