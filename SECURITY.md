# Security Policy

## Threat model

CodeBridge is designed for **trusted home/LAN use**: your own phone talking to
your own Mac. It is not designed to run on hostile networks.

- Traffic is plain HTTP on the local network. Anyone on the same network can
  read relayed codes and the message preview. Use it on networks you trust.
- Requests must carry the pairing token (`Authorization: Bearer …`), which is
  generated randomly on first launch. The token gates who can push codes, it
  does not encrypt anything.
- Repeated failed token attempts from one host are rate limited (HTTP 429
  after 5 failures per 60 seconds) to slow down brute-force guesses.
- The macOS receiver listens on all interfaces. Firewalls or VLANs can be used
  to further restrict exposure.
- The Android app stores the token in plain shared preferences and requires
  SMS permissions; it is intended for personal side-loading, not Play Store
  distribution (review Google Play's SMS permission policy first).

## Reporting a vulnerability

Please open a private security advisory on GitHub
(**Security → Report a vulnerability**) instead of a public issue. Include
reproduction steps and affected versions. You should get a response within a
few days.
