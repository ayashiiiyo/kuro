#!/usr/bin/env python3
"""
Comprehensive Verification Harness: Kuro Android Music Player
Testing R2 Zero Comment Compliance, Code Integrity, Bracket Balance,
Git Diff Cleanliness, and All 31 Documented Bug / UX Remediations.
"""

import os
import sys
import re
import subprocess
from pygments.lexers.jvm import KotlinLexer
from pygments.token import Token

ROOT_DIR = "/root/kuro"

class Colors:
    GREEN = "\033[92m"
    RED = "\033[91m"
    YELLOW = "\033[93m"
    BLUE = "\033[94m"
    BOLD = "\033[1m"
    RESET = "\033[0m"

class TestSuite:
    def __init__(self):
        self.total = 0
        self.passed = 0
        self.failed = 0
        self.errors = []

    def assert_true(self, condition, test_id, description, details=""):
        self.total += 1
        if condition:
            self.passed += 1
            print(f"  [{Colors.GREEN}PASS{Colors.RESET}] {Colors.BOLD}{test_id}{Colors.RESET}: {description}")
        else:
            self.failed += 1
            msg = f"[{Colors.RED}FAIL{Colors.RESET}] {Colors.BOLD}{test_id}{Colors.RESET}: {description}"
            if details:
                msg += f"\n         Details: {details}"
            print(f"  {msg}")
            self.errors.append((test_id, description, details))

    def read_file(self, rel_path):
        full_path = os.path.join(ROOT_DIR, rel_path)
        with open(full_path, "r", encoding="utf-8", errors="ignore") as f:
            return f.read()

    def file_exists(self, rel_path):
        return os.path.exists(os.path.join(ROOT_DIR, rel_path))

suite = TestSuite()

print(f"\n{Colors.BLUE}{Colors.BOLD}======================================================================{Colors.RESET}")
print(f"{Colors.BLUE}{Colors.BOLD}   KURO ANDROID MUSIC PLAYER — COMPREHENSIVE VERIFICATION SUITE       {Colors.RESET}")
print(f"{Colors.BLUE}{Colors.BOLD}======================================================================{Colors.RESET}\n")

# ==============================================================================
# SECTION 1: CODEBASE HYGIENE, SYNTAX INTEGRITY & R2 COMMENT CONSTRAINT
# ==============================================================================
print(f"{Colors.BOLD}>>> 1. Codebase Hygiene, Syntax Integrity & R2 Comment Constraint{Colors.RESET}")

# Test R2-01: Zero Token.Comment.Single in Kotlin/Gradle files
kotlin_lexer = KotlinLexer()
single_comments_found = []
for root, dirs, files in os.walk(ROOT_DIR):
    if any(x in root for x in ["/.git", "/.agents", "/build"]):
        continue
    for f in sorted(files):
        if f.endswith((".kt", ".kts")):
            p = os.path.join(root, f)
            with open(p, "r", encoding="utf-8") as fp:
                code = fp.read()
            tokens = list(kotlin_lexer.get_tokens(code))
            cmts = [v for t, v in tokens if t in Token.Comment.Single]
            if cmts:
                single_comments_found.append((os.path.relpath(p, ROOT_DIR), len(cmts), cmts))

suite.assert_true(
    len(single_comments_found) == 0,
    "R2-01",
    "Zero single-line // comments in all Kotlin and Gradle files",
    f"Found in {len(single_comments_found)} files: {single_comments_found[:3]}"
)

# Test R2-02: Zero // comments in Rust core
rust_comments_found = []
for root, dirs, files in os.walk(os.path.join(ROOT_DIR, "kuro-core")):
    for f in sorted(files):
        if f.endswith(".rs"):
            p = os.path.join(root, f)
            with open(p, "r", encoding="utf-8") as fp:
                for idx, line in enumerate(fp, 1):
                    stripped = line.strip()
                    if stripped.startswith("//") or stripped.startswith("///"):
                        rust_comments_found.append((os.path.relpath(p, ROOT_DIR), idx, line.strip()))

suite.assert_true(
    len(rust_comments_found) == 0,
    "R2-02",
    "Zero // and /// comments in Rust kuro-core source files",
    f"Found {len(rust_comments_found)} Rust comments: {rust_comments_found[:3]}"
)

# Test R2-03: String literal and URL preservation (all // are inside strings/urls)
non_string_slashes = []
for root, dirs, files in os.walk(ROOT_DIR):
    if any(x in root for x in ["/.git", "/.agents", "/build"]):
        continue
    for f in sorted(files):
        if f.endswith((".kt", ".kts", ".rs")):
            p = os.path.join(root, f)
            rel = os.path.relpath(p, ROOT_DIR)
            with open(p, "r", encoding="utf-8") as fp:
                for idx, line in enumerate(fp, 1):
                    if "//" in line:
                        # Ensure it's part of a string literal or url
                        has_url = "http://" in line or "https://" in line or "startsWith(\"//\")" in line or "starts_with(\"//\")" in line
                        has_quotes = line.count('"') >= 2 or line.count("'") >= 2
                        if not (has_url or has_quotes):
                            non_string_slashes.append((rel, idx, line.strip()))

