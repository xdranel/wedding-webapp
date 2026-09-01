#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

reason=manual
lock_held=0
while [[ $# -gt 0 ]]; do
	case "$1" in
		--reason)
			[[ $# -ge 2 ]] || die "--reason requires a value"
			reason="$2"
			shift 2
			;;
		--lock-held)
			lock_held=1
			shift
			;;
		*)
			die "Usage: backup.sh [--reason NAME] [--lock-held]"
			;;
	esac
done

[[ "$reason" =~ ^[a-z0-9-]{1,32}$ ]] || die "backup reason must use lowercase letters, digits, or hyphens"
require_root
load_env
if [[ "$lock_held" == 1 ]]; then
	[[ -e /proc/$$/fd/9 && "$(readlink -f /proc/$$/fd/9)" == "$LOCK_FILE" ]] || die "--lock-held requires the inherited operation lock"
	flock -n 9 || die "inherited operation lock is not held"
else
	acquire_operation_lock
fi

backup_root="$(env_value BACKUP_DIRECTORY)"
retention_days="$(env_value BACKUP_RETENTION_DAYS)"
min_free_gib="$(env_value BACKUP_MIN_FREE_GIB)"
media_root="$(env_value MEDIA_DIRECTORY)"
image="$(env_value APP_IMAGE)"

[[ "$backup_root" == /* && "$backup_root" != / ]] || die "BACKUP_DIRECTORY must be an absolute non-root path"
if [[ "$backup_root" != /var/backups/wedding ]]; then
	[[ "${WEDDING_TEST_MODE:-0}" == 1 && "$backup_root" =~ ^/tmp/wedding-phase7c-[A-Za-z0-9._-]+/backups$ ]] || die "backup directory is outside the production root"
fi
[[ "$retention_days" =~ ^[0-9]+$ && "$retention_days" -ge 1 && "$retention_days" -le 3650 ]] || die "invalid BACKUP_RETENTION_DAYS"
[[ "$min_free_gib" =~ ^[0-9]+$ && "$min_free_gib" -ge 1 && "$min_free_gib" -le 1048576 ]] || die "invalid BACKUP_MIN_FREE_GIB"

umask 077
backup_parent="$(dirname -- "$backup_root")"
[[ -d "$backup_parent" && ! -L "$backup_parent" && "$(realpath -e -- "$backup_parent")" == "$backup_parent" ]] || die "backup parent must exist, be canonical, and not be a symlink"
read -r parent_owner parent_mode < <(stat -c '%u %a' "$backup_parent")
[[ "$parent_owner" == 0 && $((8#$parent_mode & 022)) -eq 0 ]] || die "backup parent must be root-owned and not group/world-writable"
if [[ -e "$backup_root" ]]; then
	[[ -d "$backup_root" && ! -L "$backup_root" ]] || die "backup directory must not be a symlink or non-directory"
	read -r backup_owner backup_mode < <(stat -c '%u %a' "$backup_root")
	[[ "$backup_owner" == 0 && "$backup_mode" == 700 ]] || die "existing backup directory must be root-owned with mode 0700"
else
	install -d -o 0 -g 0 -m 0700 "$backup_root"
fi
[[ ! -L "$backup_root" && "$(realpath -e -- "$backup_root")" == "$backup_root" ]] || die "backup directory must be canonical and not a symlink"
read -r backup_owner backup_mode < <(stat -c '%u %a' "$backup_root")
[[ "$backup_owner" == 0 && "$backup_mode" == 700 ]] || die "backup directory must be root-owned with mode 0700"
[[ "$media_root" == /* && "$media_root" != / && -d "$media_root" && ! -L "$media_root" ]] || die "MEDIA_DIRECTORY must be an existing absolute non-root directory"
[[ "$(realpath -e -- "$media_root")" == "$media_root" ]] || die "media directory must be canonical"

available_bytes="$(df -PB1 "$backup_root" | awk 'NR == 2 {print $4}')"
required_bytes="$((min_free_gib * 1024 * 1024 * 1024))"
[[ "$available_bytes" =~ ^[0-9]+$ && "$available_bytes" -ge "$required_bytes" ]] || die "backup directory does not meet minimum free space"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
partial="$backup_root/$timestamp.partial"
completed="$backup_root/$timestamp"
[[ ! -e "$partial" && ! -e "$completed" ]] || die "backup timestamp already exists"
mkdir -m 0700 "$partial"

app_stopped=0
restart_app() {
	local status=$?
	trap - EXIT
	if [[ "$app_stopped" == 1 ]]; then
		if ! compose up -d app || ! wait_for_health; then
			printf 'ERROR: application restart or health check failed after backup\n' >&2
			status=1
		fi
	fi
	exit "$status"
}
trap restart_app EXIT

app_stopped=1
compose stop app

compose exec -T mysql sh -c \
	'exec mysqldump --user=root --password="$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --events --hex-blob --set-gtid-purged=OFF "$MYSQL_DATABASE"' \
	| gzip -9 >"$partial/database.sql.gz"

tar --one-file-system --directory "$media_root" -czf "$partial/media.tar.gz" .

flyway_version="$(compose exec -T mysql sh -c \
	'exec mysql --user=root --password="$MYSQL_ROOT_PASSWORD" --batch --skip-column-names "$MYSQL_DATABASE" --execute="SELECT COALESCE((SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1), '\''none'\'')"')"
database_bytes="$(stat -c %s "$partial/database.sql.gz")"
media_bytes="$(stat -c %s "$partial/media.tar.gz")"

{
	printf 'timestamp=%s\n' "$timestamp"
	printf 'reason=%s\n' "$reason"
	printf 'image=%s\n' "$image"
	printf 'flyway_version=%s\n' "$flyway_version"
	printf 'database_bytes=%s\n' "$database_bytes"
	printf 'media_bytes=%s\n' "$media_bytes"
} >"$partial/manifest.env"

(
	cd "$partial"
	sha256sum database.sql.gz media.tar.gz manifest.env >manifest.sha256
	sha256sum --check manifest.sha256
)

mv -- "$partial" "$completed"

while IFS= read -r -d '' partial_child; do
	printf 'WARNING: Incomplete backup remains: %s\n' "$(basename -- "$partial_child")" >&2
done < <(find "$backup_root" -mindepth 1 -maxdepth 1 -type d -name '*.partial' -print0)

while IFS= read -r -d '' child; do
	resolved="$(realpath -e -- "$child")"
	[[ "$(dirname -- "$resolved")" == "$backup_root" && "$(basename -- "$resolved")" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || die "refusing unsafe retention target"
	rm -r -- "$resolved"
done < <(find "$backup_root" -mindepth 1 -maxdepth 1 -type d \
	-name '????????T??????Z' -mtime "+$retention_days" -print0)

printf 'Backup completed: %s\n' "$timestamp"
