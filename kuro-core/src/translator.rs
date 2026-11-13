use serde::{Deserialize, Serialize};

pub const ENDPOINT_FAL: &str = "https://oneshot.fal.free.deepl.com/v1/translate";
pub const ENDPOINT_KEF: &str = "https://oneshot.kef.free.deepl.com/v1/translate";
pub const USER_AGENT: &str = "DeepL/26.41.0 (com.deepl.mobiletranslator; build:264100; Android 14)";

#[derive(Serialize)]
struct DeepLRequest<'a> {
    text: &'a [String],
    target_lang: &'a str,
}

#[derive(Deserialize)]
struct DeepLTranslationItem {
    text: String,
}

#[derive(Deserialize)]
struct DeepLResponse {
    translations: Option<Vec<DeepLTranslationItem>>,
}

/** Builds JSON payload for DeepL Mobile batch translation request across supported languages. */
pub fn build_payload(texts: &[String], target_lang: &str) -> String {
    let trimmed = target_lang.trim().to_lowercase();
    let normalized_lang = if trimmed.is_empty() {
        "id"
    } else if trimmed == "in" {
        "id"
    } else {
        trimmed.as_str()
    };

    let req = DeepLRequest {
        text: texts,
        target_lang: normalized_lang,
    };

    serde_json::to_string(&req).unwrap_or_else(|_| "{\"text\":[],\"target_lang\":\"id\"}".to_string())
}

/** Parses DeepL API response JSON and extracts translated strings. */
pub fn parse_response(response_json: &str) -> Vec<String> {
    if response_json.trim().is_empty() {
        return Vec::new();
    }

    match serde_json::from_str::<DeepLResponse>(response_json) {
        Ok(parsed) => {
            if let Some(items) = parsed.translations {
                items.into_iter().map(|item| item.text).collect()
            } else {
                Vec::new()
            }
        }
        Err(_) => Vec::new(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_build_payload() {
        let lines = vec!["Hello".to_string(), "World".to_string()];
        let json = build_payload(&lines, "id");
        assert!(json.contains("\"target_lang\":\"id\""));
        assert!(json.contains("Hello"));
        assert!(json.contains("World"));

        let json_ja = build_payload(&lines, "JA");
        assert!(json_ja.contains("\"target_lang\":\"ja\""));
    }

    #[test]
    fn test_parse_response() {
        let resp = r#"{"translations":[{"detected_source_language":"EN","text":"Halo"},{"detected_source_language":"EN","text":"Dunia"}]}"#;
        let res = parse_response(resp);
        assert_eq!(res.len(), 2);
        assert_eq!(res[0], "Halo");
        assert_eq!(res[1], "Dunia");
    }
}
