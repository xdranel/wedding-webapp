#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

readonly WORK_DIR=/tmp/wedding-phase7d
readonly COMPOSE_PROJECT_NAME=wedding-phase7d
readonly ZAP_IMAGE=zaproxy/zap-stable:2.17.0
readonly K6_IMAGE=grafana/k6:1.8.0
readonly TRIVY_IMAGE=aquasec/trivy:0.69.1
readonly TOOL_LABELS='lighthouse@13.3.0 axe-core@4.13.0'
readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

usage() {
	cat <<'EOF'
Usage: scripts/production/verify-release.sh --image ghcr.io/OWNER/wedding-webapp:vX.Y.Z

Runs serial release gates against a disposable synthetic Compose project.
Requires: docker compose, curl, jq, openssl, node/npm, and Maven.
Reports are written below /tmp/wedding-phase7d/reports.
Operational backup/restore/erasure, restart, WAN-loss, and Quick Tunnel drills
remain explicit checklist steps in docs/testing/phase-7d-production-acceptance.md.
EOF
}

[[ ${1:-} != --help && ${1:-} != -h ]] || { usage; exit 0; }
[[ ${1:-} == --image && ${2:-} =~ ^ghcr\.io/[a-z0-9._-]+/[a-z0-9._-]+:v[0-9]+\.[0-9]+\.[0-9]+$ ]] || {
	usage >&2; exit 2;
}
readonly APP_IMAGE="$2"

for command in docker curl jq openssl node npm; do command -v "$command" >/dev/null || { echo "Missing command: $command" >&2; exit 1; }; done
docker compose version >/dev/null

