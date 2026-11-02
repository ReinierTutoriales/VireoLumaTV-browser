//! JNI bridge between VireoLumaTV and Brave's adblock-rust engine.
//!
//! The engine is created once per filter-list generation and shared by every WebView thread:
//! with the "single-thread" feature disabled `adblock::Engine` is `Sync`, so checks need no lock.
//! Handles are raw pointers to boxed [Blocker]s owned by `com.reiniertutoriales.vireolumatv.adblock.RustAdblock`.

mod scoped;

use adblock::blocker::BlockerResult;
use adblock::lists::{FilterSet, ParseOptions};
use adblock::request::Request;
use adblock::resources::{PermissionMask, Resource};
use adblock::Engine;
use scoped::Scoped;
use jni::errors::ThrowRuntimeExAndDefault;
use jni::objects::{JBooleanArray, JByteArray, JClass, JObjectArray, JString};
use std::collections::HashSet;
use jni::sys::{jint, jlong};
use jni::{Env, EnvUnowned};

/// No match.
const ALLOW: jint = 0;
/// Blocked; the caller answers with an empty error response.
const BLOCK: jint = 1;
/// Blocked and a `$redirect` resource is available (see `nativeVerdict`).
const BLOCK_REDIRECT: jint = 2;
/// `nativeVerdict` answer for a request blocked without a `$redirect` resource.
const VERDICT_BLOCK: &str = "!";
/// Permission of `trusted-*` scriptlet resources (see AdblockResources) and of the lists that
/// may use them. Other lists, including a custom subscription, cannot inject those scriptlets.
const TRUSTED: PermissionMask = PermissionMask::from_bits(1);

fn text(env: &Env, value: &JString) -> jni::errors::Result<String> {
    value.try_to_string(env)
}

/// The list engine and the rules it cannot apply itself (see [scoped]).
struct Blocker {
    engine: Engine,
    scoped: Option<Scoped>,
}

/// Serialized [Blocker]: magic, main engine length (u64 LE), main engine, scoped rule source.
const MAGIC: &[u8; 5] = b"VIRO1";

impl Blocker {
    fn serialize(&self) -> Vec<u8> {
        let main = self.engine.serialize();
        let scoped = self.scoped.as_ref().map(Scoped::source).unwrap_or("");
        let mut out = Vec::with_capacity(MAGIC.len() + 8 + main.len() + scoped.len());
        out.extend_from_slice(MAGIC);
        out.extend_from_slice(&(main.len() as u64).to_le_bytes());
        out.extend_from_slice(&main);
        out.extend_from_slice(scoped.as_bytes());
        out
    }

    fn deserialize(bytes: &[u8], resources: &str) -> Option<Self> {
        let rest = bytes.strip_prefix(MAGIC.as_slice())?;
        let length = usize::try_from(u64::from_le_bytes(rest.get(..8)?.try_into().ok()?)).ok()?;
        let main = rest.get(8..8usize.checked_add(length)?)?;
        let source = std::str::from_utf8(&rest[8 + length..]).ok()?.to_owned();
        let mut engine = Engine::default();
        engine.deserialize(main).ok()?;
        load_resources(&mut engine, resources);
        Some(Blocker { engine, scoped: Scoped::build(source, options, parse_resources(resources)) })
    }

    /// Main answer first; a hostname-scoped rule only blocks what the main lists did not except.
    fn check(&self, request: &Request, source: &str) -> BlockerResult {
        let result = self.engine.check_network_request(request);
        if result.should_block() || result.exception.is_some() {
            return result;
        }
        let Some(scoped) = self.scoped.as_ref().and_then(|s| s.check(request, hostname(source))) else { return result };
        if !scoped.important {
            let exceptions = self.engine.check_network_request_subset(request, true, false);
            if exceptions.exception.is_some() {
                return result;
            }
        }
        scoped
    }
}

/// Compiles lists one at a time into a [Blocker].
struct Builder {
    set: FilterSet,
    scoped: String,
    resources: Vec<Resource>,
}

