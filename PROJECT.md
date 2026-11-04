# Project: Kuro Android Music Player Remediation

## Architecture
Kuro is a modern Android music player built with Jetpack Compose, Kotlin coroutines, ExoPlayer / Media3 `MediaSessionService`, and YouTube Music / InnerTube integration.
The architecture is structured as follows:
- **Presentation Layer**: Compose UI (`MainActivity`, `HomeScreen`, `SearchScreen`, `LibraryScreen`, `FullPlayerSheet`, `FullScreenLyricsSheet`, `QueueBottomSheet`, `TrackOptionsBottomSheet`, `ProfileMenuSheet`, `LoginWebViewDialog`, `SettingsDialog`).
- **ViewModel Layer**: `MusicViewModel` providing reactive state flows to the UI.
- **Player & Queue Engine**: `PlayerQueueManager` (shared singleton for queue, shuffle, repeat, error backoff) and `PlaybackService` (`MediaSessionService` wrapping `ExoPlayer`).
- **Data & Repository Layer**: `MusicRepository`, `LyricsRepository` (`lyrics_cache_v5`, LRCLIB + InnerTube), `AuthManager`, `SettingsManager`, `TasteManager`, `AudioOutputManager`.

## Feature Inventory
| # | Feature / Issue ID | Description | Milestone | Source |
|---|-------------------|-------------|-----------|--------|
| 1 | NAV-CORE-01 | Staged back-handling in SearchScreen; back stack preservation in MainActivity | M4 | BUG_AND_UX_REPORT.md §Search Back-Nav |
| 2 | UI-01 | Light Theme text contrast and visibility in SearchScreen (replace 11 Color.White) | M4 | BUG_AND_UX_REPORT.md §UI-01 |
| 3 | UI-02 | Missing BackHandler in LibraryScreen PlaylistDetailView | M4 | BUG_AND_UX_REPORT.md §UI-02 |
| 4 | UI-03 | Missing BackHandler in HomeScreen Genre Filter | M4 | BUG_AND_UX_REPORT.md §UI-03 |
| 5 | UI-04 | Missing BackHandler in FullPlayerSheet / FullScreenLyricsSheet | M4 | BUG_AND_UX_REPORT.md §UI-04 |
| 6 | UI-05 | Dead action buttons in TrackOptionsBottomSheet | M4 | BUG_AND_UX_REPORT.md §UI-05 |
| 7 | UI-06 | Dead action buttons in ProfileMenuSheet | M4 | BUG_AND_UX_REPORT.md §UI-06 |
| 8 | UI-07 | Screen rotation state loss (remember -> rememberSaveable) | M4 | BUG_AND_UX_REPORT.md §UI-07 |
| 9 | UI-08 | Missing window insets / display cutout padding on player sheets | M4 | BUG_AND_UX_REPORT.md §UI-08 |
| 10 | UI-09 | Aggressive auto-scroll in FullScreenLyricsSheet ignoring user touch | M4 | BUG_AND_UX_REPORT.md §UI-09 |
| 11 | UI-10 | Notification & widget tap missing EXTRA_EXPAND_PLAYER & onNewIntent | M4 | BUG_AND_UX_REPORT.md §UI-10 |
| 12 | UI-11 | Dead orphaned component SyncedLyricsView.kt deletion | M4 | BUG_AND_UX_REPORT.md §UI-11 |
| 13 | UI-12 | Double dynamic dominant color extraction animation flash | M4 | BUG_AND_UX_REPORT.md §UI-12 |
| 14 | PB-01 | PlaybackService vs MusicViewModel background media decoupling | M1 | BUG_AND_UX_REPORT.md §PB-01 |
| 15 | PB-02 | Missing background track completion progression (STATE_ENDED in service) | M1 | BUG_AND_UX_REPORT.md §PB-02 |
| 16 | PB-03 | Unthrottled error cascade & machine-gun skipping (max 3 consecutive) | M3 | BUG_AND_UX_REPORT.md §PB-03 |
| 17 | PB-04 | Stale 3-hour googlevideo stream cache (20m TTL + eviction on error) | M2 | BUG_AND_UX_REPORT.md §PB-04 |
| 18 | PB-05 | Missing Recents queue insertion branch in playTrack | M3 | BUG_AND_UX_REPORT.md §PB-05 |
| 19 | PB-06 | Pseudorandom shuffle with replacement -> Fisher-Yates deck & history | M1/M3 | BUG_AND_UX_REPORT.md §PB-06 |
| 20 | PB-07 | Scrubber drag seek flooding ExoPlayer (onValueChangeFinished buffer) | M4 | BUG_AND_UX_REPORT.md §PB-07 |
| 21 | PB-08 | Involuntary audio resumption when seeking while paused | M3 | BUG_AND_UX_REPORT.md §PB-08 |
| 22 | PB-09 | Tapping next in repeat-one loops current song (user skip advances) | M3 | BUG_AND_UX_REPORT.md §PB-09 |
| 23 | PB-10 | 1.2-second progress bar and clock freeze (DISCONTINUITY_REASON_SEEK) | M3 | BUG_AND_UX_REPORT.md §PB-10 |
| 24 | PB-11 | Missing foreground service demotion on 5m pause timeout / focus loss | M1 | BUG_AND_UX_REPORT.md §PB-11 |
| 25 | PB-12 | Duplicate track ID index collision in queue (playTrackAtIndex) | M3 | BUG_AND_UX_REPORT.md §PB-12 |
| 26 | PB-13 | Missing queue management operations (remove, move, clear upcoming) | M3/M4 | BUG_AND_UX_REPORT.md §PB-13 |
| 27 | PB-14 | MediaSession over-reporting available commands (bind to queue bounds) | M1 | BUG_AND_UX_REPORT.md §PB-14 |
| 28 | PB-15 | Monotonic progress guard backward snap on >= 200ms discrepancies | M3 | BUG_AND_UX_REPORT.md §PB-15 |
| 29 | PB-16 | False-positive vocal suppression at 00:00 (isIntroToken check) | M2 | BUG_AND_UX_REPORT.md §PB-16 |
| 30 | PB-17 | Highlighted lyrics persisting through guitar solos (endTimeMs in LyricLine) | M2 | BUG_AND_UX_REPORT.md §PB-17 |
| 31 | PB-18 | Missing LRC [offset:+/-ms] tag parsing | M2 | BUG_AND_UX_REPORT.md §PB-18 |
| 32 | PB-19 | Google Translate separator breakage (\n~~~\n -> robust delimiter) | M2 | BUG_AND_UX_REPORT.md §PB-19 |
| 33 | ST-01 | Infinite retry in REPEAT_MODE_ONE (isErrorRecovery parameter) | M3 | BUG_AND_UX_REPORT.md §ST-01 |
| 34 | ST-02 | Negative cache 7-day poisoning fix on transient network errors | M2 | BUG_AND_UX_REPORT.md §ST-02 |
| 35 | ST-03 | Settings clear cache supporting active lyrics_cache_v5 directory | M2 | BUG_AND_UX_REPORT.md §ST-03 |
| 36 | ST-04 | Shared _searchResults StateFlow collision (dedicated _genreTracks) | M3/M4 | BUG_AND_UX_REPORT.md §ST-04 |
| 37 | ST-05 | Lyrics duration resync at STATE_READY leaves _isLoadingLyrics stuck | M3 | BUG_AND_UX_REPORT.md §ST-05 |
| 38 | ST-06 | Stale cookies trigger re-login trap in LoginWebViewDialog | M2 | BUG_AND_UX_REPORT.md §ST-06 |
| 39 | ST-07 | Android 14+ SecurityException in AudioOutputManager (RECEIVER_NOT_EXPORTED) | M1 | BUG_AND_UX_REPORT.md §ST-07 |
| 40 | ST-08 | Main-thread synchronous JSON disk serialization (Dispatchers.IO) | M2/M3 | BUG_AND_UX_REPORT.md §ST-08 |
| 41 | ST-09 | Unsynchronized compound operations on playlistQueue | M3 | BUG_AND_UX_REPORT.md §ST-09 |
| 42 | R2-COM | Clean removal of all single-line // comments across entire codebase | M5 | ORIGINAL_REQUEST.md §R2 |
| 43 | R3-VER | Lightweight test/verification suite with zero local Gradle APK builds | M5/M6 | ORIGINAL_REQUEST.md §R3 |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M0 | Survey & Planning | Complete codebase mapping and feature inventory | none | DONE |
| M1 | Playback Core & Service Decoupling | PlayerQueueManager, PlaybackService, AudioOutputManager (PB-01, PB-02, PB-11, PB-14, ST-07) | M0 | DONE |
| M2 | Data Layer, Repositories, Caching & Auth | MusicRepository, LyricsRepository, LyricsModel, LrcParser, SettingsManager, TasteManager, AuthManager, LoginWebViewDialog (PB-04, PB-16..19, ST-02, ST-03, ST-06, ST-08) | M0 | DONE |
| M3 | ViewModel & Queue State Engine | MusicViewModel integration with PlayerQueueManager, error backoff, repeat-one fix, seek-while-paused fix, queue operations, smooth progress clock, _genreTracks isolation (PB-03, PB-05, PB-06, PB-08, PB-09, PB-10, PB-12, PB-13, PB-15, ST-01, ST-04, ST-05, ST-08, ST-09) | M1, M2 | DONE |
| M4 | UI, Navigation & Sheet Polish | MainActivity, SearchScreen, HomeScreen, LibraryScreen, FullPlayerSheet, FullScreenLyricsSheet, QueueBottomSheet, TrackOptionsBottomSheet, ProfileMenuSheet, KuroWidgets, DynamicThemeHelper, SyncedLyricsView deletion (NAV-CORE-01, UI-01..12, PB-07, PB-13 UI, ST-04 UI) | M3 | DONE |
| M5 | Comment Hygiene (R2) & Verification Suite | Token-based clean removal of all // comments across all 24 source files while preserving URLs and strings; Lightweight AST/syntax and logic verification harness | M1..M4 | DONE |
| M6 | Independent Review, Adversarial Stress Testing & Forensic Audit | 2 Reviewers, 2 Challengers, 1 Forensic Auditor (teamwork_auditor) | M5 | DONE |

