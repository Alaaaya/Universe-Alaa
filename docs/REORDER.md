Media order in 0.3

In real-provider Live TV, hold OK on a channel to enter move mode. Hold OK on a category in Live TV, Movies or Series to move categories. Up and Down change its position, a fresh OK press saves, and Back cancels. An arrow marker and footer identify the active move. The initial long-press release and repeated OK key-downs do not select or play an item. Ordinary short OK keeps its existing selection/play action.

Order is local to this device and isolated by manual/panel source, account, media kind and category. Only ID arrays and SHA-256 scope keys are persisted. Provider cache refreshes do not replace the saved order; unseen IDs are appended, unavailable IDs are omitted, and temporarily absent saved IDs are retained for future merging. Cancel never writes the draft. Leaving the foreground discards an unfinished move. It does not change playlist credentials, provider catalogues or the panel protocol.

Version name stays 0.3, with version code 4 so it can replace the earlier pairing-only 0.3 build (code 3). Secure pairing and clock-lease hardening from the existing repository are retained. This is a local debug APK, not a store release. Native Robolectric key, persistence and rendering tests are not proof of physical remote/decoder behavior; real TV verification is still needed.