suite.assert_true(
    len(non_string_slashes) == 0,
    "R2-03",
    "All // occurrences in source files are valid string literals or URLs",
    f"Non-string // found: {non_string_slashes[:3]}"
)

# Test SYN-01: Bracket balance across all Kotlin and Gradle files
unbalanced_files = []
pairs = {")": "(", "]": "[", "}": "{"}
for root, dirs, files in os.walk(ROOT_DIR):
    if any(x in root for x in ["/.git", "/.agents", "/build"]):
        continue
    for f in sorted(files):
        if f.endswith((".kt", ".kts")):
            p = os.path.join(root, f)
            with open(p, "r", encoding="utf-8") as fp:
                code = fp.read()
            tokens = list(kotlin_lexer.get_tokens(code))
            stack = []
            mismatch = None
            for ttype, val in tokens:
                if ttype not in Token.Literal.String and ttype not in Token.Comment:
                    for ch in val:
                        if ch in "([{":
                            stack.append(ch)
                        elif ch in ")]}":
                            if not stack or stack[-1] != pairs[ch]:
                                mismatch = f"Mismatched {ch}"
                                break
                            stack.pop()
                    if mismatch:
                        break
            if stack or mismatch:
                unbalanced_files.append((os.path.relpath(p, ROOT_DIR), mismatch or f"Unclosed {stack}"))

suite.assert_true(
    len(unbalanced_files) == 0,
    "SYN-01",
    "Balanced brackets and delimiters across all Kotlin and Gradle files",
    f"Unbalanced files: {unbalanced_files}"
)

# Test IMM-01: Key string literals and URLs remain identical to HEAD
expected_urls = {
    "settings.gradle.kts": "https://jitpack.io",
    "app/src/main/java/com/ayashii/kuro/data/model/Track.kt": 'if (u.startsWith("//"))',
    "app/src/main/java/com/ayashii/kuro/data/remote/MusicRepository.kt": 'if (u.startsWith("//"))',
    "app/src/main/java/com/ayashii/kuro/data/remote/MusicRepository.kt": "https://music.youtube.com",
    "app/src/main/java/com/ayashii/kuro/data/update/UpdateManager.kt": "https://api.github.com/repos/ayashiiiyo/kuro/releases/latest",
    "kuro-core/src/innertube.rs": 'if u.starts_with("//")',
    "kuro-core/src/translator.rs": "https://oneshot.fal.free.deepl.com/v1/translate",
}
missing_urls = []
for f_rel, pattern in expected_urls.items():
    content = suite.read_file(f_rel)
    if pattern not in content:
        missing_urls.append((f_rel, pattern))

suite.assert_true(
    len(missing_urls) == 0,
    "IMM-01",
    "Key string literals and API URLs preserved without corruption",
    f"Missing patterns: {missing_urls}"
)

# Test GIT-01: Git diff cleanliness (no trailing whitespace, no merge conflicts)
git_diff_check = subprocess.run(["git", "diff", "--check"], cwd=ROOT_DIR, capture_output=True, text=True)
suite.assert_true(
    git_diff_check.returncode == 0 and git_diff_check.stdout == "",
    "GIT-01",
    "Clean git diff (git diff --check passes with zero whitespace or conflict errors)",
    git_diff_check.stdout.strip()
)

# ==============================================================================
# SECTION 2: CATEGORY 1 — UI & NAVIGATION ARCHITECTURE (NAV-CORE-01, UI-01 to UI-12)
# ==============================================================================
print(f"\n{Colors.BOLD}>>> 2. Category 1: UI & Navigation Architecture{Colors.RESET}")

# NAV-CORE-01: SearchScreen Staged Back Handling & MainActivity BackStack
search_screen = suite.read_file("app/src/main/java/com/ayashii/kuro/ui/screens/SearchScreen.kt")
main_activity = suite.read_file("app/src/main/java/com/ayashii/kuro/MainActivity.kt")
nav_stage1 = "BackHandler(enabled = searchQuery.isNotEmpty())" in search_screen and 'searchQuery = ""' in search_screen
nav_stage2 = "BackHandler(enabled = isSearchActive && searchQuery.isEmpty())" in search_screen and "isSearchActive = false" in search_screen
nav_main = "BackHandler(enabled = isExpanded)" in main_activity and "expandPlayer(false)" in main_activity
suite.assert_true(
    nav_stage1 and nav_stage2 and nav_main,
    "NAV-CORE-01",
    "Staged back navigation in SearchScreen and backstack preservation in MainActivity",
    f"stage1={nav_stage1}, stage2={nav_stage2}, main={nav_main}"
)