## Interface Contracts
### PlayerQueueManager (Singleton)
```kotlin
package com.ayashii.kuro.data.player

import com.ayashii.kuro.data.model.Track
import kotlinx.coroutines.flow.StateFlow

interface IPlayerQueueManager {
    val queueState: StateFlow<List<Track>>
    val currentIndex: StateFlow<Int>
    val currentTrack: StateFlow<Track?>
    val repeatMode: StateFlow<Int>
    val isShuffle: StateFlow<Boolean>
    val consecutiveErrors: StateFlow<Int>

    fun setQueue(tracks: List<Track>, startIndex: Int = 0)
    fun playTrack(track: Track, newQueue: List<Track> = emptyList())
    fun playTrackAtIndex(index: Int)
    fun addToQueue(track: Track)
    fun removeFromQueue(index: Int): Boolean
    fun moveQueueItem(fromIndex: Int, toIndex: Int): Boolean
    fun clearUpcomingQueue()
    fun getNextTrack(userInitiated: Boolean, isErrorRecovery: Boolean = false): Track?
    fun getPreviousTrack(): Track?
    fun hasNext(): Boolean
    fun hasPrevious(): Boolean
    fun toggleShuffle()
    fun toggleRepeat()
    fun recordPlaybackSuccess()
    fun recordPlaybackFailure(): Int
    fun resetErrorCount()
}
```

