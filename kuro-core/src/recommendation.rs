use crate::sanitizer::clean_title;
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet};

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct CandidateTrack {
    pub id: String,
    pub title: String,
    pub artist: String,
    pub url: String,
    #[serde(rename = "thumbnailUrl")]
    pub thumbnail_url: String,
    #[serde(rename = "durationSeconds")]
    pub duration_seconds: i64,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct TasteProfile {
    #[serde(default)]
    pub scores: HashMap<String, i32>,
    #[serde(default)]
    pub top_artists: Vec<String>,
}

fn normalize_title_key(raw: &str) -> String {
    let cleaned = clean_title(raw).to_lowercase();
    let mut out = String::with_capacity(cleaned.len());
    for ch in cleaned.chars() {
        if ch.is_alphanumeric() {
            out.push(ch);
        }
    }
    out
}

/**
 * Preserves YouTube Music's native neural-network radio ordering while
 * deduplicating identical song titles (covers/alternate uploads) and
 * pushing repeatedly skipped tracks (negative score) to the end.
 */
pub fn rank_candidates(candidates: Vec<CandidateTrack>, profile: &TasteProfile) -> Vec<CandidateTrack> {
    if candidates.len() <= 1 {
        return candidates;
    }

    let mut primary = Vec::with_capacity(candidates.len());
    let mut skipped_tail = Vec::new();
    let mut seen_titles = HashSet::new();

    for track in candidates {
        let title_key = normalize_title_key(&track.title);
        if !title_key.is_empty() && !seen_titles.insert(title_key) {
            continue;
        }
        let raw_score = *profile.scores.get(&track.id).unwrap_or(&0);
        if raw_score <= -4 {
            skipped_tail.push(track);
        } else {
            primary.push(track);
        }
    }

    primary.extend(skipped_tail);
    primary
}
