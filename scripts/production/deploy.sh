#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

version="${1:-}"
[[ $# -eq 1 && "$version" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "Usage: deploy.sh vX.Y.Z"

require_root
load_env
acquire_operation_lock

previous_image="$(env_value APP_IMAGE)"
[[ "$previous_image" =~ ^([a-z0-9.-]+(/[a-z0-9._-]+)+):v[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "current APP_IMAGE must use an immutable semantic version"
repository="${BASH_REMATCH[1]}"
target_image="$repository:$version"
[[ "$target_image" != "$previous_image" ]] || die "requested image is already configured"

flyway_version() {
	compose exec -T mysql sh -c \
		'exec mysql --user=root --password="$MYSQL_ROOT_PASSWORD" --batch --skip-column-names "$MYSQL_DATABASE" --execute="SELECT COALESCE(MAX(version), '\''none'\'') FROM flyway_schema_history"'
}

previous_flyway_version="$(flyway_version)"
backup_output="$("$SCRIPT_DIR/backup.sh" --reason pre-deploy --lock-held)"
backup_timestamp="$(sed -n 's/^Backup completed: //p' <<<"$backup_output")"
[[ "$backup_timestamp" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || die "pre-deploy backup did not complete"

env_changed=0
temporary_env=
deployment_failure() {
	local status=$?
	trap - EXIT
	if [[ -n "$temporary_env" && -e "$temporary_env" ]]; then
		rm -f -- "$temporary_env"
	fi
	if [[ "$status" -ne 0 && "$env_changed" == 1 ]]; then
		current_flyway_version="$(flyway_version 2>/dev/null || printf 'unknown')"
		printf 'ERROR: deployment failed; previous image=%s backup=%s\n' "$previous_image" "$backup_timestamp" >&2
		if [[ "$current_flyway_version" == "$previous_flyway_version" ]]; then
			printf 'Recovery command: sudo /opt/wedding/scripts/production/deploy.sh %s\n' "${previous_image##*:}" >&2
		else
			printf 'Schema changed or is unknown; do not roll the image back. Follow the documented restore.sh procedure using backup %s.\n' "$backup_timestamp" >&2
		fi
	fi
	exit "$status"
}
trap deployment_failure EXIT

APP_IMAGE="$target_image" compose pull app
temporary_env="$(mktemp "${ENV_FILE}.deploy.XXXXXX")"
awk -v image="$target_image" '
	/^APP_IMAGE=/ { print "APP_IMAGE=" image; next }
	{ print }
' "$ENV_FILE" >"$temporary_env"
chown --reference="$ENV_FILE" "$temporary_env"
chmod --reference="$ENV_FILE" "$temporary_env"
mv -- "$temporary_env" "$ENV_FILE"
temporary_env=
env_changed=1

compose up -d app
wait_for_health

env_changed=0
trap - EXIT
printf 'Deployment completed: %s (backup: %s, previous Flyway: %s)\n' \
	"$target_image" "$backup_timestamp" "$previous_flyway_version"
