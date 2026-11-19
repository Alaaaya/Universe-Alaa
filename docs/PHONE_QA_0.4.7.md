Universe 0.4.7 keeps the original TV scene on phones, scaled uniformly from the same 1920x1080 coordinates. The separate stacked phone layout and alternate phone player controls are removed. Landscape is requested for every device.

Touch uses existing click actions, first-tap activation, swipe pagination in category/content/season/episode/channel regions, and video taps to reveal the original controls. TV input gating and remote handlers remain unchanged. Category badges, updater and other 0.4.6 fixes remain.

Full build and unit suite passed with 116 tests, zero failures/errors, four existing skips. Native Robolectric renders captured 24 phone landscape and 24 matching TV scenes across red/gold/royal/Harak, covering home, settings, live, movies, series and player. Contact sheet visually reviewed; no blank screens or missing TV rails/grids. Phone versus downscaled TV mean per-channel pixel differences range 0.482–3.410 out of 255 (font/raster differences). Same coordinate assertions, first real tap, swipe paging, original native player/back and touch-enabled TV geometry pass.

APK package com.alaa.universe, code 11, name 0.4.7. ZIP CRC passes. Signing certificate matches 0.4.5 and 0.4.6. Public download SHA matches local artifact; durable version.json reports 0.4.7. Existing durable server init retained.

No physical phone/TV playback or update installation was tested.
