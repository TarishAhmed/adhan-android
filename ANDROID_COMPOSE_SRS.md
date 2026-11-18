# Adhan — Native Android (Kotlin + Jetpack Compose) Software Requirements Specification

| | |
|---|---|
| **Document** | `docs/ANDROID_COMPOSE_SRS.md` |
| **Spec version** | 2.0 (supersedes `docs/NATIVE_REBUILD_SRS.md`) |
| **Source app** | Flutter app in this repository, `version: 1.0.10+10` |
| **Target app** | Native Android, Kotlin, Jetpack Compose, Material 3 |
| **Application ID** | `org.tarish.adhan` (unchanged — ships as an in-place update) |
| **Target versionName / versionCode** | `2.0.0` / `11` (must be > 10) |
| **Written against** | the Flutter source as of commit `8d27ffd`, plus live calls to the alislam.org API on 2026‑11‑16 |

---

## 0. How to use this document (read first)

This document is written so that an engineer **or an AI coding agent** can build the complete native app from it in one pass, without reading the Flutter code. It is the single source of truth. Where it disagrees with the Flutter code, **this document wins** — every such disagreement is deliberate and is listed in the defect register (§17) with the reason.

### 0.1 Normative language

- **MUST / MUST NOT** — required for acceptance.
- **SHOULD** — required unless there is a concrete technical reason not to; document the reason in code.
- **MAY** — optional.
- `D-nn` — a resolved product decision (§1.4).
- `BUG-nn` — a defect in the Flutter app and its required fix (§17).
- `AC-nn` — an acceptance criterion (§19).

### 0.2 Build order for a one-shot implementation

Implement in this order; each step compiles and runs on its own:

1. Project skeleton, Gradle setup, theme, fonts, strings (§2, §13, §14).
2. Domain model + pure algorithms with unit tests (§4, §7, §18). **Do this before any UI.** The algorithms are where the bugs were.
3. Network + Room + DataStore + repositories (§5, §6).
4. Location + geocoding (§8.6, §9).
5. Navigation shell + all screens (§8).
6. Alarm scheduling, adhan playback service, notifications, receivers (§10).
7. WorkManager jobs (§11).
8. Home-screen widget (§12).
9. Remote Config update gate (§5.4, §8.9).
10. Flutter→native data migration (§6.4).
11. Run the acceptance checklist (§19).

### 0.3 Things an implementer must not do

- MUST NOT replace the alislam.org timetable API with AlAdhan, the `adhan` calculation library, or any local calculation. Times come from alislam.org only (Ahmadiyya timings).
- MUST NOT index the prayer array by position. Always match by name (BUG-01).
- MUST NOT format coordinates with the default locale (`"%.2f".format(x)` gives `51,51` in German/Indonesian locales) (§4.3).
- MUST NOT schedule prayer alarms with plain `setExact()` — it does not fire in Doze (BUG-05).
- MUST NOT add accounts, analytics, ads, Quran, hadith, tasbih, mosque finder or cloud sync.

---

## 1. Product overview

### 1.1 What the app is

Adhan is a privacy-respecting prayer-times companion for the Ahmadiyya Muslim community. It:

1. Downloads **monthly prayer timetables** for the user's coordinates from `alislam.org` and caches them for offline use.
2. Shows **the next prayer, a live countdown**, and a **segmented urgency bar** for the current prayer window on the Home screen.
3. Shows the **Qibla** direction with a live compass and a small map.
4. Plays the **adhan** at each enabled prayer time, with per-prayer sound, optional advance reminder, and weekday selection.
5. Provides a **home-screen widget** showing the next prayer.
6. Keeps working after reboot, app update, time/timezone change, Doze and process death.
7. Supports **remote force-update / soft-update** prompts via Firebase Remote Config.

### 1.2 Users

| Persona | Need |
|---|---|
| Daily worshipper | Reliable adhan at the right time, every day, without opening the app |
| Traveller | Times and Qibla follow GPS, or a manually chosen city |
| Older / low-tech user | Big readable next-prayer card; works with defaults; no account |

### 1.3 Non-goals (do not build)

Accounts, login, cloud sync, analytics SDKs, ads, Quran/hadith/tasbih, mosque finder, calculation-method picker, multiple languages (English only, but all strings in `strings.xml` so translation is possible later), iOS.

### 1.4 Resolved product decisions

These resolve contradictions or gaps in the Flutter app. Implement them as stated.

| ID | Decision | Why |
|---|---|---|
| D-01 | Notifications for the five daily prayers default to **ON** once the user grants notification permission. Sunrise is never notifiable. | Flutter app had two contradictory defaults (true in one getter, false in the getters actually used), so every prayer was silently OFF after onboarding even when the user granted permission (BUG-09). |
| D-02 | The **Fajr window ends at Sunrise**, not at Zuhr. Between Sunrise and Zuhr there is no active prayer window ("neutral"). | Fajr can't be prayed after sunrise. The Flutter code's own comment says "till 10 min before sunrise" but the code uses Zuhr (BUG-12). |
| D-03 | The adhan audio is played by the app's own **foreground playback service**, not by the notification channel sound. Prayer notification channels are silent (vibration only). | Android notification-channel sounds are immutable after creation; the Flutter app worked around this by overwriting a file behind a fixed `content://` URI, which breaks for the "Default" option, for missing files, and on devices where SystemUI can't read the app's FileProvider (BUG-15/16/17). A service also gives a **Stop** button. |
| D-04 | "Advance time" creates a **separate reminder** N minutes before the prayer (short notification, default notification sound), and the adhan still plays **at** the prayer time. | The Flutter app played the full adhan N minutes early with the text "It's time for X prayer", which is wrong (BUG-18). |
| D-05 | Weekday chips are **honoured** by the scheduler. At least one day must stay selected. | UI existed, scheduler ignored it (BUG-10). |
| D-06 | Prayer times are **displayed in the location's timezone** (from the API's `locationInfo.timezone`). If that differs from the device timezone, Home shows a small "Times shown in {zone}" hint. Alarms always fire at the absolute instant. | Flutter used the device timezone for display and stored the device timezone even for a searched city abroad (BUG-24). |
| D-07 | Theme mode (System/Light/Dark) is **persisted**. | It reset on every launch (BUG-27). |
| D-08 | Before today's Fajr, the current window is **yesterday's Isha** (real data), not a synthetic "Isha starting now". | BUG-13. |
| D-09 | The battery-optimisation prompt appears once, **only when the app is still optimised**. | Condition was inverted (BUG-30) and the dialog could not show at all (BUG-31). |
| D-10 | `ACCESS_BACKGROUND_LOCATION` is **removed**. Location is only read while the app is in the foreground. | Never used in background; it triggers Play policy review (BUG-35). |
| D-11 | The compass shows **true-north** heading (magnetic heading + declination). | Qibla bearing is relative to true north; the Flutter compass used magnetic north (BUG-40). |
| D-12 | The default adhan ("Makkah") is **bundled in the APK** (`res/raw/adhan_makkah.mp3`, ~405 KB) so a fresh install plays a real adhan with no network. Other sounds download on demand. | Enabling a prayer without opening the sound picker produced a silent / missing-file notification (BUG-16). |
| D-13 | Manual city search shows **place names** (locality, region, country), not just coordinates. | Results were bare lat/lng pairs (BUG-26). |
| D-14 | The widget shows the country line and remaining time. | Country was always empty and remaining time was computed but never shown (BUG-33). |
| D-15 | Sunrise is shown on Home as a muted line under the timeline ("Sunrise 6:54 AM"), not as a chip, and never in the countdown. | Users need to know when Fajr ends (D-02) without making Sunrise look like a prayer. |

---

## 2. Technology stack and project setup

### 2.1 Platform targets

| Item | Value |
|---|---|
| `minSdk` | **26** (Android 8.0) — needed for `java.time`, `HijrahDate`, notification channels |
| `targetSdk` / `compileSdk` | **36** |
| Language | Kotlin (latest stable 2.x), JVM target 17 |
| UI | Jetpack Compose + Material 3, single Activity |
| Orientation | Portrait and landscape both supported (no lock); layouts scroll |

### 2.2 Libraries

Use the **latest stable** release of each at build time. Minimum versions known to have the needed APIs are listed.

| Concern | Library | Min version |
|---|---|---|
| Compose | `androidx.compose:compose-bom` | 2025.01.00 |
| M3 | `androidx.compose.material3:material3` | from BOM (≥1.3) |
| Icons | `androidx.compose.material:material-icons-extended` | from BOM |
| Activity | `androidx.activity:activity-compose` | 1.9 |
| Navigation | `androidx.navigation:navigation-compose` (type-safe routes) | 2.8 |
| Lifecycle | `androidx.lifecycle:lifecycle-runtime-compose`, `lifecycle-viewmodel-compose`, `lifecycle-process` | 2.8 |
| DI | Hilt (`com.google.dagger:hilt-android`, `androidx.hilt:hilt-navigation-compose`, `androidx.hilt:hilt-work`) | 2.52 / 1.2 |
| Annotation processing | KSP | matching Kotlin |
| JSON | `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.7 |
| HTTP | Retrofit 2 + OkHttp 4 + `retrofit2-kotlinx-serialization-converter` (or Retrofit's built-in kotlinx converter) | 2.11 / 4.12 |
| DB | Room (`room-runtime`, `room-ktx`, `room-compiler` via KSP) | 2.6 |
| Prefs | `androidx.datastore:datastore-preferences` | 1.1 |
| Background | `androidx.work:work-runtime-ktx` | 2.9 |
| Widget | `androidx.glance:glance-appwidget`, `glance-material3` | 1.1 |
| Location | `com.google.android.gms:play-services-location` | 21.3 |
| Audio | `androidx.media3:media3-exoplayer` (preview + adhan service) | 1.4 |
| Map | `org.osmdroid:osmdroid-android` (OSM raster tiles, no API key) | 6.1.20 |
| Remote config | Firebase BoM + `firebase-config` (+ `google-services` Gradle plugin) | BoM 33 |
| Splash | `androidx.core:core-splashscreen` | 1.0.1 |
| Testing | JUnit4, `kotlinx-coroutines-test`, Turbine, Truth, Robolectric, `androidx.work:work-testing`, `room-testing`, Compose UI test | — |

Not used / not needed: Accompanist (permissions are handled with `rememberLauncherForActivityResult`), Gson, RxJava, any prayer calculation library.

### 2.3 Gradle essentials

- Use a version catalog (`gradle/libs.versions.toml`).
- Plugins: `com.android.application`, `org.jetbrains.kotlin.android`, `org.jetbrains.kotlin.plugin.compose`, `org.jetbrains.kotlin.plugin.serialization`, `com.google.devtools.ksp`, `com.google.dagger.hilt.android`, `com.google.gms.google-services`.
- `buildFeatures { compose = true; buildConfig = true }`.
- Release: `isMinifyEnabled = true`, `isShrinkResources = true`, R8 rules for kotlinx.serialization models (`@Serializable` classes keep), Retrofit, Room, osmdroid.
- **Signing**: see BUG-44. The release `signingConfig` MUST use the same key that signed the installed 1.0.x builds, otherwise the update will not install over them. Read keystore path/passwords from `keystore.properties` (git-ignored); never commit secrets.
- Copy `android/app/google-services.json` from this repo (Firebase project `adhan-ahmadiyya`, package `org.tarish.adhan`) into the new `app/`.
- `versionCode = 11`, `versionName = "2.0.0"`.

### 2.4 Package layout

```
org.tarish.adhan
├── AdhanApp.kt                      @HiltAndroidApp; WorkManager Configuration.Provider (HiltWorkerFactory)
├── MainActivity.kt                  single activity, edge-to-edge, splash, setContent { AdhanRoot() }
├── core/
│   ├── time/Clock.kt                interface Clock { fun now(): Instant; fun zone(): ZoneId } + SystemClock impl
│   ├── time/Ticker.kt               fun tickerFlow(periodMs): Flow<Instant>
│   ├── format/CoordinateFormat.kt   §4.3
│   ├── format/TimeFormat.kt         time / countdown / widget-remaining formatting
│   ├── result/AppError.kt           sealed error model (§3.4)
│   └── di/                          Hilt modules (Network, Database, DataStore, Dispatchers, Clock)
├── domain/
│   ├── model/                       Prayer, PrayerDay, StoredLocation, PrayerPrefs, Window, Urgency …
│   ├── PrayerWindowCalculator.kt    §7.4 (pure)
│   ├── NextPrayerFinder.kt          §7.3 (pure)
│   ├── QiblaMath.kt                 §7.7 (pure)
│   ├── Greeting.kt                  §7.8 (pure)
│   ├── HijriFormatter.kt            §7.9 (pure)
│   └── AlarmPlanner.kt              §10.3 (pure: prefs + days → list of AlarmSpec)
├── data/
│   ├── remote/AlislamApi.kt         Retrofit interface + DTOs (§5.1)
│   ├── remote/SoundDownloader.kt    §5.2
│   ├── local/AdhanDatabase.kt       Room (§6.2)
│   ├── local/SettingsStore.kt       DataStore (§6.1)
│   ├── PrayerTimesRepository.kt     cache-first month/day access (§6.3)
│   ├── LocationRepository.kt        GPS + geocoder + stored location (§9)
│   ├── SoundRepository.kt           catalog + local files (§5.2)
│   ├── UpdateRepository.kt          Remote Config (§5.4)
│   └── migration/FlutterMigration.kt §6.4
├── alarm/
│   ├── AlarmScheduler.kt            AlarmManager wrapper (§10.4)
│   ├── AdhanAlarmReceiver.kt        fires at prayer/reminder/test time (§10.5)
│   ├── SystemEventsReceiver.kt      boot, package replaced, time/timezone changed, exact-alarm permission (§10.7)
│   ├── AdhanPlaybackService.kt      foreground service, mediaPlayback (§10.6)
│   └── NotificationChannels.kt      §10.2
├── work/
│   ├── SyncWorker.kt                daily timetable sync + reschedule (§11)
│   ├── MaintenanceWorker.kt         weekly cleanup (§11)
│   ├── WidgetRefreshWorker.kt       §11, §12
│   └── WorkScheduler.kt             enqueue helpers
├── widget/
│   ├── NextPrayerWidget.kt          Glance widget (§12)
│   └── NextPrayerWidgetReceiver.kt
└── ui/
    ├── theme/ (Color.kt, Type.kt, Shape.kt, Theme.kt, Tokens.kt)
    ├── navigation/ (Routes.kt, AdhanNavHost.kt, BottomBar.kt)
    ├── components/ (StateView, PrayerProgressBar, SectionHeader, …)
    ├── onboarding/  home/  qibla/  more/  notifications/  location/  appearance/  advanced/  update/
    └── AdhanRoot.kt
```

---

## 3. Architecture

### 3.1 Layers

```
Compose screens ──observe── ViewModels (StateFlow<UiState>) ──call── Repositories ──► Room / DataStore / Retrofit / Fused / Geocoder / Sensors
                                                                    ▲
Receivers / Services / Workers ─────────────────────────────────────┘ (via Hilt @AndroidEntryPoint / @HiltWorker)
```

- **Unidirectional data flow.** Each screen has one `@HiltViewModel` exposing a single `StateFlow<XxxUiState>` and plain functions for events.
- **Single source of truth** for prayer times: Room. The UI never holds its own copy beyond the ViewModel's derived state. (The Flutter app had two caches — SQLite plus a Riverpod JSON-persist DB `riverpod.db` keyed without location — BUG-22.)
- **Pure domain logic** (window, next prayer, qibla, alarm planning, formatting) is in plain Kotlin with no Android imports, takes `Instant`/`ZoneId` inputs, and is unit-tested.
- **Clock injection.** Everything that needs "now" takes a `Clock` so tests can fix time.

### 3.2 Coroutines and threading

- Repositories expose `suspend` functions and `Flow`s; they switch to `Dispatchers.IO` internally (inject an `@IoDispatcher`).
- ViewModels use `viewModelScope`; state via `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`.
- `BroadcastReceiver`s that do I/O use `goAsync()` + a coroutine with a 9-second timeout, or hand work to a `OneTimeWorkRequest` (expedited) if it might take longer.
- An application-scoped `CoroutineScope(SupervisorJob() + Dispatchers.Default)` is provided by Hilt for fire-and-forget work (e.g. widget refresh after a change).

### 3.3 Reactive "reschedule" rule

Any change that affects alarms MUST call `AlarmCoordinator.rescheduleAll(reason)`, which runs on the app scope, is **debounced 500 ms**, and is idempotent. Triggers:

- notification prefs changed (enable, sound, advance, days)
- stored location changed
- a new month of data stored
- app start, boot, package replaced, time set, timezone changed, exact-alarm permission granted
- each adhan alarm firing (to roll the horizon forward, §10.3)

The Flutter app used a `needs_reschedule` flag + a 5-second WorkManager one-off (`rescheduleAll`) for this; that indirection is no longer needed because alarm planning is a fast local operation. Keep a WorkManager fallback only in `SyncWorker` (daily).

### 3.4 Error model

```kotlin
sealed interface AppError {
    data object NoNetwork : AppError                 // IOException, UnknownHost, timeout
    data class Http(val code: Int) : AppError        // non-2xx from alislam.org
    data object BadData : AppError                   // JSON parse / validation failure (§5.1.4)
    data object LocationServicesOff : AppError
    data object LocationPermissionDenied : AppError
    data object LocationPermissionPermanentlyDenied : AppError
    data object LocationTimeout : AppError
    data object GeocoderUnavailable : AppError
    data object NoLocation : AppError                // nothing stored and can't get GPS
    data class Unknown(val cause: Throwable) : AppError
}
```

UI maps each to a human message (strings in §14) — never show raw `toString()` of exceptions to users (the Flutter app showed `Exception: Permissions denied` text, BUG-28).

---

## 4. Domain model

### 4.1 Prayers

```kotlin
enum class PrayerName(val key: String, val displayName: String, val isSalah: Boolean) {
    FAJR("fajr", "Fajr", true),
    SUNRISE("sunrise", "Sunrise", false),
    ZUHR("zuhr", "Zuhr", true),
    ASR("asr", "Asr", true),
    SUNSET("sunset", "Sunset", false),
    MAGHRIB("maghrib", "Maghrib", true),
    ISHA("isha", "Isha", true);

