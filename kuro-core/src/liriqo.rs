use serde::{Deserialize, Serialize};

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct LiriqoWord {
    pub text: String,
    pub start: i64,
    pub end: i64,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct LiriqoTimedLine {
    #[serde(default)]
    pub id: Option<usize>,
    pub text: String,
    pub start: i64,
    pub end: i64,
    #[serde(default)]
    pub words: Option<Vec<LiriqoWord>>,
    #[serde(default, rename = "isCue")]
    pub is_cue: Option<bool>,
    #[serde(default, rename = "isInstrumental")]
    pub is_instrumental: Option<bool>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct LiriqoTrack {
    pub provider: String,
    #[serde(default, rename = "syncLevel")]
    pub sync_level: Option<String>,
    #[serde(default)]
    pub tier: Option<String>,
    #[serde(default)]
    pub timed: Option<Vec<LiriqoTimedLine>>,
    #[serde(default)]
    pub plain: Option<String>,
    #[serde(default)]
    pub lrc: Option<String>,
    #[serde(default)]
    pub ttml: Option<String>,
}

#[derive(Debug, Deserialize, Serialize, Clone)]
pub struct LiriqoResponse {
    #[serde(default, rename = "videoId")]
    pub video_id: Option<String>,
    #[serde(default)]
    pub tracks: Option<Vec<LiriqoTrack>>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct NormalizedLyricLine {
    pub time_ms: i64,
    pub text: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub end_time_ms: Option<i64>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct NormalizedLyrics {
    pub provider: String,
    pub is_synced: bool,
    pub lines: Vec<NormalizedLyricLine>,
}

#[derive(Debug, Deserialize)]
struct NetEaseCreditToken {
    #[serde(default)]
    tx: String,
}

#[derive(Debug, Deserialize)]
struct NetEaseJsonLine {
    #[serde(default)]
    t: Option<i64>,
    #[serde(default)]
    c: Option<Vec<NetEaseCreditToken>>,
}

const PROVIDER_RANK: &[&str] = &[
    "apple_music_liriqo",
    "musixmatch",
    "unison_community",
    "netease",
    "kugou_legato",
    "qq_portato",
    "lrclib",
    "ytm_line",
];

fn provider_rank(provider: &str) -> usize {
    PROVIDER_RANK
        .iter()
        .position(|&p| p == provider)
        .unwrap_or(999)
}

fn unwrap_netease_json_line(text: &str) -> Option<(Option<i64>, String)> {
    let t = text.trim();
    if !t.starts_with('{') || !t.ends_with('}') {
        return None;
    }
    if let Ok(parsed) = serde_json::from_str::<NetEaseJsonLine>(t) {
        if let Some(tokens) = parsed.c {
            let joined: String = tokens.into_iter().map(|tok| tok.tx).collect();
            return Some((parsed.t, joined.trim().to_string()));
        }
    }
    Some((None, String::new()))
}

fn is_junk_line(text: &str) -> bool {
    let t = text.trim();
    if t.is_empty() {
        return true;
    }
    if let Some((_, unwrapped)) = unwrap_netease_json_line(t) {
        if unwrapped.is_empty() {
            return true;
        }
        return is_junk_line(&unwrapped);
    }
    if (t.starts_with('{') && t.ends_with('}'))
        || t.contains("\"tx\":")
        || t.contains("\"c\":[")
    {
        return true;
    }
    let lower = t.to_lowercase();
    if lower.starts_with("——") || lower.starts_with("···") {
        return true;
    }
    if lower.contains("纯音乐")
        || lower.contains("请欣赏")
        || lower.contains("无歌词")
        || lower.contains("純音樂")
        || lower.contains("instrumental")
        || lower.contains("translation provided")
        || lower.contains("版权所有")
        || lower.contains("lirik tidak tersedia")
    {
        return true;
    }
    if lower.starts_with("written by")
        || lower.starts_with("produced by")
        || lower.starts_with("composed by")
        || lower.starts_with("arranged by")
        || lower.starts_with("lyrics by")
        || lower.starts_with("lyricist")
        || lower.starts_with("composer")
        || lower.starts_with("arranger")
        || lower.starts_with("penulis lirik")
        || lower.starts_with("komposer")
        || lower.starts_with("aransemen")
        || lower.starts_with("produser")
        || lower.starts_with("作词")
        || lower.starts_with("作詞")
        || lower.starts_with("作曲")
        || lower.starts_with("编曲")
        || lower.starts_with("編曲")
        || lower.starts_with("词曲")
        || lower.starts_with("制作人")
        || lower.starts_with("製作人")
        || lower.starts_with("和声")
        || lower.starts_with("混音")
        || lower.starts_with("母带")
        || lower.starts_with("录音")
        || lower.starts_with("吉他")
        || lower.starts_with("贝斯")
        || lower.starts_with("鼓")
        || lower.starts_with("弦乐")
        || lower.starts_with("演唱")
        || lower.starts_with("原唱")
        || lower.starts_with("出品")
        || lower.starts_with("发行")
        || lower.starts_with("词:")
        || lower.starts_with("词：")
        || lower.starts_with("曲:")
        || lower.starts_with("曲：")
        || lower.starts_with("编:")
        || lower.starts_with("编：")
    {
        return true;
    }
    false
}

fn sanitize_lyric_line_text(raw: &str) -> Option<(Option<i64>, String)> {
    let trimmed = raw.trim();
    if trimmed.is_empty() {
        return None;
    }
    if let Some((json_time, unwrapped)) = unwrap_netease_json_line(trimmed) {
        if unwrapped.is_empty() || is_junk_line(&unwrapped) {
            return None;
        }
        return Some((json_time, unwrapped));
    }
    if is_junk_line(trimmed) {
        return None;
    }
    Some((None, trimmed.to_string()))
}

pub fn parse_liriqo_response(json_str: &str, duration_ms: i64) -> Option<NormalizedLyrics> {
    if json_str.trim().is_empty() {
        return None;
    }

    let parsed: LiriqoResponse = serde_json::from_str(json_str).ok()?;
    let tracks = parsed.tracks?;
    if tracks.is_empty() {
        return None;
    }

    /* Candidate: (score, is_synced, provider, lines) */
    let mut candidates: Vec<(i32, bool, String, Vec<NormalizedLyricLine>)> = Vec::new();

    for track in tracks {
        let p_rank = provider_rank(&track.provider) as i32;
        let is_syllable_or_word = track.sync_level.as_deref() == Some("syllable")
            || track.sync_level.as_deref() == Some("word");

        /* Priority 1: Parsed timed lines */
        if let Some(timed) = track.timed {
            if !timed.is_empty() {
                let mut valid_lines: Vec<NormalizedLyricLine> = Vec::new();
                for line in timed {
                    if line.is_instrumental == Some(true) || line.is_cue == Some(true) {
                        continue;
                    }
                    let (_, clean_text) = match sanitize_lyric_line_text(&line.text) {
                        Some(pair) => pair,
                        None => continue,
                    };
                    if line.start < 0 || line.end <= line.start {
                        continue;
                    }
                    /* Fits duration guard: skip line if starts > 10s after track duration */
                    if duration_ms > 0 && line.start > duration_ms + 10_000 {
                        continue;
                    }

                    valid_lines.push(NormalizedLyricLine {
                        time_ms: line.start,
                        text: clean_text,
                        end_time_ms: Some(line.end),
                    });
                }

                if !valid_lines.is_empty() {
                    valid_lines.sort_by_key(|l| l.time_ms);
                    let score = if is_syllable_or_word {
                        1000 - p_rank
                    } else {
                        500 - p_rank
                    };
                    candidates.push((score, true, track.provider.clone(), valid_lines));
                    continue;
                }
            }
        }

        /* Priority 2: Standard LRC fallback if timed was absent */
        if let Some(lrc_str) = track.lrc {
            if !lrc_str.trim().is_empty() {
                let mut valid_lines = parse_lrc_lines(&lrc_str, duration_ms);
                if !valid_lines.is_empty() {
                    valid_lines.sort_by_key(|l| l.time_ms);
                    let score = 300 - p_rank;
                    candidates.push((score, true, track.provider.clone(), valid_lines));
                    continue;
                }
            }
        }

        /* Priority 3: Plain text fallback */
        if let Some(plain_str) = track.plain {
            if !plain_str.trim().is_empty() {
                let mut valid_lines: Vec<NormalizedLyricLine> = Vec::new();
                for line in plain_str.lines() {
                    if let Some((_, clean_text)) = sanitize_lyric_line_text(line) {
                        valid_lines.push(NormalizedLyricLine {
                            time_ms: 0,
                            text: clean_text,
                            end_time_ms: None,
                        });
                    }
                }
                if !valid_lines.is_empty() {
                    let score = 100 - p_rank;
                    candidates.push((score, false, track.provider.clone(), valid_lines));
                }
            }
        }
    }

    if candidates.is_empty() {
        return None;
    }

    /* Sort candidates by highest score */
    candidates.sort_by(|a, b| b.0.cmp(&a.0));
    let best = candidates.into_iter().next()?;

    Some(NormalizedLyrics {
        provider: best.2,
        is_synced: best.1,
        lines: best.3,
    })
}

fn parse_lrc_lines(lrc: &str, duration_ms: i64) -> Vec<NormalizedLyricLine> {
    let mut lines = Vec::new();
    for raw_line in lrc.lines() {
        let trimmed = raw_line.trim();
        if trimmed.is_empty()
            || trimmed.starts_with("[ti:")
            || trimmed.starts_with("[ar:")
            || trimmed.starts_with("[al:")
            || trimmed.starts_with("[by:")
            || trimmed.starts_with("[offset:")
        {
            continue;
        }

        if trimmed.starts_with('{') && trimmed.ends_with('}') {
            if let Some((Some(json_time), clean_text)) = sanitize_lyric_line_text(trimmed) {
                if duration_ms <= 0 || json_time <= duration_ms + 10_000 {
                    lines.push(NormalizedLyricLine {
                        time_ms: json_time.max(0),
                        text: clean_text,
                        end_time_ms: None,
                    });
                }
            }
            continue;
        }

        /* Match [mm:ss.xx] or [mm:ss:xx] */
        if let Some(close_bracket) = trimmed.find(']') {
            if trimmed.starts_with('[') && close_bracket > 1 {
                let tag = &trimmed[1..close_bracket];
                let raw_text = trimmed[close_bracket + 1..].trim();
                let (_, clean_text) = match sanitize_lyric_line_text(raw_text) {
                    Some(pair) => pair,
                    None => continue,
                };

                if let Some(time_ms) = parse_lrc_timestamp(tag) {
                    if duration_ms > 0 && time_ms > duration_ms + 10_000 {
                        continue;
                    }
                    lines.push(NormalizedLyricLine {
                        time_ms,
                        text: clean_text,
                        end_time_ms: None,
                    });
                }
            }
        }
    }
    lines
}

fn parse_lrc_timestamp(tag: &str) -> Option<i64> {
    let parts: Vec<&str> = tag.split(':').collect();
    if parts.len() < 2 {
        return None;
    }
    let minutes: i64 = parts[0].parse().ok()?;
    let seconds_str = parts[1];

    let sec_parts: Vec<&str> = seconds_str.split('.').collect();
    if sec_parts.len() == 2 {
        let sec: i64 = sec_parts[0].parse().ok()?;
        let frac_str = sec_parts[1];
        let frac: i64 = if frac_str.len() == 2 {
            frac_str.parse::<i64>().ok()? * 10
        } else if frac_str.len() == 3 {
            frac_str.parse::<i64>().ok()?
        } else {
            0
        };
        Some(minutes * 60_000 + sec * 1_000 + frac)
    } else {
        let sec: i64 = seconds_str.parse().ok()?;
        Some(minutes * 60_000 + sec * 1_000)
    }
}
