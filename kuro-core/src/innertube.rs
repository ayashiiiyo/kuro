use crate::sanitizer::clean_title;
use serde::{Deserialize, Serialize};
use std::collections::HashSet;

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct ParsedTrack {
    pub id: String,
    pub title: String,
    pub artist: String,
    pub url: String,
    #[serde(rename = "thumbnailUrl")]
    pub thumbnail_url: String,
    #[serde(rename = "durationSeconds")]
    pub duration_seconds: i64,
}

pub fn pack_tracks(tracks: &[ParsedTrack]) -> String {
    if tracks.is_empty() {
        return String::new();
    }
    let mut out = String::with_capacity(tracks.len() * 128);
    for (idx, t) in tracks.iter().enumerate() {
        if idx > 0 {
            out.push('\n');
        }
        let clean_id = t.id.replace(['\x1F', '\n', '\r'], "");
        let clean_t = t.title.replace(['\x1F', '\n', '\r'], " ");
        let clean_a = t.artist.replace(['\x1F', '\n', '\r'], " ");
        let clean_u = t.url.replace(['\x1F', '\n', '\r'], "");
        let clean_th = t.thumbnail_url.replace(['\x1F', '\n', '\r'], "");
        out.push_str(&clean_id);
        out.push('\x1F');
        out.push_str(&clean_t);
        out.push('\x1F');
        out.push_str(&clean_a);
        out.push('\x1F');
        out.push_str(&clean_u);
        out.push('\x1F');
        out.push_str(&clean_th);
        out.push('\x1F');
        out.push_str(&t.duration_seconds.to_string());
    }
    out
}

pub fn parse_automix_radio(json_str: &str, current_video_id: &str) -> Vec<ParsedTrack> {
    let mut tracks = Vec::new();
    let val: serde_json::Value = match serde_json::from_str(json_str) {
        Ok(v) => v,
        Err(_) => return tracks,
    };

    let tabs = val
        .pointer("/contents/singleColumnMusicWatchNextResultsRenderer/tabbedRenderer/watchNextTabbedResultsRenderer/tabs")
        .and_then(|v| v.as_array());

    let tabs = match tabs {
        Some(t) => t,
        None => return tracks,
    };

    let mut queue_tab: Option<&serde_json::Value> = None;
    for t in tabs {
        if let Some(content) = t.pointer("/tabRenderer/content/musicQueueRenderer") {
            queue_tab = Some(content);
            break;
        }
    }

    if queue_tab.is_none() {
        if let Some(first_tab) = tabs.get(0) {
            queue_tab = first_tab.pointer("/tabRenderer/content/musicQueueRenderer");
        }
    }

    let items = queue_tab
        .and_then(|q| q.pointer("/content/playlistPanelRenderer/contents"))
        .and_then(|c| c.as_array());

    let items = match items {
        Some(it) => it,
        None => return tracks,
    };

    let mut seen_titles = HashSet::new();

    for item in items {
        let renderer = match item.get("playlistPanelVideoRenderer") {
            Some(r) => r,
            None => continue,
        };

        let video_id = renderer
            .get("videoId")
            .and_then(|v| v.as_str())
            .unwrap_or("");

        if video_id.is_empty() || video_id == current_video_id {
            continue;
        }

        let raw_title = renderer
            .pointer("/title/runs/0/text")
            .and_then(|v| v.as_str())
            .unwrap_or("");

        let cleaned_title = clean_title(raw_title);
        let norm_title: String = cleaned_title
            .to_lowercase()
            .chars()
            .filter(|c| c.is_alphanumeric())
            .collect();
        if !norm_title.is_empty() && !seen_titles.insert(norm_title) {
            continue;
        }

        let raw_artist = renderer
            .pointer("/longBylineText/runs/0/text")
            .and_then(|v| v.as_str())
            .unwrap_or("Unknown Artist");

        let dur_str = renderer
            .pointer("/lengthText/runs/0/text")
            .and_then(|v| v.as_str())
            .unwrap_or("");

        let dur_sec = parse_duration_to_seconds(dur_str);

        let thumb_url = renderer
            .pointer("/thumbnail/thumbnails")
            .and_then(|t| t.as_array())
            .and_then(|arr| arr.last())
            .and_then(|thumb| thumb.get("url"))
            .and_then(|u| u.as_str())
            .unwrap_or("");

        tracks.push(ParsedTrack {
            id: video_id.to_string(),
            title: cleaned_title,
            artist: raw_artist.replace(" - Topic", "").trim().to_string(),
            url: format!("https://www.youtube.com/watch?v={}", video_id),
            thumbnail_url: upgrade_thumbnail(thumb_url),
            duration_seconds: dur_sec,
        });
    }

    tracks
}

