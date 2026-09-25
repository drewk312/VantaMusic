# Firebase Setup

VANTA uses Firebase Authentication only for cloud account features. Local playback,
imports, and the offline library continue to work without Firebase.

1. Create a Firebase project on the no-cost Spark plan.
2. Add an Android app with package name `com.audiophile.musicplayer`.
3. In **Authentication > Sign-in method**, enable **Email/Password**.
4. Download `google-services.json` and place it at `app/google-services.json`.
   This repository ignores that file. It contains project identifiers, not a server
   secret, but keeping it untracked avoids coupling public builds to one Firebase app.
5. Set `FIREBASE_PROJECT_ID` in the Cloudflare Worker environment to the Firebase
   project ID. Production sync rejects requests when this value is absent.
6. Set `FIREBASE_PROJECT_ID` in the station backend environment. Production station
   methods reject requests whose Firebase ID tokens cannot be verified for that project.

Development behavior:

- Without `app/google-services.json`, the Account screen reports that cloud sign-in is
  unavailable. It does not create a fake cloud user.
- The Worker supports its explicit development-only token path. Production must use
  Firebase ID tokens, `FIREBASE_PROJECT_ID`, and the `RATE_LIMITER` binding. A
  configured legacy HMAC secret is not accepted in production.
- Do not enable phone/SMS authentication for the no-cost path.

Manual smoke check after configuration:

1. Create an email/password account in VANTA.
2. Confirm the account screen shows the authenticated email.
3. Enable sync and confirm the Worker receives `Authorization: Bearer <Firebase ID token>`.
4. Repeat a sync request with a different path or body user ID; it must return `403`.
5. Sign out, then confirm sync/station calls stop while offline playback still works.

Station calls use the Firebase ID token in `Authorization: Bearer <token>` and retry
once with a refreshed token after a `401`. They do not use a locally generated cloud
user ID or a manually stored station bearer token.
