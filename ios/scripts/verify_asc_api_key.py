#!/usr/bin/env python3
"""Pre-flight check for the App Store Connect API credentials.

Mints the same ES256 JWT that fastlane's `sigh`/`gym`/`pilot` actions use and
calls a harmless read-only endpoint. A 401 here means the (key id, issuer id,
.p8) triple is wrong *before* we spend ~25 minutes on a Flutter + Xcode build
only to fail at the upload step.

Uses the stdlib only — macOS runners have Python 3 and openssl preinstalled,
and adding a pip/gem dependency to a signing step is not worth it.

Environment:
  APP_STORE_CONNECT_API_KEY_ID      Key ID, e.g. VCG894892N
  APP_STORE_CONNECT_API_ISSUER_ID   Issuer ID (a UUID)
  ASC_P8_PATH                       Path to the AuthKey_<id>.p8 file
  ASC_EXPECT_BUNDLE_ID              Optional: warn if this app is not visible

Exits non-zero when the credentials are rejected. Never prints key material.
"""

import base64
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

API_URL = "https://api.appstoreconnect.apple.com/v1/apps?limit=200"


def b64u(raw: bytes) -> bytes:
    return base64.urlsafe_b64encode(raw).rstrip(b"=")


def der_to_raw(der: bytes) -> bytes:
    """ECDSA DER SEQUENCE(INTEGER r, INTEGER s) -> raw r||s, 32 bytes each.

    openssl emits DER; JWS ES256 wants the fixed-width concatenation.
    """
    if der[0] != 0x30:
        raise ValueError("signature is not a DER sequence")
    # Skip the SEQUENCE tag plus its length bytes (long form if high bit set).
    i = 2 + ((der[1] & 0x7F) if der[1] & 0x80 else 0)
    parts = []
    for _ in range(2):
        if der[i] != 0x02:
            raise ValueError("expected a DER INTEGER")
        length = der[i + 1]
        parts.append(der[i + 2:i + 2 + length].lstrip(b"\x00").rjust(32, b"\x00"))
        i += 2 + length
    return parts[0] + parts[1]


def mint_token(key_id: str, issuer_id: str, p8_path: str) -> str:
    now = int(time.time())
    header = {"alg": "ES256", "kid": key_id, "typ": "JWT"}
    payload = {
        "iss": issuer_id,
        "iat": now,
        "exp": now + 1200,
        "aud": "appstoreconnect-v1",
    }
    signing_input = (
        b64u(json.dumps(header, separators=(",", ":")).encode())
        + b"."
        + b64u(json.dumps(payload, separators=(",", ":")).encode())
    )
    der = subprocess.run(
        ["openssl", "dgst", "-sha256", "-sign", p8_path, "-binary"],
        input=signing_input,
        capture_output=True,
        check=True,
    ).stdout
    return (signing_input + b"." + b64u(der_to_raw(der))).decode()


def describe(name: str, value: str, expected_len: int) -> bool:
    """Report a credential's shape without revealing it. True if it looks sane."""
    ok = len(value) == expected_len and value == value.strip()
    print(f"  {name:<24} {len(value)} chars (expect {expected_len})", end="")
    if value != value.strip():
        print(" -- HAS SURROUNDING WHITESPACE", end="")
    print("  " + ("ok" if ok else "SUSPECT"))
    return ok


def main() -> None:
    key_id = os.environ.get("APP_STORE_CONNECT_API_KEY_ID", "")
    issuer_id = os.environ.get("APP_STORE_CONNECT_API_ISSUER_ID", "")
    p8_path = os.environ.get("ASC_P8_PATH", "")
    expect_bundle_id = os.environ.get("ASC_EXPECT_BUNDLE_ID", "")

    if not (key_id and issuer_id and p8_path):
        sys.exit(
            "APP_STORE_CONNECT_API_KEY_ID, APP_STORE_CONNECT_API_ISSUER_ID and "
            "ASC_P8_PATH must all be set"
        )
    if not os.path.exists(p8_path):
        sys.exit(f"private key not found at {p8_path}")

    print("credential shape:")
    shape_ok = describe("key id", key_id, 10)
    shape_ok &= describe("issuer id", issuer_id, 36)

    try:
        token = mint_token(key_id.strip(), issuer_id.strip(), p8_path)
    except subprocess.CalledProcessError:
        sys.exit("::error::openssl could not sign with the .p8 — the key is malformed")

    request = urllib.request.Request(
        API_URL, headers={"Authorization": f"Bearer {token}"}
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            body = json.load(response)
            status = response.status
    except urllib.error.HTTPError as error:
        raw = error.read().decode(errors="replace")
        print(f"\n::error::App Store Connect rejected the credentials (HTTP {error.code})")
        try:
            for item in json.loads(raw).get("errors", []):
                print(f"  {item.get('title')}: {item.get('detail')}")
        except ValueError:
            print(raw[:600])
        if error.code == 401:
            print(
                "\n  A 401 means the key id, issuer id and .p8 do not form a valid\n"
                "  triple, or the key was revoked. Re-copy all three secrets from\n"
                "  App Store Connect > Users and Access > Integrations > Team Keys.\n"
                "  Watch for trailing newlines: GitHub stores secrets verbatim."
            )
        sys.exit(1)

    print(f"\nHTTP {status} — credentials accepted by Apple")

    apps = [
        (app["attributes"].get("bundleId"), app["attributes"].get("name"))
        for app in body.get("data", [])
    ]
    print(f"apps visible to this key ({len(apps)}):")
    for bundle_id, name in sorted(apps, key=lambda pair: pair[0] or ""):
        marker = "  <-- target" if bundle_id == expect_bundle_id else ""
        print(f"  {bundle_id or '?':<32} {name}{marker}")

    if expect_bundle_id and not any(b == expect_bundle_id for b, _ in apps):
        print(
            f"\n::error::{expect_bundle_id} is not visible to this key. Either the\n"
            "  App Store Connect app record does not exist yet, it lives in a\n"
            "  different Apple account, or this key lacks access to it."
        )
        sys.exit(1)

    if not shape_ok:
        print(
            "\n::warning::A credential had an unexpected length. Auth succeeded, so\n"
            "  this is cosmetic, but re-paste the secret to keep logs clean."
        )


if __name__ == "__main__":
    main()