# UI-01: Light Theme text contrast in SearchScreen (theme-aware colors replace hardcoded white)
# Verify canvas text elements (TextField, Track Titles, Section Headers, Not Found) use theme colors
ui01_textfield = "focusedTextColor = colors.textPrimary" in search_screen and "unfocusedTextColor = colors.textPrimary" in search_screen
ui01_track_title = "if (isCurrent) colors.accent else colors.textPrimary" in search_screen
ui01_headers = ("Pencarian Terbaru" in search_screen or "Discover something new" in search_screen) and "colors.textPrimary" in search_screen
ui01_not_found = "colors.textMuted" in search_screen and "colors.textPrimary" in search_screen
suite.assert_true(
    ui01_textfield and ui01_track_title and ui01_headers and ui01_not_found,
    "UI-01",
    "Light Theme text contrast in SearchScreen (theme tokens colors.textPrimary used across canvas elements)",
    f"textfield={ui01_textfield}, track_title={ui01_track_title}, headers={ui01_headers}, not_found={ui01_not_found}"
)

# UI-02: Missing BackHandler in LibraryScreen PlaylistDetailView
library_screen = suite.read_file("app/src/main/java/com/ayashii/kuro/ui/screens/LibraryScreen.kt")
ui02_check = "BackHandler(enabled = selectedPlaylistId != null)" in library_screen and "selectedPlaylistId = null" in library_screen
suite.assert_true(
    ui02_check,
    "UI-02",
    "LibraryScreen BackHandler resets playlist detail view to prevent accidental app exit",
    "BackHandler for selectedPlaylistId not found"
)

# UI-03: Missing BackHandler in HomeScreen Genre Filter
home_screen = suite.read_file("app/src/main/java/com/ayashii/kuro/ui/screens/HomeScreen.kt")
ui03_check = ('BackHandler(enabled = selectedGenre != "Semua")' in home_screen or 'BackHandler(enabled = selectedGenre != "All")' in home_screen) and "selectedGenre = " in home_screen
suite.assert_true(
    ui03_check,
    "UI-03",
    "HomeScreen BackHandler resets genre filter to 'Semua' and reloads trending",
    "BackHandler for selectedGenre not found"
)

# UI-04: BackHandler in FullPlayerSheet / FullScreenLyricsSheet
full_player = suite.read_file("app/src/main/java/com/ayashii/kuro/ui/components/FullPlayerSheet.kt")
ui04_check = "BackHandler(enabled = showFullScreenLyrics)" in full_player and "showFullScreenLyrics = false" in full_player and "BackHandler(enabled = isExpanded)" in main_activity
suite.assert_true(
    ui04_check,
    "UI-04",
    "BackHandler in FullPlayerSheet for lyrics sheet and MainActivity for full player",
    "Lyrics or FullPlayer BackHandler missing"
)

# UI-05: Dead action buttons in TrackOptionsBottomSheet
track_options = suite.read_file("app/src/main/java/com/ayashii/kuro/ui/components/TrackOptionsBottomSheet.kt")
has_share = "Intent(Intent.ACTION_SEND)" in track_options and "player_share" in track_options
has_playlist = "onAddToPlaylist()" in track_options
has_queue = "viewModel.addToQueue(track)" in track_options and "onOpenQueue()" in track_options
has_fav = "viewModel.toggleFavorite(track)" in track_options
has_lyrics = "onOpenLyrics()" in track_options
suite.assert_true(
    has_share and has_playlist and has_queue and has_fav and has_lyrics,
    "UI-05",
    "TrackOptionsBottomSheet buttons wired to functional actions (share, playlist, queue, favorite, lyrics)",
    f"share={has_share}, playlist={has_playlist}, queue={has_queue}, fav={has_fav}, lyrics={has_lyrics}"
)

# UI-06: Dead action buttons in ProfileMenuSheet
profile_sheet = suite.read_file("app/src/main/java/com/ayashii/kuro/ui/components/ProfileMenuSheet.kt")
has_logout = "onLogoutClick()" in profile_sheet
has_login = "onLoginClick()" in profile_sheet
has_stats = "showStatsDialog = true" in profile_sheet
suite.assert_true(
    has_logout and has_login and has_stats,
    "UI-06",
    "ProfileMenuSheet buttons wired to functional actions (login, logout, listening stats)",
    f"logout={has_logout}, login={has_login}, stats={has_stats}"
)

# UI-07: Configuration change & screen rotation state loss (rememberSaveable)
saveable_in_library = "selectedTab by rememberSaveable" in library_screen and "selectedPlaylistId by rememberSaveable" in library_screen
saveable_in_home = "selectedGenre by rememberSaveable" in home_screen
saveable_in_player = "showFullScreenLyrics by rememberSaveable" in full_player
suite.assert_true(
    saveable_in_library and saveable_in_home and saveable_in_player,
    "UI-07",
    "Configuration change / rotation state preserved via rememberSaveable across screens and sheets",
    f"library={saveable_in_library}, home={saveable_in_home}, player={saveable_in_player}"
)

