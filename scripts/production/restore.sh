#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

timestamp="${1:-}"
[[ $# -eq 1 && "$timestamp" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || die "Usage: restore.sh YYYYMMDDTHHMMSSZ"

require_root
load_env
acquire_operation_lock

backup_root="$(env_value BACKUP_DIRECTORY)"
media_root="$(env_value MEDIA_DIRECTORY)"
database="$(env_value MYSQL_DATABASE)"
[[ "$backup_root" == /* && "$backup_root" != / && -d "$backup_root" && ! -L "$backup_root" ]] || die "invalid backup directory"
if [[ "$backup_root" != /var/backups/wedding ]]; then
	[[ "${WEDDING_TEST_MODE:-0}" == 1 && "$backup_root" =~ ^/tmp/wedding-phase7c-[A-Za-z0-9._-]+/backups$ ]] || die "backup directory is outside the production root"
fi
[[ "$(realpath -e -- "$backup_root")" == "$backup_root" ]] || die "backup directory must be canonical"
read -r backup_owner backup_mode < <(stat -c '%u %a' "$backup_root")
[[ "$backup_owner" == 0 && "$backup_mode" == 700 ]] || die "backup directory must be root-owned with mode 0700"
[[ "$media_root" == /* && "$media_root" != / && -d "$media_root" && ! -L "$media_root" ]] || die "invalid media directory"
[[ "$(realpath -e -- "$media_root")" == "$media_root" ]] || die "media directory must be canonical"
[[ "$database" =~ ^[A-Za-z0-9_]+$ ]] || die "invalid MYSQL_DATABASE"

selected="$backup_root/$timestamp"
[[ -d "$selected" && ! -L "$selected" && "$(realpath -e -- "$selected")" == "$selected" ]] || die "completed backup not found"
[[ "$(dirname -- "$selected")" == "$backup_root" ]] || die "backup must be a direct child"
read -r selected_owner selected_mode < <(stat -c '%u %a' "$selected")
[[ "$selected_owner" == 0 && "$selected_mode" == 700 ]] || die "backup must be root-owned with mode 0700"
for artifact in database.sql.gz media.tar.gz manifest.env manifest.sha256; do
	[[ -f "$selected/$artifact" && ! -L "$selected/$artifact" ]] || die "missing or unsafe backup artifact: $artifact"
done
[[ "$(wc -l <"$selected/manifest.sha256")" == 3 ]] || die "invalid checksum manifest"
for artifact in database.sql.gz media.tar.gz manifest.env; do
	grep -Eq "^[0-9a-f]{64}  ${artifact}$" "$selected/manifest.sha256" || die "invalid checksum manifest"
done

(
	cd "$selected"
	sha256sum --check manifest.sha256
)
gzip -t "$selected/database.sql.gz"
tar -tzf "$selected/media.tar.gz" >/dev/null
while IFS= read -r entry; do
	[[ "$entry" != /* && "$entry" != .. && "$entry" != ../* && "$entry" != */.. && "$entry" != */../* ]] || die "unsafe media archive path"
done < <(tar -tzf "$selected/media.tar.gz")
compose exec -T mysql sh -c 'exec mysqladmin --user=root --password="$MYSQL_ROOT_PASSWORD" ping --silent' >/dev/null

database_uncompressed_bytes="$(gzip -dc "$selected/database.sql.gz" | wc -c)"
media_uncompressed_bytes="$(LC_ALL=C tar -tvzf "$selected/media.tar.gz" | awk '{total += $3} END {printf "%.0f", total}')"
media_available_bytes="$(df -PB1 "$(dirname -- "$media_root")" | awk 'NR == 2 {print $4}')"
mysql_available_bytes="$(compose exec -T mysql df -PB1 /var/lib/mysql | awk 'NR == 2 {print $4}')"
[[ "$database_uncompressed_bytes" =~ ^[0-9]+$ && "$media_uncompressed_bytes" =~ ^[0-9]+$ ]] || die "could not measure restore size"
[[ "$media_available_bytes" =~ ^[0-9]+$ && "$media_available_bytes" -ge "$media_uncompressed_bytes" ]] || die "insufficient free space for media staging"
[[ "$mysql_available_bytes" =~ ^[0-9]+$ && "$mysql_available_bytes" -ge $((database_uncompressed_bytes * 2)) ]] || die "insufficient free space for database restore"

printf 'Type RESTORE %s to continue: ' "$timestamp"
IFS= read -r confirmation
[[ "$confirmation" == "RESTORE $timestamp" ]] || die "confirmation did not match"

safety_output="$("$SCRIPT_DIR/backup.sh" --reason pre-restore --lock-held)"
safety_timestamp="$(sed -n 's/^Backup completed: //p' <<<"$safety_output")"
[[ "$safety_timestamp" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || die "safety backup did not report a completed timestamp"

staging="${media_root}.restore-${timestamp}.staging"
previous="${media_root}.restore-${timestamp}.previous"
[[ ! -e "$staging" && ! -e "$previous" ]] || die "restore staging path already exists"
mkdir -m 0700 "$staging"
tar --same-owner -xzf "$selected/media.tar.gz" -C "$staging"

app_stopped=1
mutation_started=0
restart_app() {
	local status=$?
	trap - EXIT
	if [[ "$app_stopped" == 1 ]]; then
		if ! compose up -d app || ! wait_for_health; then
			printf 'ERROR: application restart or health check failed after restore\n' >&2
			status=1
		fi
	fi
	if [[ "$status" -ne 0 ]]; then
		printf 'ERROR: restore failed; mutation_started=%s staging=%s previous=%s\n' \
			"$mutation_started" "$staging" "$previous" >&2
	fi
	exit "$status"
}
trap restart_app EXIT

compose stop app
mutation_started=1
compose exec -T mysql sh -c \
	'exec mysql --user=root --password="$MYSQL_ROOT_PASSWORD" --execute="DROP DATABASE IF EXISTS \`$MYSQL_DATABASE\`; CREATE DATABASE \`$MYSQL_DATABASE\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"'
gzip -dc "$selected/database.sql.gz" \
	| compose exec -T mysql sh -c 'exec mysql --user=root --password="$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'

mv -- "$media_root" "$previous"
if ! mv -- "$staging" "$media_root"; then
	mv -- "$previous" "$media_root"
	die "media replacement failed"
fi

compose up -d app
wait_for_health
app_stopped=0
rm -r -- "$previous"
trap - EXIT
printf 'Restore completed: %s (safety backup: %s)\n' "$timestamp" "$safety_timestamp"