    companion object {
        /** API sends "Fajr", "Sunrise", ... Compare case-insensitively. Unknown → null (ignored). */
        fun fromApi(raw: String): PrayerName? = entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
        val SALAH = listOf(FAJR, ZUHR, ASR, MAGHRIB, ISHA)
    }
}
```

Spelling is fixed: **Zuhr** (not Dhuhr), **Maghrib**, **Isha**, **Fajr**, **Asr**.

### 4.2 Timetable

```kotlin
/** One calendar day of times for one location. All instants are absolute. */
data class PrayerDay(
    val locationKey: String,     // "51.51,-0.13" (§4.3)
    val date: LocalDate,         // calendar date in the LOCATION's timezone
    val zone: ZoneId,            // location timezone from API
    val times: Map<PrayerName, Instant>,   // must contain all 5 salah + SUNRISE; SUNSET optional
) {
    operator fun get(p: PrayerName): Instant? = times[p]
}
```

### 4.3 Coordinates and the cache key

- The **location key** is `"{lat2},{lng2}"` where `lat2`/`lng2` are the coordinates rounded to **2 decimal places, HALF_UP, using `Locale.ROOT`/`Locale.US`**, e.g. `51.5074,-0.1278 → "51.51,-0.13"`.
- Normalise negative zero: `-0.00` → `0.00`.
- The API query uses the same 2-decimal strings (`lat=51.51&lng=-0.13`). This is ~1.1 km precision and matches what the Flutter app sent, so cached results remain comparable.
- Store the **full-precision** coordinates separately for Qibla bearing/distance and map display.

```kotlin
fun formatCoord(v: Double): String {
    val s = BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).toPlainString()
    return if (s == "-0.00") "0.00" else s
}
fun locationKey(lat: Double, lng: Double) = "${formatCoord(lat)},${formatCoord(lng)}"
```

(BUG-02: the Flutter widget service used `lat.toString()` — e.g. `12.9` instead of `12.90` — as the DB key, causing permanent cache misses and an API call on every widget refresh, once a minute.)

### 4.4 Stored location

```kotlin
data class StoredLocation(
    val lat: Double, val lng: Double,          // full precision
    val city: String?,                         // locality or best available name
    val region: String?,                       // admin area (state/province)
    val country: String?,                      // country name
    val zone: ZoneId?,                         // from API locationInfo.timezone once known; null until first fetch
    val source: Source,                        // GPS or MANUAL
    val updatedAt: Instant,                    // when this location was set (shown as "Last updated")
) { enum class Source { GPS, MANUAL }
    val key get() = locationKey(lat, lng)
    val displayName get() = city ?: region ?: "%.3f, %.3f".format(Locale.US, lat, lng)
}
```

### 4.5 Notification preferences

```kotlin
data class PrayerNotificationPrefs(
    val prayer: PrayerName,                  // one of SALAH
    val enabled: Boolean = true,             // D-01
    val soundId: String = AdhanSound.DEFAULT_ID,   // "makkah"
    val advanceMinutes: Int = 0,             // 0,5,10,15,20,25,30
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),  // non-empty
)
```

### 4.6 Adhan sound catalog

```kotlin
data class AdhanSound(val id: String, val displayName: String, val remoteFile: String?, val bundledRes: Int?, val kind: Kind) {
    enum class Kind { ADHAN, SYSTEM_DEFAULT, SILENT }
}
```

Catalog (order = display order). `remoteFile` is relative to `https://raw.githubusercontent.com/TarishAhmed/Athan-MP3/master/Sounds/` and MUST be URL-path-encoded when requested (spaces → `%20`). Local filename = `{id}.mp3`.

| id | Display name | Remote file | Notes |
|---|---|---|---|
| `system_default` | Default notification sound | — | Kind SYSTEM_DEFAULT: play `RingtoneManager.getDefaultUri(TYPE_NOTIFICATION)` once |
| `silent` | Silent (vibrate only) | — | Kind SILENT (new; low cost, frequently requested) |
| `makkah` | Makkah | `Athan Makkah.mp3` | **Default.** Also bundled as `R.raw.adhan_makkah` (D-12) |
| `mishary_alafasi` | Mishary Alafasi | `Athan Mishary Alafasi.mp3` | |
| `mansoor_az_zahrani` | Mansoor Az-Zahrani | `Athan Mansoor Az-Zahrani.mp3` | |
| `hamdan_almalki` | Hamdan Almalki | `Athan Hamdan Almalki.mp3` | |
| `hamad_deghreri` | Hamad Deghreri | `Athan Hamad Deghreri.mp3` | |
| `ibrahim_al_arkani` | Ibrahim Al-Arkani | `Athan Ibrahim Al-Arkani.mp3` | |
| `majed_al_hamathani` | Majed Al-Hamathani | `Athan Majed Al-hamathani.mp3` | |
| `malek_chebae_fajr` | Malek Chebae (Fajr) | `Athan Al-fajer - Malek chebae.mp3` | |
| `mohammad_almenshawy` | Mohammad Almenshawy | `Athan Mohammad Almenshawy.mp3` | |
| `mohammad_refat` | Mohammad Ref'at | `Athan Mohammad Ref3at.mp3` | |
| `nasser_alqatami` | Nasser Alqatami | `Athan Nasser Alqatami.mp3` | |
| `suhaib_khatba` | Suhaib Khatba | `Athan Suhaib Khatba.mp3` | |
| `abed_albase` | Abed Albase | `Athan Abed Albase6.mp3` | |
| `ahmad_nuyne` | Ahmad Nuyne | `Athan Ahmad Nuyne3.mp3` | |
| `feroz_alam` | Feroz Alam | `Azan_FerozAlam.mp3` | Same voice alislam.org returns in its `audio` field |
| `mishary_rashid_alafasy` | Mishary Rashid Alafasy | `mishary_rashid_alafasy.mp3` | |
| `mishary_rashid_alafasy_2` | Mishary Rashid Alafasy 2 | `mishary_rashid_alafasy_2.mp3` | |
| `mishary_rashid_alafasy_3` | Mishary Rashid Alafasy 3 | `mishary_rashid_alafasy_3.mp3` | |
| `mansour_al_zahrani` | Mansour Al-Zahrani | `mansour-al-zahrani.mp3` | |
| `hafiz_mustafa_ozcan` | Hafiz Mustafa Ozcan | `hafiz_mustafa_ozcan.mp3` | |
| `karl_jenkins` | Karl Jenkins | `karl_jenkins.mp3` | |
| `ahmad_al_nafees` | Ahmad Al Nafees | `ahmad_al_nafees.mp3` | |

Migration mapping from Flutter: the Flutter app stored the **full URL** as the sound preference (e.g. `https://raw.githubusercontent.com/.../Athan%20Makkah.mp3`) or the sentinel `defaultNotificationSound`. Map by URL-decoding the last path segment and matching `remoteFile`; map the sentinel to `system_default`; unknown → `makkah`.

### 4.7 Prayer window (Home hero)

```kotlin
enum class Urgency { GREEN, YELLOW, RED, NEUTRAL }

data class Segment(val start: Double, val end: Double, val urgency: Urgency)   // fractions 0..1 of the window

data class PrayerWindow(
    val start: PrayerName, val startAt: Instant,   // window opener (FAJR, SUNRISE, ZUHR, ASR, MAGHRIB, ISHA)
    val end: PrayerName,   val endAt: Instant,     // window closer
    val progress: Float,                           // 0..1, elapsed/total, clamped
    val urgency: Urgency,                          // urgency right now
    val segments: List<Segment>,                   // static colouring of the whole window
)
```

### 4.8 Home state

```kotlin
data class HomeUiState(
    val greeting: Greeting,                // §7.8
    val locationLabel: String?,
    val zoneHint: String?,                 // non-null when location zone != device zone (D-06)
    val next: NextPrayer?,                 // name + time (+ isTomorrow)
    val window: PrayerWindow?,
    val today: List<TimelineItem>,         // 5 salah chips
    val sunrise: Instant?,                 // D-15
    val gregorian: String, val hijri: String?,
    val status: Status,                    // Loading | Ready | Error(AppError) | NoLocation
    val refreshing: Boolean,
    val stale: Boolean,                    // showing cached data after a failed refresh
)
```

The countdown string is **not** part of this state; it is computed in a composable from `next.time` with a 1-second ticker so the rest of the screen doesn't recompose every second.

---

## 5. External interfaces

### 5.1 alislam.org monthly timetable API (source of truth for times)

#### 5.1.1 Request

```
GET https://www.alislam.org/adhan/api/timings/month?lat={lat2}&lng={lng2}&month={1..12}&year={yyyy}
```

- `lat2`/`lng2`: 2-decimal strings from §4.3.
- No auth, no headers required. Send `User-Agent: Adhan-Android/{versionName}`.
- OkHttp timeouts: connect 30 s, read 30 s, call 45 s. One automatic retry on `IOException` with 2 s backoff; no retry on 4xx/5xx.
- An invalid query (e.g. non-numeric lat) returns **HTTP 500** (verified). Treat any non-2xx as `AppError.Http`.
- Logging: `HttpLoggingInterceptor` at `BASIC` in debug only, `NONE` in release (the Flutter app logged full response bodies in release — BUG-43).

#### 5.1.2 Response (verified 2026‑11‑16, `lat=51.51&lng=-0.13&month=11&year=2026`)

```json
{
  "locationInfo": { "lat": "51.51", "lng": "-0.13", "timezone": "Europe/London" },
  "multiDayTimings": [
    {
      "prayers": [
        { "name": "Fajr",    "time": 1793510640000, "audio": "https://alislam.org/adhan/objects/Azan_FerozAlam.mp3", "offset": 0 },
        { "name": "Sunrise", "time": 1793516040000, "audio": null, "offset": 0 },
        { "name": "Zuhr",    "time": 1793533740000, "audio": "https://alislam.org/adhan/objects/Azan_FerozAlam.mp3", "offset": 0 },
        { "name": "Asr",     "time": 1793541900000, "audio": "https://alislam.org/adhan/objects/Azan_FerozAlam.mp3", "offset": 0 },
        { "name": "Sunset",  "time": 1793550840000, "audio": null, "offset": 0 },
        { "name": "Maghrib", "time": 1793550900000, "audio": "https://alislam.org/adhan/objects/Azan_FerozAlam.mp3", "offset": 0 },
        { "name": "Isha",    "time": 1793556000000, "audio": "https://alislam.org/adhan/objects/Azan_FerozAlam.mp3", "offset": 0 }
      ],
      "coordinates": { "latitude": 51.51, "longitude": -0.13 },
      "date": 1793542622859,
      "offset": 0
    }
    // … one element per day of the month (30 for November), in day order
  ]
}
```

In Europe/London local time that first day is: Fajr 05:24, Sunrise 06:54, Zuhr 11:49, Asr 14:05, Sunset 16:34, Maghrib 16:35, Isha 18:00.

Facts established by testing (London, Sydney, Riyadh, New York, Tromsø):

- `multiDayTimings.length` == number of days in the requested month; element `i` is day `i+1`.
- Each `time` is **epoch milliseconds (UTC instant)**.
- `locationInfo.timezone` is an IANA zone for the coordinates (`Europe/London`, `Australia/Sydney`, `Asia/Riyadh`, `America/New_York`, `Europe/Oslo`). **Use it.**
- `date` is **unreliable as a calendar key**: it is a UTC timestamp whose time-of-day equals the server's current time, so its calendar date in a given zone depends on when the request was made (for Sydney it was 01:18 local on the right day; at a different time of day it lands on the previous day). The Flutter app keyed its DB by `date.day` in device-local time (BUG-03). **Do not use `date` for anything.**
- `audio` and `offset` are ignored.
- High latitudes (Tromsø, June) still return all 7 entries with plausible ordering; no nulls observed. Still code defensively (§5.1.4).

#### 5.1.3 DTOs

```kotlin
@Serializable data class MonthResponseDto(val locationInfo: LocationInfoDto? = null, val multiDayTimings: List<DayDto> = emptyList())
@Serializable data class LocationInfoDto(val lat: String? = null, val lng: String? = null, val timezone: String? = null)
@Serializable data class DayDto(val prayers: List<PrayerDto> = emptyList(), val coordinates: CoordinatesDto? = null, val date: Long? = null)
@Serializable data class PrayerDto(val name: String, val time: Long? = null, val audio: String? = null)
@Serializable data class CoordinatesDto(val latitude: Double? = null, val longitude: Double? = null)
```

`Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }`.

#### 5.1.4 Mapping and validation (DTO → `List<PrayerDay>`)

1. `zone = ZoneId.of(locationInfo.timezone)`; if missing/invalid, fall back to the **previously stored zone for this location key**, else the device zone, and log a warning.
2. For each index `i`, `date = LocalDate.of(year, month, i + 1)`. If `i + 1 > month.lengthOfMonth()`, drop the element.
3. Build `times` from `prayers` via `PrayerName.fromApi(name)`; skip entries with unknown name or null time.
4. **Cross-check**: the Fajr instant converted to `zone` must fall on `date`. If not, try to find the correct day by Fajr's local date; if that fails, drop the day.
5. A day is **valid** only if it has all of FAJR, SUNRISE, ZUHR, ASR, MAGHRIB, ISHA and they are strictly increasing in that order. Invalid days are dropped and logged.
6. If fewer than `lengthOfMonth - 1` valid days remain, the whole response is `AppError.BadData` and nothing is written.

### 5.2 Adhan MP3 downloads

