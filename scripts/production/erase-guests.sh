#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

[[ $# -eq 0 ]] || die "Usage: erase-guests.sh"
require_root
load_env
acquire_operation_lock

backup_root="$(env_value BACKUP_DIRECTORY)"
database="$(env_value MYSQL_DATABASE)"
image="$(env_value APP_IMAGE)"
[[ "$backup_root" == /var/backups/wedding || ("${WEDDING_TEST_MODE:-0}" == 1 && "$backup_root" =~ ^/tmp/wedding-phase7c-[A-Za-z0-9._-]+/backups$) ]] || die "backup directory is outside the production root"
[[ -d "$backup_root" && ! -L "$backup_root" && "$(realpath -e -- "$backup_root")" == "$backup_root" ]] || die "invalid backup directory"
read -r backup_owner backup_mode < <(stat -c '%u %a' "$backup_root")
[[ "$backup_owner" == 0 && "$backup_mode" == 700 ]] || die "backup directory must be root-owned with mode 0700"
[[ "$database" =~ ^[A-Za-z0-9_]+$ ]] || die "invalid MYSQL_DATABASE"

while IFS= read -r -d '' child; do
	[[ ! -L "$child" ]] || die "refusing symlink in backup directory"
	resolved="$(realpath -e -- "$child")"
	name="$(basename -- "$resolved")"
	[[ -d "$resolved" && "$(dirname -- "$resolved")" == "$backup_root" ]] || die "refusing unsafe backup child"
	[[ "$name" =~ ^[0-9]{8}T[0-9]{6}Z(\.partial)?$ ]] || die "unexpected entry in backup directory"
done < <(find "$backup_root" -mindepth 1 -maxdepth 1 -print0)

printf '%s\n' 'This permanently deletes every guest, category, RSVP, check-in, correction, and all guest-bearing backups.'
printf '%s' 'Type ERASE ALL GUEST DATA to continue: '
IFS= read -r confirmation
[[ "$confirmation" == "ERASE ALL GUEST DATA" ]] || die "confirmation did not match"

app_stopped=1
commit_succeeded=0
restart_app() {
	local status=$?
	trap - EXIT
	if [[ "$app_stopped" == 1 ]]; then
		if ! compose up -d app || ! wait_for_health; then
			printf 'ERROR: application restart or health check failed after erasure\n' >&2
			status=1
		fi
	fi
	if [[ "$status" -ne 0 && "$commit_succeeded" == 1 ]]; then
		printf 'ERROR: guest rows were erased, but backup cleanup or the clean baseline is incomplete\n' >&2
	fi
	exit "$status"
}
trap restart_app EXIT

compose stop app
read -r correction_count check_in_count rsvp_count guest_count category_count < <(
	compose exec -T mysql sh -c \
		'exec mysql --user=root --password="$MYSQL_ROOT_PASSWORD" --batch --skip-column-names "$MYSQL_DATABASE" --execute="SELECT (SELECT COUNT(*) FROM check_in_correction), (SELECT COUNT(*) FROM check_in), (SELECT COUNT(*) FROM rsvp), (SELECT COUNT(*) FROM guest), (SELECT COUNT(*) FROM guest_category)"'
)
for count in "$correction_count" "$check_in_count" "$rsvp_count" "$guest_count" "$category_count"; do
	[[ "$count" =~ ^[0-9]+$ ]] || die "could not record initial row counts"
done

compose exec -T mysql sh -c \
	'exec mysql --user=root --password="$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
	<"$SCRIPT_DIR/sql/erase-guests.sql"
commit_succeeded=1

remaining="$(compose exec -T mysql sh -c \
	'exec mysql --user=root --password="$MYSQL_ROOT_PASSWORD" --batch --skip-column-names "$MYSQL_DATABASE" --execute="SELECT (SELECT COUNT(*) FROM check_in_correction) + (SELECT COUNT(*) FROM check_in) + (SELECT COUNT(*) FROM rsvp) + (SELECT COUNT(*) FROM guest) + (SELECT COUNT(*) FROM guest_category)"')"
[[ "$remaining" == 0 ]] || die "guest-data verification failed after commit"

while IFS= read -r -d '' child; do
	resolved="$(realpath -e -- "$child")"
	[[ -d "$resolved" && "$(dirname -- "$resolved")" == "$backup_root" ]] || die "refusing unsafe backup deletion"
	rm -r -- "$resolved"
done < <(find "$backup_root" -mindepth 1 -maxdepth 1 -type d -print0)

baseline_output="$("$SCRIPT_DIR/backup.sh" --reason post-erasure --lock-held)"
baseline_timestamp="$(sed -n 's/^Backup completed: //p' <<<"$baseline_output")"
[[ "$baseline_timestamp" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || die "clean baseline backup did not complete"
app_stopped=0

receipt="guest erasure completed timestamp=$(date -u +%Y%m%dT%H%M%SZ) image=$image operator=${SUDO_USER:-root} corrections=$correction_count check_ins=$check_in_count rsvps=$rsvp_count guests=$guest_count categories=$category_count clean_baseline=$baseline_timestamp"
logger --tag wedding-erasure -- "$receipt" || true
printf '%s\n' "$receipt"
trap - EXIT
