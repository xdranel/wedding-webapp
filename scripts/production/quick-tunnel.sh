#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 0 ]]; then
	printf 'ERROR: Quick Tunnel accepts no arguments or custom domain.\n' >&2
	exit 2
fi

printf 'TESTING ONLY: URL is temporary and cannot satisfy production acceptance.\n' >&2
exec docker run --rm --add-host=host.docker.internal:host-gateway \
	cloudflare/cloudflared:2026.8.1 \
	tunnel --no-autoupdate --url http://host.docker.internal:8080