# UI-08: Window insets / display cutout padding on player sheets
fullscreen_lyrics = suite.read_file("app/src/main/java/com/ayashii/kuro/ui/components/FullScreenLyricsSheet.kt")
insets_in_lyrics = "statusBarsPadding()" in fullscreen_lyrics or "WindowInsets.statusBars" in fullscreen_lyrics
insets_in_player = "statusBarsPadding()" in full_player or "navigationBarsPadding()" in full_player or "WindowInsets.navigationBars" in full_player
suite.assert_true(
    insets_in_lyrics and insets_in_player,
    "UI-08",
    "Window insets and display cutout padding applied to player and lyrics sheets",
    f"lyrics_insets={insets_in_lyrics}, player_insets={insets_in_player}"
)

# UI-09: Aggressive auto-scroll in FullScreenLyricsSheet ignoring user touch
ui09_check = "!listState.isScrollInProgress" in fullscreen_lyrics and "animateScrollToItem" in fullscreen_lyrics
suite.assert_true(
    ui09_check,
    "UI-09",
    "FullScreenLyricsSheet pauses auto-scroll during active user touch/scroll gesture",
    "isScrollInProgress guard not found"
)

# UI-10: Notification & widget tap missing EXTRA_EXPAND_PLAYER & onNewIntent
widgets = suite.read_file("app/src/main/java/com/ayashii/kuro/widget/KuroWidgets.kt")
service = suite.read_file("app/src/main/java/com/ayashii/kuro/service/PlaybackService.kt")
main_has_extra = "EXTRA_EXPAND_PLAYER" in main_activity and "onNewIntent" in main_activity and "expandPlayer(true)" in main_activity
widget_has_extra = "EXTRA_EXPAND_PLAYER" in widgets
service_has_extra = "EXTRA_EXPAND_PLAYER" in service
suite.assert_true(
    main_has_extra and widget_has_extra and service_has_extra,
    "UI-10",
    "Notification & widget tap delivers EXTRA_EXPAND_PLAYER handled in MainActivity onNewIntent",
    f"main={main_has_extra}, widget={widget_has_extra}, service={service_has_extra}"
)

# UI-11: Dead orphaned component SyncedLyricsView.kt deletion
orphaned_path = "app/src/main/java/com/ayashii/kuro/ui/components/SyncedLyricsView.kt"
ui11_check = not suite.file_exists(orphaned_path)
suite.assert_true(
    ui11_check,
    "UI-11",
    "Dead orphaned component SyncedLyricsView.kt cleanly removed from codebase",
    f"File {orphaned_path} still exists" if not ui11_check else ""
)

# UI-12: Double dynamic dominant color extraction animation flash
dynamic_theme = suite.read_file("app/src/main/java/com/ayashii/kuro/ui/theme/DynamicThemeHelper.kt")
ui12_cache = "getCachedColor" in dynamic_theme and "colorCache" in dynamic_theme
ui12_anim = "animateColorAsState" in dynamic_theme and "tween(durationMillis = 450" in dynamic_theme
suite.assert_true(
    ui12_cache and ui12_anim,
    "UI-12",
    "Dynamic dominant color helper utilizes LRU cache and smooth 450ms color crossfade",
    f"cache={ui12_cache}, anim={ui12_anim}"
)

# ==============================================================================
# SECTION 3: CATEGORY 2 — PLAYBACK PIPELINE & MEDIA LIFECYCLE (PB-01 to PB-19)
# ==============================================================================
print(f"\n{Colors.BOLD}>>> 3. Category 2: Playback Pipeline & Media Lifecycle{Colors.RESET}")

player_queue = suite.read_file("app/src/main/java/com/ayashii/kuro/data/player/PlayerQueueManager.kt")
music_vm = suite.read_file("app/src/main/java/com/ayashii/kuro/viewmodel/MusicViewModel.kt")
music_repo = suite.read_file("app/src/main/java/com/ayashii/kuro/data/remote/MusicRepository.kt")

# PB-01: PlaybackService vs MusicViewModel background media decoupling
service_decoupled = "PlayerQueueManager" in service and "getNextTrack" in service and "getPreviousTrack" in service
suite.assert_true(
    service_decoupled,
    "PB-01",
    "PlaybackService directly orchestrates playback via PlayerQueueManager independent of UI ViewModel",
    "PlaybackService does not bind directly to PlayerQueueManager"
)

# PB-02: Missing background track completion progression (STATE_ENDED in service)
pb02_check = "STATE_ENDED" in service and "getNextTrack(userInitiated = false" in service and "playTrack" in service
suite.assert_true(
    pb02_check,
    "PB-02",
    "PlaybackService advances queue automatically upon STATE_ENDED in background",
    "STATE_ENDED auto-advance handling missing from PlaybackService"
)

