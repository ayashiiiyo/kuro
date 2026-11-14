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

pub fn rank_candidates(candidates: Vec<CandidateTrack>, profile: &TasteProfile) -> Vec<CandidateTrack> {
    if candidates.len() <= 1 {
        return candidates;
    }

    let top_artists_set: HashSet<String> = profile
        .top_artists
        .iter()
        .map(|a| a.trim().to_lowercase())
        .collect();

    let total = candidates.len() as i32;
    let mut scored: Vec<(i32, usize, CandidateTrack)> = candidates
        .into_iter()
        .enumerate()
        .map(|(idx, track)| {
            let base_order_bonus = ((total - (idx as i32)) * 6) / total.max(1);
            let affinity = calculate_affinity_score(&track, profile, &top_artists_set);
            (base_order_bonus + affinity, idx, track)
        })
        .collect();

    scored.sort_by(|a, b| b.0.cmp(&a.0).then_with(|| a.1.cmp(&b.1)));

    /* De-cluster consecutive tracks by the same artist so radio feels dynamic */
    let mut result: Vec<CandidateTrack> = Vec::with_capacity(scored.len());
    let mut deferred: Vec<CandidateTrack> = Vec::new();
    let mut artist_counts: HashMap<String, usize> = HashMap::new();

    for (score, _, track) in scored {
        if score <= -6 {
            deferred.push(track);
            continue;
        }
        let artist_key = track.artist.trim().to_lowercase();
        let count = artist_counts.entry(artist_key.clone()).or_insert(0);
        let same_as_prev = result
            .last()
            .map(|prev| prev.artist.trim().to_lowercase() == artist_key)
            .unwrap_or(false);

        if same_as_prev || *count >= 2 {
            deferred.push(track);
        } else {
            *count += 1;
            result.push(track);
        }
    }

    result.extend(deferred);
    result
}

fn calculate_affinity_score(
    track: &CandidateTrack,
    profile: &TasteProfile,
    top_artists: &HashSet<String>,
) -> i32 {
    let raw_score = *profile.scores.get(&track.id).unwrap_or(&0);
    let mut score = if raw_score < 0 {
        /* Heavy penalty for explicitly skipped tracks */
        (raw_score * 3).max(-30)
    } else {
        /* Moderate boost for liked/played tracks without burying new discoveries */
        raw_score.min(10)
    };

    let artist_clean = track.artist.trim().to_lowercase();
    if top_artists.contains(&artist_clean) && raw_score >= 0 {
        score += 4;
    }

    score
}