- URL: `BASE + URLEncoded(remoteFile)` with `BASE = https://raw.githubusercontent.com/TarishAhmed/Athan-MP3/master/Sounds/` (verified 200, `Athan Makkah.mp3` = 405,632 bytes).
- Stored at `filesDir/adhan/{id}.mp3` (the same `filesDir/adhan/` directory the Flutter app used, so previously downloaded files may be reused — see §6.4).
- Download to `filesDir/adhan/{id}.mp3.part`, then `renameTo` atomically. Never leave a partial file under the final name (BUG-19: Flutter wrote bytes straight to the final path and later treated any file with a matching name prefix as valid).
- Validate: HTTP 200, `Content-Type` audio/* or application/octet-stream, size > 10 KB.
- `makkah` resolves to `R.raw.adhan_makkah` when no downloaded file exists. Fetch the file at build time: `curl -o app/src/main/res/raw/adhan_makkah.mp3 "https://raw.githubusercontent.com/TarishAhmed/Athan-MP3/master/Sounds/Athan%20Makkah.mp3"`.
- `SoundRepository.resolve(id): SoundSource` returns `File(path)` | `RawRes(id)` | `SystemDefault` | `Silent` | `Missing`. At alarm time, `Missing` falls back to the bundled Makkah adhan (never silence).
- When a prayer's sound is set to a non-downloaded sound, the download starts immediately (foreground, with progress on the chip). If it fails, keep the selection, show a snackbar "Couldn't download {name}. It will be retried on Wi‑Fi." and enqueue a `SoundDownloadWorker` (network constraint, exponential backoff).
- On "Clear cached data" (Advanced) sounds are **not** deleted; a separate "Delete downloaded sounds" action shows total size and deletes `filesDir/adhan/*` except sounds currently selected by any prayer.

### 5.3 Location services

- **Fused Location Provider** `getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationToken)` with a 10 s timeout; on timeout use `lastLocation` if younger than 30 min; else `AppError.LocationTimeout`.
- If location services are off, use `SettingsClient.checkLocationSettings` and launch the resolution (`IntentSenderRequest`) from the UI so the user can turn them on in-place.
- **Geocoder** (`android.location.Geocoder`): on API 33+ use the listener overloads; below, call the blocking ones on `Dispatchers.IO`. If `Geocoder.isPresent()` is false → `AppError.GeocoderUnavailable` (search disabled with an explanation; GPS still works, city falls back to coordinates).
  - Reverse: `getFromLocation(lat, lng, 1)` → `locality ?: subAdminArea ?: adminArea`, `adminArea`, `countryName`.
  - Forward: `getFromLocationName(query, 8)`.

### 5.4 Firebase Remote Config (update gate)

| Key | Type | Default |
|---|---|---|
| `force_update_version` | string semver | `"1.0.0"` |
| `force_update_url` | string URL | `""` |
| `general_update_version` | string semver | `"1.0.0"` |
| `general_update_url` | string URL | `""` |

- Settings: `fetchTimeoutInSeconds = 60`, `minimumFetchIntervalInSeconds = 3600` (debug builds: 0).
- `setDefaultsAsync` then `fetchAndActivate()` once per cold start, after first frame; failures are silent.
- Version compare (keep identical semantics): split on `.`, take first 3 parts, missing parts = 0, non-numeric part = 0; compare major, minor, patch; "higher" means **strictly greater**. Ignore any `-suffix`/`+build` by stripping from the first `-` or `+`.
- Current version = `BuildConfig.VERSION_NAME` (`2.0.0`). **Note for the operator**: after shipping 2.0.0, any Remote Config value ≤ `2.0.0` no longer triggers.
- Outcome:
  - `force > current` → **Force** (blocking screen, §8.9).
  - else `general > current` → **General** (dismissible dialog, once per process).
  - else none.
- Opening the URL: `Intent(ACTION_VIEW, Uri.parse(url))` wrapped in try/catch for `ActivityNotFoundException`. Do **not** pre-check with `resolveActivity`/`queryIntentActivities` (package visibility on Android 11+ makes that return null without a `<queries>` entry — this is exactly why the Flutter `canLaunchUrl` check silently did nothing, BUG-32). If the URL is empty, fall back to `market://details?id=org.tarish.adhan`, then `https://play.google.com/store/apps/details?id=org.tarish.adhan`.

### 5.5 OpenStreetMap tiles (Qibla map)

- Tile source: osmdroid `TileSourceFactory.MAPNIK` (`https://tile.openstreetmap.org/{z}/{x}/{y}.png`).
- MUST set `Configuration.getInstance().userAgentValue = "org.tarish.adhan/{versionName}"` before creating a `MapView` (OSM tile usage policy).
- MUST show the attribution text "© OpenStreetMap contributors" on the map (BUG-41: missing).
- Tile cache: osmdroid default in `cacheDir/osmdroid`, limited to 50 MB.

---

## 6. Persistence

### 6.1 DataStore (`settings.preferences_pb`)

| Key | Type | Default | Meaning |
|---|---|---|---|
| `onboarding_done` | Boolean | false | Onboarding completed or skipped |
| `theme_mode` | String | `"system"` | `system` / `light` / `dark` (D-07) |
| `battery_prompt_shown` | Boolean | false | One-time battery prompt shown (D-09) |
| `exact_alarm_prompt_shown` | Boolean | false | One-time exact-alarm explainer shown |
| `auto_location` | Boolean | true | Follow GPS; false = manual city |
| `loc_lat`, `loc_lng` | Double | — | Full-precision coordinates |
| `loc_city`, `loc_region`, `loc_country` | String | — | Place names |
| `loc_zone` | String | — | IANA zone (from API) |
| `loc_source` | String | — | `GPS` / `MANUAL` |
| `loc_updated_at` | Long | — | epoch ms |
| `notif_{key}_enabled` | Boolean | true | per salah key (`fajr`,`zuhr`,`asr`,`maghrib`,`isha`) |
| `notif_{key}_sound` | String | `"makkah"` | sound id (§4.6) |
| `notif_{key}_advance` | Int | 0 | minutes: 0,5,10,15,20,25,30 |
| `notif_{key}_days` | String | `"1,2,3,4,5,6,7"` | ISO day-of-week numbers, comma-separated |
| `last_sync_at` | Long | 0 | last successful API fetch |
| `last_gps_refresh_at` | Long | 0 | throttle for auto GPS refresh (§9.2) |
| `scheduled_alarm_keys` | String set | ∅ | request codes currently scheduled (§10.4) |
| `flutter_migrated` | Boolean | false | §6.4 done |

Expose typed flows: `SettingsStore.themeMode: Flow<ThemeMode>`, `location: Flow<StoredLocation?>`, `prayerPrefs: Flow<Map<PrayerName, PrayerNotificationPrefs>>`, etc.

### 6.2 Room database `adhan.db` (version 1)

```kotlin
@Entity(tableName = "prayer_day", primaryKeys = ["location_key", "date"])
data class PrayerDayEntity(
    @ColumnInfo(name = "location_key") val locationKey: String,   // "51.51,-0.13"
    @ColumnInfo(name = "date") val date: String,                  // ISO "2026-11-01" (location-local)
    val zone: String,                                              // "Europe/London"
    val fajr: Long, val sunrise: Long, val zuhr: Long, val asr: Long,
    val sunset: Long?, val maghrib: Long, val isha: Long,          // epoch ms
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)

@Entity(tableName = "cached_month", primaryKeys = ["location_key", "year", "month"])
data class CachedMonthEntity(
    @ColumnInfo(name = "location_key") val locationKey: String,
    val year: Int, val month: Int,
    val zone: String,
    @ColumnInfo(name = "day_count") val dayCount: Int,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)
```

DAO essentials:

```kotlin
@Query("SELECT * FROM prayer_day WHERE location_key = :key AND date = :date") suspend fun day(key: String, date: String): PrayerDayEntity?
@Query("SELECT * FROM prayer_day WHERE location_key = :key AND date BETWEEN :from AND :to ORDER BY date") fun range(key: String, from: String, to: String): Flow<List<PrayerDayEntity>>
@Query("SELECT * FROM cached_month WHERE location_key = :key AND year = :y AND month = :m") suspend fun month(key: String, y: Int, m: Int): CachedMonthEntity?
@Upsert suspend fun upsertDays(days: List<PrayerDayEntity>)
@Upsert suspend fun upsertMonth(m: CachedMonthEntity)
@Query("DELETE FROM prayer_day WHERE date < :before") suspend fun deleteDaysBefore(before: String)
@Query("DELETE FROM cached_month WHERE (year * 100 + month) < :yyyymm") suspend fun deleteMonthsBefore(yyyymm: Int)
@Query("DELETE FROM prayer_day WHERE location_key != :keep") suspend fun deleteOtherLocations(keep: String)
// + deleteAll, counts for the Advanced screen
```

Write a month atomically in one `@Transaction` (days + month row).

### 6.3 Cache policy (`PrayerTimesRepository`)

```kotlin
interface PrayerTimesRepository {
    /** Today, tomorrow, yesterday for the current location; emits on DB change. */
    fun observeDays(key: String, from: LocalDate, to: LocalDate): Flow<List<PrayerDay>>
    suspend fun getDay(loc: StoredLocation, date: LocalDate, allowNetwork: Boolean = true): Result<PrayerDay>
    suspend fun ensureMonths(loc: StoredLocation, months: List<YearMonth>, force: Boolean = false): Result<Unit>
    suspend fun refreshCurrent(loc: StoredLocation): Result<Unit>   // force current (+ next if ≤ 7 days left)
}
```

Rules:

1. A month is **complete** for a key if `cached_month` has a row with `day_count >= lengthOfMonth - 1`. (Flutter treated a month as valid if *any* day was in the future — partial months passed — BUG-21.)
2. **Read path** (`getDay`): DB → if missing and `allowNetwork`, `ensureMonths([YearMonth.from(date)])` then DB again.
3. **Required coverage** at any time: the month containing yesterday, today, and tomorrow (location-local dates), plus the **next month when ≤ 7 days remain** in the current one. This guarantees tomorrow's Fajr across month/year boundaries (BUG-04/BUG-14).
4. **Manual refresh** (pull-to-refresh / refresh button) refetches the current month even if cached (`force = true`); on failure keep showing cached data and mark `stale`.
5. **Location change**: fetch coverage for the new key immediately; keep other keys' rows until the weekly cleanup (lets a user flip between two cities offline).
6. **Cleanup** (weekly): delete `prayer_day` rows with `date < today − 7 days`; delete rows for keys other than the current one whose newest `fetched_at` is older than 30 days; delete matching `cached_month` rows. (Flutter deleted by `created_at < now − 90 days`, which could delete prefetched future months that were fetched long ago — BUG-23.)
7. Concurrency: a per-`(key, YearMonth)` `Mutex` so the UI, `SyncWorker`, and the widget never fetch the same month twice concurrently.

### 6.4 Migration from the Flutter app (in-place update)

Run once in `AdhanApp.onCreate` on a background coroutine **before** the first alarm reschedule (gate with `flutter_migrated`). Each step is wrapped in try/catch; a failed step never blocks the app.

1. **Preferences.** Read `getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)`. All Flutter keys are prefixed with `flutter.`; ints are stored as `Long`.

   | Flutter key | → | Native |
   |---|---|---|
   | `flutter.onboarding_done_v1` | → | `onboarding_done` |
   | `flutter.battery_opt_prompted_v1` | → | `battery_prompt_shown` |
   | `flutter.settings_auto_location` | → | `auto_location` |
   | `flutter.stored_lat` / `stored_lng` (2-dec strings) | → | `loc_lat` / `loc_lng` (`toDouble()`) |
   | `flutter.stored_city` | → | `loc_city` |
   | `flutter.stored_timezone` | → | `loc_zone` (provisional; overwritten by the API on next fetch) |
   | (derived) | → | `loc_source = if auto_location then GPS else MANUAL` |
   | `flutter.notification_pref_{fajr,zuhr,asr,maghrib,isha}` | → | `notif_{key}_enabled` — **only if the key exists**; missing → default true (D-01) |
   | `flutter.notification_sound_{key}` | → | `notif_{key}_sound` via §4.6 mapping |
   | `flutter.notification_advance_{key}` (Long) | → | `notif_{key}_advance` (clamp 0..30, round to nearest 5) |
   | `flutter.notification_days_{key}` (`"Mon,Tue,…"`) | → | `notif_{key}_days` (`Mon→1 … Sun→7`; empty → all) |
   | `flutter.notification_*_sunrise`, `needs_reschedule`, `db_metrics`, `notify_*`, bare prayer-name keys | → | dropped |

   Do **not** delete the Flutter prefs file (allows debugging); just set `flutter_migrated = true`.

2. **Databases.** `deleteDatabase("prayer_timings.db")` and `deleteDatabase("riverpod.db")`. Data is refetched (one API call).
3. **Sounds.** For each `filesDir/adhan/*.mp3` whose name (URL-decoded) equals a catalog `remoteFile`, rename to `{id}.mp3`. Delete the per-prayer copies `fajr.mp3`, `sunrise.mp3`, `zuhr.mp3`, `asr.mp3`, `maghrib.mp3`, `isha.mp3`.
4. **Old WorkManager jobs.** `WorkManager.cancelAllWork()` then `pruneWork()` (the Flutter plugin's worker classes no longer exist). Then enqueue the new jobs (§11).
5. **Old notification channels.** Delete channels and groups whose IDs end in `_prayer_channel` / `_prayer_channel_group` (they carried the old file-URI sounds).
6. **Old alarms.** The Flutter `flutter_local_notifications` and `android_alarm_manager_plus` alarms target receiver classes that no longer exist; firing them is harmless. Nothing to do; the new scheduler takes over.
7. **Widget.** The old provider class `org.tarish.adhan.AdhanHomeWidget` is replaced. **Keep a receiver with that exact class name** (`AdhanHomeWidget : GlanceAppWidgetReceiver`) so widgets already placed on users' home screens keep working after the update (renaming the provider class removes existing widgets).

---

## 7. Core algorithms (pure Kotlin, unit-tested)

All functions take `now: Instant`, `zone: ZoneId` (location zone) and data; none reads the system clock.

### 7.1 "Today" in the location

```kotlin
fun localToday(now: Instant, zone: ZoneId): LocalDate = now.atZone(zone).toLocalDate()
```

The Home screen needs `yesterday`, `today`, `tomorrow` `PrayerDay`s (location-local). Observe them with `observeDays(key, today − 1, today + 1)`. The ViewModel MUST recompute `today` on every tick so the screen rolls over at local midnight **while open** (BUG-06: the Flutter streams captured "today" once and kept yesterday's list forever, so the hero went blank after midnight until restart).

### 7.2 Salah sequence

`events(day) = [FAJR, SUNRISE, ZUHR, ASR, MAGHRIB, ISHA]` mapped to instants (SUNSET is ignored everywhere except storage).

### 7.3 Next prayer

```
nextSalah(now, today, tomorrow):
  for p in SALAH: if today[p] > now → return (p, today[p], isTomorrow=false)
  if tomorrow != null → return (FAJR, tomorrow[FAJR], isTomorrow=true)
  return null
```

- Sunrise is never "next prayer" (consistent with the Flutter widget).
- Strictly greater: at exactly the prayer instant, that prayer is no longer "next".

### 7.4 Current window and urgency (D-02, D-08)

Boundaries, in order:

```
W0  ISHA(yesterday)  → FAJR(today)
W1  FAJR(today)      → SUNRISE(today)
W2  SUNRISE(today)   → ZUHR(today)        (neutral window)
W3  ZUHR(today)      → ASR(today)
W4  ASR(today)       → MAGHRIB(today)
W5  MAGHRIB(today)   → ISHA(today)
W6  ISHA(today)      → FAJR(tomorrow)
```

Find the window with `startAt <= now < endAt`. If W0 needs `yesterday` and it's missing, or W6 needs `tomorrow` and it's missing, return `null` window (UI shows the bar empty, everything else still works). `total = endAt − startAt` (seconds), `elapsed = now − startAt`, `remaining = endAt − now`, `progress = elapsed/total` clamped to [0,1].

**Green end (fraction of window) by window start:**

| Window | `greenEnd` |
|---|---|
| FAJR → SUNRISE | 1/3 |
| SUNRISE → ZUHR | — (neutral; single segment `[0,1] NEUTRAL`, urgency NEUTRAL) |
| ZUHR → ASR | 1/2 |
| ASR → MAGHRIB | 1/2 |
| MAGHRIB → ISHA | 1/5 |
| ISHA → FAJR(next) | `(islamicMidnight − startAt) / total`, clamped [0,1], where `islamicMidnight = maghrib + (fajrNext − maghrib) / 2` and `maghrib` is the Maghrib of the **same day as the Isha** that opened the window |

**Red tail:** `redStart = max(greenEnd, (total − 600) / total)` — last 10 minutes.

**Segments** (non-neutral windows):

```
segments = buildList {
  if (greenEnd > 0)        add(Segment(0.0, greenEnd, GREEN))
  if (redStart > greenEnd) add(Segment(greenEnd, redStart, YELLOW))
  add(Segment(redStart, 1.0, RED))
}
```

**Urgency now** (non-neutral windows), evaluated in this order:

1. `remainingSeconds <= 600` → RED
2. `progress < greenEnd` → GREEN
3. else → YELLOW

(Flutter compared `progress <= 0.33` against a segment drawn at `1/3`, and used `remaining.inMinutes <= 10` — floored minutes — against a 600-second segment, so the colour and the bar disagreed for up to a minute. Unify as above — BUG-11.)

### 7.5 Countdown text

```
remaining = max(0, next.time − now) in whole seconds (floor)
text = "%02d:%02d:%02d".format(h, m, s)   // h may exceed 24 only if data is wrong; cap display at 99:59:59
```

Rendered with tabular figures. The accessibility label is "{h} hours {m} minutes remaining" and is updated at most once per minute (§15).

### 7.6 Widget remaining text

```
mins = ceil(max(0, next.time − now) / 60s)
mins == 0          → "now"
mins < 60          → "in {m} min"
m == 0             → "in {h} h"
else               → "in {h} h {m} min"
```

(Flutter used `abs()` on minutes, so a stale widget showed a positive time after the prayer had passed — BUG-34. Ceil so "in 1 min" shows until the time arrives.)

### 7.7 Qibla

Kaaba: **21.4225° N, 39.8262° E**.

```kotlin
fun qiblaBearing(lat: Double, lng: Double): Double {   // degrees from TRUE north, 0..360
    val φ1 = Math.toRadians(lat); val φ2 = Math.toRadians(21.4225)
    val Δλ = Math.toRadians(39.8262 - lng)
    val y = sin(Δλ) * cos(φ2)
    val x = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(Δλ)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}
fun distanceKm(lat: Double, lng: Double): Double = haversine(lat, lng, 21.4225, 39.8262, R = 6371.0088)
fun relativeAngle(qibla: Double, headingTrue: Double): Double = ((qibla - headingTrue + 540.0) % 360.0) - 180.0   // -180..180
fun isValid(lat: Double, lng: Double) = lat in -90.0..90.0 && lng in -180.0..180.0
```

Use **full-precision** coordinates, not the 2-decimal key.

Reference values (must pass as unit tests, ±0.1° / ±1 km):

| City | lat, lng | Bearing | Distance |
|---|---|---|---|
| New York | 40.7128, −74.0060 | 58.48° | 10,306 km |
| London | 51.5074, −0.1278 | 118.99° | 4,794 km |
| Tokyo | 35.6762, 139.6503 | 293.00° | 9,472 km |
| Karachi | 24.8607, 67.0011 | 267.74° | 2,800 km |
| Jakarta | −6.2088, 106.8456 | 295.15° | 7,920 km |
| Qadian | 31.8190, 75.3760 | 260.66° | 3,699 km |

Direction names (for accessibility text): N [337.5,22.5), NE, E, SE, S, SW, W, NW in 45° sectors.

### 7.8 Greeting (device-local hour)

| Hour | Text |
|---|---|
| 00–04 | Peaceful night |
| 05–11 | Good morning |
| 12–16 | Good afternoon |
| 17–20 | Good evening |
| 21–23 | Peaceful night |

Recompute on each minute tick.

### 7.9 Dates footer

- Gregorian: `DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)` of the location-local today (e.g. "November 16, 2026").
- Hijri: `HijrahDate.from(localToday)` (Umm al-Qura, built into `java.time` on API 26+) formatted as `"{d} {MonthName} {yyyy} AH (approx.)"` with month names:
  `Muharram, Safar, Rabi' al-Awwal, Rabi' al-Thani, Jumada al-Ula, Jumada al-Akhirah, Rajab, Sha'ban, Ramadan, Shawwal, Dhu al-Qi'dah, Dhu al-Hijjah`.
  If `HijrahDate.from` throws (date outside the table's range), omit the line.
- Keep the "(approx.)" label; tabular Hijri may differ by a day from moon sighting.

### 7.10 Time formatting

- Prayer clock times: `DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)` respecting the system 12/24-hour setting — use `android.text.format.DateFormat.is24HourFormat(context)` to choose between patterns `"h:mm a"` and `"HH:mm"` (localized time style alone ignores the user's 24-hour toggle). Always format in the **location zone** (D-06).
- "Last updated" on the Location screen: `MMM d, h:mm a` (or 24-h variant) of `StoredLocation.updatedAt` in device zone (BUG-25: Flutter printed *now*).

---

## 8. Screens and navigation

### 8.1 Navigation graph

Type-safe Navigation Compose routes (`@Serializable` objects):

| Route | Kind | Screen |
|---|---|---|
| `Onboarding` | full screen, start destination when `onboarding_done == false` | §8.2 |
| `Main` (nested graph) → `Home`, `Qibla`, `More` | bottom-bar tabs | §8.3–8.5 |
| `Notifications` | pushed over tabs (no bottom bar) | §8.6 |
| `Location` | pushed | §8.7 |
| `Appearance` | pushed | §8.8 |
| `Advanced` | pushed | §8.10 |
| `ForceUpdate` | full screen overlay, not in back stack | §8.9 |

Rules:

- Start destination is decided **before** first composition from DataStore (keep the splash screen up via `installSplashScreen().setKeepOnScreenCondition { !ready }` until `onboarding_done` and `theme_mode` are read) — avoids a flash of Home before redirect.
- Finishing onboarding: `navigate(Main) { popUpTo(Onboarding) { inclusive = true } }`.
- Tabs: standard bottom-nav pattern — `popUpTo(Home) { saveState = true }`, `launchSingleTop = true`, `restoreState = true`. Re-selecting the current tab does nothing. `HapticFeedbackType.SegmentTick` (or `TextHandleMove` on older Compose) on tab change.
- Home's "Qibla" quick action **switches tab** (same as tapping the Qibla tab) rather than pushing a second copy (BUG-42: Flutter `push`ed a shell route).
- Deep link from notification tap: `adhan://home` → Home tab.
- Bottom bar: M3 `NavigationBar`, labels **Home**, **Qibla**, **More**; icons `Icons.Outlined.Home`/`Icons.Rounded.Home`, `Icons.Outlined.Explore`/`Icons.Rounded.Explore`, `Icons.Outlined.MoreHoriz`/`Icons.Rounded.MoreHoriz` (unselected/selected).

Transitions (match the Flutter app):

- Tabs: fade-through — incoming fades in over 300 ms starting at 30% with scale 0.94→1.0 using emphasized-decelerate `CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)`; outgoing fades out in the first 30% (~90 ms).
- Pushed screens: enter = fade-in + slide-up from 6% of height, 350 ms emphasized-decelerate; exit (pop) = reverse, 300 ms.

Global overlays rendered by `AdhanRoot` above the `NavHost`, using a host that **is** inside the Activity's composition (so dialogs work — BUG-31):

1. Update gate (§8.9).
2. Battery prompt (§8.10.3), once, ≥ 2 s after Home first shows, only after onboarding.
3. Exact-alarm prompt (§10.8), once, if `!canScheduleExactAlarms()` and any prayer is enabled.

Only one global dialog at a time; priority Force update > General update > Exact alarm > Battery.

### 8.2 Onboarding (first launch)

Layout: full-screen `HorizontalPager` (3 pages, user swipe enabled), **Skip** `TextButton` top-end, page indicator bottom-centre (selected = 24×8 dp pill `primary`, others 8×8 dp `outlineVariant`, 250 ms width animation). Each page: 144 dp circle (`primaryContainer`) with a 72 dp icon (`onPrimaryContainer`), title (displaySmall, Noto Serif, centred), body (bodyLarge, `onSurfaceVariant`, centred), 240 dp-wide `Button` CTA.

| # | Icon | Title | Body | CTA | Action |
|---|---|---|---|---|---|
| 1 | `Icons.Outlined.DarkMode` (crescent) | Welcome to Adhan | Accurate prayer times, Qibla direction, and gentle reminders — wherever you are. | Next | go to page 2 |
| 2 | `Icons.Outlined.LocationOn` | Allow location | We use your location to get accurate prayer times and the Qibla direction. Your coordinates are sent to alislam.org only to fetch your timetable. | Allow location | request `ACCESS_COARSE_LOCATION` + `ACCESS_FINE_LOCATION` together; regardless of result go to page 3. If denied, the page shows a secondary button **Choose city instead** that opens Location (§8.7) after onboarding finishes |
| 3 | `Icons.Outlined.NotificationsActive` | Allow notifications | Hear the adhan at each prayer time. You can choose the sound, reminders and days for every prayer. | Allow notifications | on API 33+ request `POST_NOTIFICATIONS`; then finish |

- Body copy on page 2 is corrected: the Flutter text claimed "Nothing leaves your device", which is false (BUG-29).
- **Skip** finishes immediately (sets `onboarding_done = true`) without requesting anything.
- Finish = set `onboarding_done`, trigger first location fetch + sync (§9), `rescheduleAll("onboarding")`, navigate to Main.
- If the user declines notifications, prayers stay "enabled" in prefs (D-01) but the Notifications screen shows the permission banner and no alarms are scheduled (§10.3 rule 1).

### 8.3 Home

`Scaffold` + `LargeTopAppBar` (`exitUntilCollapsedScrollBehavior`) + `PullToRefreshBox` wrapping a `LazyColumn`.

**Top app bar**

- Title: greeting (§7.8), `titleLarge` in **Noto Serif** when collapsed, headlineMedium Noto Serif when expanded.
- Actions: `AssistChip` with leading `Icons.Outlined.LocationOn` (18 dp) and the location label (city; `…` while loading; "Set location" when none) → navigates to Location; then `IconButton(Icons.Rounded.Refresh)` with content description "Refresh prayer times" which rotates 360° in 500 ms while refreshing and is disabled while refreshing.

**Body items, top to bottom:**

1. **Zone hint** (only when D-06 applies): `labelMedium`, `onSurfaceVariant`: "Times shown in {zone city} time" (e.g. "Times shown in London time"; derive from the zone ID's last segment, underscores → spaces).
2. **Hero card** — 16 dp horizontal margin, 8 dp top, 16 dp bottom; 20 dp padding; corner 24 dp; background linear gradient top-start→bottom-end `primaryContainer` → `surfaceContainerHigh`.
   - `UP NEXT` (labelSmall, letterSpacing 1.2 sp, weight 600, `onPrimaryContainer` @70%).
   - Row: next prayer name (displaySmall, Noto Serif, `onPrimaryContainer`, lineHeight 1.0) | right-aligned next prayer clock time (titleMedium, `onPrimaryContainer` @80%) + "tomorrow" suffix if `isTomorrow`.
   - 12 dp gap, countdown `HH:MM:SS` (headlineMedium, **Inter** weight 600, tabular figures, `onPrimaryContainer`) + "remaining" (bodySmall @70%). Digits crossfade 150 ms when the string changes (`AnimatedContent` keyed by the string; to avoid a full-string fade each second, crossfade only the characters that changed — a `Row` of per-character `AnimatedContent` is fine).
   - 16 dp gap, **PrayerProgressBar** (§8.3.1) for the current window.
   - 8 dp gap, row: window start name (left) and window end name (right), bodySmall @70%. For W2 show "Sunrise" → "Zuhr". For W0 show "Isha" → "Fajr".
   - Content description of the whole card (merged semantics): "Next prayer {name} at {time}, in {h} hours {m} minutes."
3. **Today timeline** — `LazyRow`, 16 dp content padding, 12 dp spacing, 5 chips (Fajr, Zuhr, Asr, Maghrib, Isha), each 96×96 dp, corner 16 dp:
   - Name (labelLarge, weight 600) and time (bodySmall, tabular).
   - **Current** chip (the salah whose window contains now; Fajr only until Sunrise; Isha from Isha until next Fajr; none during Sunrise→Zuhr): `primaryContainer` fill, 2 dp `primary` border, text `onPrimaryContainer`.
   - **Passed** chips: normal style; **upcoming**: normal. Others: `surfaceContainerHigh` fill, 1 dp `outlineVariant` border, name `onSurface`, time `onSurfaceVariant`.
   - A small `Icons.Outlined.NotificationsOff` (14 dp, `outline`) at top-end of chips whose notification is disabled or excluded today by day selection (quick visual of alarm state; new, low cost).
   - Below the row (D-15): "Sunrise {time}" (labelMedium, `outline`), start-aligned at 16 dp.
4. **Quick actions** — row of 3 equal tiles (12 dp gaps, 24 dp top margin): `surfaceContainerHigh`, corner 16 dp, 16 dp vertical padding, 28 dp `primary` icon + labelMedium 600 label. Qibla (`Explore`) → Qibla tab; Notifications (`Notifications`) → Notifications; Location (`LocationOn`) → Location. Haptic on tap.
5. **Dates footer** — centred: Gregorian (bodyMedium, `onSurfaceVariant`), 2 dp, Hijri "(approx.)" (bodySmall, `outline`). 32 dp bottom padding.

**States**

| State | Hero area shows |
|---|---|
| Loading (no cached data yet) | Skeleton card same size as hero (shimmer: animated `surfaceContainerHighest` ↔ `surfaceContainerHigh` alpha 0.6→1.0, 1 s), skeleton timeline of 5 chips |
| No location & no permission | `StateView(icon = LocationOff, title = "Set your location", body = "Allow location access or choose a city to see prayer times.", primary = "Allow location", secondary = "Choose city")` |
| Location error | `StateView` with message from §14 for the specific `AppError`, action "Try again" (+ "Choose city") |
| Data error with no cache | `StateView(CloudOff, "Couldn't load prayer times", message, "Try again")` |
| Data error with cache (`stale`) | Normal content + a one-line `labelSmall` banner under the hero: "Showing saved times — couldn't refresh." |

**Refresh behaviour** (pull or button):

1. If `auto_location`, refresh GPS location (§9.2, ignoring the throttle).
2. `refreshCurrent(location)` (force fetch current month, and next month if ≤ 7 days left).
3. Reschedule alarms and refresh widget.
4. Snackbar: success "Prayer times refreshed" (2 s) or failure "Couldn't refresh. Pull down to retry."

**Ticking**

- ViewModel combines `observeDays(...)`, location, prefs, and a `tickerFlow(1 minute aligned to the minute boundary)` to produce `HomeUiState`; it also re-evaluates exactly at the next boundary (next prayer instant / window end / local midnight) by computing `delay(min(nextBoundary − now, 60 s))`.
- The countdown composable has its own 1-second ticker started with `LaunchedEffect`/`produceState` and **stops when the screen is not resumed** (`collectAsStateWithLifecycle` / `LifecycleResumeEffect`).

#### 8.3.1 PrayerProgressBar component

- Height 10 dp, fully rounded (5 dp radius), track `surfaceContainerHighest`.
- Filled portion = `progress × width`. Within the filled portion, draw each segment's colour clipped to `[segment.start, min(segment.end, progress)]`. The right end of the fill is rounded.
- Colours: GREEN = `primary`, YELLOW = `Color(0xFFFB8C00)` (Material orange 600) — define as `extendedColors.warning` with dark-theme variant `Color(0xFFFFB74D)` for contrast, RED = `error`, NEUTRAL = `outline` @40%.
- Animate progress changes with `animateFloatAsState(tween(250))`.
- Draw with a single `Canvas`; no per-segment composables.
- Semantics: `progressBarRangeInfo(progress, 0f..1f)` and `stateDescription` "{start} window, {urgency word}" where urgency word = "plenty of time" / "getting late" / "ending soon" / "" for neutral.

### 8.4 Qibla (tab)

`Scaffold` with `TopAppBar` "Qibla compass" and a Refresh action (re-reads location like Home refresh step 1 and recomputes). Content is a vertically scrolling column, 16 dp padding:

1. **Calibration tips card** (`primaryContainer`, corner 12 dp, 12 dp padding, `TipsAndUpdates` icon): title "Calibration tips", body "Move your phone in a figure-8, away from metal or magnets, and hold it flat. Tilt: {n}°". `n` from the accelerometer: `tilt = 90 − clamp(|z| / 9.81, 0, 1) × 90`, rounded to integer, updated at most 4×/s. When the rotation-vector sensor reports accuracy `UNRELIABLE` or `LOW`, the card switches to `errorContainer` and the title becomes "Compass needs calibration".
2. **Compass** — 280 dp circle, 3 dp `primary` border, soft shadow.
   - The **dial** (surface fill, N/NE/E/SE/S/SW/W/NW labels at radius 120 dp in `primary`, bold 16 sp) rotates by **−heading** (true north).
   - A **Kaaba marker** (30 dp green `#2E7D32` circle, 2 dp white border, "🕋" 20 sp) sits on the dial at angle `qiblaBearing`, counter-rotated so it stays upright.
   - A **fixed needle** (4×120 dp, pointing to the top of the phone) whose colour encodes alignment: shortest angular distance `d = |relativeAngle|`; `d ≤ 15°` green `#2E7D32`; `15 < d ≤ 60` lerp green→orange `#FB8C00`; `> 60` lerp orange→red `#C62828` up to 180°.
   - Centre dot 20 dp `primary` with 3 dp `surface` border.
   - When `d ≤ 3°` for ≥ 300 ms, fire one `HapticFeedbackType.Confirm` (or `LongPress` on older Compose) — re-arm after `d > 8°`.
   - Rotation uses the **shortest path** (unwrap angles) so the dial doesn't spin 359° when crossing north; animate with `animateFloatAsState(spring(stiffness = Spring.StiffnessLow))` on an unwrapped value.
3. **Info cards** (stacked, 12 dp gap; `surface`, 1 dp `outline`@20% border, corner 12 dp): Compass heading "{x.x}°" (`Explore`), Compass accuracy "Excellent/Good/Fair/Poor" (`Sensors`) — mapping `SENSOR_STATUS_ACCURACY_HIGH→Excellent, MEDIUM→Good, LOW→Fair, UNRELIABLE→Poor`, Qibla direction "{x.x}° {NE…}" (`Navigation`), Distance to Kaaba "{n} km" with grouping separator (`Straighten`).
4. **Map** — 220 dp tall, corner 12 dp, osmdroid `MapView` in `AndroidView`, zoom 3–4 so both points are visible (`zoomToBoundingBox` of user + Kaaba with 48 px padding), user marker (`MyLocation` tinted `#1E88E5`), Kaaba marker (🕋 text drawable), **geodesic** polyline (great-circle points, 64 intermediate points — Flutter drew a straight Mercator line, which is not the qibla path, BUG-41), attribution overlay "© OpenStreetMap contributors". Disable map rotation; allow pinch/pan. Handle `onResume/onPause/onDetach` from the lifecycle.

**Sensors** (`CompassSensor` class exposing `Flow<CompassReading(headingTrue, accuracy)>`):

- Prefer `Sensor.TYPE_ROTATION_VECTOR`; fall back to `TYPE_GEOMAGNETIC_ROTATION_VECTOR`; fall back to accelerometer + magnetometer (`getRotationMatrix`). Rate `SENSOR_DELAY_UI`.
- `getRotationMatrixFromVector` → `remapCoordinateSystem` according to `display.rotation` (0/90/180/270) → `getOrientation` → azimuth in degrees (0..360).
- **True north**: `headingTrue = (azimuthMagnetic + GeomagneticField(lat, lng, altitude=0f, now).declination + 360) % 360` (D-11, BUG-40).
- Low-pass filter on the unit vector (`sin`, `cos`) with α = 0.15 to avoid wrap-around artefacts.
- Register only while the Qibla tab is visible and resumed; unregister on pause.
- If none of the sensor combos exist: show `StateView(ExploreOff, "Compass not available", "This device has no compass sensor. Use the map below to find the Qibla direction.")` **and still show the map and the bearing number** (Flutter showed only an error). The manifest must declare the sensors `required="false"` (BUG-39).

### 8.5 More (tab)

`TopAppBar` "More". `LazyColumn` of sections; section header = labelSmall, `primary`, letterSpacing 1.2, weight 600, uppercase, padding (16, 12, 16, 8).

| Section | Row | Subtitle | Destination |
|---|---|---|---|
| PRAYER | Notifications (`Notifications`) | Manage prayer time alerts | Notifications |
| | Location (`LocationOn`) | Auto-detect or set a city manually | Location |
| APPEARANCE | Theme (`Palette`) | current mode name | Appearance |
| ADVANCED | Advanced (`Tune`) | Battery, alarms, cached data | Advanced |
| ABOUT | About this app (`Info`) | — | About dialog |

Each row: `ListItem` with `primary` leading icon, trailing `ChevronRight` (`outline`), haptic on tap. Footer: "Adhan" bodySmall `outline`, centred, 32 dp top/bottom.

**About dialog** (`AlertDialog`): app icon, "Adhan", "Version {versionName} ({versionCode})", "Prayer times provided by alislam.org.", "Map data © OpenStreetMap contributors.", "© {year} Adhan". Buttons: "Licenses" (opens a simple screen/dialog listing third-party licences — use `com.google.android.gms:play-services-oss-licenses` or a static list) and "Close".

### 8.6 Notifications

`TopAppBar` "Notifications" with back. `LazyColumn`:

1. **Permission banners** (show all that apply, in this order; each `errorContainer` card, corner 16 dp, icon + text + tonal button):
   - Notifications blocked (`!NotificationManagerCompat.areNotificationsEnabled()`): "Notifications are off. Turn them on to hear the adhan." → **Enable**: if permission can be requested (API 33+, not permanently denied) request it; else open `Settings.ACTION_APP_NOTIFICATION_SETTINGS`.
   - Exact alarms not allowed (API 31+, `!alarmManager.canScheduleExactAlarms()`): "Allow alarms so the adhan plays exactly on time." → **Allow**: `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` with `package:` URI.
   - Battery optimised (`!isIgnoringBatteryOptimizations`): `tertiaryContainer` (not error) "Battery optimisation may delay the adhan." → **Fix** opens the battery sheet (§8.10.3).
   - A prayer channel blocked by the user in system settings (`channel.importance == IMPORTANCE_NONE`): "{Prayer} alerts are blocked in system settings." → **Open** `Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS`.
   - Re-check all of these on every `ON_RESUME`.
2. **Five prayer cards** (Fajr, Zuhr, Asr, Maghrib, Isha), 16 dp horizontal margin, 8 dp gap, `surfaceContainerHigh`, corner 16 dp, 16 dp padding:
   - Line 1: name (titleMedium 600; `onSurface` if enabled else `onSurfaceVariant`) + today's time (bodyMedium, tabular, `onSurfaceVariant`).
   - Line 2 (bodySmall, single line, ellipsis): "{sound name}" + " • reminder {n} min before" if advance > 0 + " • {days summary}" if not all days. Days summary: "Weekdays" (Mon–Fri), "Weekends" (Sat, Sun), "Every day" (omitted), else comma list "Mon, Wed, Fri".
   - Trailing `Switch` bound to `enabled`; light haptic; immediate persist + reschedule.
   - Whole card clickable → opens the **editor sheet** (§8.6.1).
   - If today's times aren't available, omit the time text (don't block the list on network — Flutter showed a spinner/error for the whole list when the month failed to load, BUG-20).

#### 8.6.1 Prayer notification editor (bottom sheet)

`ModalBottomSheet` with two internal pages using `AnimatedContent` (slide horizontally, 300 ms):

**Page 1 — "{Prayer}"** (top bar: title centred, close `IconButton` end):

- Switch row "Adhan for {Prayer}" bound to `enabled` (so the sheet is usable on its own).
- **DAYS** label; 7 toggle chips Mon…Sun (38×40 dp, corner 12 dp; selected = `primary` fill + `onPrimary` text; unselected = 1 dp `outline` border; disabled styling when the prayer is off). The **first day of the week follows the locale** (`WeekFields.of(locale).firstDayOfWeek`). Deselecting the last selected day is blocked with snackbar "Keep at least one day selected."
- **SOUND** label; `ListItem` showing the current sound name, trailing `ChevronRight` → page 2.
- **REMINDER** label; `Slider` 0..30 step 5 (`steps = 5`), value label "{n} min" / "Off" at 0; text to the right (56 dp) with the same label. Persist only `onValueChangeFinished` (Flutter persisted and enqueued a reschedule on every drag tick, BUG-37). Helper text under the slider when > 0: "You'll get a reminder {n} minutes before {Prayer}; the adhan still plays on time."
- **Test** `FilledTonalButton` full width, icon `NotificationsActive`, "Play test adhan": schedules a test alarm 5 s ahead through the real alarm path (§10.5 kind TEST); snackbar "Test adhan in 5 seconds". Disabled with explanation if notifications are blocked. (Flutter's snackbar said 2 s; keep text and behaviour consistent — BUG-38.)

**Page 2 — "{Prayer} — Sound"** (back arrow start, close end):

- `FlowRow` of `FilterChip`s for every catalog entry, 8 dp spacing; selected chip shows a check; a chip whose sound is downloading shows a 14 dp circular progress (determinate if `Content-Length` known); downloaded sounds show a small `DownloadDone` icon, not-downloaded show `CloudDownload`.
- Tapping a chip: persist selection → ensure file (download if needed) → **preview** it with ExoPlayer (stop any current preview first). Tapping the selected chip again toggles preview stop/play.
- A persistent bottom row while previewing: "Playing {name}" + Stop button.
- Stop preview when the sheet closes, the app backgrounds, or another chip is tapped. Use `AudioAttributes.USAGE_MEDIA` for preview (not alarm) and request transient audio focus.

### 8.7 Location

`TopAppBar` "Location" with back. Content (`LazyColumn`):

1. **Current location card** (`primaryContainer`, corner 16 dp, 16 dp padding): "CURRENT LOCATION" label with `LocationOn` icon; city (headlineSmall, Noto Serif) or coordinates if no name; second line "{region}, {country}" when available; coordinates "51.5074°, −0.1278°" (bodySmall, tabular, 4 decimals); source chip "GPS" / "Manual"; "Last updated: {updatedAt}" (§7.10). Loading → 60 dp spinner; error → inline StateView.
2. **Use current location** `FilledTonalButton` (full width, `MyLocation` icon): sets `auto_location = true` and performs a **fresh** GPS fix (§9.2, no throttle, no cache shortcut). Shows progress in the button. (BUG-07: the Flutter "Use current location" and the auto-detect toggle only re-read the cached location, so they never actually refreshed GPS after the first fix.)
3. **Auto-detect location** `ListItem` + `Switch` bound to `auto_location`; supporting text "Using GPS to find your location" / "Using a manually set city". Turning it on triggers the same fresh fix; turning it off keeps the current location as MANUAL.
4. Divider, **SEARCH CITY** label, `OutlinedTextField` (corner 16 dp, leading `Search`, trailing clear button or 16 dp spinner while searching, placeholder "e.g. Mecca, London, Karachi", IME action Search).
   - Debounce 400 ms, minimum 2 non-blank characters, cancel the previous query when a new one starts (`mapLatest`).
   - Results: up to 8 `ListItem`s — headline = `locality ?: featureName ?: adminArea`, supporting = "{adminArea}, {countryName}", trailing = coordinates (labelSmall). Deduplicate by 2-decimal key. (D-13)
   - Empty: "No matches found". Error: "Search isn't available right now." (`GeocoderUnavailable`) or "Couldn't search. Check your connection."
5. **Selecting a result**: store as `StoredLocation(source = MANUAL, updatedAt = now, zone = null)`; set `auto_location = false`; fetch coverage for the new key (this sets `zone` from the API); reschedule; refresh widget; pop back with snackbar on the previous screen "Location set to {city}". If the fetch fails, keep the new location and show the error on Home (it will retry).

### 8.8 Appearance

`TopAppBar` "Appearance". Section "THEME": `SingleChoiceSegmentedButtonRow` with System (`BrightnessAuto`), Light (`LightMode`), Dark (`DarkMode`); persisted immediately (D-07); applies without restart. Section "LANGUAGE": a non-interactive `ListItem` "English" with supporting "More languages coming soon" and a `Check` icon in `primary` (Flutter used hardcoded `Colors.green`).

### 8.9 Update gate

- **Force**: full-screen composable drawn over everything (not a nav destination), `BackHandler { /* consume */ }`. Centre column, 24 dp padding: 120 dp rounded-square (corner 24) `primary`@10% with 60 dp `SystemUpdate` icon `primary`; "Update required" (headlineMedium bold `primary`); "A required update is available. Please update to keep using Adhan." (bodyLarge); version chip "Version {v}" (`NewReleases` icon); full-width 56 dp `Button` "Update now" (`Download` icon) → open URL (§5.4); helper "Install the update, then reopen the app."; footer (bodySmall @60%) "This update is required for reliability and security." The gate re-checks on every `ON_RESUME` so it disappears if the user returns after updating via another path.
  - **Alarms keep working** while the gate is shown (the gate is UI-only).
- **General**: `AlertDialog`, `SystemUpdate` icon, "Update available", "Version {v} is available with fixes and improvements.", buttons "Later" / "Update". Shown at most once per process; dismissible.

### 8.10 Advanced

`TopAppBar` "Advanced". Rows:

1. **Battery optimisation** (`BatteryChargingFull`): supporting text green (`primary`) "Not restricted — alarms will be reliable" or `error` "Restricted — the adhan may be late". Tap → battery sheet (§8.10.3). Re-evaluate on resume.
2. **Exact alarms** (API 31+) (`Alarm`): "Allowed" / "Not allowed — tap to allow" → `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`.
3. **Scheduled alarms** (`Schedule`): opens a dialog listing the next 10 planned alarms from `scheduled_alarm_keys` + planner output: "{Prayer} — {kind} — {EEE h:mm a}". Helps users and support verify scheduling. (New; replaces the Flutter debug "background service" dialog whose Start/Stop/status were placeholders, BUG-45.)
4. **Cached data** (`Storage`): dialog with "Saved days: {n}", "Locations: {n}", "Downloaded sounds: {n} ({size})", "Last sync: {relative time}", actions **Refresh now**, **Clear saved times** (confirm dialog; then immediately refetch for the current location so Home doesn't go blank), **Delete downloaded sounds** (confirm).
5. Debug builds only, under a "DEBUG" header in `error` colour: **Run sync now** (enqueue `SyncWorker` expedited), **Update widget now**, **Fire test alarm for each prayer in 10 s steps**, **Dump state to logcat**.

#### 8.10.3 Battery sheet

`ModalBottomSheet`:

- Title "Keep the adhan on time". Text: "Some phones stop apps in the background. For reliable adhan:" then numbered list: "1. Turn off battery optimisation for Adhan." "2. Allow background activity / autostart (on some phones)." "3. Lock Adhan in recent apps (Xiaomi, Huawei)."
- Status line ("Battery optimisation: On/Off").
- Buttons (each `OutlinedButton`, full width, start-aligned text):
  - **Turn off battery optimisation** → `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `package:org.tarish.adhan` (requires `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`); on `ActivityNotFoundException` fall back to `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`.
  - **Open battery settings** → `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`.
  - OEM-specific, by `Build.MANUFACTURER.lowercase()` (also check `Build.BRAND`):
    - `xiaomi`, `redmi`, `poco`: "Open Autostart (MIUI/HyperOS)" → component `com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity`; "Open battery saver" → `com.miui.powerkeeper/.ui.HiddenAppsConfigActivity` with extras `package_name`, `package_label`.
    - `huawei`, `honor`: "Open app launch" → `com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity`, fallback `com.huawei.systemmanager/.optimize.process.ProtectActivity`.
    - `samsung`: "Open sleeping apps" → action `android.settings.APP_BATTERY_SETTINGS` (fallback `com.samsung.android.lool/com.samsung.android.sm.battery.ui.BatteryActivity`).
    - `oneplus`, `oppo`, `realme`: "Allow auto-launch" → `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` `package:` URI.
    - `vivo`, `iqoo`: "Allow background" → `com.vivo.permissionmanager/.activity.BgStartUpManagerActivity`, fallback app details.
  - Every OEM intent is launched in try/catch; on failure open app details settings.
