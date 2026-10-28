Isolated universe panel v2 client

Construct PanelClient with KeystorePanelStore(applicationContext), HttpsPanelTransport, System::currentTimeMillis, SecureRandom and a listener. Call open on a background thread once per app open. Display displayCode only; never display the stored token. No UI or playback code is wired by this change.

Only one client/store owner per process is supported. Listener callbacks run on the caller thread. onConfig receives a defensive JSON copy; the integration must consume playlists without logging or persisting plaintext. onStopped must stop access and discard any provider objects held outside this module. Permanent denied responses remove the module's cached config. No code-only or legacy endpoint exists.

Offline policy is an explicit maximum 24-hour lease from the last successful config and never beyond expires_at. open emits cache first and then refreshes. A transport failure preserves this lease; denial ends it. The integration must also enforce lease expiry while playback remains open; the module intentionally has no timer/background scheduler. A blocked/pending device should refresh on later app opens, not rapidly poll. Registration/network backoff is durable and jittered. Successful config calls persist a minimum 60-second cooldown, shared across restarts.

Renewal is allowed only after an observed pending response, no observed active response, and authoritative registration_expired. Inactive is never pending proof. Unexpected unauthorized, unknown state or collision never rotates identity. An absent encrypted file with an existing Keystore alias is also identity loss, not first installation. If local ciphertext or Keystore key is lost/corrupt, fail closed and request admin-assisted recovery; never erase the envelope to retry. Whole app data erasure is outside this module's detection capability.

KeystorePanelStore keeps identity, active/pending proof, retries and config in one AES-GCM authenticated AtomicFile under noBackupFilesDir. No cleartext fallback. Production Android Keystore and atomic crash/power-loss behavior require device testing. JVM protocol tests use injected memory storage and fake transport, never real customer data or live registration.

Transport uses only the documented public anon key, a fixed HTTPS backend and two allowlisted RPCs. Redirects disabled; bounded bodies and timeouts. Provider URLs returned in config are not fetched by this module. API contract source /home/kit/tmp/panel/API.md, live verified 2026-10-28.

Hardening review: every active observation durably records everActive and removes pendingProof before metadata/config validation. Active responses must include matching device_code and universe app_type. Missing, mismatched, malformed or expired active config never grants renewal permission.
