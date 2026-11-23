# Atlas theme (أطلس)

Atlas is a selectable Arabic-first landscape skin inside Universe-Alaa, not a replacement for the default theme. Select **أطلس · Atlas** under Theme. The separate debug-signed Atlas Preview build starts in Atlas and installs beside the standard app (`com.alaa.universe.atlaspreview`). It starts disconnected; use **تغيير المصدر** to connect your own permitted playlist. No accounts, credentials or fixtures are bundled.

- Home: a flat green palette and vertical navigation with visible D-pad focus.
- Live: categories at right, channels in the middle, and one in-page preview on the left. OK on a channel starts preview; pressing OK again on the playing channel or choosing **ملء الشاشة** opens fullscreen intentionally. Back returns to the inline browser. Up/down traverses or pages the active list, left/right changes columns; on phone, tap or swipe vertically inside the desired list.
- Movies and series: right-aligned titles and a 5 × 2 poster grid, with a separate vertical category rail. Scroll or use D-pad down from the bottom row to reach the next page.
- Settings: separate vertical menus for appearance and playlist/app actions. Legacy theme choices remain available.

Atlas binds only the visible page (at most 9 categories and 10 channel/poster controls at a time), uses the existing off-main-thread catalogue index and ordering caches, and retains one preview widget across live pages. There are no shadows, blur, or scale transitions. This limits render work; measure frame times, memory and stream startup on the intended low-end hardware before treating it as a performance guarantee.

Build privately with JDK 17 and the Android 36 SDK:

```sh
./gradlew :app:assembleAtlasPreview
./gradlew :app:testDebugUnitTest --tests 'com.alaa.universe.AtlasThemeTest'
```

The native-GUI tests use invented fixture data (9,000 live channels), exercise D-pad navigation and phone touch paths and render the five screens. They do not stand in for a production provider or a low-end physical TV.