- One-time prompt (D-09): after onboarding, ≥ 2 s after Home first shows, if `!isIgnoringBatteryOptimizations` and `battery_prompt_shown == false`, show this sheet and set the flag when it's dismissed.

---

## 9. Location behaviour

### 9.1 Sources of truth

- `StoredLocation` in DataStore is **the** location used everywhere (UI, alarms, widget, workers).
- GPS is consulted only in the foreground, never by workers (D-10).

### 9.2 Automatic GPS refresh (when `auto_location == true`)

Trigger a fix:

- after onboarding / first launch with permission;
- on app foreground (`ProcessLifecycleOwner` `ON_START`) if `now − last_gps_refresh_at > 6 h`;
- on Home refresh / Qibla refresh / "Use current location" (no throttle).

Procedure:

1. Check permission; if missing → state `LocationPermissionDenied` (Home shows the "Set your location" StateView; never auto-prompt outside an explicit user action, except during onboarding).
2. Check location settings; if off → `LocationServicesOff` (UI offers "Turn on" via settings resolution).
3. Get a fix (§5.3). Set `last_gps_refresh_at = now`.
4. If there is no stored location, or the distance from the stored location > **2 km** (haversine on full-precision coords), reverse-geocode and **replace** the stored location (`source = GPS`, `updatedAt = now`, keep `zone` only if the 2-decimal key is unchanged, else null until fetched). Then ensure coverage, reschedule, update widget.
5. If moved ≤ 2 km, update nothing except `last_gps_refresh_at` (prevents cache-key flapping at rounding boundaries).

