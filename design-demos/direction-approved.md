# Direction gate — approved

The user asked to redo the earlier three concepts because they looked alike. New comparison screenshot: `/root/kuro-design-directions.png`; source preview: `thumbs.html`.

Previous project reference remains `/root/desain-kurov2.png`, concept 2 (“Modern & Elegant”), but this new three-way comparison is not yet selected.

User selected direction 2, exact wording: “2 sih...tapi apakah preview sama hasil nanti bakal beda?”

User also approved the Home preview after asking for it and responding “Oke Approved udah kamu ubah kodenya?” Home implementation is now authorized to follow `home-preview.html`.

Implement direction 2: Modern & Elegan, with large full-bleed artwork, title/artist and favorite, consistent playback controls, secondary actions, synced lyrics card, black Kuro background and restrained red favorite accent. Preserve all existing functionality and keep performance work bounded to artwork/cache and progress polling.

Fidelity expectation: match the approved structure and hierarchy, not pixel-perfect output. Real Android rendering, device aspect ratio/insets, user-selected track art and live lyric state will naturally change the exact pixels. Build/runtime screenshot validation requires Android SDK, which is currently unavailable.