pub fn parse_ytm_search(json_str: &str) -> Vec<ParsedTrack> {
    let mut tracks = Vec::new();
    let mut seen_ids = HashSet::new();
    let val: serde_json::Value = match serde_json::from_str(json_str) {
        Ok(v) => v,
        Err(_) => return tracks,
    };

    let tabs = match val
        .pointer("/contents/tabbedSearchResultsRenderer/tabs")
        .and_then(|v| v.as_array())
    {
        Some(t) => t,
        None => return tracks,
    };

    for tab in tabs {
        let sections = match tab
            .pointer("/tabRenderer/content/sectionListRenderer/contents")
            .and_then(|v| v.as_array())
        {
            Some(s) => s,
            None => continue,
        };

        for sec in sections {
            if let Some(card_shelf) = sec.get("musicCardShelfRenderer") {
                for trk in parse_card_shelf(card_shelf) {
                    if !trk.id.is_empty() && seen_ids.insert(trk.id.clone()) {
                        tracks.push(trk);
                    }
                }
            }

            if let Some(items) = sec
                .pointer("/musicShelfRenderer/contents")
                .and_then(|v| v.as_array())
            {
                for item in items {
                    if let Some(trk) = parse_responsive_item(item) {
                        if !trk.id.is_empty() && seen_ids.insert(trk.id.clone()) {
                            tracks.push(trk);
                        }
                    }
                }
            }
        }
    }

    tracks
}

fn parse_card_shelf(card_shelf: &serde_json::Value) -> Vec<ParsedTrack> {
    let mut out = Vec::new();
    let title = card_shelf
        .pointer("/title/runs/0/text")
        .and_then(|v| v.as_str())
        .unwrap_or("");

    let mut video_id = card_shelf
        .pointer("/title/runs/0/navigationEndpoint/watchEndpoint/videoId")
        .and_then(|v| v.as_str())
        .unwrap_or("");

    if video_id.is_empty() {
        video_id = card_shelf
            .pointer("/onTap/watchEndpoint/videoId")
            .and_then(|v| v.as_str())
            .unwrap_or("");
    }

    if video_id.is_empty() {
        if let Some(buttons) = card_shelf.get("buttons").and_then(|b| b.as_array()) {
            for b in buttons {
                if let Some(vid) = b
                    .pointer("/buttonRenderer/command/watchEndpoint/videoId")
                    .and_then(|v| v.as_str())
                {
                    if !vid.is_empty() {
                        video_id = vid;
                        break;
                    }
                }
            }
        }
    }

    if !video_id.is_empty() && !title.is_empty() {
        let mut artist = String::from("Unknown Artist");
        let mut dur_sec = 0i64;
        if let Some(runs) = card_shelf.pointer("/subtitle/runs").and_then(|r| r.as_array()) {
            for run in runs {
                let txt = run.get("text").and_then(|t| t.as_str()).unwrap_or("").trim();
                if txt.is_empty() {
                    continue;
                }
                let d = parse_duration_to_seconds(txt);
                if d > 0 && dur_sec <= 0 {
                    dur_sec = d;
                    continue;
                }
                if !is_metadata_noise(txt) && artist == "Unknown Artist" {
                    artist = txt.replace(" - Topic", "").trim().to_string();
                }
            }
        }
        let thumb_url = card_shelf
            .pointer("/thumbnail/musicThumbnailRenderer/thumbnail/thumbnails")
            .and_then(|t| t.as_array())
            .and_then(|arr| arr.last())
            .and_then(|th| th.get("url"))
            .and_then(|u| u.as_str())
            .unwrap_or("");

        out.push(ParsedTrack {
            id: video_id.to_string(),
            title: clean_title(title),
            artist,
            url: format!("https://www.youtube.com/watch?v={}", video_id),
            thumbnail_url: upgrade_thumbnail(thumb_url),
            duration_seconds: dur_sec,
        });
    }

    if let Some(sub_contents) = card_shelf.get("contents").and_then(|c| c.as_array()) {
        for item in sub_contents {
            if let Some(t) = parse_responsive_item(item) {
                out.push(t);
            }
        }
    }

    out
}

