//! JNI bridge between VireoLumaTV and Brave's adblock-rust engine.
//!
//! The engine is created once per filter-list generation and shared by every WebView thread:
//! with the "single-thread" feature disabled `adblock::Engine` is `Sync`, so checks need no lock.
//! Handles are raw pointers to boxed engines owned by `com.reiniertutoriales.vireolumatv.adblock.RustAdblock`.

use adblock::lists::{FilterSet, ParseOptions};
use adblock::request::Request;
use adblock::resources::Resource;
use adblock::Engine;
use jni::errors::ThrowRuntimeExAndDefault;
use jni::objects::{JByteArray, JClass, JObjectArray, JString};
use std::collections::HashSet;
use jni::sys::{jint, jlong};
use jni::{Env, EnvUnowned};

/// No match.
const ALLOW: jint = 0;
/// Blocked; the caller answers with an empty error response.
const BLOCK: jint = 1;
/// Blocked and a `$redirect` resource is available (see `nativeRedirect`).
const BLOCK_REDIRECT: jint = 2;

fn text(env: &Env, value: &JString) -> jni::errors::Result<String> {
    value.try_to_string(env)
}

fn engine<'a>(handle: jlong) -> Option<&'a Engine> {
    if handle == 0 {
        None
    } else {
        // SAFETY: handles are only produced by `into_handle` and destroyed once by `nativeDestroy`.
        Some(unsafe { &*(handle as *const Engine) })
    }
}

fn into_handle(engine: Engine) -> jlong {
    Box::into_raw(Box::new(engine)) as jlong
}

fn parse_resources(resources_json: &str) -> Vec<Resource> {
    serde_json::from_str::<Vec<Resource>>(resources_json).unwrap_or_default()
}

fn load_resources(engine: &mut Engine, resources_json: &str) {
    engine.use_resources(parse_resources(resources_json));
}

/// Name of a `redirect=`/`redirect-rule=` option without its `:priority` suffix.
fn redirect_target(option: &str) -> Option<&str> {
    let value = option.strip_prefix("redirect=").or_else(|| option.strip_prefix("redirect-rule="))?;
    Some(value.split(':').next().unwrap_or(value))
}

/// Drops network rules whose `$redirect` resource is not shipped. adblock-rust would still block the
/// request with no replacement (a hard error for players expecting e.g. the IMA SDK); uBlock Origin
/// discards such filters instead, so the request goes through.
fn without_unknown_redirects(list: String, known: &HashSet<&str>) -> String {
    if !list.contains("redirect") {
        return list;
    }
    let keep = |line: &str| {
        if !line.contains("redirect") || line.contains("##") || line.contains("#@#") {
            return true;
        }
        let Some(dollar) = line.rfind('$') else { return true };
        line[dollar + 1..]
            .split(',')
            .filter_map(redirect_target)
            .all(|name| name == "none" || known.contains(name))
    };
    let mut out = String::with_capacity(list.len());
    for line in list.lines().filter(|line| keep(line)) {
        out.push_str(line);
        out.push('\n');
    }
    out
}