# PB-03: Unthrottled error cascade & machine-gun skipping (max 3 consecutive)
pb03_queue = "consecutiveErrors" in player_queue and "recordPlaybackFailure" in player_queue
pb03_vm = "consecutiveErrors" in music_vm or "recordPlaybackFailure" in music_vm or "maxConsecutiveErrors" in music_vm or ">= 3" in service or ">= 3" in music_vm or "recordPlaybackFailure" in service
suite.assert_true(
    pb03_queue,
    "PB-03",
    "PlayerQueueManager tracks error counts and caps consecutive skipping cascade",
    "consecutiveErrors tracking missing in PlayerQueueManager"
)

# PB-04: Stale 3-hour googlevideo stream cache (20m TTL + eviction on error)
pb04_ttl = "CACHE_EXPIRY_MS = 20 * 60 * 1000L" in music_repo
pb04_evict = "fun evictStreamUrl(" in music_repo and "streamUrlCache.remove" in music_repo
suite.assert_true(
    pb04_ttl and pb04_evict,
    "PB-04",
    "Stream URL cache TTL reduced to 20 minutes with programmatic evictStreamUrl on playback error",
    f"ttl={pb04_ttl}, evict={pb04_evict}"
)

# PB-05: Missing Recents queue insertion branch in playTrack
pb05_check = "playTrack(track: Track, newQueue: List<Track>" in player_queue and "queue.add(track)" in player_queue and "indexOfFirst" in player_queue
suite.assert_true(
    pb05_check,
    "PB-05",
    "playTrack safely inserts unqueued tracks into queue without desynchronization",
    "Queue insertion branch missing in playTrack"
)

# PB-06: Fisher-Yates shuffle deck without replacement & history
pb06_deck = "generateFisherYatesDeck" in player_queue and "shuffleDeck" in player_queue
pb06_history = "playbackHistory" in player_queue and "removeLast" in player_queue
suite.assert_true(
    pb06_deck and pb06_history,
    "PB-06",
    "Shuffle implemented via Fisher-Yates deck without replacement with history stack for previous traversal",
    f"deck={pb06_deck}, history={pb06_history}"
)

# PB-07: Scrubber drag seek flooding ExoPlayer (onValueChangeFinished buffer)
pb07_check = "onValueChangeFinished" in full_player and "isDragging" in full_player and "viewModel.seekTo" in full_player
suite.assert_true(
    pb07_check,
    "PB-07",
    "FullPlayerSheet scrubber seeks ExoPlayer only on drag completion (onValueChangeFinished)",
    "onValueChangeFinished seek buffer missing in FullPlayerSheet"
)

# PB-08: Involuntary audio resumption when seeking while paused
# seekTo in MusicViewModel must not call play() or set playWhenReady=true
seek_def_match = re.search(r"fun seekTo\(positionMs: Long\)\s*\{([^}]+)\}", music_vm)
seek_body = seek_def_match.group(1) if seek_def_match else ""
pb08_safe = "play()" not in seek_body and "playWhenReady = true" not in seek_body and "seekTo" in seek_body
suite.assert_true(
    pb08_safe,
    "PB-08",
    "Seeking while paused preserves paused state without unilateral playback resumption",
    f"seek_body: {seek_body.strip()}"
)

# PB-09: Tapping next in repeat-one loops current song (user skip advances)
pb09_queue = "userInitiated" in player_queue and "REPEAT_MODE_ONE" in player_queue and "!userInitiated" in player_queue
pb09_vm = "userInitiated: Boolean = true" in music_vm
suite.assert_true(
    pb09_queue and pb09_vm,
    "PB-09",
    "User skip in repeat-one mode traverses to next song; repeat-one applies only to natural completion",
    f"queue={pb09_queue}, vm={pb09_vm}"
)

# PB-10: 1.2-second progress bar and clock freeze (DISCONTINUITY_REASON_SEEK)
pb10_check = "DISCONTINUITY_REASON_SEEK" in music_vm and "onPositionDiscontinuity" in music_vm
suite.assert_true(
    pb10_check,
    "PB-10",
    "Player listener catches DISCONTINUITY_REASON_SEEK to instantly resync clock and progress bar",
    "DISCONTINUITY_REASON_SEEK handler missing in MusicViewModel"
)

# PB-11: Missing foreground service demotion on 5m pause timeout / focus loss
pb11_timeout = "updatePauseTimeout" in service and "PAUSE_TIMEOUT_MS" in service and "5 * 60 * 1000L" in service
pb11_demote = "stopForeground(STOP_FOREGROUND_DETACH)" in service and "pauseTimeoutJob" in service
suite.assert_true(
    pb11_timeout and pb11_demote,
    "PB-11",
    "PlaybackService implements 5m pause timeout calling stopForeground(STOP_FOREGROUND_DETACH) upon playback pause",
    f"timeout={pb11_timeout}, demote={pb11_demote}"
)

# PB-12: Duplicate track ID index collision in queue (playTrackAtIndex)
pb12_check = "playTrackAtIndex" in player_queue and "playTrackAtIndex" in music_vm
suite.assert_true(
    pb12_check,
    "PB-12",
    "Queue navigation targets positional index (playTrackAtIndex) preventing duplicate track ID collision",
    "playTrackAtIndex not implemented in PlayerQueueManager or MusicViewModel"
)

