#!/usr/bin/env bash
# Smoke test against a deployed ReadShelf: proves the service answers, not that a deploy ran.
#
#   scripts/smoke.sh https://readshelf-api.fly.dev https://readshelf.pages.dev
#
# Every check is a behaviour we would otherwise only assume: that auth round-trips, that
# validation survived the move to production, and that CORS admits our web origin and nobody
# else. Exits non-zero on the first failure, so it can gate a release.
set -uo pipefail

API=${1:-}
WEB=${2:-}
if [ -z "$API" ] || [ -z "$WEB" ]; then
  echo "usage: $0 <api-base-url> <web-origin>" >&2
  exit 2
fi
API=${API%/}; WEB=${WEB%/}

pass=0; fail=0
ok()   { printf '  \033[32mok\033[0m   %s\n' "$1"; pass=$((pass+1)); }
bad()  { printf '  \033[31mFAIL\033[0m %s\n     %s\n' "$1" "$2"; fail=$((fail+1)); }
check(){ [ "$2" = "$3" ] && ok "$1" || bad "$1" "expected $3, got $2"; }

# A fresh address every run: register is idempotent only in the sense that a repeat is a 409.
EMAIL="smoke-$(date +%s)-$RANDOM@example.com"
PASSWORD="smoke-password-123"

echo "API $API"
echo "WEB $WEB"
echo

# 1 — the service is up at all.
check "1 GET /" "$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 "$API/")" "200"

# 2 — register returns a token we can actually use.
body=$(curl -s --max-time 20 -X POST "$API/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
token=$(printf '%s' "$body" | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
[ -n "$token" ] && ok "2 POST /auth/register returns a token" \
  || bad "2 POST /auth/register returns a token" "body: ${body:0:200}"

# 3 — login with the same credentials.
body=$(curl -s --max-time 20 -X POST "$API/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
login_token=$(printf '%s' "$body" | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
[ -n "$login_token" ] && ok "3 POST /auth/login returns a token" \
  || bad "3 POST /auth/login returns a token" "body: ${body:0:200}"

# 4 — the token identifies the account we just made.
body=$(curl -s --max-time 20 "$API/me" -H "Authorization: Bearer ${login_token:-none}")
case "$body" in
  *"$EMAIL"*) ok "4 GET /me echoes the registered email" ;;
  *)          bad "4 GET /me echoes the registered email" "body: ${body:0:200}" ;;
esac

# 5 — search reaches the upstream and maps it.
body=$(curl -s --max-time 30 "$API/search?q=dune")
case "$body" in
  *'"title"'*) ok "5 GET /search?q=dune returns books" ;;
  *)           bad "5 GET /search?q=dune returns books" "body: ${body:0:200}" ;;
esac

# 6 — validation did not get lost on the way to production.
check "6 GET /search?q= is rejected" \
  "$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 "$API/search?q=")" "400"

# 7 — the browser origin we actually serve is admitted.
allowed=$(curl -s -o /dev/null -D- --max-time 20 -X OPTIONS "$API/search?q=dune" \
  -H "Origin: $WEB" -H 'Access-Control-Request-Method: GET' \
  | tr -d '\r' | sed -n 's/^[Aa]ccess-[Cc]ontrol-[Aa]llow-[Oo]rigin: //p')
[ "$allowed" = "$WEB" ] && ok "7 CORS preflight admits $WEB" \
  || bad "7 CORS preflight admits $WEB" "allow-origin: ${allowed:-<none>}"

# 8 — and nobody else. An open CORS policy on authenticated routes lets any page on the
# internet drive this API from a visitor's browser, so this one is a release blocker.
#
# The absence of the header only means something once we know the server answered: against a
# dead backend there is no header either, and this check would pass while nothing works.
headers=$(curl -s -o /dev/null -D- --max-time 20 -X OPTIONS "$API/search?q=dune" \
  -H 'Origin: https://evil.test' -H 'Access-Control-Request-Method: GET' | tr -d '\r')
status=$(printf '%s' "$headers" | sed -n '1s/^HTTP[^ ]* \([0-9]*\).*/\1/p')
evil=$(printf '%s' "$headers" | sed -n 's/^[Aa]ccess-[Cc]ontrol-[Aa]llow-[Oo]rigin: //p')
if [ -z "$status" ]; then
  bad "8 CORS preflight refuses https://evil.test" "no response from $API"
elif [ -n "$evil" ]; then
  bad "8 CORS preflight refuses https://evil.test" "allow-origin: $evil"
else
  ok "8 CORS preflight refuses https://evil.test"
fi

echo
echo "passed $pass, failed $fail"
[ "$fail" -eq 0 ] || exit 1
