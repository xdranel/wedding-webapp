#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

[[ $# -le 1 ]] || die "expected at most one argument"

case "${1:-}" in
	--help)
		printf 'Usage: %s [--internal]\n' "$0"
		;;
	--internal)
		load_env
		compose exec -T app curl --fail --silent --show-error \
			--connect-timeout 2 --max-time 5 \
			http://localhost:8081/actuator/health/readiness
		;;
	'')
		wait_for_health || die "public application health check timed out"
		printf 'Application health check passed.\n'
		;;
	*)
		die "unknown argument: $1"
		;;
esac
