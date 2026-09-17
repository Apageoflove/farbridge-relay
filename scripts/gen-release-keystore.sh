#!/bin/bash
set -euo pipefail
ROOT=/data/phone-mirror/android
KEYTOOL=$ROOT/.toolchains/jdk17/bin/keytool
DIR=$ROOT/keystore
mkdir -p "$DIR"
if [ -f "$DIR/release.keystore" ] && [ -f "$DIR/keystore.properties" ]; then echo "keystore already exists"; exit 0; fi
PASS=$(openssl rand -hex 16)
"$KEYTOOL" -genkeypair -v -keystore "$DIR/release.keystore" -alias phone-mirror -keyalg RSA -keysize 2048 -validity 10950 -storepass "$PASS" -keypass "$PASS" -dname "CN=Phone Mirror, OU=Personal, O=PhoneMirror" 2>/dev/null
printf "storeFile=release.keystore\nstorePassword=%s\nkeyAlias=phone-mirror\nkeyPassword=%s\n" "$PASS" "$PASS" > "$DIR/keystore.properties"
chmod 600 "$DIR/release.keystore" "$DIR/keystore.properties"
echo "keystore created (passwords stored in keystore/keystore.properties, git-ignored)"