fn parse_responsive_item(item: &serde_json::Value) -> Option<ParsedTrack> {
    let r = item.get("musicResponsiveListItemRenderer")?;
    let cols = r.get("flexColumns")?.as_array()?;
    if cols.is_empty() {
        return None;
    }

    let title = cols
        .get(0)?
        .pointer("/musicResponsiveListItemFlexColumnRenderer/text/runs/0/text")?
        .as_str()?
        .trim();
    if title.is_empty() {
        return None;
    }

    let video_id = extract_video_id(r, cols);
    if video_id.is_empty() {
        return None;
    }

    let fixed_cols = r.get("fixedColumns").and_then(|f| f.as_array());
    let (artist, dur_sec) = extract_artist_and_duration(cols, fixed_cols);

    let thumb_url = r
        .pointer("/thumbnail/musicThumbnailRenderer/thumbnail/thumbnails")
        .and_then(|t| t.as_array())
        .and_then(|arr| arr.last())
        .and_then(|th| th.get("url"))
        .and_then(|u| u.as_str())
        .unwrap_or("");

    Some(ParsedTrack {
        id: video_id,
        title: clean_title(title),
        artist,
        url: String::new(),
        thumbnail_url: upgrade_thumbnail(thumb_url),
        duration_seconds: dur_sec,
    })
    .map(|mut t| {
        t.url = format!("https://www.youtube.com/watch?v={}", t.id);
        t
    })
}

fn extract_video_id(r: &serde_json::Value, cols: &[serde_json::Value]) -> String {
    if let Some(vid) = r.pointer("/playlistItemData/videoId").and_then(|v| v.as_str()) {
        if !vid.is_empty() {
            return vid.to_string();
        }
    }
    for col in cols {
        if let Some(runs) = col
            .pointer("/musicResponsiveListItemFlexColumnRenderer/text/runs")
            .and_then(|v| v.as_array())
        {
            for run in runs {
                if let Some(vid) = run
                    .pointer("/navigationEndpoint/watchEndpoint/videoId")
                    .and_then(|v| v.as_str())
                {
                    if !vid.is_empty() {
                        return vid.to_string();
                    }
                }
            }
        }
    }
    if let Some(vid) = r
        .pointer("/overlay/musicItemThumbnailOverlayRenderer/content/musicPlayButtonRenderer/playNavigationEndpoint/watchEndpoint/videoId")
        .and_then(|v| v.as_str())
    {
        if !vid.is_empty() {
            return vid.to_string();
        }
    }
    if let Some(vid) = r
        .pointer("/navigationEndpoint/watchEndpoint/videoId")
        .and_then(|v| v.as_str())
    {
        if !vid.is_empty() {
            return vid.to_string();
        }
    }
    if let Some(items) = r.pointer("/menu/menuRenderer/items").and_then(|v| v.as_array()) {
        for m in items {
            if let Some(vid) = m
                .pointer("/menuNavigationItemRenderer/navigationEndpoint/watchEndpoint/videoId")
                .and_then(|v| v.as_str())
            {
                if !vid.is_empty() {
                    return vid.to_string();
                }
            }
        }
    }
    String::new()
}

