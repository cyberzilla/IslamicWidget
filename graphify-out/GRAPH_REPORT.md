# Graph Report - .  (2026-04-29)

## Corpus Check
- 74 files · ~113,373 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 616 nodes · 588 edges · 45 communities detected
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 8 edges (avg confidence: 0.82)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Astronomy Engine Core|Astronomy Engine Core]]
- [[_COMMUNITY_Main Activity & Settings UI|Main Activity & Settings UI]]
- [[_COMMUNITY_Islamic Astronomy & Prayer Calc|Islamic Astronomy & Prayer Calc]]
- [[_COMMUNITY_Documentation & Assets|Documentation & Assets]]
- [[_COMMUNITY_Adzan Logger & Diagnostics|Adzan Logger & Diagnostics]]
- [[_COMMUNITY_Islamic Widget Provider|Islamic Widget Provider]]
- [[_COMMUNITY_Moon Phase Renderer|Moon Phase Renderer]]
- [[_COMMUNITY_Terse Vector Math|Terse Vector Math]]
- [[_COMMUNITY_Vector Operations|Vector Operations]]
- [[_COMMUNITY_Adzan Playback Service|Adzan Playback Service]]
- [[_COMMUNITY_Lunar Context Calculator|Lunar Context Calculator]]
- [[_COMMUNITY_Gravity Simulator|Gravity Simulator]]
- [[_COMMUNITY_Qibla Compass Activity|Qibla Compass Activity]]
- [[_COMMUNITY_Time & Julian Date|Time & Julian Date]]
- [[_COMMUNITY_Islamic App Utilities|Islamic App Utilities]]
- [[_COMMUNITY_Developer Mode Helper|Developer Mode Helper]]
- [[_COMMUNITY_Hilal Info Activity|Hilal Info Activity]]
- [[_COMMUNITY_Lunar Widget Provider|Lunar Widget Provider]]
- [[_COMMUNITY_Quote Database Helper|Quote Database Helper]]
- [[_COMMUNITY_Quote Widget Provider|Quote Widget Provider]]
- [[_COMMUNITY_Update Receiver|Update Receiver]]
- [[_COMMUNITY_State Vector Math|State Vector Math]]
- [[_COMMUNITY_Icon Helper|Icon Helper]]
- [[_COMMUNITY_Silent Mode Receiver|Silent Mode Receiver]]
- [[_COMMUNITY_Settings Manager|Settings Manager]]
- [[_COMMUNITY_Rotation Matrix|Rotation Matrix]]
- [[_COMMUNITY_Body State|Body State]]
- [[_COMMUNITY_Audio Adzan Manager|Audio Adzan Manager]]
- [[_COMMUNITY_Compass Widget Provider|Compass Widget Provider]]
- [[_COMMUNITY_Update Helper|Update Helper]]
- [[_COMMUNITY_DateTime Utility|DateTime Utility]]
- [[_COMMUNITY_Boot Receiver|Boot Receiver]]
- [[_COMMUNITY_Quote Update Manager|Quote Update Manager]]
- [[_COMMUNITY_Fading Scroll View|Fading Scroll View]]
- [[_COMMUNITY_Widget Pin Receiver|Widget Pin Receiver]]
- [[_COMMUNITY_Spherical Coordinates|Spherical Coordinates]]
- [[_COMMUNITY_Observer Position|Observer Position]]
- [[_COMMUNITY_Pascal Array|Pascal Array]]
- [[_COMMUNITY_Major Bodies|Major Bodies]]
- [[_COMMUNITY_Elongation Info|Elongation Info]]
- [[_COMMUNITY_Search Context|Search Context]]
- [[_COMMUNITY_Body Gravity Calc|Body Gravity Calc]]
- [[_COMMUNITY_Position Function|Position Function]]
- [[_COMMUNITY_Body Position|Body Position]]
- [[_COMMUNITY_Altitude Search|Altitude Search]]

## God Nodes (most connected - your core abstractions)
1. `MainActivity` - 31 edges
2. `AdzanLogger` - 18 edges
3. `IslamicWidgetProvider` - 16 edges
4. `TerseVector` - 16 edges
5. `MoonPhaseRenderer` - 15 edges
6. `IslamicAstronomy` - 14 edges
7. `Vector` - 13 edges
8. `AdzanService` - 11 edges
9. `MoonContext` - 11 edges
10. `GravitySimulator` - 11 edges

