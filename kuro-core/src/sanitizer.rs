use lazy_static::lazy_static;
use regex::Regex;

lazy_static! {
    static ref NOISE_REGEX: Regex = Regex::new(
        r"(?i)\s*[\(\[](?:official\s*(?:music\s*)?video|official\s*audio|music\s*video|lyric\s*video|lyrics|audio|visualizer|remastered|remaster|4k|hd|hq|mv|clip\s*officiel|video\s*oficial)[\)\]]"
    ).unwrap();
    static ref PIPE_REGEX: Regex = Regex::new(
        r"(?i)\s*\|\s*(?:official\s*(?:music\s*)?video|official\s*audio|visualizer).*"
    ).unwrap();
}

pub fn clean_title(raw: &str) -> String {
    let step1 = NOISE_REGEX.replace_all(raw, "");
    let step2 = PIPE_REGEX.replace_all(&step1, "");
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
        assert_eq!(clean_title("Clean Title"), "Clean Title");
    }
}