### MusicRepository Stream Eviction
```kotlin
fun evictStreamUrl(songUrl: String)
```

### LyricsRepository Negative Cache Rule
Negative cache entries (`$trackId.empty`) MUST ONLY be written when the upstream API returns a confirmed empty lyrics response (HTTP 200 with empty lyrics, or 404). Any network exception, timeout, or DNS failure (`IOException`, `SocketTimeoutException`, `UnknownHostException`) must NOT commit to `negativeCache` or disk.

## Code Layout
- `app/src/main/java/com/ayashii/kuro/data/player/` — PlayerQueueManager.kt
- `app/src/main/java/com/ayashii/kuro/service/` — PlaybackService.kt
- `app/src/main/java/com/ayashii/kuro/data/remote/` — MusicRepository.kt
- `app/src/main/java/com/ayashii/kuro/data/lyrics/` — LyricsRepository.kt, LyricsModel.kt, LrcParser.kt
- `app/src/main/java/com/ayashii/kuro/data/settings/` — SettingsManager.kt
- `app/src/main/java/com/ayashii/kuro/data/auth/` — AuthManager.kt
- `app/src/main/java/com/ayashii/kuro/data/audio/` — AudioOutputManager.kt
- `app/src/main/java/com/ayashii/kuro/data/recommendation/` — TasteManager.kt
- `app/src/main/java/com/ayashii/kuro/viewmodel/` — MusicViewModel.kt
- `app/src/main/java/com/ayashii/kuro/ui/` — Screens and Components
- `app/src/main/java/com/ayashii/kuro/widget/` — KuroWidgets.kt
- `app/src/main/java/com/ayashii/kuro/MainActivity.kt`
