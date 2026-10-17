# Weather Wallpaper — handover

State as of 14 October 2026. Read this before starting work in a new chat.

## The project

- An Android live wallpaper, package `com.weatherwall.app`, written in Kotlin with Canvas drawing.
- Gitea repo: `Dawie_Personal/SouthAfricaLWP` (https://git.sudologix.co.za).
- Live weather comes from the user's location or a typed city (`WeatherRepository.kt`).
- The weather changes the sky, clouds, rain, storm and fog. The sun, moon, Milky Way and stars follow the real day and night.
- Three scenes are chosen in Settings:
  - **Cape Town (Table Bay):** Table Mountain with its tablecloth, the city, the stadium, boats, a cruise ship, whales and dolphins, hadedas, the banner plane and the Airlink stadium flypast.
  - **Savanna:** an acacia plain with a herd, a lion hunt that brings in vultures, Egyptian geese in a V, and grass that changes with the season.
  - **Jhb City:** Hillbrow Tower, Ponte City with its Vodacom sign, jacarandas, 737s in local airline colours, and pigeon flocks that settle on rooftops.

## Branches

| Branch | What it is |
|---|---|
| `master` | The first Cape Town version |
| `Cape-Town` | The Cape Town scene, frozen |
| `Cape-Town_Savanna` | Cape Town and Savanna |
| `Jhb-City` | All three scenes |
| **`Battery-Optimize`** | **The current working branch.** All three scenes, plus the battery work and every feature listed below. Pushed and clean. |
| `Altron-HQ` | All three scenes plus a fourth, **Altron — head office** (`render/AltronLandscape.kt`), branched from the cloud-levels work on GitHub. |
| `Rugby` | Has no rugby work: it points at an older `Battery-Optimize` commit. A rugby scene was planned and then cancelled. Whether to delete the branch is still open, so ask the user. |

The user asks for a new branch for each new scene.

## Recent work on `Battery-Optimize`, newest first

- **Holiday banner plane**
  - On SA holidays the banner plane tows a greeting such as "Merry Christmas!" or "Eid Mubarak!" (`Holidays.greeting`).
  - Each holiday has its own symbol in place of the Mavericks advert's two stars (`render/BannerSymbols.kt`). Examples: Santa, a crescent and star, a diya, the SA flag, a protea, a braai.
  - Cape Town: the plane flies every day, with the Mavericks banner on ordinary days.
  - Savanna and Jhb City: the plane flies only on holidays. In Jhb it flies higher, above the skyline.
  - The plane flies by day only. The exception is the fireworks preview, where it also flies at night (`holidayPlaneAtNight`).
- **Holiday fireworks** (`render/Fireworks.kt`)
  - About 20 SA holidays, each with its own colours and burst types. Easter, Diwali, Eid and Chinese New Year are calculated or looked up for 2026–2032.
  - The fireworks run from 30 minutes after sunset until sunrise, at half pace after 1am, with a midnight finale on New Year's Eve.
  - Settings has a "Fireworks — random holiday (night)" preview option.
- **Moon:** rises 30 minutes after sunset and sets 30 minutes after sunrise, on an arc like the sun's.
- **Milky Way:** a stylised arc from 2 hours after sunset to half an hour before sunrise. Its colours were taken from photos (`render/MilkyWay.kt`).
- **Rain sky** (`render/StormCeiling.kt`, an AGSL shader on Android 13 and later)
  - A Cape cold-front sky. The tablecloth stays on the mountain and is darker in rain.
  - Storm tuning lives in `StormDesign`.
- **Battery**
  - The frame rate adapts to how much is moving, with three tiers (`motionTier`: FAST, SLOW, CALM).
  - A Smoothness setting chooses 30, 24 or 15 fps; the default is 24.
  - Drawing pauses while the wallpaper is hidden or the phone is locked.
  - Debug timing is logged under the `Perf` tag (`PerfMeter.kt`).
- **Demo mode:** `runDemo()` in `WeatherWallpaperService.kt` plays a 60-second tour suited to each scene.

## Code map (`app/src/main/java/com/weatherwall/app/`)

### Altron head office scene (branch `Altron-HQ`)

- `render/AltronLandscape.kt`, with its settings in `AltronDesign`. The building is laid out in the reference photo's pixels (747 × 1042), using the photo's two-point perspective: the near corner, a left and a right vanishing point on eye level, and the long wall's bays spaced evenly in inverse distance.
- The scene has three looks:
  - Daylight colours.
  - The photo's dusk colours, from twilight.
  - Night: the dusk colours darkened, with the sign, carport lamps and foyer lit.
- The ALTRON wordmark is drawn as strokes and mapped onto the wall with `Matrix.setPolyToPoly`. The A has no crossbar, and the T has a teal flash on the left.
- `AltronDesign.ZOOM` controls the framing: 1 fits the photo's full width; above 1 zooms in on `FOCUS_X`.
- Scene id: `altron_hq`.

### Engine and settings

- `WeatherWallpaperService.kt`: the engine, the frame loop, pausing on lock, preference listeners, the demo and the fireworks preview.
- `SettingsActivity.kt`: scene, Smoothness, location and "Preview a condition". The debug intent extras are listed under "Testing on the phone".
- `Prefs.kt`: all preference keys.
- `DemoReceiver.kt`: debug-only broadcast receiver.

### Shared drawing (`render/`)

- `render/WeatherRenderer.kt`: owns the visitors that outlive weather crossfades. That covers wildlife, geese, jets, pigeons, the flypast, the banner plane, boats and the fireworks, plus the visitor scheduling ("lanes") and `desiredFps`.
- `render/WeatherScene.kt`: the sky, sun, moon, Milky Way, stars and clouds for one weather setup; it picks the scene's `Landscape`.
- `render/Landscape.kt`: the `Landscape` interface, `Scenes` (`cape_town`, `savanna`, `jhb_city`) and each scene's horizon.

### Scenes

- Cape Town: `CapeTownLandscape.kt`, `MountainDesign.kt`, `ShorelineCity.kt`, `Boats.kt`, `CruiseShip.kt`, `MarineLife.kt`, `StadiumFlypast.kt`, `BannerPlane.kt` and `BannerSymbols.kt`.
- Savanna: `SavannaLandscape.kt`, `SavannaDesign.kt`, `SavannaWildlife.kt`, `AnimalArt.kt`, `GooseSkein.kt` and `Season.kt`.
- Jhb City: `JhbCityLandscape.kt` (with `JhbDesign`), `JhbAirTraffic.kt` and `PigeonFlock.kt`.

## How the user likes to work

1. **Preview first.** For anything visual, build an in-chat `show_widget` preview with sliders and an "Apply … to my phone" button that calls `sendPrompt` with the chosen values. Write the Kotlin only after the user approves. The Kotlin should match the preview's maths and constants.
2. Build, install on the phone, check with screenshots, then commit and push.
3. **Make preview widgets defensive.** Wrap the script in an IIFE with try/catch and show any error text on the page. Blank widgets have happened.
4. Describe things in plain words, and confirm before doing anything public or hard to undo.

## Build and test

- **Build:** `./gradlew assembleDebug`. The APK is `app/build/outputs/apk/debug/app-debug.apk`.
- **adb location:** `~/AppData/Local/Android/Sdk/platform-tools/adb.exe` (it is not on PATH). In Git Bash, set `MSYS_NO_PATHCONV=1`.
- **Phone:** a Xiaomi with Nova launcher. It often drops off adb, so give every adb command a timeout.
- **Home screen:** `input keyevent` is blocked. Use `am start -a android.intent.action.MAIN -c android.intent.category.HOME` instead.
- **Screenshots:** `adb shell screencap -p /sdcard/x.png`, then `adb pull`.

### Testing on the phone

The debug extras go through SettingsActivity:

`am start -f 0x10008000 -n com.weatherwall.app/.SettingsActivity …`

| Extra | Effect |
|---|---|
| `--es scene cape_town\|savanna\|jhb_city` | Switch scene |
| `--es override CLEAR:night`, `FIREWORKS` or `auto` | Preview a condition, or go back to live weather |
| `--ez plane\|ship\|birds\|geese\|lions\|jet\|pigeons\|flypast true` | Send that visitor now |
| `--es sea …`, `--es boat …` | Sea life and boats |

The `DemoReceiver` broadcast also works:

`am broadcast -n com.weatherwall.app/.DemoReceiver …`

| Extra | Effect |
|---|---|
| `--es scene …` and `--es override …` | Same as above |
| `--ef time_offset_h N` | Shift the clock by N hours |
| `--ez demo true` | Play the demo |
| `--es fireworks "<Holiday name>"`, `finale` or `off` | Force a holiday's fireworks |

Notes for testing:
- When a value has a space in it, quote the whole shell command: `adb shell "am broadcast … --es fireworks 'Eid al-Fitr'"`.
- **Afterwards always reset** with `--es override auto`, `--es fireworks off` and the user's scene (normally `cape_town`).

## Gotchas

- **Gitea push:** the first `git push` often fails with an auth error. Retry once.
- **AGSL:** `flat` is a reserved word in shaders. Using it breaks the shader and the sky falls back to the plain clouds.
- **Shader paints:** set `p.color = Color.BLACK` before applying a shader, or a leftover alpha dims it.
- **Text when flying left:** mirrored planes must draw their text unmirrored.
- **Cleaning up debug code:** re-read the diff afterwards. A regex cleanup once deleted a real line, and the pigeons never took off.
- **Python edits from bash:** for multi-line edits, write the script to a file first; inline heredocs with special characters have failed. Never put a bare `cat > file` before a command: it waits for input forever.