fn request(url: &str, source: &str, kind: &str) -> Option<Request> {
    Request::new(url, source, kind, "GET").ok()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_reiniertutoriales_vireolumatv_adblock_RustAdblock_nativeCompile<'caller>(
    mut unowned: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    lists: JObjectArray<'caller, JString<'caller>>,
    resources: JString<'caller>,
) -> jlong {
    unowned
        .with_env(|env| -> jni::errors::Result<jlong> {
            let resources = parse_resources(&text(env, &resources)?);
            let known: HashSet<&str> = resources
                .iter()
                .flat_map(|r| std::iter::once(r.name.as_str()).chain(r.aliases.iter().map(String::as_str)))
                .collect();
            let mut set = FilterSet::new(false);
            // One list at a time: no combined copy of several megabytes of filter text.
            for index in 0..lists.len(env)? {
                let list = lists.get_element(env, index)?;
                let list = text(env, &list)?;
                set.add_filter_list(without_unknown_redirects(list, &known), ParseOptions::default());
            }
            let mut engine = Engine::new_with_filter_set(set);
            engine.use_resources(resources);
            Ok(into_handle(engine))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_reiniertutoriales_vireolumatv_adblock_RustAdblock_nativeDeserialize<'caller>(
    mut unowned: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    data: JByteArray<'caller>,
    resources: JString<'caller>,
) -> jlong {
    unowned
        .with_env(|env| -> jni::errors::Result<jlong> {
            let bytes = env.convert_byte_array(&data)?;
            let resources = text(env, &resources)?;
            let mut engine = Engine::default();
            if engine.deserialize(&bytes).is_err() {
                return Ok(0);
            }
            load_resources(&mut engine, &resources);
            Ok(into_handle(engine))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_reiniertutoriales_vireolumatv_adblock_RustAdblock_nativeSerialize<'caller>(
    mut unowned: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) -> JByteArray<'caller> {
    unowned
        .with_env(|env| -> jni::errors::Result<JByteArray<'caller>> {
            let data = engine(handle).map(|e| e.serialize()).unwrap_or_default();
            env.byte_array_from_slice(&data)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_reiniertutoriales_vireolumatv_adblock_RustAdblock_nativeDestroy<'caller>(
    _unowned: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
) {
    if handle != 0 {
        // SAFETY: see `engine`; Kotlin clears its copy of the handle before calling this.
        drop(unsafe { Box::from_raw(handle as *mut Engine) });
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_reiniertutoriales_vireolumatv_adblock_RustAdblock_nativeCheck<'caller>(
    mut unowned: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    url: JString<'caller>,
    source: JString<'caller>,
    kind: JString<'caller>,
) -> jint {
    unowned
        .with_env(|env| -> jni::errors::Result<jint> {
            let Some(engine) = engine(handle) else { return Ok(ALLOW) };
            let (url, source, kind) = (text(env, &url)?, text(env, &source)?, text(env, &kind)?);
            let Some(request) = request(&url, &source, &kind) else { return Ok(ALLOW) };
            let result = engine.check_network_request(&request);
            Ok(if !result.should_block() {
                ALLOW
            } else if result.redirect.is_some() {
                BLOCK_REDIRECT
            } else {
                BLOCK
            })
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

/// `data:` URL of the `$redirect` resource for a blocked request, or an empty string.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_reiniertutoriales_vireolumatv_adblock_RustAdblock_nativeRedirect<'caller>(
    mut unowned: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    url: JString<'caller>,
    source: JString<'caller>,
    kind: JString<'caller>,
) -> JString<'caller> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'caller>> {
            let (url, source, kind) = (text(env, &url)?, text(env, &source)?, text(env, &kind)?);
            let redirect = engine(handle)
                .and_then(|engine| request(&url, &source, &kind).map(|r| engine.check_network_request(&r)))
                .filter(|result| result.should_block())
                .and_then(|result| result.redirect)
                .unwrap_or_default();
            env.new_string(redirect)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

/// Page filters for a document URL as JSON:
/// `{"hide":[selectors],"procedural":[json],"script":"injected scriptlets","generichide":bool}`.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_reiniertutoriales_vireolumatv_adblock_RustAdblock_nativeCosmetic<'caller>(
    mut unowned: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    url: JString<'caller>,
) -> JString<'caller> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'caller>> {
            let url = text(env, &url)?;
            let json = match engine(handle) {
                Some(engine) => {
                    let resources = engine.url_cosmetic_resources(&url);
                    serde_json::json!({
                        "hide": resources.hide_selectors,
                        "procedural": resources.procedural_actions,
                        "script": resources.injected_script,
                        "generichide": resources.generichide,
                    })
                    .to_string()
                }
                None => String::from("{}"),
            };
            env.new_string(json)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}
