#!/usr/bin/env bash
# End-to-end check through nginx, as a browser would reach the app. Run from the project root after
#   docker compose up -d --build
# Usage: bash scripts/smoke.sh [base-url]     (default http://localhost:8080; reads ADMIN_* from .env)
# It writes test data (a user and a product), so run it locally, never against production.
set -u
BASE="${1:-http://localhost:8080}"
set -a; . ./.env; set +a
JAR=$(mktemp); ADMIN_JAR=$(mktemp); trap 'rm -f "$JAR" "$ADMIN_JAR"' EXIT
pass=0; fail=0

check() { # name expected actual
  if [ "$2" = "$3" ]; then pass=$((pass + 1)); echo "  ok   $1"; else fail=$((fail + 1)); echo "  FAIL $1 (expected $2, got $3)"; fi
}
status() { # jar method path [json] [no-csrf]
  local jar=$1 method=$2 path=$3 body=${4:-} token
  token=$(awk '$6 == "XSRF-TOKEN" { print $7 }' "$jar")
  local args=(-s -o /tmp/smoke-body -w '%{http_code}' -b "$jar" -c "$jar" -X "$method" "$BASE$path")
  [ -n "$body" ] && args+=(-H 'Content-Type: application/json' --data "$body")
  [ "${5:-}" != "no-csrf" ] && [ -n "$token" ] && args+=(-H "X-XSRF-TOKEN: $token")
  curl "${args[@]}"
}

echo "Pages and headers"
check "home page"            200 "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/")"
headers=$(curl -s -D - -o /dev/null "$BASE/")
check "CSP header"           1 "$(grep -ci '^content-security-policy:' <<<"$headers")"
check "nosniff header"       1 "$(grep -ci '^x-content-type-options: nosniff' <<<"$headers")"
check "no nginx version"     0 "$(grep -ci '^server: nginx/' <<<"$headers")"

echo "Anonymous"
curl -s -o /dev/null -c "$JAR" "$BASE/api/auth/csrf"
check "list products"        200 "$(status "$JAR" GET /api/products)"
check "me without login"     401 "$(status "$JAR" GET /api/auth/me)"
check "create without login" 401 "$(status "$JAR" POST /api/products '{"name":"x","price":1}')"
check "unknown path hidden"  401 "$(status "$JAR" GET /api/nope)"
check "no stack trace"       0 "$(grep -c 'Exception' /tmp/smoke-body)"
check "actuator not public"  404 "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/actuator/health")"

echo "User"
email="user$RANDOM$RANDOM@example.com"
check "weak password"        400 "$(status "$JAR" POST /api/auth/register "{\"email\":\"$email\",\"password\":\"short\"}")"
check "register"             201 "$(status "$JAR" POST /api/auth/register "{\"email\":\"$email\",\"password\":\"correct-horse-1\"}")"
check "register again"       409 "$(status "$JAR" POST /api/auth/register "{\"email\":\"$email\",\"password\":\"correct-horse-1\"}")"
check "wrong password"       401 "$(status "$JAR" POST /api/auth/login "{\"email\":\"$email\",\"password\":\"wrong-password\"}")"
check "login"                200 "$(status "$JAR" POST /api/auth/login "{\"email\":\"$email\",\"password\":\"correct-horse-1\"}")"
check "no hash in answer"    0 "$(grep -ci 'password' /tmp/smoke-body)"
check "session HttpOnly"     1 "$(grep -c '#HttpOnly_.*SID' "$JAR")"
check "me is USER"           1 "$(status "$JAR" GET /api/auth/me >/dev/null; grep -c '"role":"USER"' /tmp/smoke-body)"
check "user cannot create"   403 "$(status "$JAR" POST /api/products '{"name":"x","price":1}')"
check "POST without CSRF"    403 "$(status "$JAR" POST /api/auth/logout '' no-csrf)"
check "logout"               204 "$(status "$JAR" POST /api/auth/logout)"
check "me after logout"      401 "$(status "$JAR" GET /api/auth/me)"

echo "Admin"
curl -s -o /dev/null -c "$ADMIN_JAR" "$BASE/api/auth/csrf"
check "admin login"          200 "$(status "$ADMIN_JAR" POST /api/auth/login "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")"
check "bad price"            400 "$(status "$ADMIN_JAR" POST /api/products '{"name":"Tea","price":-5}')"
check "create product"       201 "$(status "$ADMIN_JAR" POST /api/products '{"name":"  Tea  ","price":40}')"
id=$(sed -E 's/.*"id":([0-9]+).*/\1/' /tmp/smoke-body)
check "name was trimmed"     1 "$(grep -c '"name":"Tea"' /tmp/smoke-body)"
check "delete product"       204 "$(status "$ADMIN_JAR" DELETE "/api/products/$id")"
check "delete again"         404 "$(status "$ADMIN_JAR" DELETE "/api/products/$id")"
check "unknown api path"     404 "$(status "$ADMIN_JAR" GET /api/nope)"
check "no stack trace"       0 "$(grep -c 'Exception' /tmp/smoke-body)"

echo "Rate limit (login is limited to 10/min after a burst of 5)"
limited=0
for _ in $(seq 1 15); do
  [ "$(status "$JAR" POST /api/auth/login '{"email":"nobody@example.com","password":"wrong-password"}')" = 429 ] && limited=1
done
check "login flood gets 429" 1 "$limited"

echo
echo "passed $pass, failed $fail"
[ "$fail" -eq 0 ]
