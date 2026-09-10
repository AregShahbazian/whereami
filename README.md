# Where Am I

One screen, one job: show where you are in a form you can paste into a chat.

Reverse-geocoded **city / province / country** first — that is the line that
matters — then the coordinates, then the approximate street address. Each is
independently copyable, plus a **Copy all** button, an accuracy readout, a fix
timestamp, and **Refresh**.

No map, no history, no saved places, no accounts, no analytics, no ads.

## Build

```
./gradlew assembleDebug        # sideloadable debug APK
./gradlew assembleRelease      # signed release APK, for testing the shipped build
./gradlew bundleRelease        # .aab for the Play Console
```

`local.properties` needs `sdk.dir=<path to your Android SDK>` (Android Studio
writes it for you).

Debug builds use `applicationIdSuffix = ".debug"` so they install alongside
the release build, and they show a fixed made-up location (`DemoLocation.kt`
in the `debug` source set) so store screenshots never contain a real
position. The `release` source set returns null there and R8 strips the branch.

Release signing reads `key.properties` (gitignored, along with `*.jks` /
`*.keystore` — see the template `key.properties.example`).
Without it, release builds fall back to debug signing so a fresh clone still
builds.

## Decisions

**Location provider: `FusedLocationProviderClient`**, not the platform
`LocationManager`. Reliability is the entire product here, and
`getCurrentLocation(PRIORITY_HIGH_ACCURACY, …)` is one call that behaves the
same on every supported API level; the platform equivalent is API 30+ with a
fiddly pre-30 fallback. `play-services-location` does not pull in the
advertising ID, so the Data safety declaration is unaffected.

**Permissions: coarse + fine only.** `ACCESS_BACKGROUND_LOCATION` is
deliberately absent — the app only reads location while it is on screen, and
declaring it would trigger Play's Permissions Declaration Form and a demo-video
review for no benefit.

**No `INTERNET` permission.** `android.location.Geocoder` runs in the system
process, so the address lookup is not our network access. Coordinates always
render regardless, so a device with no geocoding backend degrades rather than
dead-ends.

**No "Open in Maps" button.** The premise of the app is that opening Maps to
answer "where am I" is the annoying part.

## Layout

| File | Role |
|---|---|
| `MainActivity.kt` | Activity, runtime permission flow, clipboard, Settings intents |
| `WhereAmIViewModel.kt` | The screen's state machine |
| `LocationSource.kt` | Fused provider + geocoder; all platform contact |
| `Model.kt` | `Fix`, `Place`, `UiState` |
| `WhereAmIScreen.kt` | Compose UI and theme |
| `DemoLocation.kt` | Per-build-type: fixed demo fix in `debug/`, `null` in `release/` |

Store listing assets and publishing notes are kept outside this repo.

## Status

In closed testing on Google Play.

## License

MIT — see [LICENSE](LICENSE).