## Surprising Connections (you probably didn't know these)
- `Moon Full Texture Bitmap` --conceptually_related_to--> `Dynamic Hijri Calendar`  [INFERRED]
  app/src/main/res/drawable-nodpi/moon_full_texture.png → README.md
- `Client-Side Language Switcher` --semantically_similar_to--> `Multi-Language Support (ID/EN/AR)`  [INFERRED] [semantically similar]
  docs/index.html → README.md
- `Default App Icon SVG` --semantically_similar_to--> `Docs Favicon SVG`  [INFERRED] [semantically similar]
  icons/default.svg → docs/default.svg
- `Islamic Quotes Widget` --conceptually_related_to--> `Quranic Tafsir Quotes Database`  [INFERRED]
  README.md → quotes/README.md
- `Quranic Tafsir Quotes Database` --shares_data_with--> `Multi-Language Support (ID/EN/AR)`  [INFERRED]
  quotes/README.md → README.md

## Hyperedges (group relationships)
- **Hijri Calendar Icon System** — hijri_calendar_icons, icons_default_svg, readme_hijri_calendar [INFERRED 0.85]
- **Adzan Audio Assets** — adzan_regular_audio, adzan_subuh_audio, readme_adzan [EXTRACTED 0.95]
- **Landing Page & Documentation** — index_landing_page, app_screenshots, docs_default_svg, readme_islamic_widget [INFERRED 0.80]

## Communities

### Community 0 - "Astronomy Engine Core"
Cohesion: 0.01
Nodes (47): Aberration, ApsisInfo, ApsisKind, AscentInfo, AtmosphereInfo, AxisInfo, Body, ConstellationBoundary (+39 more)

### Community 1 - "Main Activity & Settings UI"
Cohesion: 0.06
Nodes (1): MainActivity

### Community 2 - "Islamic Astronomy & Prayer Calc"
Cohesion: 0.07
Nodes (13): CalculationMethod, EclipseReminder, HighLatitudeRule, HijriDate, HilalCriteria, HilalReport, HilalResult, IslamicAstronomy (+5 more)

### Community 3 - "Documentation & Assets"
Cohesion: 0.08
Nodes (26): Regular Adzan Audio (MP3), Fajr/Subuh Adzan Audio (MP3), App Screenshots Gallery, Docs Favicon SVG, Hijri Day Number Icons (1-30), Default App Icon SVG, Client-Side Language Switcher, Landing Page Website (+18 more)

### Community 4 - "Adzan Logger & Diagnostics"
Cohesion: 0.1
Nodes (2): AdzanLogger, Event

### Community 5 - "Islamic Widget Provider"
Cohesion: 0.11
Nodes (1): IslamicWidgetProvider

### Community 6 - "Moon Phase Renderer"
Cohesion: 0.11
Nodes (3): LunarEclipseState, MoonPhaseData, MoonPhaseRenderer

### Community 7 - "Terse Vector Math"
Cohesion: 0.12
Nodes (1): TerseVector

### Community 8 - "Vector Operations"
Cohesion: 0.15
Nodes (1): Vector

### Community 9 - "Adzan Playback Service"
Cohesion: 0.17
Nodes (1): AdzanService

### Community 10 - "Lunar Context Calculator"
Cohesion: 0.18
Nodes (1): MoonContext

### Community 11 - "Gravity Simulator"
Cohesion: 0.18
Nodes (1): GravitySimulator

### Community 12 - "Qibla Compass Activity"
Cohesion: 0.2
Nodes (1): CompassActivity

### Community 13 - "Time & Julian Date"
Cohesion: 0.2
Nodes (1): Time

### Community 14 - "Islamic App Utilities"
Cohesion: 0.22
Nodes (1): IslamicAppUtils

### Community 15 - "Developer Mode Helper"
Cohesion: 0.25
Nodes (1): DeveloperModeHelper

### Community 16 - "Hilal Info Activity"
Cohesion: 0.29
Nodes (1): HilalInfoActivity

### Community 17 - "Lunar Widget Provider"
Cohesion: 0.29
Nodes (1): LunarWidgetProvider

### Community 18 - "Quote Database Helper"
Cohesion: 0.29
Nodes (1): QuoteDatabaseHelper

### Community 19 - "Quote Widget Provider"
Cohesion: 0.29
Nodes (1): QuoteWidgetProvider

### Community 20 - "Update Receiver"
Cohesion: 0.29
Nodes (1): UpdateReceiver

### Community 21 - "State Vector Math"
Cohesion: 0.29
Nodes (1): StateVector

### Community 22 - "Icon Helper"
Cohesion: 0.33
Nodes (1): IconHelper

