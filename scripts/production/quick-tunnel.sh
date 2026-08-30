#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

if [[ $# -ne 0 ]]; then
	die "Quick Tunnel accepts no arguments or custom domain"
fi

load_env
printf 'TESTING ONLY: URL is temporary and cannot satisfy production acceptance.\n' >&2
compose --profile public run --rm --no-deps cloudflared \
	tunnel --no-autoupdate --url http://app:8080
