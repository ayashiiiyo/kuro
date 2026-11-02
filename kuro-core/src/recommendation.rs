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

pub fn rank_candidates(mut candidates: Vec<CandidateTrack>, profile: &TasteProfile) -> Vec<CandidateTrack> {
    if candidates.is_empty() {
        return candidates;
    }

    let top_artists_set: HashSet<String> = profile
        .top_artists
        .iter()
        .map(|a| a.trim().to_lowercase())
        .collect();

    candidates.sort_by(|a, b| {
        let score_a = calculate_affinity_score(a, profile, &top_artists_set);
        let score_b = calculate_affinity_score(b, profile, &top_artists_set);
        score_b.cmp(&score_a)
    });

    candidates
}

fn calculate_affinity_score(
    track: &CandidateTrack,
    profile: &TasteProfile,
    top_artists: &HashSet<String>,
) -> i32 {
    let mut score = *profile.scores.get(&track.id).unwrap_or(&0);
    if score < 0 {
        score = 0;
    }

    let artist_clean = track.artist.trim().to_lowercase();
    if top_artists.contains(&artist_clean) {
        score += 5;
    }

    score
}