impl Builder {
    fn new(resources: Vec<Resource>) -> Self {
        Builder { set: FilterSet::new(false), scoped: String::new(), resources }
    }

    fn add(&mut self, list: String, trusted: bool) {
        let known: HashSet<&str> = self.resources.iter()
            .flat_map(|r| std::iter::once(r.name.as_str()).chain(r.aliases.iter().map(String::as_str)))
            .collect();
        let list = without_unknown_redirects(list, &known);
        scoped::collect(&list, trusted, &mut self.scoped);
        self.set.add_filter_list(list, options(trusted));
    }

    fn finish(self) -> Blocker {
        let scoped = Scoped::build(self.scoped, options, self.resources.clone());
        let mut engine = Engine::new_with_filter_set(self.set);
        engine.use_resources(self.resources);
        Blocker { engine, scoped }
    }
}

/// Host of an http(s) URL without user info or port ("" when there is none).
fn hostname(url: &str) -> &str {
    let rest = url.split_once("://").map_or("", |(_, rest)| rest);
    let authority = rest.split(['/', '?', '#']).next().unwrap_or("");
    let host = authority.rsplit_once('@').map_or(authority, |(_, host)| host);
    if host.starts_with('[') { host } else { host.split(':').next().unwrap_or("") }
}

fn engine<'a>(handle: jlong) -> Option<&'a Blocker> {
    if handle == 0 {
        None
    } else {
        // SAFETY: handles are only produced by `into_handle` and destroyed once by `nativeDestroy`.
        Some(unsafe { &*(handle as *const Blocker) })
    }
}

