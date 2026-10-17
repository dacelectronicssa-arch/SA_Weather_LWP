# Weather Wallpaper: Cape Town

An Android live wallpaper of Table Bay seen from Bloubergstrand. Its sky follows the live weather and the real positions of the sun, moon and stars, and the bay has the occasional visitor.

## The scene
- **Landscape:** Devil's Peak, Table Mountain, Lion's Head and Signal Hill. The buttress cliffs are lit from whichever side the sun is on, and the "tablecloth" cloud pours over the table on cloudy days and when the south-easter blows.
- **Shoreline:** the city and Cape Town Stadium. At night the city lights come on and the stadium is floodlit.
- **Water:** the bay with waves, sun and moon glints, and reflected lights, plus a beach with waves washing in.

## The weather
Live conditions come from [Open-Meteo](https://open-meteo.com), which is free and needs no key, and refresh every 30 minutes while the wallpaper is visible. There are 8 conditions, each with a day and a night version: clear, partly cloudy, cloudy, fog, drizzle, rain, snow and thunderstorm. When the weather changes, the scenes crossfade.

## The sky
- **Sun and moon:** real positions for your location, including real rise and set times and heights. Their left-to-right spread is folded onto the screen, because from Cape Town they cross the northern sky behind this south-facing view.
- **Moon phase:** calculated from the real angle between the sun and the moon. The lit side always faces the sun, and the dark side shows faint earthshine in a dark sky.
- **Stars and Milky Way:** they turn around the south celestial pole by the real sidereal time. The Milky Way is washed out by a bright moon.
- **Twilight:** a coral sky glow, pink alpenglow on the mountain and a warm tint on the bay around sunrise and sunset.

## Visitors
There's always something happening. Visitors run in four independent lanes that freely overlap:

| Lane | Next one arrives after the last leaves | When |
|---|---|---|
| Boats: MSC Armonia, container ship, yacht, jetski (max 3 at once, each in its own distance band) | A new one every 15–45 s while fewer than 3 are out | Day and night; jetski by day only |
| Mavericks banner plane | 20–60 s | Daytime, not in thunderstorms |
| Whales and dolphins (tail 40%, dolphins 40%, breach 20%, never the same twice in a row) | 5–20 s | Daytime |
| Flock of 3–7 hadedas | 15–45 s | Daytime and twilight |

If the scene is ever completely empty, something launches straight away: a whale or dolphin event by day, or the ship at night. Visitors that came due while the screen was off arrive within seconds of you looking.

## Building
- **Requirements:** JDK 17 and the Android SDK (platform 35, build-tools 35). `local.properties` points at the SDK.
- **Build:** run `./gradlew assembleDebug`.
- **Install:** run `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- **Xiaomi/HyperOS phones:** enable **Install via USB** in Developer options. Avoid `am force-stop` while the app is the active wallpaper, because MIUI then reverts to the default wallpaper.

## Debug hooks (debug builds only)
Each of these launches the settings screen with an option:

```
adb shell am start -f 0x10008000 -n com.weatherwall.app/.SettingsActivity <extras>
```

| Extra | Effect |
|---|---|
| `--es override CLEAR:night` | Force a condition (`auto` returns to live weather) |
| `--ef time_offset_h 15.9` | Shift the sky clock (`0` = real time) |
| `--ez plane true` | Send the banner plane over now |
| `--ez ship true` | Send the MSC Armonia past now |
| `--es sea tail\|breach\|dolphins` | Start a whale or dolphin event now |
| `--ez birds true` | Send a flock of hadedas over now |
| `--es boat yacht\|jetski\|container` | Send that boat across now |

## Code map (`app/src/main/java/com/weatherwall/app/`)
- `WeatherWallpaperService.kt`: the wallpaper engine, which runs the frame loop, the visibility handling and the weather refresh
- `WeatherRepository.kt`: location and the Open-Meteo requests
- `SettingsActivity.kt`: the settings screen and the debug hooks
- `render/WeatherRenderer.kt`: crossfades between scenes and schedules the visitors
- `render/WeatherScene.kt`: the sky, stars, Milky Way, sun, moon, clouds and weather effects
- `render/CapeTownLandscape.kt`: the mountains, tablecloth, city, stadium, bay and beach
- `render/MountainDesign.kt`: the mountain shapes (back ridge and Devil's Peak) and rock settings, exported from the in-chat mountain designer. Replace the values here to apply a new design. `TableclothDesign` in the same file holds the tablecloth waterfall settings (from the in-chat tablecloth preview).
- `render/ShorelineCity.kt`: the city along the shore (Woodstock to Sea Point), generated from the settings in `ShorelineDesign`, which come from the in-chat shoreline designer. The same generator is used in the designer, so the layout matches.
- `render/SunRays.kt`: the god-rays sun, a warm disc and glow with slowly turning, shimmering beams that warm as the sun gets low. Settings are in `SunDesign`, from the in-chat sun designer.
- `render/FairClouds.kt`: clouds for clear, partly cloudy, cloudy, fog and snow: long layered decks and high wisps, painted once into bitmaps. Settings are in `CloudDesign`, from the in-chat cloud designer.
- `render/StormCeiling.kt`: the rain sky, a live storm-cloud shader running on the GPU (Android 13 and later; older phones keep the drawn clouds). Its settings are in `StormDesign`, from the in-chat storm preview.
- `render/SceneConfig.kt`: maps each weather condition to its look
- `render/Astro.kt`, `SkyClock.kt` and `MoonPhase.kt`: the astronomy
- `render/MilkyWay.kt`, `BannerPlane.kt`, `CruiseShip.kt`, `Boats.kt` (yacht, jetski, container ship), `MarineLife.kt` and `HadedaFlock.kt`: the Milky Way and the visitors
