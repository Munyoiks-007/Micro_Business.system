# Daraja callback and reconciliation contract

The Android application can request a sandbox STK push, but it cannot receive a trusted public Safaricom webhook. It must leave accepted attempts `PENDING` and timeouts `UNCERTAIN`; neither state is revenue. This client has no callback/reconciliation implementation, so no M-Pesa payment is promoted to `COMPLETED` by this app.

## Required trusted service

Before production use, move Daraja OAuth credentials and STK initiation to a trusted HTTPS service. An app-embedded Gradle property is useful for sandbox development but is not a secret after it has been packaged in an APK. Do not put production credentials in Android `BuildConfig`.

Suggested authenticated API:

1. `POST /v1/payment-intents` with Firebase ID token, local sale ID, amount in integer minor units, currency `KES`, and normalized customer phone. The service verifies the Firebase token, derives the UID from it, checks the sale belongs to that UID, and creates a unique intent/idempotency key.
2. The service sends STK Push and stores the expected owner UID, sale ID, amount, phone, checkout request ID, merchant request ID, and status. The Android app receives references and a pending status only.
3. Safaricom calls a public HTTPS endpoint such as `POST /v1/daraja/stk/callback`. Treat the callback as an untrusted notification unless Safaricom provides a verifiable signature for the configured product. Validate its shape and correlate both request IDs to exactly one stored intent. Independently reconcile through a Daraja status query that is supported for the configured product and identifiers. Safaricom's Transaction Status API requires an M-Pesa receipt number or originator conversation ID; do not assume the checkout request ID is valid for that API. If a result cannot be independently verified, leave the intent pending/uncertain. Verify expected amount, phone, receipt presence/format, and allowed state transition. Reject unknown or mismatched IDs and never accept an app-provided UID as ownership proof.
4. Process callbacks idempotently using a unique callback/checkout key and a database transaction. Duplicate callbacks return an acknowledgment without applying revenue twice. Only a verified successful result with all expected fields can transition the mapped sale to completed. A confirmed failure/cancellation may transition to failed/cancelled. Pending and uncertain states never become success from a timeout or a client retry.
5. The app queries `GET /v1/payment-intents/{id}` with Firebase ID token. The service checks the authenticated UID matches the stored owner and returns a signed/authenticated canonical state. Android updates Room from that trusted response; it does not trust arbitrary callback payloads.

## Operations

For local sandbox builds, put these keys in the user Gradle properties file (`%USERPROFILE%\.gradle\gradle.properties` on Windows, `~/.gradle/gradle.properties` on macOS/Linux), or pass them as `-P` Gradle properties. Do not commit credentials in the project. The app deliberately leaves Daraja disabled when any required value is missing:

```properties
DARAJA_SANDBOX_CONSUMER_KEY=your-sandbox-consumer-key
DARAJA_SANDBOX_CONSUMER_SECRET=your-sandbox-consumer-secret
DARAJA_SANDBOX_SHORT_CODE=174379
DARAJA_SANDBOX_PASS_KEY=your-sandbox-passkey
DARAJA_SANDBOX_CALLBACK_URL=https://your-public-https-host/callback
```

The callback URL must be a real public HTTPS endpoint for callbacks to reach your service. It does not affect OAuth token issuance. HTTP 401/403 from the OAuth endpoint means the configured consumer key/secret were rejected; a missing configuration is reported separately. Never paste production credentials into Android Gradle properties because packaged `BuildConfig` values can be extracted from the APK.

Keep sandbox and production credentials, callback hostnames, and Daraja base URLs in separate server-side environments. Test valid, duplicate, delayed, malformed, mismatched amount/phone/reference, and failure callbacks in sandbox before production approval. Configure production callbacks only after the trusted service is deployed and monitored. Never expose secrets, full callback bodies, access tokens, or customer credentials in logs.