fn extract_artist_and_duration(
    cols: &[serde_json::Value],
    fixed_cols: Option<&Vec<serde_json::Value>>,
) -> (String, i64) {
    let mut artist = String::new();
    let mut fallback_artist = String::new();
    let mut duration_sec = 0i64;

    if let Some(fcols) = fixed_cols {
        for f in fcols {
            if let Some(runs) = f
                .pointer("/musicResponsiveListItemFixedColumnRenderer/text/runs")
                .and_then(|v| v.as_array())
            {
                for run in runs {
                    let txt = run.get("text").and_then(|v| v.as_str()).unwrap_or("").trim();
                    let d = parse_duration_to_seconds(txt);
                    if d > 0 {
                        duration_sec = d;
                        break;
                    }
                }
            }
        }
    }

    for col in cols.iter().skip(1) {
        let runs = match col
            .pointer("/musicResponsiveListItemFlexColumnRenderer/text/runs")
            .and_then(|v| v.as_array())
        {
            Some(r) => r,
            None => continue,
        };
        for run in runs {
            let txt = run.get("text").and_then(|v| v.as_str()).unwrap_or("").trim();
            if txt.is_empty() {
                continue;
            }
            let d = parse_duration_to_seconds(txt);
            if d > 0 && duration_sec <= 0 {
                duration_sec = d;
                continue;
            }
            if is_metadata_noise(txt) {
                continue;
            }
            let browse_id = run
                .pointer("/navigationEndpoint/browseEndpoint/browseId")
                .and_then(|v| v.as_str())
                .unwrap_or("");
            if browse_id.starts_with("UC") || browse_id.to_ascii_lowercase().contains("artist") {
                if artist.is_empty() {
                    artist = txt.to_string();
                }
            } else if fallback_artist.is_empty() {
                fallback_artist = txt.to_string();
            }
        }
    }

    let chosen = if !artist.is_empty() {
        artist
    } else if !fallback_artist.is_empty() {
        fallback_artist
    } else {
        String::from("Unknown Artist")
    };

    (chosen.replace(" - Topic", "").trim().to_string(), duration_sec)
}

fn is_metadata_noise(text: &str) -> bool {
    let lower = text.trim().to_lowercase();
    if lower.is_empty() {
        return true;
    }
    if matches!(
        lower.as_str(),
        "song"
            | "video"
            | "lagu"
            | "video musik"
            | "music video"
            | "single"
            | "album"
            | "ep"
            | "episode"
            | "podcast"
            | "playlist"
            | "artist"
            | "artis"
            | "•"
            | "·"
            | "-"
            | "|"
            | "explicit"
            | "e"
    ) {
        return true;
    }
    if parse_duration_to_seconds(&lower) > 0 {
        return true;
    }
    if lower.contains("views")
        || lower.contains("ditonton")
        || lower.contains("plays")
        || lower.contains("diputar")
        || lower.contains("rb x")
        || lower.contains("jt x")
    {
        return true;
    }
    false
}

fn parse_duration_to_seconds(duration_str: &str) -> i64 {
    if duration_str.is_empty() {
        return 0;
    }
    let normalized = duration_str.trim().replace('.', ":");
    let parts: Vec<&str> = normalized.split(':').collect();
    if parts.len() != 2 && parts.len() != 3 {
        return 0;
    }
    if !parts.iter().all(|p| !p.is_empty() && p.len() <= 2 && p.bytes().all(|b| b.is_ascii_digit())) {
        return 0;
    }
    match parts.len() {
        2 => {
            let m: i64 = parts[0].parse().unwrap_or(0);
            let s: i64 = parts[1].parse().unwrap_or(0);
            (m * 60) + s
        }
        3 => {
            let h: i64 = parts[0].parse().unwrap_or(0);
            let m: i64 = parts[1].parse().unwrap_or(0);
            let s: i64 = parts[2].parse().unwrap_or(0);
            (h * 3600) + (m * 60) + s
        }
        _ => 0,
    }
}

fn upgrade_thumbnail(raw: &str) -> String {
    if raw.is_empty() {
        return String::new();
    }
    let mut u = raw.trim().to_string();
    if u.starts_with("//") {
        u = format!("https:{}", u);
    }
    if u.contains("googleusercontent.com") {
        if u.contains("=") {
            let base = u.split('=').next().unwrap_or(&u);
            return format!("{}=w1080-h1080-l90-rj", base);
        } else {
            return format!("{}=w1080-h1080-l90-rj", u);
        }
    }
    if u.contains("ytimg.com") {
        return u
            .replace("/default.jpg", "/hqdefault.jpg")
            .replace("/default.webp", "/hqdefault.webp")
            .replace("/mqdefault.jpg", "/hqdefault.jpg")
            .replace("/mqdefault.webp", "/hqdefault.webp");
    }
    u
}