cleanup() {
	docker compose --project-name "$COMPOSE_PROJECT_NAME" --env-file "$WORK_DIR/.env" \
		-f "$ROOT_DIR/compose.production.yaml" down --volumes --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT
rm -rf -- "$WORK_DIR"
mkdir -p "$WORK_DIR/media" "$WORK_DIR/reports" "$WORK_DIR/sessions"
chmod 0777 "$WORK_DIR/media"

secret="$(openssl rand -hex 32)"
db_password="$(openssl rand -hex 24)"
cat >"$WORK_DIR/.env" <<EOF
APP_IMAGE=$APP_IMAGE
APP_BIND_ADDRESS=127.0.0.1
MYSQL_DATABASE=wedding
MYSQL_USER=wedding
MYSQL_PASSWORD=$db_password
MYSQL_ROOT_PASSWORD=$(openssl rand -hex 24)
DB_USERNAME=wedding
DB_PASSWORD=$db_password
ADMIN_USERNAME=phase7d-owner
ADMIN_PASSWORD=phase7d-owner-password
INVITATION_BASE_URL=http://127.0.0.1:8080/i
INVITATION_SIGNING_SECRET=$secret
MEDIA_DIRECTORY=$WORK_DIR/media
CLOUDFLARE_TUNNEL_TOKEN=unused
EOF

compose=(docker compose --project-name "$COMPOSE_PROJECT_NAME" --env-file "$WORK_DIR/.env" -f "$ROOT_DIR/compose.production.yaml")
"${compose[@]}" up -d mysql app

wait_for_health() {
	for _ in {1..60}; do curl -fsS --max-time 3 http://127.0.0.1:8080/login >/dev/null && return; sleep 2; done
	return 1
}
wait_for_health
"${compose[@]}" exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
	<"$ROOT_DIR/verification/phase-7d-synthetic.sql"

# Regression and immutable-image security gates run serially.
(cd "$ROOT_DIR" && ./mvnw test && node --test src/test/js/*.test.js)
docker image inspect "$APP_IMAGE" >"$WORK_DIR/reports/image-inspect.json"
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock "$TRIVY_IMAGE" image \
	--ignore-unfixed --severity HIGH,CRITICAL --exit-code 1 "$APP_IMAGE" \
	>"$WORK_DIR/reports/trivy.txt"

echo "$TOOL_LABELS" >"$WORK_DIR/reports/tool-versions.txt"
npm --prefix "$ROOT_DIR/verification" ci
npm --prefix "$ROOT_DIR/verification" exec -- playwright install chromium

# Fixture URL is signed at runtime; no invitation token is written to Git.
uuid=70000000-0000-0000-0000-000000000001
signature="$(printf '%s' "$uuid:1" | openssl dgst -sha256 -hmac "$secret" -binary | base64 | tr '+/' '-_' | tr -d '=')"
guest_url="http://127.0.0.1:8080/i/$uuid/1/$signature?language=EN"
jq -n --arg url "$guest_url" '[range(0;100) | $url]' >"$WORK_DIR/invitations.json"

npm --prefix "$ROOT_DIR/verification" exec -- lighthouse "$guest_url" \
	--config-path="$ROOT_DIR/verification/lighthouse.config.cjs" --output=json \
	--output-path="$WORK_DIR/reports/lighthouse.json" --chrome-flags='--headless --no-sandbox'
jq -e '.categories.performance.score >= 0.80 and .categories.accessibility.score >= 0.90
	and .categories["best-practices"].score >= 0.90' \
	"$WORK_DIR/reports/lighthouse.json" >/dev/null
VERIFY_FIXTURE_KIND=synthetic VERIFY_BASE_URL=http://127.0.0.1:8080 VERIFY_GUEST_URL="$guest_url" \
	VERIFY_ADMIN_USERNAME=phase7d-owner VERIFY_ADMIN_PASSWORD=phase7d-owner-password \
	VERIFY_STAFF_USERNAME=phase7d-staff-1 VERIFY_STAFF_PASSWORD=phase7d-staff-password \
	VERIFY_REPORT="$WORK_DIR/reports/accessibility.json" node "$ROOT_DIR/verification/accessibility.mjs"

docker run --rm --network host --user "$(id -u):$(id -g)" --env HOME=/zap/wrk \
	-v "$ROOT_DIR/verification/zap-rules.tsv:/zap/wrk/zap-rules.tsv:ro" \
	-v "$WORK_DIR/reports:/zap/wrk:rw" "$ZAP_IMAGE" zap-baseline.py \
	-t http://127.0.0.1:8080/login -c zap-rules.tsv -J zap.json -r zap.html

# Session fixture generation is deliberately bounded to five distinct accounts/guests.
echo '[]' >"$WORK_DIR/staff.json"
for number in {1..5}; do
	jar="$WORK_DIR/sessions/$number.cookies"
	login_html="$(curl -fsS -c "$jar" http://127.0.0.1:8080/login)"
	csrf="$(grep -o 'name="_csrf"[^>]*value="[^"]*"' <<<"$login_html" | sed 's/.*value="\([^"]*\)"/\1/')"
	curl -fsS -b "$jar" -c "$jar" -X POST -d "username=phase7d-staff-$number" \
		-d 'password=phase7d-staff-password' --data-urlencode "_csrf=$csrf" http://127.0.0.1:8080/login >/dev/null
	page="$(curl -fsS -b "$jar" "http://127.0.0.1:8080/check-in/preview/guest/$number")"
	csrf="$(grep -o 'name="_csrf"[^>]*value="[^"]*"' <<<"$page" | sed 's/.*value="\([^"]*\)"/\1/')"
	cookie="$(sed 's/^#HttpOnly_//' "$jar" | awk '!/^#/ && NF >= 7 {print $6"="$7}' | paste -sd ';' -)"
	jq --argjson n "$number" --arg csrf "$csrf" --arg cookie "$cookie" \
		'. + [{baseUrl:"http://127.0.0.1:8080",guestId:$n,search:("Synthetic Guest "+($n|tostring)),guestVersion:0,csrf:$csrf,cookie:$cookie}]' \
		"$WORK_DIR/staff.json" >"$WORK_DIR/staff.next" && mv "$WORK_DIR/staff.next" "$WORK_DIR/staff.json"
done

docker run --rm --network host --user "$(id -u):$(id -g)" -v "$ROOT_DIR/verification/k6:/scripts:ro" \
	-v "$WORK_DIR:/fixtures:ro" -e INVITATION_FIXTURES=/fixtures/invitations.json "$K6_IMAGE" run /scripts/invitation-read.js
docker run --rm --network host --user "$(id -u):$(id -g)" -v "$ROOT_DIR/verification/k6:/scripts:ro" \
	-v "$WORK_DIR:/fixtures:ro" -e STAFF_FIXTURES=/fixtures/staff.json "$K6_IMAGE" run /scripts/staff-check-in.js

printf 'Automated release gates passed. Complete the explicit operator drills in the Phase 7D checklist.\n'
