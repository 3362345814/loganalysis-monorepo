#!/usr/bin/env sh
set -eu

REPO="${LOGANALYSIS_REPO:-3362345814/loganalysis-monorepo}"
VERSION="${LOGANALYSIS_VERSION:-latest}"
INSTALL_DIR="${LOGANALYSIS_INSTALL_DIR:-/usr/local/bin}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "missing required command: $1" >&2
    exit 1
  fi
}

require_cmd curl

detect_os() {
  case "$(uname -s)" in
    Linux) echo "linux" ;;
    Darwin) echo "darwin" ;;
    *)
      echo "unsupported OS: $(uname -s)" >&2
      exit 1
      ;;
  esac
}

detect_arch() {
  case "$(uname -m)" in
    x86_64|amd64) echo "amd64" ;;
    arm64|aarch64) echo "arm64" ;;
    *)
      echo "unsupported architecture: $(uname -m)" >&2
      exit 1
      ;;
  esac
}

resolve_latest() {
  require_cmd sed
  require_cmd tr
  tag="$(curl -fsSL "https://api.github.com/repos/${REPO}/releases/latest" | tr -d '\n' | sed -n 's/.*"tag_name":"\([^"]*\)".*/\1/p')"
  if [ -z "$tag" ]; then
    echo "failed to resolve latest release tag" >&2
    exit 1
  fi
  echo "$tag"
}

verify_checksum() {
  file="$1"
  checksums="$2"

  if command -v sha256sum >/dev/null 2>&1; then
    expected="$(grep "  $(basename "$file")$" "$checksums" | awk '{print $1}')"
    actual="$(sha256sum "$file" | awk '{print $1}')"
  elif command -v shasum >/dev/null 2>&1; then
    expected="$(grep "  $(basename "$file")$" "$checksums" | awk '{print $1}')"
    actual="$(shasum -a 256 "$file" | awk '{print $1}')"
  else
    echo "warning: no sha256 checksum tool found, skip verification" >&2
    return 0
  fi

  if [ -z "$expected" ]; then
    echo "checksum entry not found for $(basename "$file")" >&2
    exit 1
  fi

  if [ "$expected" != "$actual" ]; then
    echo "checksum mismatch for $(basename "$file")" >&2
    exit 1
  fi
}

OS="$(detect_os)"
ARCH="$(detect_arch)"

if [ "$VERSION" = "latest" ]; then
  VERSION="$(resolve_latest)"
fi

ASSET="loganalysis-${OS}-${ARCH}"
BASE_URL="https://github.com/${REPO}/releases/download/${VERSION}"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

BIN_PATH="${TMP_DIR}/${ASSET}"
CHECKSUM_PATH="${TMP_DIR}/checksums.txt"

curl -fsSL -o "$BIN_PATH" "${BASE_URL}/${ASSET}"
curl -fsSL -o "$CHECKSUM_PATH" "${BASE_URL}/checksums.txt"
verify_checksum "$BIN_PATH" "$CHECKSUM_PATH"

chmod +x "$BIN_PATH"

TARGET_DIR="$INSTALL_DIR"
if [ ! -d "$TARGET_DIR" ]; then
  mkdir -p "$TARGET_DIR" 2>/dev/null || true
fi

if [ ! -w "$TARGET_DIR" ]; then
  TARGET_DIR="${HOME}/.local/bin"
  mkdir -p "$TARGET_DIR"
  echo "${INSTALL_DIR} is not writable, fallback to ${TARGET_DIR}" >&2
fi

cp "$BIN_PATH" "${TARGET_DIR}/loganalysis"
chmod +x "${TARGET_DIR}/loganalysis"

echo "loganalysis installed to ${TARGET_DIR}/loganalysis"
if [ "$TARGET_DIR" = "${HOME}/.local/bin" ]; then
  echo "add ${HOME}/.local/bin to PATH if needed"
fi
