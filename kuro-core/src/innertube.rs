use crate::sanitizer::clean_title;
use serde::{Deserialize, Serialize};

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
            title: clean_title(raw_title),
            artist: raw_artist.replace(" - Topic", "").trim().to_string(),
            url: format!("https://www.youtube.com/watch?v={}", video_id),
            thumbnail_url: upgrade_thumbnail(thumb_url),
            duration_seconds: dur_sec,
        });
    }

    tracks
}

fn parse_duration_to_seconds(duration_str: &str) -> i64 {
    if duration_str.is_empty() {
        return 0;
    }
    let parts: Vec<&str> = duration_str.split(':').collect();
    match parts.len() {
        2 => {
            let m: i64 = parts[0].trim().parse().unwrap_or(0);
            let s: i64 = parts[1].trim().parse().unwrap_or(0);
            (m * 60) + s
        }
        3 => {
            let h: i64 = parts[0].trim().parse().unwrap_or(0);
            let m: i64 = parts[1].trim().parse().unwrap_or(0);
            let s: i64 = parts[2].trim().parse().unwrap_or(0);
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
