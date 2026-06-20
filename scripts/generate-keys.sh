#!/usr/bin/env bash
# Generates RSA-2048 key pair for local dev JWT signing AND distributes the
# public key to every service that validates JWTs.
# Run once from the repo root:  bash scripts/generate-keys.sh
# Output: czar-auth/src/main/resources/keys/private.pem  (auth service only)
#         <each service>/src/main/resources/keys/public.pem
# All .pem files are gitignored. In production, keys live in GCP Secret Manager.

set -e

AUTH_KEYS_DIR="czar-auth/src/main/resources/keys"
mkdir -p "$AUTH_KEYS_DIR"

echo "Generating RSA-2048 private key..."
openssl genrsa -out "$AUTH_KEYS_DIR/private.pem" 2048

echo "Extracting public key..."
openssl rsa -in "$AUTH_KEYS_DIR/private.pem" -pubout -out "$AUTH_KEYS_DIR/public.pem"

echo "Distributing public key to all JWT-validating services..."
for SERVICE in czar-gateway czar-user czar-planner czar-notes czar-voice-ai; do
  DEST="$SERVICE/src/main/resources/keys"
  mkdir -p "$DEST"
  cp "$AUTH_KEYS_DIR/public.pem" "$DEST/public.pem"
  echo "  ✓ $DEST/public.pem"
done

echo ""
echo "Done. Keys written to:"
echo "  $AUTH_KEYS_DIR/private.pem — JWT signing  (gitignored, auth service only)"
echo "  <each service>/src/main/resources/keys/public.pem — JWT verification (gitignored)"
echo ""
echo "Run 'docker compose --profile apps up --build' to rebuild images with the new keys."