### Community 23 - "Silent Mode Receiver"
Cohesion: 0.33
Nodes (1): SilentModeReceiver

### Community 24 - "Settings Manager"
Cohesion: 0.4
Nodes (1): SettingsManager

### Community 25 - "Rotation Matrix"
Cohesion: 0.4
Nodes (1): RotationMatrix

### Community 26 - "Body State"
Cohesion: 0.4
Nodes (1): BodyState

### Community 27 - "Audio Adzan Manager"
Cohesion: 0.5
Nodes (1): AudioAdzanManager

### Community 28 - "Compass Widget Provider"
Cohesion: 0.5
Nodes (1): CompassWidgetProvider

### Community 29 - "Update Helper"
Cohesion: 0.5
Nodes (1): UpdateHelper

### Community 30 - "DateTime Utility"
Cohesion: 0.5
Nodes (1): DateTime

### Community 31 - "Boot Receiver"
Cohesion: 0.67
Nodes (1): BootReceiver

### Community 32 - "Quote Update Manager"
Cohesion: 0.67
Nodes (1): QuoteUpdateManager

### Community 33 - "Fading Scroll View"
Cohesion: 0.67
Nodes (1): TopFadingNestedScrollView

### Community 34 - "Widget Pin Receiver"
Cohesion: 0.67
Nodes (1): WidgetPinReceiver

### Community 35 - "Spherical Coordinates"
Cohesion: 0.67
Nodes (1): Spherical

### Community 36 - "Observer Position"
Cohesion: 0.67
Nodes (1): Observer

### Community 37 - "Pascal Array"
Cohesion: 0.67
Nodes (1): PascalArray2

### Community 38 - "Major Bodies"
Cohesion: 0.67
Nodes (1): MajorBodies

### Community 39 - "Elongation Info"
Cohesion: 1.0
Nodes (1): ElongationInfo

### Community 40 - "Search Context"
Cohesion: 1.0
Nodes (1): SearchContext

### Community 41 - "Body Gravity Calc"
Cohesion: 1.0
Nodes (1): BodyGravCalc

### Community 42 - "Position Function"
Cohesion: 1.0
Nodes (1): PositionFunction

### Community 43 - "Body Position"
Cohesion: 1.0
Nodes (1): BodyPosition

### Community 44 - "Altitude Search"
Cohesion: 1.0
Nodes (1): SearchContext_Altitude