### 9.3 First-launch without stored location

`HomeViewModel` sees `location == null`:
- if permission granted → start a fix (loading state);
- else → "Set your location" state with both actions.

### 9.4 Permission rationale

If `shouldShowRequestPermissionRationale` is true, show an `AlertDialog` before re-requesting: "Location is used only to find prayer times and the Qibla for where you are. You can also choose a city instead." Buttons: "Choose city" / "Continue". If permanently denied, the primary action becomes "Open settings" (`ACTION_APPLICATION_DETAILS_SETTINGS`).

---

## 10. Adhan alarms, playback and notifications

This is the most important subsystem: the adhan must play on time every day without the user opening the app.

### 10.1 Overview

```
AlarmPlanner (pure) ──► AlarmScheduler ──► AlarmManager.setAlarmClock / setExactAndAllowWhileIdle
                                                   │ (at fire time)
                                                   ▼
                                         AdhanAlarmReceiver
                                   ┌───────────────┼────────────────┐
                              kind=ADHAN       kind=REMINDER      kind=TEST
                                   │               │                │
                     start AdhanPlaybackService   post reminder    same as ADHAN
                     (foreground, mediaPlayback)  notification     with test text
                                   │
                    post "It's time for X" notification with Stop action,
                    play sound with USAGE_ALARM, stop at end / on Stop / on dismiss
                                   │
                         then: AlarmCoordinator.rescheduleAll("fired") + widget refresh
```

### 10.2 Notification channels

Create all channels in `AdhanApp.onCreate` (idempotent). Channel IDs are **new** (old Flutter channels are deleted in §6.4).

