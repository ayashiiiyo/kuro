fn normalize_ascii_spaces(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    let mut prev_space = false;
    for ch in s.trim().chars() {
        if ch.is_whitespace() {
            if !prev_space {
                out.push(' ');
                prev_space = true;
            }
        } else {
            out.push(ch.to_ascii_lowercase());
            prev_space = false;
        }
    }
    out
}

fn is_noise_tag(content: &str) -> bool {
    let norm = normalize_ascii_spaces(content);
    let s = norm.as_str();
    if matches!(
        s,
        "official music video"
            | "official video"
            | "official audio"
            | "official lyric video"
            | "official lyrics"
            | "official visualizer"
            | "official mv"
            | "music video"
            | "lyric video"
            | "lyrics"
            | "audio"
            | "visualizer"
            | "remastered"
            | "remaster"
            | "4k"
            | "hd"
            | "hq"
            | "mv"
            | "clip officiel"
            | "video oficial"
    ) {
        return true;
    }
    if s.starts_with("official ")
        && (s.contains("video")
            || s.contains("audio")
            || s.contains("lyric")
            || s.contains("mv")
            || s.contains("visualizer")
            || s.contains("4k")
            || s.contains("hd"))
    {
        return true;
    }
    if (s.starts_with("remastered") || s.starts_with("remaster "))
        && s.bytes().any(|b| b.is_ascii_digit())
    {
        return true;
    }
    false
}

fn strip_bracketed_noise(input: &str) -> String {
    let mut result = String::with_capacity(input.len());
    let chars: Vec<char> = input.chars().collect();
    let len = chars.len();
    let mut i = 0;

    while i < len {
        let ch = chars[i];
        if ch == '(' || ch == '[' {
            let close_ch = if ch == '(' { ')' } else { ']' };
            let mut j = i + 1;
            while j < len && chars[j] != close_ch && chars[j] != '(' && chars[j] != '[' {
                j += 1;
            }
            if j < len && chars[j] == close_ch {
                let inner: String = chars[i + 1..j].iter().collect();
                if is_noise_tag(&inner) {
                    while result.ends_with(' ') || result.ends_with('\t') {
                        result.pop();
                    }
                    i = j + 1;
                    continue;
                }
            }
        }
        result.push(ch);
        i += 1;
    }
    result
}

fn strip_pipe_noise(input: &str) -> String {
    if let Some(pipe_idx) = input.find('|') {
        let after = normalize_ascii_spaces(&input[pipe_idx + 1..]);
        if after.starts_with("official music video")
            || after.starts_with("official video")
            || after.starts_with("official audio")
            || after.starts_with("official lyric")
            || after.starts_with("visualizer")
        {
            return input[..pipe_idx].trim_end().to_string();
        }
    }
    input.to_string()
}

pub fn clean_title(raw: &str) -> String {
    let step1 = strip_bracketed_noise(raw);
    let step2 = strip_pipe_noise(&step1);
    step2.trim().to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_clean_title() {
        assert_eq!(clean_title("Song Name (Official Music Video)"), "Song Name");
        assert_eq!(clean_title("Another Track [Audio]"), "Another Track");
        assert_eq!(clean_title("Hit Song | Official Video"), "Hit Song");
        assert_eq!(clean_title("Track (Official Lyric Video)"), "Track");
        assert_eq!(clean_title("Clean Title"), "Clean Title");
    }
}
