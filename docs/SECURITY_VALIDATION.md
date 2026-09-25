# Security Validation

Production Worker deployments require `CACHE`, `SOCIAL_KV`, `GATEWAY_API_KEY`, and
`FIREBASE_PROJECT_ID`. Sync access uses a Firebase ID token whose verified `sub` claim
is the canonical user ID. The Worker derives identity from that claim and never from
the path or request body.

```sh
# Unauthenticated sync: 401
curl -i https://gateway.example/sync/library/user-a

# Signed token for user-a accessing user-b: 403
curl -i -H "Authorization: Bearer $USER_A_SYNC_TOKEN" \
  https://gateway.example/sync/library/user-b

# Matching signed identity and body: 200
curl -i -X POST https://gateway.example/sync/library/user-a \
  -H "Authorization: Bearer $USER_A_SYNC_TOKEN" \
  -H 'Content-Type: application/json' \
  --data '{"vantaUserId":"user-a","deviceName":"Pixel","generatedAtMs":1730000000000}'
```

Production station methods require a Firebase ID token in `Authorization: Bearer`.
The verified Firebase `sub` is the only station user identity; JSON-RPC `userToken`
and request fields cannot replace it. A verified Firebase request does not require an
additional partner token. Legacy user tokens and demo login are accepted only when
`NODE_ENV=development` and `FIREBASE_PROJECT_ID` is absent.

```sh
# Production demo login: JSON-RPC error 1004; no user token is issued.
curl -i -X POST https://station.example/jsonrpc \
  -H 'Content-Type: application/json' \
  --data '{"jsonrpc":"2.0","method":"auth.userLogin","params":{"username":"demo","password":"anything"},"id":1}'

# Missing Firebase token for a model request: HTTP 401 / JSON-RPC error 1003.
curl -i -X POST https://station.example/jsonrpc \
  -H 'Content-Type: application/json' \
  --data '{"jsonrpc":"2.0","method":"station.createStation","params":{"seedText":"jazz"},"id":1}'

# With a valid Firebase token, oversized counts are clamped server-side (create 5-30, refill 5-20).
curl -i -X POST https://station.example/jsonrpc \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $FIREBASE_ID_TOKEN" \
  --data '{"jsonrpc":"2.0","method":"station.createStation","params":{"seedText":"jazz","trackCount":10000},"id":1}'
```

Without the Worker `RATE_LIMITER` binding, production protected routes return `503` rather
than proceeding without enforcement. Development bypasses require explicit
`ENVIRONMENT=development` (or `NODE_ENV=development`), and sync additionally requires
`DEV_SYNC_USER_ID` plus a matching `X-Dev-Sync-User` header.
