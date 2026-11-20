0.4.10 investigation
Actual API28 x86 emulator,768MB RAM128MB heap, hardware acceleration.
Initial network failure isolated to emulator Wifi virtual network with no route; switched to ethernet emulator -feature -Wifi. Actual dialog M3U tab, URL field and Connect button completes12.492s in first native instrumentation run. This run only checked closure; strengthened test now also checks cached channel count >8000.
Second native suite includes panel open/load callback using production real HTTPS/AndroidKeyStore networking on worker with main-looper heartbeat assertions. Config transport uses public fixture, never impersonates owner registration.
Main-thread Connect already uses IO executor, not changed to another speculative framework. Slow per-byte String.format SHA hex (~32formatter calls/channel) replaced by character lookup preserving stable IDs; regex compiled once per list. 90s operation deadline covers parsing/save with progress; manual95s watchdog permits retry, ignores late response. Manual connection no longer loses callback generation to asynchronous panel change.
Unit tests121 zero failures4existing skips.
Physical owner device still must verify. No claim owner authenticated panel connection tested.
