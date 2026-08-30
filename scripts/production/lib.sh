#!/usr/bin/env bash
set -Eeuo pipefail

WEDDING_HOME="${WEDDING_HOME:-/opt/wedding}"
[[ "$WEDDING_HOME" == /* && "$WEDDING_HOME" != / ]] || {
	printf 'ERROR: WEDDING_HOME must be an absolute non-root path\n' >&2
	exit 1
}

COMPOSE_FILE="$WEDDING_HOME/compose.production.yaml"
ENV_FILE="$WEDDING_HOME/.env"
LOCK_DIR="/run/lock/wedding"
LOCK_FILE="$LOCK_DIR/operation.lock"

die() {
	printf 'ERROR: %s\n' "$*" >&2
	exit 1
}

require_root() {
	[[ ${EUID:-$(id -u)} -eq 0 ]] || die "run with sudo"
}

load_env() {
	require_root
	local canonical owner mode name
	canonical="$(realpath -e -- "$WEDDING_HOME")" || die "missing deployment directory: $WEDDING_HOME"
	[[ "$canonical" == "$WEDDING_HOME" && ! -L "$WEDDING_HOME" ]] || die "deployment directory must be canonical and not a symlink"
	read -r owner mode < <(stat -c '%u %a' "$WEDDING_HOME")
	[[ "$owner" == 0 && $((8#$mode & 022)) -eq 0 ]] || die "$WEDDING_HOME must be root-owned and not group/world-writable"
	[[ -f "$ENV_FILE" && ! -L "$ENV_FILE" ]] || die "missing regular environment file: $ENV_FILE"
	read -r owner mode < <(stat -c '%u %a' "$ENV_FILE")
	[[ "$owner" == 0 && "$mode" == 640 ]] || die "$ENV_FILE must be owned by root with mode 0640"
	for name in APP_IMAGE MYSQL_DATABASE MYSQL_USER MYSQL_PASSWORD MYSQL_ROOT_PASSWORD \
		DB_USERNAME DB_PASSWORD ADMIN_USERNAME ADMIN_PASSWORD INVITATION_BASE_URL \
		INVITATION_SIGNING_SECRET MEDIA_DIRECTORY; do
		grep -Eq "^${name}=.+$" "$ENV_FILE" || die "missing required environment name: $name"
	done
	[[ -f "$COMPOSE_FILE" && ! -L "$COMPOSE_FILE" ]] || die "missing production Compose file"
	read -r owner mode < <(stat -c '%u %a' "$COMPOSE_FILE")
	[[ "$owner" == 0 && $((8#$mode & 022)) -eq 0 ]] || die "production Compose file must be root-owned and not group/world-writable"
}

compose() {
	docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

acquire_operation_lock() {
	require_root
	local owner mode
	read -r owner mode < <(stat -c '%u %a' /run/lock)
	[[ "$owner" == 0 && $((8#$mode & 022)) -eq 0 ]] || die "/run/lock must be root-owned and not group/world-writable"
	[[ ! -L "$LOCK_DIR" ]] || die "operation lock directory must not be a symlink"
	install -d -o 0 -g 0 -m 0755 "$LOCK_DIR"
	[[ ! -L "$LOCK_FILE" ]] || die "operation lock file must not be a symlink"
	exec 9>"$LOCK_FILE"
	flock -n 9 || die "another operation is running"
}

wait_for_health() {
	local attempt
	for ((attempt = 1; attempt <= 30; attempt++)); do
		curl --fail --silent --show-error --connect-timeout 2 --max-time 5 \
			http://localhost:8080/login >/dev/null 2>&1 && return 0
		sleep 2
	done
	return 1
}
