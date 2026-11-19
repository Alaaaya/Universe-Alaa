0.4.6 QA scope

Phone touch layout is separate from the original TV design-coordinate layout. TV detection uses UI mode; phone touch is enabled only with a touchscreen and not television UI mode.

Issues addressed during code review
- Fixed first-tap focus consumption by turning off focusableInTouchMode on phone action rows; TV focus flags are unchanged.
- Replaced scaled-down TV targets with minimum 48dp back and 56dp list/action rows, readable 16sp labels, drag scrolling, and per-route scroll reset.
- Added phone paging actions for categories, live/VOD entries, seasons, episodes and player channel overlay so touch can reach items beyond the old D-pad-only page windows.
- Added phone player buttons for previous/next channel, channel list, back, play/pause, 10-second seeking, audio, subtitles and favorites; tapping video restores hidden controls.
- Kept player overlay full-size on phones rather than shrinking controls into a TV letterbox.
- Made leanback optional, retained ordinary and TV launchers, removed global landscape lock on phones and retained runtime TV landscape.
- Resizes phone content on rotation without recreating the active playback session.
- Kept on-screen keyboard in native provider/login dialogs and added resize behavior.
- Added automatic category badges for brands, country flags, regions, genres and generic fallback. Original provider category IDs and names stay unchanged. Brands take precedence over country/language tags; specific countries take precedence over generic Arab region.
- Added updater access to the TV focus chain as well as touch layout.
- Added package/signature/version checks before handing downloaded updates to Android's installer. Only private update-cache APKs are shared through FileProvider.
- Existing provider visibility policy is unchanged.

Verification results will be appended after the build and test runs.

Visual inspection, 2026-11-19
Phone home, settings, live, movie, player and Harak home native Robolectric captures inspected individually. Full movie row titles appear rather than single-letter placeholders. Settings exposes Check for updates. Harak content is nonblank after its 250ms transition settles. No overlapping controls observed; partially visible bottom rows belong to the scrollable list. Live-player capture intentionally uses a fixture without video, so it does not establish provider playback. Country and brand labels show in lists. Current limitations include low-contrast red heading and gold player labels, and no physical Android phone/TV, network update-install, or media playback test in this environment.

Artifact verification before final tests
0.4.6 staged APK SHA256 17059930ea8f032082dd1c29cc8584bb1204800133d6426caaa91d3202b7bca6. Both 0.4.5 and 0.4.6 pass apksigner verification with matching certificate SHA256 c32a2303f317e28574a312e40081e1cd4cc8b9eb3f1eff4bb6bf899dd10b1cc7.

Final verification
Run8 completed with RESULT=0 and BUILD SUCCESSFUL. Full suite 117 tests, 0 failures, 0 errors, 4 skipped; test timestamps 2026-11-19T13:09:48Z through 13:10:15Z. assembleDebug passed. Package com.alaa.universe, versionCode10/versionName0.4.6, minSdk23/targetSdk36. ZIP CRC passes. Signing certificate matches 0.4.5. Public HTTPS APK downloaded back with curl and SHA256 matched durable version.json. Manifest also HTTP200 with Android Dalvik user-agent. Python urllib through this environment returned403, so it was not used as delivery proof. HTTP40000 serves /home/kit/universe-distribution and its restart entry exists in ~/.hako/init.sh. No physical device update install or provider playback tested. No Git push performed.