fn into_handle(blocker: Blocker) -> jlong {
    Box::into_raw(Box::new(blocker)) as jlong
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

fn options(trusted: bool) -> ParseOptions {
    ParseOptions { permissions: if trusted { TRUSTED } else { PermissionMask::default() }, ..ParseOptions::default() }
}

fn request(url: &str, source: &str, kind: &str) -> Option<Request> {
    Request::new(url, source, kind, "GET").ok()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_reiniertutoriales_vireolumatv_adblock_RustAdblock_nativeCompile<'caller>(
    mut unowned: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    lists: JObjectArray<'caller, JString<'caller>>,
    trusted: JBooleanArray<'caller>,
    resources: JString<'caller>,
) -> jlong {
    unowned
        .with_env(|env| -> jni::errors::Result<jlong> {
            let mut builder = Builder::new(parse_resources(&text(env, &resources)?));
            let count = lists.len(env)?;
            let mut flags = vec![false; count];
            if trusted.len(env)? == count {
                trusted.get_region(env, 0, &mut flags)?;
            }
            // One list at a time: no combined copy of several megabytes of filter text.
            for (index, trusted) in flags.into_iter().enumerate() {
                let list = lists.get_element(env, index)?;
                builder.add(text(env, &list)?, trusted);
            }
            Ok(into_handle(builder.finish()))
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
            Ok(Blocker::deserialize(&bytes, &resources).map_or(0, into_handle))
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
        drop(unsafe { Box::from_raw(handle as *mut Blocker) });
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
            let result = engine.check(&request, &source);
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

/// One network match for a request: "" to allow it, [`VERDICT_BLOCK`] to block it, or the `data:`
/// URL of its `$redirect` resource. Callers never need a second match for the replacement.
fn verdict(engine: &Blocker, url: &str, source: &str, kind: &str) -> String {
    let Some(request) = request(url, source, kind) else { return String::new() };
    let result = engine.check(&request, source);
    if !result.should_block() {
        String::new()
    } else {
        result.redirect.unwrap_or_else(|| VERDICT_BLOCK.to_owned())
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_reiniertutoriales_vireolumatv_adblock_RustAdblock_nativeVerdict<'caller>(
    mut unowned: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    url: JString<'caller>,
    source: JString<'caller>,
    kind: JString<'caller>,
) -> JString<'caller> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'caller>> {
            let answer = match engine(handle) {
                Some(engine) => verdict(engine, &text(env, &url)?, &text(env, &source)?, &text(env, &kind)?),
                None => String::new(),
            };
            env.new_string(answer)
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
                    let resources = engine.engine.url_cosmetic_resources(&url);
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

/// Generic `##.class` / `###id` hiding rules for the classes and ids found in a page, as a JSON
/// array of selectors. `classes` and `ids` are space-separated (neither can contain a space).
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_reiniertutoriales_vireolumatv_adblock_RustAdblock_nativeHiddenSelectors<'caller>(
    mut unowned: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    url: JString<'caller>,
    classes: JString<'caller>,
    ids: JString<'caller>,
) -> JString<'caller> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'caller>> {
            let (url, classes, ids) = (text(env, &url)?, text(env, &classes)?, text(env, &ids)?);
            let selectors = match engine(handle) {
                Some(engine) => {
                    // Exceptions (`#@#.class`) and `$generichide` are per page.
                    let page = engine.engine.url_cosmetic_resources(&url);
                    if page.generichide {
                        Vec::new()
                    } else {
                        engine.engine.hidden_class_id_selectors(
                            classes.split_ascii_whitespace(),
                            ids.split_ascii_whitespace(),
                            &page.exceptions,
                        )
                    }
                }
                None => Vec::new(),
            };
            env.new_string(serde_json::Value::from(selectors).to_string())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[cfg(test)]
mod tests {
    use super::*;

    const NOOPJS: &str = r#"[{"name":"noopjs","aliases":[],"kind":{"mime":"application/javascript"},"content":"KGZ1bmN0aW9uKCl7fSkoKQ=="}]"#;

    fn engine_for(rules: &str) -> Blocker {
        let mut builder = Builder::new(parse_resources(NOOPJS));
        builder.add(rules.to_owned(), false);
        builder.finish()
    }

    #[test]
    fn one_match_answers_allow_block_or_redirect() {
        let engine = engine_for("||ads.test^\n@@||ads.test/allowed^\n||cdn.test/ads/*$script,redirect=noopjs");
        let page = "https://page.test/";
        assert_eq!(verdict(&engine, "https://page.test/app.js", page, "script"), "");
        assert_eq!(verdict(&engine, "https://ads.test/banner.js", page, "script"), VERDICT_BLOCK);
        assert_eq!(verdict(&engine, "https://ads.test/allowed", page, "script"), "");
        let redirect = verdict(&engine, "https://cdn.test/ads/loader.js", page, "script");
        assert!(redirect.starts_with("data:application/javascript;base64,"), "{redirect}");
        assert_eq!(verdict(&engine, "not a url", page, "script"), "");
    }

    #[test]
    fn trusted_scriptlets_only_run_from_trusted_lists_and_survive_the_cache() {
        // Templates as AdblockResources builds them: base64 of the marker, trusted-* with permission 1.
        let resources = r#"[{"name":"trusted-set.js","aliases":[],"kind":"template","content":"VFJVU1RFRA==","permission":1},
            {"name":"set.js","aliases":[],"kind":"template","content":"UExBSU4=","permission":0}]"#;
        let compile = |trusted: bool| {
            let mut filters = FilterSet::new(false);
            filters.add_filter_list("site.test##+js(trusted-set)\nsite.test##+js(set)".to_owned(), options(trusted));
            let mut engine = Engine::new_with_filter_set(filters);
            load_resources(&mut engine, resources);
            engine
        };
        let script = |engine: &Engine| engine.url_cosmetic_resources("https://site.test/").injected_script;
        let custom = script(&compile(false));
        assert!(custom.contains("PLAIN") && !custom.contains("TRUSTED"), "{custom}");
        let ubo = compile(true);
        assert!(script(&ubo).contains("TRUSTED") && script(&ubo).contains("PLAIN"));
        let mut restored = Engine::default();
        restored.deserialize(&ubo.serialize()).unwrap();
        load_resources(&mut restored, resources);
        assert!(script(&restored).contains("TRUSTED"));
    }

    #[test]
    fn scoped_important_rules_override_main_exceptions_and_other_groups() {
        let normal = "||ads.test^$script\n@@||ads.test^$script\n";
        let scoped = [
            ("||ads.test^$script,to=test,important", true),
            ("||ads.test^$script,to=test", false),
            ("||ads.test^$script,to=org,important", false),
            ("||ads.test^$script,to=test\n*$script,to=test,domain=site.test,important", true),
            ("||ads.test^$script,to=test\n||ads.test^$script,to=test|~allowed.test,important", true),
            ("||ads.test^$script,to=test\n||ads.test^$script,to=~allowed.test,important", true),
        ];
        for (rules, blocked) in scoped {
            let blocker = engine_for(&format!("{normal}{rules}"));
            let restored = Blocker::deserialize(&blocker.serialize(), NOOPJS).unwrap();
            for engine in [&blocker, &restored] {
                assert_eq!(verdict(engine, "https://ads.test/a.js", "https://site.test/", "script") == VERDICT_BLOCK,
                    blocked, "{rules}");
                assert_eq!(verdict(engine, "https://elsewhere.test/a.js", "https://other.test/", "script"), "");
            }
        }
    }

    #[test]
    fn to_and_denyallow_limit_the_request_hostname_and_survive_the_cache() {
        let rules = "*$script,3p,denyallow=cdn.jsdelivr.net|cloudflare.com,domain=site.test\n\
            ||track.test^$xhr,to=~ok.track.test\n\
            *$script,3p,to=evil.*,domain=other.test\n\
            @@||allowed.test^$script,domain=site.test\n\
            *$xhr,3p,denyallow=api.test,domain=strict.test,important\n\
            @@||bad.test^$xhr,domain=strict.test\n\
            /^https:\\/\\/ads[0-9]\\.[a-z]+\\.(?:com|org)\\/x\\.js$/$script,to=com\n\
            /^https:\\/\\/m[0-9]\\./$xhr,to=net|~m1.cdn.net\n\
            *$script,3p,denyallow=a.test";
        let blocker = engine_for(rules);
        let restored = Blocker::deserialize(&blocker.serialize(), NOOPJS).unwrap();
        for engine in [&blocker, &restored] {
            let v = |url: &str, page: &str, kind: &str| verdict(engine, url, page, kind) == VERDICT_BLOCK;
            // denyallow: every third-party script on site.test except the listed hosts and subdomains.
            assert!(v("https://ads.example/a.js", "https://site.test/", "script"));
            assert!(!v("https://cdn.jsdelivr.net/x.js", "https://site.test/", "script"));
            assert!(!v("https://cdnjs.cloudflare.com/x.js", "https://site.test/", "script"));
            assert!(!v("https://ads.example/a.js", "https://elsewhere.test/", "script"));
            // A main-list exception still wins over a non-important scoped rule.
            assert!(!v("https://allowed.test/a.js", "https://site.test/", "script"));
            // to=~host on a specific pattern, and an entity in to=.
            assert!(v("https://track.test/p", "https://any.test/", "xhr"));
            assert!(!v("https://ok.track.test/p", "https://any.test/", "xhr"));
            assert!(v("https://cdn.evil.co.uk/a.js", "https://other.test/", "script"));
            assert!(!v("https://good.example/a.js", "https://other.test/", "script"));
            // $important is not lifted by exceptions; its own denyallow still applies.
            assert!(v("https://bad.test/x", "https://strict.test/", "xhr"));
            assert!(!v("https://api.test/x", "https://strict.test/", "xhr"));
            // Regex rules indexed by to= entry: without (direct) and with negated entries (keyed).
            assert!(v("https://ads1.foo.com/x.js", "https://any.test/", "script"));
            assert!(!v("https://ads1.foo.org/x.js", "https://any.test/", "script"));
            assert!(v("https://m2.cdn.net/p", "https://any.test/", "xhr"));
            assert!(!v("https://m1.cdn.net/p", "https://any.test/", "xhr"));
            // A denyallow rule without a page would block everywhere: ignored, as uBO requires domain=.
            assert!(!v("https://ads.example/a.js", "https://unrelated.test/", "script"));
        }
        // Old caches without the container are rejected so the lists are compiled again.
        assert!(Blocker::deserialize(&blocker.engine.serialize(), NOOPJS).is_none());
    }
}
