pub mod innertube;
pub mod liriqo;
pub mod recommendation;
pub mod sanitizer;
pub mod translator;

use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;

/** JNI bridge matching package: com.ayashii.kuro.data.native.KuroNativeCore */

fn to_jstring(env: &mut JNIEnv, s: &str) -> jstring {
    match env.new_string(s) {
        Ok(js) => js.into_raw(),
        Err(_) => match env.new_string("") {
            Ok(empty) => empty.into_raw(),
            Err(_) => std::ptr::null_mut(),
        },
    }
}

#[no_mangle]
pub extern "system" fn Java_com_ayashii_kuro_data_native_KuroNativeCore_cleanTrackTitle(
    mut env: JNIEnv,
    _class: JClass,
    raw_title: JString,
) -> jstring {
    let input: String = match env.get_string(&raw_title) {
        Ok(s) => s.into(),
        Err(_) => return to_jstring(&mut env, ""),
    };

    let cleaned = sanitizer::clean_title(&input);
    to_jstring(&mut env, &cleaned)
}

#[no_mangle]
pub extern "system" fn Java_com_ayashii_kuro_data_native_KuroNativeCore_rankCandidates(
    mut env: JNIEnv,
    _class: JClass,
    candidates_json: JString,
    profile_json: JString,
) -> jstring {
    let cand_str: String = match env.get_string(&candidates_json) {
        Ok(s) => s.into(),
        Err(_) => return to_jstring(&mut env, "[]"),
    };

    let prof_str: String = match env.get_string(&profile_json) {
        Ok(s) => s.into(),
        Err(_) => return to_jstring(&mut env, "[]"),
    };

    let candidates: Vec<recommendation::CandidateTrack> = match serde_json::from_str(&cand_str) {
        Ok(c) => c,
        Err(_) => return to_jstring(&mut env, &cand_str),
    };

    let profile: recommendation::TasteProfile = match serde_json::from_str(&prof_str) {
        Ok(p) => p,
        Err(_) => return to_jstring(&mut env, &cand_str),
    };

    let ranked = recommendation::rank_candidates(candidates, &profile);
    let output = serde_json::to_string(&ranked).unwrap_or_else(|_| "[]".to_string());
    to_jstring(&mut env, &output)
}

#[no_mangle]
pub extern "system" fn Java_com_ayashii_kuro_data_native_KuroNativeCore_parseAutomixTracks(
    mut env: JNIEnv,
    _class: JClass,
    raw_json: JString,
    current_video_id: JString,
) -> jstring {
    let json_str: String = match env.get_string(&raw_json) {
        Ok(s) => s.into(),
        Err(_) => return to_jstring(&mut env, "[]"),
    };

    let video_id_str: String = match env.get_string(&current_video_id) {
        Ok(s) => s.into(),
        Err(_) => String::new(),
    };

    let tracks = innertube::parse_automix_radio(&json_str, &video_id_str);
    let output = serde_json::to_string(&tracks).unwrap_or_else(|_| "[]".to_string());
    to_jstring(&mut env, &output)
}

#[no_mangle]
pub extern "system" fn Java_com_ayashii_kuro_data_native_KuroNativeCore_buildDeepLPayload(
    mut env: JNIEnv,
    _class: JClass,
    lines_json: JString,
    target_lang: JString,
) -> jstring {
    let lines_str: String = match env.get_string(&lines_json) {
        Ok(s) => s.into(),
        Err(_) => return to_jstring(&mut env, ""),
    };

    let lang_str: String = match env.get_string(&target_lang) {
        Ok(s) => s.into(),
        Err(_) => "id".to_string(),
    };

    let lines: Vec<String> = match serde_json::from_str(&lines_str) {
        Ok(l) => l,
        Err(_) => Vec::new(),
    };

    let payload = translator::build_payload(&lines, &lang_str);
    to_jstring(&mut env, &payload)
}

#[no_mangle]
pub extern "system" fn Java_com_ayashii_kuro_data_native_KuroNativeCore_parseDeepLTranslations(
    mut env: JNIEnv,
    _class: JClass,
    response_json: JString,
) -> jstring {
    let resp_str: String = match env.get_string(&response_json) {
        Ok(s) => s.into(),
        Err(_) => return to_jstring(&mut env, "[]"),
    };

    let translations = translator::parse_response(&resp_str);
    let output = serde_json::to_string(&translations).unwrap_or_else(|_| "[]".to_string());
    to_jstring(&mut env, &output)
}

#[no_mangle]
pub extern "system" fn Java_com_ayashii_kuro_data_native_KuroNativeCore_parseLiriqoLyrics(
    mut env: JNIEnv,
    _class: JClass,
    raw_json: JString,
    duration_ms: jni::sys::jlong,
) -> jstring {
    let json_str: String = match env.get_string(&raw_json) {
        Ok(s) => s.into(),
        Err(_) => return to_jstring(&mut env, ""),
    };

    let result = liriqo::parse_liriqo_response(&json_str, duration_ms);
    let output = match result {
        Some(lyrics) => serde_json::to_string(&lyrics).unwrap_or_default(),
        None => String::new(),
    };
    to_jstring(&mut env, &output)
}
