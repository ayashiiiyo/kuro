# Kuro hybrid UI — direction cards

Scope: Android music player home screen; preserve Kuro monochrome palette and Outfit/Fredoka typography. Performance intent: fewer simultaneously animated surfaces, thumbnail-sized shelf art, no decorative motion, one persistent mini-player.

1. **Hybrid / Quick Picks** — Spotify's familiar/recent shortcuts first, followed by YouTube Music-style large visual shelves. Featured assets are actual album-like music imagery; black canvas stays dominant. Form from music listening: familiar tracks should be one tap away.
2. **Hybrid / Listening Now** — prominent now-playing artwork and controls lead, then compact browse shelves. A calmer listening-first arrangement; artwork becomes the screen's visual anchor. Form from the persistent playback thread.
3. **Hybrid / Editorial Shelves** — typographic greeting and genre chips lead into asymmetric quick-pick rows, then an expansive discovery shelf. Uses editorial spacing rather than a dense grid. Form from a record-store browse wall.

Reference logic: Spotify mobile home's familiar content shortcuts and persistent playback bar (9to5Google, 2020; TYPENORM Spotify overview); YouTube Music's reduced shelf titles and removed redundant subtitles (9to5Google, 2024). References are structural only; no competitor palette/assets are used. Existing Kuro tokens lifted from Theme.kt / Type.kt: #000000 background, #161616 card, #262626 border, white/gray type, Outfit body, Fredoka display.

Performance notes for implementation after direction approval: replace 1080px artwork URLs in 50dp/144dp slots with bounded thumbnail requests; cap Coil memory cache; remove/restrict per-row infinite equalizer animation; avoid running high-frequency progress updates when player is idle. Keep playback foreground behavior unchanged.