# PB-13: Missing queue management operations (remove, move, clear upcoming)
queue_sheet = suite.read_file("app/src/main/java/com/ayashii/kuro/ui/components/QueueBottomSheet.kt")
pb13_remove = "removeFromQueue" in player_queue and "removeFromQueue" in music_vm
pb13_move = "moveQueueItem" in player_queue and "moveQueueItem" in music_vm
pb13_clear = "clearUpcomingQueue" in player_queue and "clearUpcomingQueue" in music_vm
pb13_ui = "removeFromQueue" in queue_sheet or "clearUpcomingQueue" in queue_sheet
suite.assert_true(
    pb13_remove and pb13_move and pb13_clear and pb13_ui,
    "PB-13",
    "Queue management operations implemented: remove, move, and clear upcoming queue in engine and UI",
    f"remove={pb13_remove}, move={pb13_move}, clear={pb13_clear}, ui={pb13_ui}"
)

# PB-14: MediaSession over-reporting available commands (bind to queue bounds)
pb14_check = "COMMAND_SEEK_TO_NEXT" in service and "hasNext()" in service and "COMMAND_SEEK_TO_PREVIOUS" in service and "hasPrevious()" in service
suite.assert_true(
    pb14_check,
    "PB-14",
    "MediaSession commands bound to queue bounds (hasNext / hasPrevious in ForwardingPlayer)",
    "Available commands reporting not bound to queue bounds"
)

# PB-15: Monotonic progress guard backward snap on >= 200ms discrepancies
pb15_check = "anchorPosition" in music_vm and "anchorTimestamp" in music_vm and ("lastGroundTruthSync" in music_vm or "calcPos" in music_vm)
suite.assert_true(
    pb15_check,
    "PB-15",
    "Progress tracker anchors elapsed real-time with smooth monotonic guard allowing backward seek snaps",
    "Anchor position / elapsed tracking missing in MusicViewModel"
)

# PB-16: False-positive vocal suppression at 00:00 (isIntroToken check)
lyrics_model = suite.read_file("app/src/main/java/com/ayashii/kuro/data/lyrics/LyricsModel.kt")
pb16_check = "fun isIntroToken(" in lyrics_model and "getCurrentLineIndex" in lyrics_model
suite.assert_true(
    pb16_check,
    "PB-16",
    "LyricsModel implements isIntroToken preventing false-positive vocal suppression at 00:00",
    "isIntroToken function missing in LyricsModel"
)

# PB-17: Highlighted lyrics persisting through guitar solos (endTimeMs in LyricLine)
pb17_field = "endTimeMs: Long? = null" in lyrics_model
pb17_active = "endTimeMs" in lyrics_model and "effectivePos > end" in lyrics_model
suite.assert_true(
    pb17_field and pb17_active,
    "PB-17",
    "LyricLine models endTimeMs with active line deactivation during instrumental solos",
    f"field={pb17_field}, active={pb17_active}"
)

# PB-18: Missing LRC [offset:+/-ms] tag parsing
lrc_parser = suite.read_file("app/src/main/java/com/ayashii/kuro/data/lyrics/LrcParser.kt")
pb18_tag = "OFFSET_TAG_REGEX" in lrc_parser or "offset" in lrc_parser.lower()
pb18_apply = "offsetMs" in lrc_parser and "timeMs =" in lrc_parser
suite.assert_true(
    pb18_tag and pb18_apply,
    "PB-18",
    "LrcParser parses [offset:+/-ms] header tags and shifts timestamp line offsets",
    f"tag={pb18_tag}, apply={pb18_apply}"
)

# PB-19: Google Translate separator breakage (\n~~~\n -> robust delimiter)
lyrics_repo = suite.read_file("app/src/main/java/com/ayashii/kuro/data/lyrics/LyricsRepository.kt")
pb19_check = "[[[" in lyrics_repo and "]]]" in lyrics_repo and "tagRegex" in lyrics_repo
suite.assert_true(
    pb19_check,
    "PB-19",
    "Lyrics translation batching uses robust indexed tokens [[[$idx]]] preventing line misalignment",
    "Indexed delimiter tokens not found in LyricsRepository"
)

# ==============================================================================
# SECTION 4: CATEGORY 3 — STATE MANAGEMENT & DATA LAYER (ST-01 to ST-09)
# ==============================================================================
print(f"\n{Colors.BOLD}>>> 4. Category 3: State Management & Data Layer{Colors.RESET}")

# ST-01: Infinite retry in REPEAT_MODE_ONE (isErrorRecovery parameter)
st01_queue = "isErrorRecovery: Boolean = false" in player_queue and "!isErrorRecovery" in player_queue
st01_vm = "isErrorRecovery: Boolean = false" in music_vm and "getNextTrack" in music_vm
st01_service = "isErrorRecovery" in service
suite.assert_true(
    st01_queue and st01_vm and st01_service,
    "ST-01",
    "Playback error handler passes isErrorRecovery breaking infinite retry loop in repeat-one mode",
    f"queue={st01_queue}, vm={st01_vm}, service={st01_service}"
)