## Knowledge Gaps
- **74 isolated node(s):** `Event`, `SettingsManager`, `InvalidBodyException`, `EarthNotAllowedException`, `InternalError` (+69 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Main Activity & Settings UI`** (32 nodes): `MainActivity.kt`, `MainActivity`, `.applyAppTheme()`, `.cancelAllScheduledAlarms()`, `.checkBatteryOptimizations()`, `.fetchLocation()`, `.findNestedScrollView()`, `.loadSettingsToUI()`, `.onConfigurationChanged()`, `.onCreate()`, `.onDestroy()`, `.onPause()`, `.onResume()`, `.onStop()`, `.performActualSave()`, `.requestPinWidget()`, `.saveSettingsQuietly()`, `.schedulePreviewUpdate()`, `.setDummyPreviewTimes()`, `.setupButtons()`, `.setupGlassBottomSheet()`, `.setupSlider()`, `.showBottomSheetSelector()`, `.showColorPickerDialog()`, `.showPauseActivityWarningDialog()`, `.stopTestAdzan()`, `.styleGlassBottomSheet()`, `.toggleTestAdzan()`, `.updateAddr()`, `.updateColorButtons()`, `.updateLocationUI()`, `.updatePreview()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Adzan Logger & Diagnostics`** (20 nodes): `AdzanLogger.kt`, `AdzanLogger`, `.clearLog()`, `.getLogFile()`, `.getLogFilePath()`, `.getMemoryLogs()`, `.getOrCreateSessionId()`, `.getPrayerName()`, `.log()`, `.logAdzanCompleted()`, `.logAdzanFired()`, `.logAdzanInterrupted()`, `.logAdzanPlayStart()`, `.logMuteExecuted()`, `.logScheduled()`, `.logUnmuteExecuted()`, `.readFileLog()`, `.resolveLogFile()`, `.trimLogFile()`, `Event`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Islamic Widget Provider`** (18 nodes): `IslamicWidgetProvider.kt`, `clearScheduleCache()`, `IslamicWidgetProvider`, `.applyFontSizesToAdzanMode()`, `.applyFontSizesToNormalMode()`, `.cancelExistingAlarms()`, `.checkAndEnforceSilentWindow()`, `.dpToPx()`, `.getBeforeMillis()`, `.onAppWidgetOptionsChanged()`, `.onReceive()`, `.onUpdate()`, `.refreshLunarWidget()`, `.scheduleAllPrayers()`, `.scheduleAmPmUpdate()`, `.scheduleDateChangeUpdate()`, `.scheduleSilentMode()`, `.updateAppWidget()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Terse Vector Math`** (16 nodes): `TerseVector`, `.copyFrom()`, `.decrement()`, `.div()`, `.increment()`, `.magnitude()`, `.mean()`, `.minus()`, `.mix()`, `.negate()`, `.plus()`, `.quadrature()`, `.setToZero()`, `.times()`, `.toAstroVector()`, `.unaryMinus()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Vector Operations`** (13 nodes): `Vector`, `.angleWith()`, `.div()`, `.dot()`, `.length()`, `.minus()`, `.plus()`, `.toEquatorial()`, `.toHorizontal()`, `.toObserver()`, `.toSpherical()`, `.unaryMinus()`, `.withTime()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Adzan Playback Service`** (12 nodes): `AdzanService.kt`, `AdzanService`, `.abandonAudioFocusCompat()`, `.createNotificationChannel()`, `.fadeOutAndStop()`, `.isAdzanStillRelevant()`, `.onBind()`, `.onDestroy()`, `.onStartCommand()`, `.playAdzan()`, `.registerHardwareVolumeListener()`, `.requestAudioFocusCompat()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Lunar Context Calculator`** (11 nodes): `MoonContext`, `.addn()`, `.addSol()`, `.addTheta()`, `.calcMoon()`, `.frac()`, `.longPeriodic()`, `.planetary()`, `.sine()`, `.solarN()`, `.term()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Gravity Simulator`** (11 nodes): `GravitySimulator`, `.addAcceleration()`, `.calcBodyAccelerations()`, `.calcSolarSystem()`, `.duplicate()`, `.initialEndpoint()`, `.internalBodyState()`, `.solarSystemBodyState()`, `.swap()`, `.time()`, `.update()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Qibla Compass Activity`** (10 nodes): `CompassActivity.kt`, `CompassActivity`, `.computeDeclination()`, `.fetchLatestLocation()`, `.getShortestDelta()`, `.onAccuracyChanged()`, `.onCreate()`, `.onPause()`, `.onResume()`, `.onSensorChanged()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Time & Julian Date`** (10 nodes): `Time`, `.addDays()`, `.compareTo()`, `.julianCenturies()`, `.julianMillennia()`, `.nutationEps()`, `.nutationPsi()`, `.toDateTime()`, `.toMillisecondsSince1970()`, `.toString()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Islamic App Utilities`** (9 nodes): `IslamicAppUtils.kt`, `IslamicAppUtils`, `.calculatePrayerTimes()`, `.formatCustomDate()`, `.getCalculationMethod()`, `.getEclipseReminderInfo()`, `.getLocalizedHijriMonthName()`, `.getSunnahFastingInfo()`, `.preProcessHijriPattern()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Developer Mode Helper`** (8 nodes): `DeveloperModeHelper.kt`, `DeveloperModeHelper`, `.cancelDeveloperTest()`, `.refreshLogContent()`, `.scheduleDeveloperTest()`, `.setup()`, `.setupLogViewer()`, `.updateTexts()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Hilal Info Activity`** (7 nodes): `HilalInfoActivity.kt`, `HilalInfoActivity`, `.appendRow()`, `.appendSectionTitle()`, `.dpToPx()`, `.getLocalizedPhaseName()`, `.onCreate()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Lunar Widget Provider`** (7 nodes): `LunarWidgetProvider.kt`, `LunarWidgetProvider`, `.dpToPx()`, `.getLocalizedPhaseName()`, `.onAppWidgetOptionsChanged()`, `.onUpdate()`, `.updateWidget()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Quote Database Helper`** (7 nodes): `QuoteDatabaseHelper.kt`, `getInstance()`, `QuoteDatabaseHelper`, `.copyDatabase()`, `.getRandomQuote()`, `.onCreate()`, `.onUpgrade()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Quote Widget Provider`** (7 nodes): `QuoteWidgetProvider.kt`, `QuoteWidgetProvider`, `.dpToPx()`, `.onReceive()`, `.onUpdate()`, `.showShimmer()`, `.updateAllWidgets()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Update Receiver`** (7 nodes): `UpdateReceiver.kt`, `UpdateReceiver`, `.createInstallPendingIntent()`, `.createNotificationChannel()`, `.installApk()`, `.onReceive()`, `.startInternalDownload()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `State Vector Math`** (7 nodes): `StateVector`, `.div()`, `.minus()`, `.plus()`, `.position()`, `.unaryMinus()`, `.velocity()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Icon Helper`** (6 nodes): `IconHelper.kt`, `IconHelper`, `.executePendingIconUpdate()`, `.isAppInForeground()`, `.performIconUpdate()`, `.updateLauncherIcon()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Silent Mode Receiver`** (6 nodes): `SilentModeReceiver.kt`, `SilentModeReceiver`, `.executeMute()`, `.executeUnmute()`, `.forceUpdateAllWidgets()`, `.onReceive()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Settings Manager`** (5 nodes): `SettingsManager.kt`, `cancelAllSilentAlarms()`, `restoreDefaults()`, `saveAllSettings()`, `SettingsManager`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Rotation Matrix`** (5 nodes): `RotationMatrix`, `.combine()`, `.inverse()`, `.pivot()`, `.rotate()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Body State`** (5 nodes): `BodyState`, `.copyFrom()`, `.decrement()`, `.increment()`, `.minus()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Audio Adzan Manager`** (4 nodes): `AudioAdzanManager.kt`, `AudioAdzanManager`, `.stopTestAdzan()`, `.toggleTestAdzan()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Compass Widget Provider`** (4 nodes): `CompassWidgetProvider.kt`, `CompassWidgetProvider`, `.onUpdate()`, `.updateAppWidget()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Update Helper`** (4 nodes): `UpdateHelper.kt`, `UpdateHelper`, `.checkForUpdates()`, `.isVersionNewer()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `DateTime Utility`** (4 nodes): `DateTime`, `.toDays()`, `.toString()`, `.toTime()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Boot Receiver`** (3 nodes): `BootReceiver.kt`, `BootReceiver`, `.onReceive()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Quote Update Manager`** (3 nodes): `QuoteUpdateManager.kt`, `QuoteUpdateManager`, `.setAutoUpdate()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Fading Scroll View`** (3 nodes): `TopFadingNestedScrollView.kt`, `TopFadingNestedScrollView`, `.getBottomFadingEdgeStrength()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Widget Pin Receiver`** (3 nodes): `WidgetPinReceiver.kt`, `WidgetPinReceiver`, `.onReceive()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Spherical Coordinates`** (3 nodes): `Spherical`, `.toVector()`, `.toVectorFromHorizon()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Observer Position`** (3 nodes): `Observer`, `.toStateVector()`, `.toVector()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Pascal Array`** (3 nodes): `PascalArray2`, `.get()`, `.set()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Major Bodies`** (3 nodes): `MajorBodies`, `.acceleration()`, `.accelerationIncrement()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Elongation Info`** (2 nodes): `ElongationInfo`, `.validateAngle()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Search Context`** (2 nodes): `SearchContext`, `.eval()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Body Gravity Calc`** (2 nodes): `BodyGravCalc`, `.copyFrom()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Position Function`** (2 nodes): `PositionFunction`, `.position()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Body Position`** (2 nodes): `BodyPosition`, `.position()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Altitude Search`** (2 nodes): `SearchContext_Altitude`, `.eval()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `TerseVector` connect `Terse Vector Math` to `Astronomy Engine Core`?**
  _High betweenness centrality (0.027) - this node is a cross-community bridge._
- **Why does `Vector` connect `Vector Operations` to `Astronomy Engine Core`?**
  _High betweenness centrality (0.022) - this node is a cross-community bridge._
- **Why does `MoonContext` connect `Lunar Context Calculator` to `Astronomy Engine Core`?**
  _High betweenness centrality (0.018) - this node is a cross-community bridge._
- **What connects `Event`, `SettingsManager`, `InvalidBodyException` to the rest of the system?**
  _74 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Astronomy Engine Core` be split into smaller, more focused modules?**
  _Cohesion score 0.01 - nodes in this community are weakly interconnected._
- **Should `Main Activity & Settings UI` be split into smaller, more focused modules?**
  _Cohesion score 0.06 - nodes in this community are weakly interconnected._
- **Should `Islamic Astronomy & Prayer Calc` be split into smaller, more focused modules?**
  _Cohesion score 0.07 - nodes in this community are weakly interconnected._