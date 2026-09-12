#!/usr/bin/env bash
set -euo pipefail

HOST="${1:-127.0.0.1}"
PORT="${2:-47821}"
TOKEN="${3:?Pass your CodeBridge token as the 3rd argument (see the Mac menu bar Settings)}"
CODE="${4:-106284}"
SENDER="${5:-GitHub}"

curl --noproxy "*" -sS -X POST "http://${HOST}:${PORT}/v1/codes" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"code\":\"${CODE}\",\"sender\":\"${SENDER}\",\"messagePreview\":\"Your verification code is ${CODE}.\",\"source\":\"manual\",\"confidence\":1.0}"

echo