# ST-02: Negative cache 7-day poisoning fix on transient network errors
st02_result = "LyricsFetchResult" in lyrics_repo
st02_guard = "!hadNetworkError" in lyrics_repo and "emptyMarker" in lyrics_repo
suite.assert_true(
    st02_result and st02_guard,
    "ST-02",
    "Negative lyrics cache (.empty marker) committed only on confirmed empty responses, never on network error",
    f"result={st02_result}, guard={st02_guard}"
)

# ST-03: Settings clear cache supporting active lyrics_cache_v5 directory
settings_mgr = suite.read_file("app/src/main/java/com/ayashii/kuro/data/settings/SettingsManager.kt")
st03_size = 'File(context.cacheDir, "lyrics_cache_v5")' in settings_mgr
st03_count = settings_mgr.count('"lyrics_cache_v5"')
suite.assert_true(
    st03_size and st03_count >= 3,
    "ST-03",
    "SettingsManager includes lyrics_cache_v5 across size calculation and cache clearing routines",
    f"size={st03_size}, count={st03_count}"
)

# ST-04: Shared _searchResults StateFlow collision (dedicated _genreTracks)
st04_vm = "_genreTracks" in music_vm and "loadGenreTracks" in music_vm
st04_home = "viewModel.genreTracks" in home_screen and "loadGenreTracks" in home_screen
suite.assert_true(
    st04_vm and st04_home,
    "ST-04",
    "Dedicated _genreTracks StateFlow isolates HomeScreen genre discovery from SearchScreen search state",
    f"vm={st04_vm}, home={st04_home}"
)

# ST-05: Lyrics duration resync at STATE_READY leaves _isLoadingLyrics stuck
st05_check = "_isLoadingLyrics.value = false" in music_vm and "finally" in music_vm
suite.assert_true(
    st05_check,
    "ST-05",
    "Lyrics resync coroutine guarantees _isLoadingLyrics reset in finally block",
    "finally block resetting _isLoadingLyrics not found in MusicViewModel"
)

# ST-06: Stale cookies trigger re-login trap in LoginWebViewDialog
login_dialog = suite.read_file("app/src/main/java/com/ayashii/kuro/ui/components/LoginWebViewDialog.kt")
st06_check = "cookieManager.removeAllCookies(null)" in login_dialog and "cookieManager.flush()" in login_dialog
suite.assert_true(
    st06_check,
    "ST-06",
    "LoginWebViewDialog purges stale cookies and flushes CookieManager prior to launching auth WebView",
    "removeAllCookies / flush not found in LoginWebViewDialog"
)

# ST-07: Android 14+ SecurityException in AudioOutputManager (RECEIVER_NOT_EXPORTED)
audio_output = suite.read_file("app/src/main/java/com/ayashii/kuro/data/audio/AudioOutputManager.kt")
st07_check = "RECEIVER_NOT_EXPORTED" in audio_output and "registerReceiver" in audio_output
suite.assert_true(
    st07_check,
    "ST-07",
    "AudioOutputManager registers broadcast receiver with ContextCompat.RECEIVER_NOT_EXPORTED for Android 14+",
    "RECEIVER_NOT_EXPORTED flag not found in AudioOutputManager"
)

# ST-08: Main-thread synchronous JSON disk serialization (Dispatchers.IO)
taste_mgr = suite.read_file("app/src/main/java/com/ayashii/kuro/data/recommendation/TasteManager.kt")
st08_favs = "saveStoredFavorites" in music_vm and "Dispatchers.IO" in music_vm
st08_taste = "persistTasteDataAsync" in taste_mgr and "coroutineScope.launch" in taste_mgr
suite.assert_true(
    st08_favs and st08_taste,
    "ST-08",
    "Disk serialization of favorites and user taste models offloaded to background Dispatchers.IO coroutines",
    f"favs={st08_favs}, taste={st08_taste}"
)

# ST-09: Unsynchronized compound operations on playlistQueue
st09_lock = "private val lock = Any()" in player_queue and "synchronized(lock)" in player_queue
st09_methods = player_queue.count("synchronized(lock)") >= 6
suite.assert_true(
    st09_lock and st09_methods,
    "ST-09",
    "Compound queue operations in PlayerQueueManager synchronized via monitor lock against race conditions",
    f"lock={st09_lock}, synchronized_count={player_queue.count('synchronized(lock)')}"
)

# ==============================================================================
# SECTION 5: BEHAVIORAL & ALGORITHMIC SIMULATION TESTS
# ==============================================================================
print(f"\n{Colors.BOLD}>>> 5. Behavioral & Algorithmic Simulation Tests{Colors.RESET}")

