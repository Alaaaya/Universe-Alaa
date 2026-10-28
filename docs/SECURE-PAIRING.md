Future pairing contract, not deployed or implemented

The old short-code-only anonymous configuration RPC must not be reused. Never authorize configuration by MAC, installation label, or display code. Do not migrate its credentials into this preview.

A trusted HTTPS service issues a short-lived pairing session containing a human-readable display code, independent random 256-bit polling secret, and expiry. Store only hashes of secrets. Issue a random installation identifier for display, formatted like a MAC if desired, but not the real network MAC. The authenticated owner claims the code with rate limits and explicit tenant ownership, then assigns a playlist or direct-channel catalogue and expiry. Atomic one-time claim prevents duplicate or raced claims.

Only the holder of the polling secret may obtain the single-use exchange result. Issue a scoped high-entropy installation credential and store it using Android Keystore-backed encryption, not in logs or unencrypted preferences. Bound retries, exponential backoff and generic failure responses prevent discovery. Production code will never ship a Supabase service-role key.

GET device/config requires a valid, unrevoked device credential, validates current status and expiry server-side, and returns only assigned catalogue entries. RLS alone does not fix SECURITY DEFINER RPC leaks. Avoid anonymous direct SELECT on credentials or channel stream URLs. Prefer trusted edge functions with explicit tenant/device checks and narrowly granted underlying access. Use access-token expiry/rotation or hash-validated opaque device tokens and owner revocation.

The owner panel uses authenticated Supabase sessions and tenant-scoped RLS. Device and playlist ownership must be checked on every mutation, including linking a reusable direct channel. Proposed channels/channel_groups/playlist_channels tables hold stable identifiers, revisions and enabled/order fields. Playback endpoints use per-device auth, never public credential-bearing M3U URLs. HTTPS by default; any legacy HTTP provider exception must be explicit and narrow.

Separate catalogue from user preferences, keyed by stable source IDs, to preserve favorite/order/resume on refresh. Distinguish pending, activated, explicitly expired/revoked and network failure. Offline grace policy needs owner decision before implementation. Do not treat a network timeout as account expiry.

Required negative tests before any production enablement include unauthenticated config, wrong-device token, cross-owner assignment, reused/expired claims, brute-force rate limits, revoked tokens, removed playlists, secret redaction and offline cache boundaries. Backend validation and native Keystore tests are not covered by phase-1 UI tests.
