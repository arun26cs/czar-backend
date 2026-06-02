#!/usr/bin/env bash
# Generates RSA-2048 key pair for local dev JWT signing.
# Run once from the repo root:  bash scripts/generate-keys.sh
# Output: czar-auth/src/main/resources/keys/private.pem
#          czar-auth/src/main/resources/keys/public.pem
# Both files are gitignored. In production, keys live in GCP Secret Manager.

set -e

KEYS_DIR="czar-auth/src/main/resources/keys"
mkdir -p "$KEYS_DIR"

echo "Generating RSA-2048 private key..."
openssl genrsa -out "$KEYS_DIR/private.pem" 2048

echo "Extracting public key..."
openssl rsa -in "$KEYS_DIR/private.pem" -pubout -out "$KEYS_DIR/public.pem"

echo "Done. Keys written to $KEYS_DIR/"
echo "  private.pem — JWT signing  (gitignored)"
echo "  public.pem  — JWT verification / JWKS (gitignored)"