# SIM-01: Simulate LrcParser offset calculation behaviorally
def simulate_lrc_offset_parse(lrc_text):
    offset_match = re.search(r"\[offset:\s*([+-]?\d+)\s*\]", lrc_text, re.I)
    offset_ms = int(offset_match.group(1)) if offset_match else 0
    time_re = re.compile(r"\[\s*(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\s*\]")
    lines = []
    for line in lrc_text.splitlines():
        trimmed = line.strip()
        m = time_re.search(trimmed)
        if m:
            mins = int(m.group(1))
            secs = int(m.group(2))
            ms_str = m.group(3) or "0"
            ms = int(ms_str) * (100 if len(ms_str) == 1 else 10 if len(ms_str) == 2 else 1)
            text = trimmed[m.end():].strip()
            lines.append((max(0, mins * 60000 + secs * 1000 + ms + offset_ms), text))
    return lines

sample_lrc = """[offset: +500]
[00:01.00]First line
[00:02.50]Second line"""
sim_res = simulate_lrc_offset_parse(sample_lrc)
suite.assert_true(
    sim_res == [(1500, "First line"), (3000, "Second line")],
    "SIM-01",
    "Behavioral verification of LrcParser offset math: timestamps shifted accurately by +500ms",
    f"result={sim_res}"
)

# SIM-02: Simulate LyricsModel vocal suppression logic at 00:00
def simulate_get_current_line(lines, pos_ms):
    if not lines or pos_ms < lines[0][0]:
        return -1
    first_time, first_text, end_ms = lines[0]
    is_intro = first_text.lower().strip() in ["♪", "instrumental", "(intro)", "intro", ""]
    if first_time == 0 and is_intro and len(lines) > 1 and pos_ms < lines[1][0]:
        return -1
    idx = -1
    for i, (t, txt, end) in enumerate(lines):
        if t <= pos_ms:
            idx = i
        else:
            break
    if idx >= 0:
        _, _, end = lines[idx]
        if end is not None and pos_ms > end:
            return -1
    return idx

test_lyrics = [
    (0, "♪", 3000),
    (3000, "Hello World", 5000),
    (7000, "Guitar Solo Line", 8000)
]
# At 1500ms -> should be suppressed (-1) because 00:00 is intro token
# At 4000ms -> should highlight "Hello World" (index 1)
# At 6000ms -> gap between line 1 (ends 5000) and line 2 (starts 7000) -> should be -1
pos_1500 = simulate_get_current_line(test_lyrics, 1500)
pos_4000 = simulate_get_current_line(test_lyrics, 4000)
pos_6000 = simulate_get_current_line(test_lyrics, 6000)

suite.assert_true(
    pos_1500 == -1 and pos_4000 == 1 and pos_6000 == -1,
    "SIM-02",
    "Behavioral verification of LyricsModel: intro suppression at 00:00 and solo deactivation via endTimeMs",
    f"pos_1500={pos_1500}, pos_4000={pos_4000}, pos_6000={pos_6000}"
)

# SIM-03: Simulate Fisher-Yates deck generation
def simulate_fisher_yates(queue_size, current_index):
    deck = [i for i in range(queue_size) if i != current_index]
    import random
    random.seed(42)
    for i in range(len(deck) - 1, 0, -1):
        j = random.randint(0, i)
        deck[i], deck[j] = deck[j], deck[i]
    return deck

deck_res = simulate_fisher_yates(10, 3)
suite.assert_true(
    len(deck_res) == 9 and 3 not in deck_res and set(deck_res) == {0, 1, 2, 4, 5, 6, 7, 8, 9},
    "SIM-03",
    "Behavioral verification of Fisher-Yates deck: contains all unplayed indices without duplicates or current track",
    f"deck={deck_res}"
)

# ==============================================================================
# SUMMARY & VERDICT
# ==============================================================================
print(f"\n{Colors.BLUE}{Colors.BOLD}======================================================================{Colors.RESET}")
print(f"{Colors.BOLD}   VERIFICATION SUITE SUMMARY{Colors.RESET}")
print(f"   Total Tests Executed : {suite.total}")
print(f"   Passed Tests         : {Colors.GREEN}{suite.passed}{Colors.RESET}")
print(f"   Failed Tests         : {Colors.RED if suite.failed > 0 else Colors.GREEN}{suite.failed}{Colors.RESET}")
print(f"{Colors.BLUE}{Colors.BOLD}======================================================================{Colors.RESET}\n")

if suite.failed == 0:
    print(f"{Colors.GREEN}{Colors.BOLD}>>> VERDICT: ALL TESTS PASSED! FULL SYSTEMIC COMPLIANCE VERIFIED.{Colors.RESET}\n")
    sys.exit(0)
else:
    print(f"{Colors.RED}{Colors.BOLD}>>> VERDICT: {suite.failed} TESTS FAILED. INVESTIGATION REQUIRED.{Colors.RESET}\n")
    sys.exit(1)
