# Dialog refresh 0.4.8

Scope is native dialogs and transient feedback only. No route, TV scene geometry, player controls or phone screen redesign.

Connect playlist uses a shared dark rounded surface, theme accents, filled primary action, destructive remove, device-code copy, labeled icon fields and Xtream/M3U tabs. Only the chosen tab is submitted; credentials remain Keystore-encrypted. All list/action/error/update/install-permission/download and playback-option dialogs use the same skin. Android system installer/permission screens remain OS-owned.

Native Robolectric captures cover phone landscape and TV dimensions across Red, Gold, Blue, Royal, Sunset Prayer and Harak. Additional captures cover scrolled password, reduced window height and RTL. Reduced-height simulation is not a physical keyboard/device test. Remote focus is explicit via state drawables and native platform navigation; touch and input fields remain native widgets.

Verification results and signature receipt will be appended after final build.

Final verification passed. Full Robolectric suite 118 tests, zero failures/errors, four existing skips. Native graphics captures for phone landscape and TV, red/gold/blue/royal/prayer/Harak, Xtream and M3U, scrolled password, 300px keyboard-resize simulation, RTL. Native renders visually inspected. Corrected dark tab text on gold. Scroll test explicitly selects actual custom form rather than platform hidden message ScrollView. Not a physical-device IME test.
APK code12/version0.4.8 zip CRC and matching previous signing certificate verified. SHA256 in artifacts/verification-0.4.8.json. All stock app dialog builder sites replaced, custom toast style; OS permission/install screens remain OS-owned. No other screen layout changes. Safe cleanup recorded separately.
