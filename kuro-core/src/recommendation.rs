use serde::{Deserialize, Serialize};
use std::collections::HashMap;

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

/**
 * Pure pass-through: Kuro local recommendation ranking is disabled so
 * 100% of YouTube Music's native algorithm sequence is preserved untouched.
 */
pub fn rank_candidates(candidates: Vec<CandidateTrack>, _profile: &TasteProfile) -> Vec<CandidateTrack> {
    candidates
}