| Channel ID | Name | Importance | Sound | Vibration | Notes |
|---|---|---|---|---|---|
| `adhan_fajr`, `adhan_zuhr`, `adhan_asr`, `adhan_maghrib`, `adhan_isha` | "Fajr adhan" … | HIGH | **none** (`setSound(null, null)`) | on, pattern `[0, 500, 300, 500]` | Group `adhan_prayers` ("Prayer times"). Lockscreen visibility PUBLIC. `setBypassDnd(false)` (user may change). Per-prayer channels let users silence one prayer from system settings. |
| `prayer_reminder` | "Prayer reminders" | DEFAULT | system default notification sound | default | For advance reminders (D-04) |
| `adhan_playback` | "Adhan playing" | LOW | none | off | Foreground-service notification while audio plays (merged with the prayer notification — see §10.6) |
| `app_updates` | "App updates" | DEFAULT | default | default | Reserved; not used for Remote Config (that's in-app) |

Because channels are silent, sound choice is purely app-controlled and changes take effect immediately (D-03).

### 10.3 Alarm planning (pure, `AlarmPlanner`)

Input: `now`, location zone, `PrayerDay`s for today and tomorrow (location-local), prefs for the 5 salah, `notificationsAllowed: Boolean`.

Output: `List<AlarmSpec>` sorted by time:

```kotlin
data class AlarmSpec(
    val kind: Kind,                 // ADHAN, REMINDER (TEST is ad-hoc, not planned)
    val prayer: PrayerName,
    val prayerTime: Instant,        // the actual salah instant
    val fireAt: Instant,            // = prayerTime for ADHAN, prayerTime - advance for REMINDER
    val date: LocalDate,            // location-local date of the salah
    val requestCode: Int,           // §10.4
)
```

Rules:

1. If `!notificationsAllowed` → empty list (nothing to schedule; UI shows banner).
2. Horizon: every salah instant in **today and tomorrow** (so even if nothing runs at midnight, tomorrow is already armed — BUG-08: Flutter relied on a WorkManager one-off at midnight to schedule each next day; WorkManager is not exact and can be deferred for hours in Doze, so Fajr was routinely missed).
3. Skip if `!prefs.enabled`.
4. Skip if `date.dayOfWeek !in prefs.days` (D-05). Day-of-week is evaluated on the **location-local date of the salah**.
5. ADHAN: include if `fireAt > now + 1 s`.
6. REMINDER: include if `advance > 0` and `fireAt > now + 1 s`. If the reminder time has passed but the adhan hasn't, skip just the reminder.
7. Never plan more than 20 alarms.

### 10.4 Scheduling (`AlarmScheduler`)

- Request code / notification ID (deterministic, collision-free for 270 years):

  ```
  requestCode = (date.toEpochDay() % 100_000).toInt() * 100 + kind.ordinal * 10 + prayer.ordinal
  ```

  (Flutter used `"$prayer-$date".hashCode & 0x7FFFFFFF` — collision-prone, and only cancelled *today's* IDs — BUG-36.)
- `PendingIntent.getBroadcast(context, requestCode, intent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)`; intent action `org.tarish.adhan.ALARM`, explicit component `AdhanAlarmReceiver`, extras: `kind`, `prayer`, `prayerTimeMillis`, `date` (ISO), `requestCode`.
- Choosing the API:
  - If `canScheduleExactAlarms()` (always true below API 31):
    - ADHAN → `alarmManager.setAlarmClock(AlarmClockInfo(fireAt, showIntent = PendingIntent to MainActivity), pi)`. Alarm-clock alarms are exempt from Doze and from most OEM batteries killers, and they show the alarm icon in the status bar — appropriate for an adhan.
    - REMINDER → `setExactAndAllowWhileIdle(RTC_WAKEUP, fireAt, pi)`.
  - Else (exact alarms denied on 31+): `setAndAllowWhileIdle(RTC_WAKEUP, fireAt, pi)` (may be up to ~10 min late) and surface the "Allow alarms" banner/prompt.
  - **Never** plain `setExact` (BUG-05: Flutter used `AndroidScheduleMode.exact` → `setExact`, which is deferred in Doze).
- `rescheduleAll(reason)`:
  1. Load location; if null → cancel all, clear keys, return.
  2. Ensure today+tomorrow data (DB only first; if tomorrow is missing try network with 10 s timeout; if still missing, plan what exists).
  3. Plan.
  4. Cancel every request code in `scheduled_alarm_keys` that is **not** in the new plan (rebuild the same `PendingIntent` with `FLAG_NO_CREATE` to cancel).
  5. Schedule every planned alarm (re-setting an identical PendingIntent replaces it).
  6. Save the new key set.
  7. Log a one-line summary (debug) "rescheduleAll(reason): n alarms, next=Fajr 05:26".
- Also schedule one **non-wakeup** `set(RTC, …)` alarm at the next window boundary (next salah, sunrise or local midnight, whichever first) with action `org.tarish.adhan.WIDGET_TICK` to refresh the widget even when all notifications are off (§12).

### 10.5 `AdhanAlarmReceiver`

`@AndroidEntryPoint`, `exported="false"`. On receive (use `goAsync()`):

- Validate staleness: if `now − fireAt > 15 min` (device was off / alarm delayed hugely), **don't play audio**; post the notification silently with text "Missed: {Prayer} was at {time}." — then reschedule. (Prevents a 4 a.m. Fajr adhan blasting at 11 a.m. after a phone is switched on.)
- **ADHAN / TEST**: `ContextCompat.startForegroundService(AdhanPlaybackService.intent(prayer, kind, prayerTime, soundId))`. Starting a foreground service from an exact-alarm broadcast is an allowed background-start exemption on Android 12+.
- **REMINDER**: post on `prayer_reminder` channel: title "{Prayer} in {n} min", body "{Prayer} begins at {time}.", small icon `ic_stat_adhan`, tap → Home, `setTimeoutAfter(n minutes)`, `setAutoCancel(true)`.
- Finally `AlarmCoordinator.rescheduleAll("fired")` and `WidgetUpdater.updateAll()`.

### 10.6 `AdhanPlaybackService`

- `foregroundServiceType="mediaPlayback"`; manifest permissions `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
- In `onStartCommand`, **immediately** `startForeground(notificationId = requestCode, notification)` (within 5 s rule) using `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)`.
- Notification (posted on the per-prayer channel `adhan_{key}`; for TEST use the prayer's channel too):
  - small icon `ic_stat_adhan` (monochrome crescent vector), colour `primary`.
  - Title: `"{Prayer}"`; body: `"It's time for {Prayer} prayer."` (TEST: title "Test — {Prayer}", body "This is how your {Prayer} adhan will sound.").
  - `setCategory(CATEGORY_ALARM)`, `setPriority(PRIORITY_HIGH)`, `setVisibility(VISIBILITY_PUBLIC)`, `setShowWhen(true)`, `setWhen(prayerTime)`.
  - Content intent → MainActivity (`adhan://home`).
  - Action **Stop adhan** (`Icons` vector `ic_stop`) → service intent `ACTION_STOP`.
  - `setDeleteIntent` → `ACTION_STOP` (swiping the notification stops the sound).
  - When playback ends, the service calls `stopForeground(STOP_FOREGROUND_DETACH)` so the notification **remains** (without the Stop action, re-posted via `notify`) and stops itself; the remaining notification auto-times-out after **60 minutes** (`setTimeoutAfter(3_600_000)`, same as Flutter).
- Audio:
  - Resolve the source via `SoundRepository.resolve(soundId)`; `Missing` → bundled Makkah.
  - ADHAN kinds: ExoPlayer (or `MediaPlayer`) with `AudioAttributes(USAGE_ALARM, CONTENT_TYPE_SONIFICATION)` — plays at **alarm volume** and is audible in silent/vibrate mode, matching the Flutter app's `AudioAttributesUsage.alarm`.
  - `SYSTEM_DEFAULT`: play `RingtoneManager.getDefaultUri(TYPE_NOTIFICATION)` once with the same attributes.
  - `SILENT`: no audio; the notification vibrates via its channel; stop service immediately after posting (detach).
  - Request `AUDIOFOCUS_GAIN_TRANSIENT`; on focus loss (e.g. phone call) **stop** (don't duck).
  - Hold a partial wakelock only via the player (`setWakeMode(C.WAKE_MODE_LOCAL)`) and release on stop.
  - If a second adhan starts while one is playing (e.g. test while real), stop the first and play the second.
  - Hard stop after 10 minutes regardless.
  - Stop on `ACTION_STOP`, on screen-off-by-power-button? **No** (power button shouldn't stop it); on volume-down key? Not possible from a service — rely on the Stop action.

### 10.7 System events (`SystemEventsReceiver`, exported where the action requires)

Handles: `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED` (skip — DataStore is credential-encrypted; wait for BOOT_COMPLETED), `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, `QUICKBOOT_POWERON` (`android.intent.action.QUICKBOOT_POWERON`, `com.htc.intent.action.QUICKBOOT_POWERON`).

On any: `goAsync()` → `rescheduleAll(reason)` → `WorkScheduler.ensurePeriodic()` → `WidgetUpdater.updateAll()`.

On `TIMEZONE_CHANGED` additionally: if `auto_location` is on, set `last_gps_refresh_at = 0` so the next foreground refreshes GPS (the user probably travelled).

(BUG-08 related: Flutter's AlarmManager reboot receiver was declared `android:enabled="false"`, and the boot path depended on plugin receivers.)

### 10.8 Exact-alarm permission flow (API 31+)

- Manifest declares `SCHEDULE_EXACT_ALARM` and `USE_EXACT_ALARM` (same as the current app). `USE_EXACT_ALARM` is auto-granted on API 33+ for apps whose core function is alarms; Google Play reviews this — if Play rejects it, remove `USE_EXACT_ALARM` and rely on `SCHEDULE_EXACT_ALARM` + the prompt below. The code MUST work in both configurations by always checking `canScheduleExactAlarms()`.
- One-time explainer dialog (global overlay, §8.1) when exact alarms are not allowed and at least one prayer is enabled: title "Allow adhan alarms", text "To play the adhan exactly on time, Adhan needs permission to set alarms.", buttons "Not now" / "Allow" (→ `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`). Set `exact_alarm_prompt_shown`. The Notifications banner remains as a persistent path.

### 10.9 Notification permission (API 33+)

- Requested on onboarding page 3 and from the Notifications banner only.
- `notificationsAllowed = NotificationManagerCompat.from(ctx).areNotificationsEnabled()`; re-evaluated on every `rescheduleAll` and on resume. If it flips to true while the app is open → `rescheduleAll("permission")`.

---

## 11. Background work (WorkManager)

Use `HiltWorkerFactory` (remove the default initializer via manifest `<provider ... tools:node="remove">` for `androidx.startup` WorkManager initializer, and implement `Configuration.Provider`).

| Unique name | Type | Schedule | Constraints | Work |
|---|---|---|---|---|
| `sync_timetable` | Periodic | every 24 h, flex 6 h, initial delay to ~03:00 local | `NetworkType.CONNECTED` | Load stored location (skip if none); ensure required coverage (§6.3 rule 3) without force; if a new month was stored or anything changed → `rescheduleAll("sync")`; always `rescheduleAll` once (self-heal); update widget; set `last_sync_at`. Retry with exponential backoff (30 s base) on network failure, max 3 attempts per period. |
| `maintenance` | Periodic | every 7 days | none (and `setRequiresBatteryNotLow(true)`) | Cleanup (§6.3 rule 6), delete orphan `.part` files, prune WorkManager. |
| `widget_refresh` | Periodic | every 15 min (minimum) | **none** | Update widget only. No network needed (BUG-46: Flutter required network for widget and planning jobs). |
| `sound_download_{id}` | One-time | on demand | `CONNECTED`, backoff | Download one sound (§5.2). |
| `sync_now` | One-time, expedited | on location change / onboarding / debug | `CONNECTED` | Same as `sync_timetable`. |

`ExistingPeriodicWorkPolicy.UPDATE` for periodic jobs (so changed definitions take effect after app updates); `REPLACE` for one-time.

`WorkScheduler.ensurePeriodic()` is called from `AdhanApp.onCreate` (after migration) and from `SystemEventsReceiver`.

There is no midnight planning job and no "fetch next month on the last day at 02:00" job — both are covered by the 48 h alarm horizon (§10.3) and the ≤ 7-days-left prefetch in `sync_timetable` (§6.3).

---

## 12. Home-screen widget (Glance)

### 12.1 Provider

- Receiver class **`org.tarish.adhan.AdhanHomeWidget`** (keep this name — §6.4 step 7) extending `GlanceAppWidgetReceiver`, `glanceAppWidget = NextPrayerWidget()`.
- `res/xml/adhan_home_widget_info.xml`:
  - `minWidth="110dp" minHeight="40dp"`, `targetCellWidth="2" targetCellHeight="1"` (API 31+), `minResizeWidth="110dp" minResizeHeight="40dp"`, `resizeMode="horizontal|vertical"`, `widgetCategory="home_screen|keyguard"`, `updatePeriodMillis="0"` (we update ourselves), `previewLayout` (API 31+) / `previewImage` (`@drawable/widget_preview`), `description="@string/widget_description"`.
- `SizeMode.Responsive(setOf(DpSize(110.dp, 40.dp), DpSize(180.dp, 110.dp), DpSize(250.dp, 110.dp)))`.

### 12.2 Content

State is computed on update (not stored as loose strings like Flutter's HomeWidget prefs):

```kotlin
data class WidgetState(val prayerName: String, val time: String, val amPm: String?, val remaining: String, val city: String, val country: String, val isTomorrow: Boolean)
```

From `NextPrayerFinder` (§7.3) with today/tomorrow from Room (**no network calls from the widget path**; if data is missing, show the placeholder "Open Adhan to load prayer times").

Layouts (Glance `GlanceTheme` with the app's colour scheme; background `GlanceTheme.colors.widgetBackground`, corner radius `system_app_widget_background_radius` on API 31+, 16 dp otherwise; padding 12 dp):

- **Compact** (< 180 dp wide or < 110 dp tall): prayer name (14 sp bold `onSurface`), time (28 sp bold; 24-h or with small AM/PM if it fits), remaining (12 sp `onSurfaceVariant`). No logo, no location.
- **Medium/Large**: top row: prayer name (18 sp bold) + app logo 20 dp at end (@90% alpha). Time 42 sp bold with AM/PM 12 sp aligned to baseline. Remaining "in 1 h 5 min" (14 sp `primary`). City (16 sp bold `onSurfaceVariant`), country (13 sp `onSurfaceVariant`) (D-14).
- Whole widget click → `actionStartActivity<MainActivity>()`.
- Content description: "Next prayer {name} at {time}, {remaining}, {city}".

### 12.3 Update triggers

`WidgetUpdater.updateAll()` (`NextPrayerWidget().updateAll(context)`), called from:

- app start and every Home state change of next prayer/location;
- `AdhanAlarmReceiver` (every fire), `WIDGET_TICK` alarm at each boundary (§10.4);
- `widget_refresh` worker (every 15 min) — keeps "in X min" roughly fresh;
- `SystemEventsReceiver`;
- location change, data sync, prefs change (none needed for prefs).

While the app is in the foreground, also update once per minute (the Flutter app did this via a timer; keep it, scoped to `ProcessLifecycleOwner` STARTED).

---

## 13. Visual design system

### 13.1 Colour schemes (from `lib/theme/theme.dart`)

Dynamic colour (Material You) is **off** — brand palette always. Medium/high-contrast schemes exist in the Flutter file; port them as optional (`UiModeManager.contrast` on API 34+) — SHOULD, not MUST.

| Role | Light | Dark |
|---|---|---|
| primary | `#1B6B51` | `#8BD6B6` |
| onPrimary | `#FFFFFF` | `#003828` |
| primaryContainer | `#A6F2D1` | `#00513B` |
| onPrimaryContainer | `#00513B` | `#A6F2D1` |
| secondary | `#4C6359` | `#B3CCBF` |
| onSecondary | `#FFFFFF` | `#1E352B` |
| secondaryContainer | `#CEE9DB` | `#354B41` |
| onSecondaryContainer | `#354B41` | `#CEE9DB` |
| tertiary | `#3E6374` | `#A6CCE0` |
| onTertiary | `#FFFFFF` | `#093544` |
| tertiaryContainer | `#C2E8FD` | `#264B5C` |
| onTertiaryContainer | `#264B5C` | `#C2E8FD` |
| error | `#BA1A1A` | `#FFB4AB` |
| onError | `#FFFFFF` | `#690005` |
| errorContainer | `#FFDAD6` | `#93000A` |
| onErrorContainer | `#93000A` | `#FFDAD6` |
| surface / background | `#F5FBF5` | `#0F1512` |
| onSurface / onBackground | `#171D1A` | `#DEE4DF` |
| surfaceVariant | `#DEE4DF` | `#404944` |
| onSurfaceVariant | `#404944` | `#BFC9C2` |
| outline | `#707974` | `#89938D` |
| outlineVariant | `#BFC9C2` | `#404944` |
| scrim / shadow | `#000000` | `#000000` |
| inverseSurface | `#2C322E` | `#DEE4DF` |
| inverseOnSurface | `#ECF2ED` | `#2C322E` |
| inversePrimary | `#8BD6B6` | `#1B6B51` |
| surfaceTint | `#1B6B51` | `#8BD6B6` |
| surfaceDim | `#D6DBD6` | `#0F1512` |
| surfaceBright | `#F5FBF5` | `#343B37` |
| surfaceContainerLowest | `#FFFFFF` | `#0A0F0D` |
| surfaceContainerLow | `#EFF5F0` | `#171D1A` |
| surfaceContainer | `#E9EFEA` | `#1B211E` |
| surfaceContainerHigh | `#E4EAE4` | `#252B28` |
| surfaceContainerHighest | `#DEE4DF` | `#303633` |

Extended colours (CompositionLocal `ExtendedColors`): `warning` light `#FB8C00` / dark `#FFB74D`; `qiblaAligned` `#2E7D32` / `#81C784`; `qiblaFar` `#C62828` / `#EF9A9A`.

System bars: edge-to-edge (`enableEdgeToEdge()`), transparent bars, light/dark icons follow theme.

### 13.2 Typography

Bundle the variable fonts from `assets/fonts/` into `res/font/`:

- `inter_variable.ttf` ← `Inter-VariableFont_opsz,wght.ttf`, `inter_italic_variable.ttf` ← `Inter-Italic-VariableFont_opsz,wght.ttf`
- `noto_serif_variable.ttf` ← `NotoSerif-VariableFont_wdth,wght.ttf`, `noto_serif_italic_variable.ttf` ← `NotoSerif-Italic-VariableFont_wdth,wght.ttf`

Define `FontFamily(Font(R.font.inter_variable, FontWeight.W400, variationSettings = FontVariation.Settings(FontVariation.weight(400))), …)` for weights 400, 500, 600, 700 (API 26+ supports variation settings). (Flutter fixed `wght` via `fontVariations`, so `fontWeight: w600` overrides silently had no effect — BUG-47; native must map each weight to its variation.)

| Style | Family | Size / line height (sp) | Weight |
|---|---|---|---|
| displayLarge | Noto Serif | 57 / 64 | 400 |
| displayMedium | Noto Serif | 45 / 52 | 400 |
| displaySmall | Noto Serif | 36 / 44 | 400 |
| headlineLarge | Noto Serif | 32 / 40 | 400 |
| headlineMedium | Noto Serif | 28 / 36 | 400 |
| headlineSmall | Noto Serif | 24 / 32 | 400 |
| titleLarge | Inter | 22 / 28 | 400 |
| titleMedium | Inter | 16 / 24 | 500 |
| titleSmall | Inter | 14 / 20 | 500 |
| bodyLarge | Inter | 16 / 24 | 400 |
| bodyMedium | Inter | 14 / 20 | 400 |
| bodySmall | Inter | 12 / 16 | 400 |
| labelLarge | Inter | 14 / 20 | 500 |
| labelMedium | Inter | 12 / 16 | 500 |
| labelSmall | Inter | 11 / 16 | 500 |

Numbers (times, countdown, coordinates): `fontFeatureSettings = "tnum"`.

### 13.3 Tokens

```kotlin
object Spacing { val xxs = 2.dp; val xs = 4.dp; val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 20.dp; val xxl = 24.dp; val xxxl = 32.dp; val huge = 48.dp }
object Radius  { val xs = 4.dp; val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 24.dp; val xxl = 32.dp }
object Motion {
    const val short1 = 50; const val short2 = 100; const val short3 = 150; const val short4 = 200
    const val medium1 = 250; const val medium2 = 300; const val medium3 = 350; const val medium4 = 400
    const val long1 = 450; const val long2 = 500; const val long3 = 550; const val long4 = 600
    val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val standardDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)
    val standardAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)
}
```

M3 `Shapes`: extraSmall 4, small 8, medium 12, large 16, extraLarge 24.

### 13.4 Icons and assets

- Launcher icon: copy `android/app/src/main/res/mipmap-*`, `mipmap-anydpi-v26/ic_launcher.xml`, `drawable-*/ic_launcher_{background,foreground,monochrome}.png` from this repo (adaptive + themed icon).
- Splash: `Theme.SplashScreen` with `windowSplashScreenBackground = #F5FBF5` (night `#0F1512`), animated icon = `ic_launcher_foreground`.
- Notification small icon `ic_stat_adhan`: new monochrome vector (crescent, 24 dp, white on transparent). Flutter used the full-colour launcher icon (`@mipmap/ic_launcher`), which renders as a white blob (BUG-48).
- Widget preview `drawable-nodpi/widget_preview.png` (re-render from the new widget).
- `assets/icon/logo.svg` → convert to `res/drawable/ic_logo.xml` (vector) for the widget and About.
- `res/raw/adhan_makkah.mp3` (D-12).

---

## 14. Strings (`res/values/strings.xml`)

All user-visible text MUST come from resources. Plurals via `<plurals>`. This is the complete English copy (corrected where noted).

```xml
<resources>
    <string name="app_name">Adhan</string>

    <!-- Navigation -->
    <string name="nav_home">Home</string>
    <string name="nav_qibla">Qibla</string>
    <string name="nav_more">More</string>

    <!-- Home -->
    <string name="greeting_morning">Good morning</string>
    <string name="greeting_afternoon">Good afternoon</string>
    <string name="greeting_evening">Good evening</string>
    <string name="greeting_night">Peaceful night</string>
    <string name="home_up_next">Up next</string>
    <string name="home_remaining">remaining</string>
    <string name="home_tomorrow">tomorrow</string>
    <string name="home_refresh">Refresh prayer times</string>
    <string name="home_refresh_success">Prayer times refreshed</string>
    <string name="home_refresh_failure">Couldn\'t refresh. Pull down to retry.</string>
    <string name="home_stale_banner">Showing saved times — couldn\'t refresh.</string>
    <string name="home_zone_hint">Times shown in %1$s time</string>
    <string name="home_sunrise">Sunrise %1$s</string>
    <string name="home_quick_qibla">Qibla</string>
    <string name="home_quick_notifications">Notifications</string>
    <string name="home_quick_location">Location</string>
    <string name="home_hijri_approx">%1$s (approx.)</string>
    <string name="home_set_location_title">Set your location</string>
    <string name="home_set_location_body">Allow location access or choose a city to see prayer times.</string>
    <string name="home_next_prayer_cd">Next prayer %1$s at %2$s, in %3$s.</string>
    <string name="location_chip_placeholder">Set location</string>

    <!-- Urgency (accessibility) -->
    <string name="urgency_green">plenty of time</string>
    <string name="urgency_yellow">getting late</string>
    <string name="urgency_red">ending soon</string>
    <string name="window_cd">%1$s window, %2$s</string>

    <!-- Errors -->
    <string name="error_location_generic">Couldn\'t get your location</string>
    <string name="error_location_off">Location is turned off</string>
    <string name="error_location_denied">Location permission is needed</string>
    <string name="error_location_denied_forever">Location permission is blocked. You can allow it in Settings or choose a city.</string>
    <string name="error_location_timeout">Couldn\'t get a GPS fix. Try again outdoors or choose a city.</string>
    <string name="error_no_network">No internet connection</string>
    <string name="error_server">The prayer-times server isn\'t responding. Try again later.</string>
    <string name="error_bad_data">Received prayer times look wrong. Try again later.</string>
    <string name="error_prayer_times_generic">Couldn\'t load prayer times</string>
    <string name="error_geocoder_unavailable">City search isn\'t available on this device.</string>
    <string name="action_try_again">Try again</string>
    <string name="action_choose_city">Choose city</string>
    <string name="action_allow_location">Allow location</string>
    <string name="action_turn_on">Turn on</string>
    <string name="action_open_settings">Open settings</string>

    <!-- Qibla -->
    <string name="qibla_title">Qibla compass</string>
    <string name="qibla_calibration_title">Calibration tips</string>
    <string name="qibla_calibration_needed">Compass needs calibration</string>
    <string name="qibla_calibration_body">Move your phone in a figure-8, away from metal or magnets, and hold it flat. Tilt: %1$d°</string>
    <string name="qibla_heading">Compass heading</string>
    <string name="qibla_accuracy">Compass accuracy</string>
    <string name="qibla_direction">Qibla direction</string>
    <string name="qibla_distance">Distance to Kaaba</string>
    <string name="qibla_distance_value">%1$s km</string>
    <string name="qibla_accuracy_excellent">Excellent</string>
    <string name="qibla_accuracy_good">Good</string>
    <string name="qibla_accuracy_fair">Fair</string>
    <string name="qibla_accuracy_poor">Poor</string>
    <string name="qibla_no_compass_title">Compass not available</string>
    <string name="qibla_no_compass_body">This device has no compass sensor. Use the map below to find the Qibla direction.</string>
    <string name="qibla_map_attribution">© OpenStreetMap contributors</string>
    <string name="qibla_aligned_cd">Facing the Qibla</string>
    <string name="qibla_turn_cd">Turn %1$s %2$d degrees to face the Qibla</string>
    <string name="qibla_left">left</string>
    <string name="qibla_right">right</string>

    <!-- More -->
    <string name="more_title">More</string>
    <string name="more_section_prayer">Prayer</string>
    <string name="more_section_appearance">Appearance</string>
    <string name="more_section_advanced">Advanced</string>
    <string name="more_section_about">About</string>
    <string name="more_notifications">Notifications</string>
    <string name="more_notifications_subtitle">Manage prayer time alerts</string>
    <string name="more_location">Location</string>
    <string name="more_location_subtitle">Auto-detect or set a city manually</string>
    <string name="more_theme">Theme</string>
    <string name="more_advanced">Advanced</string>
    <string name="more_advanced_subtitle">Battery, alarms, cached data</string>
    <string name="more_about">About this app</string>
    <string name="about_version">Version %1$s (%2$d)</string>
    <string name="about_source">Prayer times provided by alislam.org.</string>
    <string name="about_map">Map data © OpenStreetMap contributors.</string>
    <string name="about_copyright">© %1$d Adhan</string>
    <string name="about_licenses">Licenses</string>

    <!-- Appearance -->
    <string name="appearance_title">Appearance</string>
    <string name="theme_system">System</string>
    <string name="theme_light">Light</string>
    <string name="theme_dark">Dark</string>
    <string name="language">Language</string>
    <string name="language_english">English</string>
    <string name="language_more_soon">More languages coming soon</string>

    <!-- Notifications -->
    <string name="notifications_title">Notifications</string>
    <string name="notif_banner_blocked">Notifications are off. Turn them on to hear the adhan.</string>
    <string name="notif_banner_exact">Allow alarms so the adhan plays exactly on time.</string>
    <string name="notif_banner_battery">Battery optimisation may delay the adhan.</string>
    <string name="notif_banner_channel_blocked">%1$s alerts are blocked in system settings.</string>
    <string name="action_enable">Enable</string>
    <string name="action_allow">Allow</string>
    <string name="action_fix">Fix</string>
    <string name="action_open">Open</string>
    <string name="notif_reminder_suffix"> • reminder %1$d min before</string>
    <string name="days_every_day">Every day</string>
    <string name="days_weekdays">Weekdays</string>
    <string name="days_weekends">Weekends</string>
    <string name="editor_enable">Adhan for %1$s</string>
    <string name="editor_days">Days</string>
    <string name="editor_keep_one_day">Keep at least one day selected.</string>
    <string name="editor_sound">Sound</string>
    <string name="editor_sound_title">%1$s — Sound</string>
    <string name="editor_reminder">Reminder</string>
    <string name="editor_reminder_off">Off</string>
    <string name="editor_reminder_value">%1$d min</string>
    <string name="editor_reminder_help">You\'ll get a reminder %1$d minutes before %2$s; the adhan still plays on time.</string>
    <string name="editor_test">Play test adhan</string>
    <string name="editor_test_scheduled">Test adhan in 5 seconds</string>
    <string name="editor_test_blocked">Turn on notifications to test.</string>
    <string name="sound_download_failed">Couldn\'t download %1$s. It will be retried when you\'re online.</string>
    <string name="sound_playing">Playing %1$s</string>
    <string name="action_stop">Stop</string>

    <!-- Notification content -->
    <string name="notif_adhan_title">%1$s</string>
    <string name="notif_adhan_body">It\'s time for %1$s prayer.</string>
    <string name="notif_missed_body">Missed: %1$s was at %2$s.</string>
    <string name="notif_reminder_title">%1$s in %2$d min</string>
    <string name="notif_reminder_body">%1$s begins at %2$s.</string>
    <string name="notif_test_title">Test — %1$s</string>
    <string name="notif_test_body">This is how your %1$s adhan will sound.</string>
    <string name="notif_action_stop">Stop adhan</string>
    <string name="channel_adhan_name">%1$s adhan</string>
    <string name="channel_adhan_desc">Adhan at %1$s time</string>
    <string name="channel_group_prayers">Prayer times</string>
    <string name="channel_reminder_name">Prayer reminders</string>
    <string name="channel_playback_name">Adhan playing</string>

    <!-- Location -->
    <string name="location_title">Location</string>
    <string name="location_current">Current location</string>
    <string name="location_use_current">Use current location</string>
    <string name="location_auto">Auto-detect location</string>
    <string name="location_auto_on">Using GPS to find your location</string>
    <string name="location_auto_off">Using a manually set city</string>
    <string name="location_search">Search city</string>
    <string name="location_search_hint">e.g. Mecca, London, Karachi</string>
    <string name="location_search_empty">No matches found</string>
    <string name="location_search_error">Couldn\'t search. Check your connection.</string>
    <string name="location_last_updated">Last updated: %1$s</string>
    <string name="location_source_gps">GPS</string>
    <string name="location_source_manual">Manual</string>
    <string name="location_set_to">Location set to %1$s</string>
    <string name="location_rationale">Location is used only to find prayer times and the Qibla for where you are. You can also choose a city instead.</string>
    <string name="action_continue">Continue</string>

    <!-- Onboarding -->
    <string name="onb_skip">Skip</string>
    <string name="onb_next">Next</string>
    <string name="onb_welcome_title">Welcome to Adhan</string>
    <string name="onb_welcome_body">Accurate prayer times, Qibla direction, and gentle reminders — wherever you are.</string>
    <string name="onb_location_title">Allow location</string>
    <string name="onb_location_body">We use your location to get accurate prayer times and the Qibla direction. Your coordinates are sent to alislam.org only to fetch your timetable.</string>
    <string name="onb_location_cta">Allow location</string>
    <string name="onb_choose_city">Choose city instead</string>
    <string name="onb_notif_title">Allow notifications</string>
    <string name="onb_notif_body">Hear the adhan at each prayer time. You can choose the sound, reminders and days for every prayer.</string>
    <string name="onb_notif_cta">Allow notifications</string>

    <!-- Battery / exact alarm -->
    <string name="battery_title">Keep the adhan on time</string>
    <string name="battery_body">Some phones stop apps in the background. For reliable adhan:</string>
    <string name="battery_step1">1. Turn off battery optimisation for Adhan.</string>
    <string name="battery_step2">2. Allow background activity / autostart (on some phones).</string>
    <string name="battery_step3">3. Lock Adhan in recent apps (Xiaomi, Huawei).</string>
    <string name="battery_status_on">Battery optimisation: On</string>
    <string name="battery_status_off">Battery optimisation: Off</string>
    <string name="battery_turn_off">Turn off battery optimisation</string>
    <string name="battery_open_settings">Open battery settings</string>
    <string name="battery_oem_autostart">Open Autostart</string>
    <string name="battery_oem_saver">Open battery saver</string>
    <string name="battery_oem_launch">Open app launch settings</string>
    <string name="battery_oem_sleeping">Open sleeping apps</string>
    <string name="battery_oem_autolaunch">Allow auto-launch</string>
    <string name="battery_oem_background">Allow background activity</string>
    <string name="exact_title">Allow adhan alarms</string>
    <string name="exact_body">To play the adhan exactly on time, Adhan needs permission to set alarms.</string>
    <string name="action_not_now">Not now</string>

    <!-- Advanced -->
    <string name="advanced_title">Advanced</string>
    <string name="adv_battery">Battery optimisation</string>
    <string name="adv_battery_ok">Not restricted — alarms will be reliable</string>
    <string name="adv_battery_bad">Restricted — the adhan may be late</string>
    <string name="adv_exact">Exact alarms</string>
    <string name="adv_exact_ok">Allowed</string>
    <string name="adv_exact_bad">Not allowed — tap to allow</string>
    <string name="adv_scheduled">Scheduled alarms</string>
    <string name="adv_scheduled_none">No alarms scheduled</string>
    <string name="adv_cache">Cached data</string>
    <string name="adv_cache_days">Saved days: %1$d</string>
    <string name="adv_cache_locations">Locations: %1$d</string>
    <string name="adv_cache_sounds">Downloaded sounds: %1$d (%2$s)</string>
    <string name="adv_cache_last_sync">Last sync: %1$s</string>
    <string name="adv_refresh_now">Refresh now</string>
    <string name="adv_clear_times">Clear saved times</string>
    <string name="adv_clear_times_confirm">Delete all saved prayer times? They will be downloaded again.</string>
    <string name="adv_delete_sounds">Delete downloaded sounds</string>
    <string name="adv_delete_sounds_confirm">Delete downloaded adhan sounds that aren\'t in use?</string>

    <!-- Update -->
    <string name="update_required_title">Update required</string>
    <string name="update_required_body">A required update is available. Please update to keep using Adhan.</string>
    <string name="update_version">Version %1$s</string>
    <string name="update_now">Update now</string>
    <string name="update_required_help">Install the update, then reopen the app.</string>
    <string name="update_required_footer">This update is required for reliability and security.</string>
    <string name="update_available_title">Update available</string>
    <string name="update_available_body">Version %1$s is available with fixes and improvements.</string>
    <string name="action_later">Later</string>
    <string name="action_update">Update</string>

    <!-- Widget -->
    <string name="widget_description">Shows the next prayer time</string>
    <string name="widget_placeholder">Open Adhan to load prayer times</string>
    <string name="widget_now">now</string>
    <string name="widget_in_min">in %1$d min</string>
    <string name="widget_in_h">in %1$d h</string>
    <string name="widget_in_h_min">in %1$d h %2$d min</string>

    <!-- Common -->
    <string name="action_close">Close</string>
    <string name="action_ok">OK</string>
    <string name="action_cancel">Cancel</string>
    <string name="action_delete">Delete</string>
    <string name="action_refresh">Refresh</string>
    <string name="action_back">Back</string>

    <!-- Hijri months -->
    <string-array name="hijri_months">
        <item>Muharram</item><item>Safar</item><item>Rabi\' al-Awwal</item><item>Rabi\' al-Thani</item>
        <item>Jumada al-Ula</item><item>Jumada al-Akhirah</item><item>Rajab</item><item>Sha\'ban</item>
        <item>Ramadan</item><item>Shawwal</item><item>Dhu al-Qi\'dah</item><item>Dhu al-Hijjah</item>
    </string-array>
</resources>
```

Prayer display names (Fajr, Sunrise, Zuhr, Asr, Maghrib, Isha) are proper nouns and come from `PrayerName.displayName`; also add them as `prayer_fajr` … resources for future translation and use the resources in UI.

---

## 15. Non-functional requirements

### 15.1 Reliability

- The adhan MUST fire within **±60 s** of the prayer time on a stock Android 14/15 device in Doze with battery optimisation ON, as long as exact alarms are allowed (setAlarmClock).
- After reboot without opening the app, the next prayer's adhan MUST fire (BOOT_COMPLETED → reschedule from Room, no network needed because today+tomorrow are cached).
- Clearing the app from recents MUST NOT stop alarms.
- No ANRs: all disk/network off the main thread (StrictMode enabled in debug: `detectAll().penaltyLog()`).
- Crash-free: every receiver/worker/service entry point wraps its body in try/catch and logs; a failure in one prayer must not stop others being scheduled.

### 15.2 Performance

- Cold start to first meaningful Home frame (cached data) ≤ 1.5 s on a mid-range device; splash covers DataStore read only.
- Home recomposition: only the countdown recomposes every second (verify with Layout Inspector recomposition counts).
- Compass: sensor → frame latency < 50 ms; no allocations per sensor event in the hot path.
- APK size target ≤ 12 MB (fonts ~1.5 MB, Makkah MP3 0.4 MB).

### 15.3 Privacy & security

- Network calls: alislam.org (coords rounded to 2 decimals), raw.githubusercontent.com (sound files), OSM tiles (tile coordinates reveal approximate area), Firebase Remote Config. Nothing else. No analytics.
- `android:allowBackup="true"` with `dataExtractionRules`/`fullBackupContent` that **exclude** `filesDir/adhan/` (re-downloadable) and include DataStore. (Restored prefs + reschedule on first launch.)
- `usesCleartextTraffic="false"`; network security config default (HTTPS only).
- All `PendingIntent`s `FLAG_IMMUTABLE`; all components `exported="false"` except the launcher activity, the widget receiver, and `SystemEventsReceiver` (needs export for some system broadcasts; protect by checking `intent.action` against the allowed list).
- No `WRITE_EXTERNAL_STORAGE` (BUG-49), no `HIGH_SAMPLING_RATE_SENSORS` (not needed for `SENSOR_DELAY_UI`), no `ACCESS_BACKGROUND_LOCATION` (D-10), no `WAKE_LOCK` unless the chosen player needs it (ExoPlayer `setWakeMode` does — keep `WAKE_LOCK`).

### 15.4 Accessibility

- All icon buttons have `contentDescription`.
- Touch targets ≥ 48 dp.
- Supports font scale up to 200%: hero and chips wrap/scale (chips grow in height; the timeline row scrolls).
- The 1-second countdown is **not** announced every second: mark it `clearAndSetSemantics {}` and let the hero card's merged description (updated per minute) carry the information.
- Qibla: a live-region text "Turn left 23 degrees to face the Qibla" / "Facing the Qibla", announced at most every 3 s and only when the value changes by ≥ 5°.
- Colour is never the only signal: urgency bar has a text equivalent in semantics; Qibla needle colour is backed by the angle text.
- Layouts mirror correctly in RTL (`start`/`end`, no hard-coded left/right), even though only English ships.

### 15.5 Logging

`Timber` (or a small `Log` wrapper) — debug tree in debug builds only; release logs warnings/errors only, never coordinates or response bodies.

---

## 16. Android manifest (complete)

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />  <!-- see §10.8 -->
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.VIBRATE" />

    <uses-feature android:name="android.hardware.sensor.compass" android:required="false" />
    <uses-feature android:name="android.hardware.sensor.accelerometer" android:required="false" />
    <uses-feature android:name="android.hardware.location.gps" android:required="false" />

    <!-- Remove legacy permissions that merged libraries might add -->
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" tools:node="remove" />

    <application
        android:name=".AdhanApp"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Adhan.Splash"
        android:localeConfig="@xml/locales_config"
        android:enableOnBackInvokedCallback="true">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:scheme="adhan" android:host="home" />
            </intent-filter>
        </activity>

        <receiver android:name=".alarm.AdhanAlarmReceiver" android:exported="false" />

        <receiver android:name=".alarm.SystemEventsReceiver" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
                <action android:name="android.intent.action.TIME_SET" />
                <action android:name="android.intent.action.TIMEZONE_CHANGED" />
                <action android:name="android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" />
                <action android:name="android.intent.action.QUICKBOOT_POWERON" />
                <action android:name="com.htc.intent.action.QUICKBOOT_POWERON" />
            </intent-filter>
        </receiver>

        <service
            android:name=".alarm.AdhanPlaybackService"
            android:exported="false"
            android:foregroundServiceType="mediaPlayback" />

        <!-- Keep the legacy class name so existing home-screen widgets survive the update -->
        <receiver android:name=".AdhanHomeWidget" android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data android:name="android.appwidget.provider" android:resource="@xml/adhan_home_widget_info" />
        </receiver>

        <!-- Hilt WorkManager: disable default initializer -->
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data android:name="androidx.work.WorkManagerInitializer" android:value="androidx.startup" tools:node="remove" />
        </provider>
    </application>
</manifest>
```

Notes:

- `WIDGET_TICK` alarms target `AdhanAlarmReceiver` with a distinct action (or a small `WidgetTickReceiver`, not exported).
- No `FileProvider` is needed any more (sounds are played by the app itself). Keep one only if you add "share" features (none planned).
- `locales_config.xml` lists `en` only.

---

## 17. Defect register — issues in the Flutter app and their required fixes

Every item below was found by reading the Flutter source (paths are relative to the repo root) and, where noted, by calling the live API. **Severity**: Critical = user-visible wrong time / missed adhan; High = feature broken; Medium = wrong but recoverable; Low = polish or hygiene. The **Fix** column is what the native app MUST do (the referenced section has the details).

### 17.1 Prayer times and data

| ID | Sev | Where (Flutter) | Problem | Fix (native) |
|---|---|---|---|---|
| BUG-01 | High | `lib/providers/prayer_timing_provider.dart:206-210, 325-329` | Today's prayers are picked by array **index** (`timing[0]`, `[2]`, `[3]`, `[5]`, `[6]`) assuming the API order Fajr, Sunrise, Zuhr, Asr, Sunset, Maghrib, Isha. Any change in API order or a missing entry shows wrong prayers or crashes. Tomorrow's Fajr is also `prayers[0]`. | Map by `PrayerName.fromApi(name)` (§4.1, §5.1.4). Never index. |
| BUG-02 | Critical | `lib/services/home_widget_service.dart:90,110,124` | Widget looks up the DB with `location.lat.toString()` (e.g. `"12.9"`) while everything else stores `toStringAsFixed(2)` (`"12.90"`). For any coordinate whose 2nd decimal is 0 — and for fresh GPS fixes (full precision) — every lookup misses, so the widget calls the API on **every refresh (once a minute in foreground)** and stores duplicate rows under a different key, with timezone `"UTC"`. | One `locationKey()` function used everywhere (§4.3). Widget never hits the network (§12.2). |
| BUG-03 | Critical | `lib/services/prayer_database_service.dart:111` | Rows are keyed by `dayTiming.date.day` in device-local time. Verified against the live API: `date` carries the server's current time-of-day, so its calendar date in a given zone depends on when the request was made, and on the device's zone vs. the location's zone. Result: day N's times can be stored as day N±1; the scheduler and widget (which read by date) then use the wrong day, and the last/first day of a month can be missing. | Ignore `date`; use the array index + requested month, cross-checked with Fajr's local date in the API timezone (§5.1.4). |
| BUG-04 | High | `prayer_timing_provider.dart:195,314` | On the last day of a month, "tomorrow" is `null` because only the current month is loaded, so after Isha the hero shows nothing / the window can't compute until the next month is cached. | Always load yesterday/today/tomorrow across month boundaries; prefetch next month when ≤ 7 days left (§6.3, §7.1). |
| BUG-14 | Medium | `PrayerTimeMonthNotifier.build` | The month provider is built once for `DateTime.now().month`; if the app stays open across a month change it keeps showing the old month (`multiDayTimings[now.day - 1]` then indexes the wrong month). | Derive dates per tick from location-local "today" (§7.1). |
| BUG-21 | Medium | `lib/services/prayer_data_manager.dart:31,227-240` | A cached month counts as valid if **any** day is in the future, so a partially stored month is never refetched. | Track completeness in `cached_month.day_count` (§6.3 rule 1). |
| BUG-22 | Medium | `lib/providers/prayer_timing_provider.dart:51-63`, `lib/providers/storage_provider.dart` | Two caches: SQLite plus Riverpod's JSON persistence in `riverpod.db`, keyed by provider arguments (method/school) and **not by location**. After changing location, the old city's times can flash on next launch; "Clear all data" in Advanced doesn't clear this second cache. | Room is the only cache (§3.1); clearing data immediately refetches (§8.10). |
| BUG-23 | Medium | `prayer_database_service.dart: clearOldData` | Weekly cleanup deletes rows whose `created_at` is older than 90 days — this can delete **future** months that were prefetched long ago and keeps stale past days that were fetched recently. | Clean by calendar date and location age (§6.3 rule 6). |
| BUG-24 | High | `lib/screens/location_screen.dart:87`, `prayer_data_manager.dart:246-248` | A searched city is stored with the **device** timezone; `_getTimezone()` returns hard-coded `"UTC"` on one fetch path. Displayed times are in device time even for a city abroad. | Use the API's `locationInfo.timezone`; display in the location zone with a hint (D-06, §5.1.4). |
| BUG-43 | Low | `lib/api/api_helper.dart:22` | `LogInterceptor(responseBody: true)` logs full API responses (with coordinates) in release builds. | Debug-only BASIC logging (§5.1.1). |
| BUG-64 | Low | `lib/services/location_storage_service.dart:24-27`, `lib/providers/app_providers.dart:33-36` | Only 2-decimal coordinates are stored, and the app then uses those rounded values for Qibla and shows them with 4 decimals ("51.5100°"), implying precision that isn't there. | Store full precision; use 2 decimals only for the cache key/API (§4.3, §4.4). |

### 17.2 Home screen and prayer window

| ID | Sev | Where (Flutter) | Problem | Fix (native) |
|---|---|---|---|---|
| BUG-06 | High | `prayer_timing_provider.dart:182-222, 301-434` | The timeline/next-prayer streams capture "today" once and loop forever. Leave the app open past midnight and the hero goes blank after the next Fajr until the app restarts. Greeting and Gregorian/Hijri footer also never update while open. | Recompute today on every tick; boundary-aligned ticks (§7.1, §8.3 Ticking). |
| BUG-11 | Low | `prayer_timing_provider.dart:436-490` vs `492-560` | Urgency colour uses `progress <= 0.33` and `remaining.inMinutes <= 10` (floored), while the bar segments use `1/3` and exactly 600 s, so for up to a minute the badge colour and bar disagree. | One rule set (§7.4). |
| BUG-12 | High | `prayer_timing_provider.dart:451-453` | Fajr's window runs to **Zuhr**; the code comment says "till 10 min before sunrise". Users see Fajr as "current/green-yellow" for hours after sunrise, when Fajr can no longer be prayed. | Fajr ends at Sunrise; Sunrise→Zuhr is neutral (D-02, §7.4). |
| BUG-13 | Medium | `prayer_timing_provider.dart:390-419` | Before today's Fajr, a synthetic "Isha starting now" window with null progress is shown (empty bar, fixed segments unrelated to real times). | Use yesterday's Isha → today's Fajr (D-08). |
| BUG-28 | Medium | `home_screen.dart:194-203`, `location_screen.dart` etc. | Errors are shown with `error.toString()` (e.g. "Exception: Permissions denied"). Permanently-denied permission isn't distinguished and offers only "Try again". | Typed `AppError` → friendly copy + right action (§3.4, §14, §9.4). |
| BUG-42 | Low | `home_screen.dart:647` | Qibla quick action `push`es the Qibla shell route on top of Home, creating a duplicate stack entry. | Switch tab (§8.1). |
| BUG-51 | Low | `prayer_timing_provider.dart:567-582`, `home_widget_service.dart:22-33`, `main.dart:58-66` | Three overlapping widget updaters (provider listener every minute, a 1-minute `Timer`, plus a delayed update at +3 s) — combined with BUG-02 this means repeated API calls. | Single `WidgetUpdater` with defined triggers (§12.3). |

### 17.3 Notifications, alarms and background

| ID | Sev | Where (Flutter) | Problem | Fix (native) |
|---|---|---|---|---|
| BUG-05 | Critical | `lib/services/notification_service.dart:171` | Alarms use `AndroidScheduleMode.exact` → `AlarmManager.setExact`, which is **deferred while the device is in Doze**. Fajr (phone idle overnight) is the most affected. | `setAlarmClock` for adhan, `setExactAndAllowWhileIdle` for reminders (§10.4). |
| BUG-08 | Critical | `lib/services/background_prayer_service.dart:138-157`, manifest line 122 | Only **today's** alarms are scheduled; tomorrow's depend on a WorkManager one-off at midnight, which is inexact and often deferred for hours (Doze, OEM killers) → next-day Fajr frequently missed unless the app is opened. The `android_alarm_manager_plus` reboot receiver is `enabled="false"`. | Rolling 48 h horizon re-armed on every fire, boot, time change and daily sync (§10.3, §10.7). |
| BUG-09 | Critical | `lib/services/notification_preferences_service.dart:30` vs `104,114` | `getPrayerNotificationEnabled` defaults to **true**, but the getters the UI and scheduler actually use default to **false** → after onboarding (even with permission granted) **no adhan is ever scheduled** until the user discovers the Notifications screen. | Default ON (D-01); single defaults table (§6.1). |
| BUG-10 | High | `lib/services/daily_notification_scheduler.dart:54-102` | Weekday chips are saved but never read by the scheduler. | Honour days (D-05, §10.3 rule 4). |
| BUG-15 | High | `notification_service.dart:93-140`, `prayer_notification_editor_sheet.dart:426-458` | Android channel sound can't change after creation; the app "changes" sound by overwriting `files/adhan/{prayer}.mp3` behind a fixed `content://` URI. Fragile: depends on file presence and SystemUI reading it. | App-played audio via a foreground service; silent channels (D-03, §10.6). |
| BUG-16 | Critical | `prayer_notification_editor_sheet.dart:438` | The per-prayer sound file is only created when the user opens the sound picker or presses Test. Enabling a prayer with the default Makkah sound leaves `fajr.mp3` missing → notification plays nothing/system fallback. Choosing "Default Notification Sound" returns early and leaves the **previous** adhan file in place, so the old adhan keeps playing. | Bundled default (D-12) + `Missing` → bundled fallback (§5.2) + `SYSTEM_DEFAULT` handled explicitly (§10.6). |
| BUG-17 | High | `notification_service.dart:180-191`, manifest FileProvider | The channel sound is a `FileProvider` URI, but read permission is never granted to the system process that plays notification sounds (`grantUriPermission("com.android.systemui", …)`), so on many devices the sound silently fails. | No FileProvider; app plays audio itself (D-03). |
| BUG-18 | High | `daily_notification_scheduler.dart:84-96` | "Advance time" moves the **whole adhan** N minutes early and still says "It's time for X prayer." | Separate reminder + on-time adhan (D-04, §10.5). |
| BUG-19 | Medium | `notification_service.dart:193-205`, `lib/utils/sound_utils.dart:26-40` | Downloads write directly to the final path; existence is checked with `name.startsWith(baseName)`, so a truncated download is treated as valid forever. No size/type validation. | `.part` + atomic rename + validation (§5.2). |
| BUG-36 | Medium | `daily_notification_scheduler.dart:146-149, 128-141` | Notification IDs are `"$prayer-$date".hashCode & 0x7FFFFFFF` (collision-prone), and cancellation only covers **today's** IDs, so stale alarms from other days/locations can survive a location change. | Deterministic request codes + tracked key set (§10.4). |
| BUG-37 | Low | `prayer_notification_editor_sheet.dart:182-186` | The advance slider persists and enqueues a WorkManager reschedule on every drag tick. | Persist on `onValueChangeFinished` (§8.6.1). |
| BUG-38 | Low | `prayer_notification_editor_sheet.dart:257-266`, `daily_notification_scheduler.dart:105-139` | Test notification: snackbar "fires in 2s", the legacy test path uses 5 s; title shows the raw key ("Test — fajr"). | One test path, 5 s, display name (§8.6.1, §10.6). |
| BUG-46 | Low | `background_prayer_service.dart:35-176` | Widget refresh and midnight planning require `NetworkType.connected` ("to satisfy plugin requirement"), so offline devices never refresh the widget or plan alarms in background. | No network constraint for local work (§11). |
| BUG-48 | Low | `notification_service.dart:29-31` | Small icon is the full-colour launcher mipmap → renders as a white square on many devices. | Monochrome `ic_stat_adhan` (§13.4). |
| BUG-61 | Low | `notification_service.dart:84-88` | If a scheduled time is in the past it is silently moved **+1 day** but keeps today's ID and today's prayer time, producing an alarm for the wrong day's time. | Planner drops past times; scheduler never shifts (§10.3). |
| BUG-20 | Low | `lib/screens/notifications_screen.dart:58-80` | The whole Notifications list is replaced by a spinner/error while the month loads, so settings can't be changed offline on first run. | Prefs list never blocks on timetable data (§8.6). |

### 17.4 Location

| ID | Sev | Where (Flutter) | Problem | Fix (native) |
|---|---|---|---|---|
| BUG-07 | High | `lib/providers/app_providers.dart:29-41`, `location_screen.dart:108-113, 252-256` | `LocationNotifier.build()` returns the cached location whenever one exists ("trigger a background refresh" in the comment is never implemented). "Use current location", the auto-detect toggle, Home refresh and Qibla refresh all just re-read the cache → **GPS is never refreshed after the first fix**; travellers keep their old city's times forever. | Real refresh policy (§9.2). |
| BUG-25 | Low | `location_screen.dart:198-200` | "Last updated" prints the current time, not when the location was set. | `StoredLocation.updatedAt` (§7.10). |
| BUG-26 | Medium | `location_screen.dart:318-330` | Search results list only "lat, lng" with "Tap to use this location" — users can't tell which "Springfield" is which. | Place names (D-13, §8.7). |
| BUG-35 | Medium | `android/app/src/main/AndroidManifest.xml:9` | `ACCESS_BACKGROUND_LOCATION` is declared but never used; triggers Play policy review and a scary permission. | Remove (D-10, §16). |
| BUG-65 | Medium | `location_storage_service.dart:25-27` | `storeLocation` writes the city only when non-null and never clears it, so moving to a place without a locality keeps showing the **previous** city name. | Always write all fields (null clears) (§4.4). |

### 17.5 Qibla

| ID | Sev | Where (Flutter) | Problem | Fix (native) |
|---|---|---|---|---|
| BUG-39 | Medium | manifest lines 13-20 | `android.hardware.sensor.accelerometer` and `android.hardware.sensor.magnetometer` declared `required="true"`. The magnetometer feature's standard name is `android.hardware.sensor.compass`; requiring a non-standard feature may make Play treat devices as incompatible, and requiring sensors excludes compass-less devices from an app whose main purpose is prayer times. | `required="false"`, graceful no-compass UI (§8.4, §16). |
| BUG-40 | Medium | `lib/screens/qibla_compass_screen.dart`, `flutter_compass` | Heading is **magnetic** north, the Qibla bearing is **true** north; no declination correction (errors of 5–15° in many regions). No handling of display rotation. | Declination + `remapCoordinateSystem` (D-11, §8.4). |
| BUG-41 | Low | `lib/widgets/qibla_map_widget.dart:30-36, 55` | Map draws a straight Mercator line (not the great-circle Qibla path) and shows no OSM attribution (required by the tile policy). | Geodesic polyline + attribution + user agent (§5.5, §8.4). |
| BUG-50 | Medium | `qibla_map_widget.dart:22` | `useMemoized` (a hook) is called inside a plain `StatelessWidget`, which is invalid in flutter_hooks (assertion in debug, undefined in release). | N/A in native; noted so the behaviour isn't copied. |
| BUG-52 | Low | `lib/services/qibla_service.dart:59-66` | `isCompassAvailable()` only checks that a stream object exists, so devices without a compass get a spinner/zero heading instead of the "not available" message. | Check `SensorManager.getDefaultSensor` (§8.4 Sensors). |

### 17.6 App shell, dialogs, updates, settings

| ID | Sev | Where (Flutter) | Problem | Fix (native) |
|---|---|---|---|---|
| BUG-27 | Medium | `app_providers.dart:182-186` | Theme mode isn't persisted. | DataStore `theme_mode` (D-07). |
| BUG-29 | Medium | `lib/l10n/app_en.arb:88` | Onboarding says "Nothing leaves your device" but coordinates go to alislam.org and the platform geocoder. | Honest copy (§8.2). |
| BUG-30 | Medium | `lib/services/battery_optimization_service.dart:50` | Battery dialog shows only when the app is **already** exempt (`isIgnoringOptimizations && …`). | Show when still optimised (D-09). |
| BUG-31 | High | `lib/main.dart:108-109`, `lib/widgets/update_manager.dart:69`, `battery_optimization_service.dart:52` | `UpdateManager` and `BatteryOptimizationWrapper` sit in `MaterialApp.router(builder:)`, i.e. **above** the Navigator; their `showDialog(context)` calls can't find a Navigator, so the general-update dialog and first-run battery dialog fail (the battery one is swallowed by try/catch and then marked as shown). | Global overlays inside the composition (§8.1). |
| BUG-32 | High | `lib/services/update_service.dart:93-101` | `canLaunchUrl()` needs a `<queries>` entry for `https` on Android 11+; the manifest only has `PROCESS_TEXT`, so it returns false and **the Update button does nothing**, including on the blocking force-update screen (user is stuck). | Fire the intent with try/catch + store fallbacks (§5.4). |
| BUG-54 | Low | `lib/providers/update_provider.dart:13-17`, `update_manager.dart:21-29` | Update check runs twice (provider `build` and a post-frame call); the second call can read Remote Config before `fetchAndActivate` finishes and return "no update". | Single sequenced check after activation (§5.4). |
| BUG-44 | High (release) | `android/app/build.gradle.kts:41` | Release builds are signed with the **debug** key. The native update must be signed with the same key to install over existing installs; the debug keystore is machine-specific. | Locate the keystore that signed 1.0.x and use it via `keystore.properties`; plan a proper upload key for Play (§2.3). |
| BUG-45 | Low | `lib/providers/background_service_provider.dart:52-57` | Debug "Background service" status always returns `true` (placeholder); Start/Stop just cancel/register WorkManager. | Replaced by "Scheduled alarms" diagnostics (§8.10). |
| BUG-47 | Low | `lib/theme/theme.dart:386-462` | Text styles pin the variable-font `wght` axis, so `fontWeight: w600` overrides throughout the UI have no visible effect. | Map each weight to its variation (§13.2). |
| BUG-49 | Low | manifest | `WRITE_EXTERNAL_STORAGE` (maxSdk 28) and `HIGH_SAMPLING_RATE_SENSORS` are declared but unused. | Remove (§15.3). |
| BUG-33 | Medium | `android/app/src/main/java/org/tarish/adhan/AdhanHomeWidget.kt:56-64`, `home_widget_service.dart:146` | Widget country line is always `""`; the remaining time is computed into `combined` and never displayed. | Show both (D-14, §12.2). |
| BUG-34 | Low | `home_widget_service.dart:169-176` | Remaining uses `inMinutes.abs()`, so a stale widget shows "12 min" *after* the prayer passed. | Clamp at 0, ceil (§7.6). |

---

## 18. Testing requirements

### 18.1 Unit tests (JVM, MUST)

Use the fixture below (London, Europe/London, real API values) with `Clock` fixed to the listed instants.

```kotlin
val LONDON = ZoneId.of("Europe/London")
val NOV1 = mapOf(FAJR to 1793510640000, SUNRISE to 1793516040000, ZUHR to 1793533740000, ASR to 1793541900000,
                 SUNSET to 1793550840000, MAGHRIB to 1793550900000, ISHA to 1793556000000)   // 05:24 06:54 11:49 14:05 16:34 16:35 18:00
val NOV2 = mapOf(FAJR to 1793597160000, SUNRISE to 1793602560000, ZUHR to 1793620140000, ASR to 1793628180000,
                 SUNSET to 1793637120000, MAGHRIB to 1793637180000, ISHA to 1793642340000)   // 05:26 06:56 11:49 14:03 16:32 16:33 17:59
val NOV30 = mapOf(FAJR to 1796019120000, SUNRISE to 1796024520000, ZUHR to 1796039640000, ASR to 1796045760000,
                  SUNSET to 1796054160000, MAGHRIB to 1796054220000, ISHA to 1796059740000)
```

**Window / urgency / next prayer** (§7.3, §7.4):

| now (London) | epoch ms | Window | progress | urgency | Next salah | Countdown |
|---|---|---|---|---|---|---|
| Nov 1 05:40 | 1793511600000 | FAJR→SUNRISE | 0.1778 | GREEN | Zuhr 11:49 | 06:09:00 |
| Nov 1 06:00 | 1793512800000 | FAJR→SUNRISE | 0.4000 | YELLOW | Zuhr | 05:49:00 |
| Nov 1 06:46 | 1793515560000 | FAJR→SUNRISE | 0.9111 | RED (8 min left) | Zuhr | 05:03:00 |
| Nov 1 09:00 | 1793523600000 | SUNRISE→ZUHR | 0.4271 | NEUTRAL | Zuhr | 02:49:00 |
| Nov 1 12:30 | 1793536200000 | ZUHR→ASR | 0.3015 | GREEN | Asr 14:05 | 01:35:00 |
| Nov 1 13:00 | 1793538000000 | ZUHR→ASR | 0.5221 | YELLOW | Asr | 01:05:00 |
| Nov 1 16:40 | 1793551200000 | MAGHRIB→ISHA | 0.0588 | GREEN | Isha 18:00 | 01:20:00 |
| Nov 1 17:00 | 1793552400000 | MAGHRIB→ISHA | 0.2941 | YELLOW | Isha | 01:00:00 |
| Nov 1 17:52 | 1793555520000 | MAGHRIB→ISHA | 0.9059 | RED | Isha | 00:08:00 |
| Nov 1 20:00 | 1793563200000 | ISHA→FAJR(Nov 2) | 0.1749 | GREEN | Fajr 05:26 (tomorrow) | 09:26:00 |
| Nov 1 23:30 | 1793575800000 | ISHA→FAJR(Nov 2) | 0.4810 | YELLOW (after 23:00:30) | Fajr (tomorrow) | 05:56:00 |
| Nov 2 03:00 | 1793588400000 | ISHA(Nov 1)→FAJR(Nov 2) | 0.7872 | YELLOW | Fajr 05:26 (today) | 02:26:00 |

Segments:

- FAJR→SUNRISE (5400 s): `[0, 0.33333] GREEN`, `[0.33333, 0.88889] YELLOW`, `[0.88889, 1] RED`.
- ISHA(Nov 1)→FAJR(Nov 2) (41160 s): Islamic midnight = 1793574030000 (23:00:30); `[0, 0.43805] GREEN`, `[0.43805, 0.98542] YELLOW`, `[0.98542, 1] RED`.
- SUNRISE→ZUHR: `[0, 1] NEUTRAL`.

Edge cases (MUST test): exactly at a prayer instant (window switches, next advances); missing `tomorrow` at 20:00 → window null, next null; missing `yesterday` at 03:00 → window null, next = today's Fajr; month boundary (Nov 30 Isha → Dec 1 Fajr loaded from a second month); DST transitions (use `Europe/London` 2026-03-29 and 2026-10-25 synthetic days: windows computed on instants must stay positive and correct).

**Alarm planner** (§10.3): now = Nov 1 20:00 London; all five enabled; Fajr advance 15; Asr days = all except Monday (Nov 2 is a Monday). Expected, in order:

| Kind | Prayer | fireAt (London) | requestCode |
|---|---|---|---|
| REMINDER | Fajr | Nov 2 05:11 | 2075910 |
| ADHAN | Fajr | Nov 2 05:26 | 2075900 |
| ADHAN | Zuhr | Nov 2 11:49 | 2075902 |
| ADHAN | Maghrib | Nov 2 16:33 | 2075905 |
| ADHAN | Isha | Nov 2 17:59 | 2075906 |

(`epochDay(2026-11-02) = 20759`; Asr skipped by day filter; nothing left today.) Also: `notificationsAllowed = false` → empty; all disabled → empty; advance reminder already past but adhan future → only adhan.

**Other units:**

- `formatCoord`: `51.5074→"51.51"`, `-0.1278→"-0.13"`, `12.9→"12.90"`, `-0.001→"0.00"`, `0.005→"0.01"`, with `Locale.setDefault(Locale.GERMANY)` still `"51.51"`.
- DTO mapping (§5.1.4): real response fixture parses to 30 valid days; shuffled `prayers` order maps correctly; unknown name ignored; a day missing Isha is dropped; `date` field changed to a wrong day has **no effect**; invalid timezone falls back.
- Qibla table §7.7; `relativeAngle(10, 350) == 20`, `relativeAngle(350, 10) == -20`, `relativeAngle(180, 0) == -180 or 180` (document which; pick `-180`).
- Version compare: `("1.0.11","1.0.10")→true`, `("1.0.10","1.0.10")→false`, `("2","1.9.9")→true`, `("1.0","1.0.0")→false`, `("1.0.10-beta","1.0.9")→true`, `("","1.0.0")→false`, `("abc","1.0.0")→false`.
- Greeting boundaries: 04:59 night, 05:00 morning, 11:59 morning, 12:00 afternoon, 16:59 afternoon, 17:00 evening, 20:59 evening, 21:00 night.
- Widget remaining: 0 s → "now"; 59 s → "in 1 min"; 60 s → "in 1 min"; 61 s → "in 2 min"; 3600 s → "in 1 h"; 3900 s → "in 1 h 5 min"; −30 s → "now".
- Hijri: `2026-11-16` → `HijrahDate` with the expected day/month from `java.time` (assert against `HijrahDate.from(LocalDate.of(2026,11,16))` fields and month name array).
- Flutter migration: a fake `FlutterSharedPreferences` map with all keys → expected DataStore values (incl. URL→sound id mapping, `"Mon,Wed"`→`{1,3}`, missing enable key → true).

### 18.2 Integration / instrumented tests (SHOULD)

- Room DAO tests (in-memory).
- `PrayerTimesRepository` with `MockWebServer` serving the London fixture: cache-first, force refresh, failure keeps cache, per-month mutex (two concurrent calls → one request).
- `AlarmScheduler` with Robolectric `ShadowAlarmManager`: correct API chosen (setAlarmClock vs setExactAndAllowWhileIdle vs inexact), cancellation of removed keys.
- `SyncWorker` with `TestListenableWorkerBuilder`.
- Compose UI tests: onboarding skip → Home; Home shows hero with fixed clock + fake repo; Notifications toggle persists; sheet blocks deselecting the last day; Location search shows names (fake geocoder).

### 18.3 Manual test matrix (MUST before release)

| # | Scenario | Device/API |
|---|---|---|
| M1 | Fresh install, grant all, wait for next prayer with screen off ≥ 1 h (`adb shell dumpsys deviceidle force-idle`) — adhan plays on time | Pixel API 34/35 |
| M2 | Reboot, don't open app, next prayer plays | any |
| M3 | Deny exact alarms (API 34 fresh install without USE_EXACT_ALARM) → banner shows, adhan still plays (≤ 10 min late) | API 34 |
| M4 | Update over Flutter 1.0.10 install: prefs migrated, existing widget still on home screen, alarms rescheduled, no duplicate old channels | any |
| M5 | Airplane mode for 3 days → times still shown and alarms fire (cached months) | any |
| M6 | Manual city in another timezone (device Asia/Kolkata, city London) → times shown in London time with hint, alarms at correct absolute instants | any |
| M7 | Xiaomi/Samsung device with default battery settings — check battery sheet shortcuts open | OEM |
| M8 | No-compass emulator → Qibla shows message + map + bearing | emulator |
| M9 | Force update via Remote Config `force_update_version = 9.0.0` → blocking screen, button opens URL/Play | any |
| M10 | Font scale 200 %, TalkBack on Home and Qibla | any |

---

## 19. Acceptance criteria

The native build is accepted when all MUST items pass:

| ID | Criterion |
|---|---|
| AC-01 | First launch shows onboarding; Skip or finishing lands on Home; onboarding never shows again. |
| AC-02 | With GPS allowed, Home shows the 5 salah for today matching alislam.org for that location (spot-check against the API URL in §5.1.1). |
| AC-03 | Hero shows the next salah, its time, and a 1 s countdown accurate to wall clock; bar colours follow §7.4 including Fajr ending at Sunrise and the Isha midnight split; the screen rolls over correctly at local midnight while left open. |
| AC-04 | Pull-to-refresh and the refresh button refetch, update widget, show the success/failure snackbar; failure keeps cached data with the stale banner. |
| AC-05 | Manual city search shows place names; selecting one updates times, Qibla, widget; auto-detect turns off; "Use current location" performs a real new GPS fix. |
| AC-06 | Qibla bearing matches §7.7 within 0.1°; compass heading is true-north; dial rotates smoothly without 360° jumps; no-compass devices get the fallback UI. |
| AC-07 | After onboarding with notifications allowed, all five adhans are scheduled by default (Advanced → Scheduled alarms lists them). |
| AC-08 | Adhan plays at prayer time with the selected sound in Doze, with a Stop action; swiping the notification stops audio. |
| AC-09 | Advance 15 min → reminder notification at T−15 and adhan at T. |
| AC-10 | Unselecting a weekday prevents that prayer's adhan on that day only. |
| AC-11 | "Play test adhan" plays within ~5 s with the selected sound. |
| AC-12 | After reboot (app not opened), the next adhan fires. After `adb shell cmd alarm` time change or timezone change, alarms are rescheduled to the right instants. |
| AC-13 | Widget shows next prayer name, time, remaining, city and country; compact layout at small sizes; survives update from Flutter 1.0.10 without being re-added. |
| AC-14 | Remote Config force version > app version blocks the UI; the button opens the URL (or Play Store if empty); general version shows a dismissible dialog once. |
| AC-15 | Battery sheet appears once (only if optimised) and its buttons open the right settings on Pixel, Samsung and Xiaomi (or fall back to app details). |
| AC-16 | Offline with cached data: Home, Notifications, widget and alarms all work. |
| AC-17 | Theme System/Light/Dark persists across restarts; fonts are Inter + Noto Serif; colours match §13.1. |
| AC-18 | Flutter → native migration (§6.4) preserves onboarding state, location, per-prayer settings; old channels removed. |
| AC-19 | All unit tests in §18.1 pass. |
| AC-20 | No `ACCESS_BACKGROUND_LOCATION`, `WRITE_EXTERNAL_STORAGE` or `HIGH_SAMPLING_RATE_SENSORS` in the merged manifest (`./gradlew :app:processReleaseManifest` + inspect). |

---

## 20. Appendix

### 20.1 Key interfaces (signatures to implement)

```kotlin
interface Clock { fun now(): Instant; fun deviceZone(): ZoneId }

interface SettingsStore {
    val onboardingDone: Flow<Boolean>; suspend fun setOnboardingDone()
    val themeMode: Flow<ThemeMode>; suspend fun setThemeMode(m: ThemeMode)
    val autoLocation: Flow<Boolean>; suspend fun setAutoLocation(v: Boolean)
    val location: Flow<StoredLocation?>; suspend fun setLocation(l: StoredLocation?)
    val prayerPrefs: Flow<Map<PrayerName, PrayerNotificationPrefs>>
    suspend fun updatePrayerPrefs(p: PrayerName, transform: (PrayerNotificationPrefs) -> PrayerNotificationPrefs)
    // + flags from §6.1
}

interface LocationRepository {
    val current: Flow<StoredLocation?>
    suspend fun refreshFromGps(force: Boolean): Result<StoredLocation>     // §9.2
    suspend fun search(query: String): Result<List<PlaceResult>>          // §8.7
    suspend fun setManual(place: PlaceResult): StoredLocation
}
data class PlaceResult(val name: String, val region: String?, val country: String?, val lat: Double, val lng: Double)

interface SoundRepository {
    val catalog: List<AdhanSound>
    fun isAvailable(id: String): Boolean
    fun download(id: String): Flow<DownloadState>     // Progress(fraction) | Done | Failed(AppError)
    fun resolve(id: String): SoundSource
}

interface AlarmCoordinator { fun rescheduleAll(reason: String) }   // debounced, app-scoped
interface WidgetUpdater { suspend fun updateAll() }
interface UpdateRepository { suspend fun check(): UpdateInfo }       // UpdateInfo(type: NONE|GENERAL|FORCE, version, url)
```

### 20.2 What changed vs. the previous spec (`docs/NATIVE_REBUILD_SRS.md`)

- Verified the API against live responses: added `offset` field, `locationInfo.timezone` use, and proved the `date` field is unreliable (BUG-03).
- New defects: BUG-02, 03, 05, 06, 07, 11, 12, 15–19, 21–25, 31, 32, 36, 39–41, 44, 46–52, 54, 61, 64, 65. (IDs 53, 55–60, 62–63 are intentionally unused.)
- Replaced "keep channel-per-prayer + FileProvider sounds" with app-played audio (D-03).
- Replaced midnight WorkManager planning with a 48 h exact-alarm horizon (§10.3).
- Added Flutter→native migration (§6.4), full manifest, full strings, Room schema, test fixtures with expected values.

### 20.3 One-paragraph brief for a coding agent

> Build **Adhan** (`org.tarish.adhan`, versionCode 11, versionName 2.0.0) as a native Android app in Kotlin with Jetpack Compose, Material 3, Hilt, Room, DataStore, WorkManager, AlarmManager, Glance, Media3 and Firebase Remote Config, following `docs/ANDROID_COMPOSE_SRS.md` exactly. Times come only from the alislam.org monthly API (§5.1), mapped by prayer name and by array index for the date — never by the `date` field. Implement and unit-test the pure algorithms in §7 first using the fixtures in §18.1. Schedule adhans with `setAlarmClock` over a rolling 48-hour horizon and play audio from a `mediaPlayback` foreground service with a Stop action (§10). Implement every fix in §17 and every decision in §1.4. Migrate Flutter preferences on first run and keep the widget receiver class name `AdhanHomeWidget` (§6.4). Finish by walking through the acceptance criteria in §19.
