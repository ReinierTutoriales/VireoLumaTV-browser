//! JNI bridge between VireoLumaTV and Brave's adblock-rust engine.
//!
//! The engine is created once per filter-list generation and shared by every WebView thread:
//! with the "single-thread" feature disabled `adblock::Engine` is `Sync`, so callers need no lock
//! of their own. adblock-rust still serializes each network check on its internal regex-manager
//! mutex (adblock 0.13.5 `Blocker::borrow_regex_manager`): checks are thread-safe, not parallel.
//! Handles are raw pointers to boxed [Blocker]s owned by `com.reiniertutoriales.vireolumatv.adblock.RustAdblock`.

mod scoped;

use adblock::blocker::BlockerResult;
use adblock::cosmetic_filter_cache::UrlSpecificResources;
use adblock::lists::{FilterSet, ParseOptions};
use adblock::request::Request;
use adblock::resources::{PermissionMask, Resource};
use adblock::Engine;
use scoped::Scoped;
use jni::errors::ThrowRuntimeExAndDefault;
use jni::objects::{JBooleanArray, JClass, JObjectArray, JString};
use std::collections::{HashSet, VecDeque};
use std::fs::File;
use std::io::{self, BufWriter, Write};
use std::sync::{Arc, Mutex};
use jni::sys::{jboolean, jint, jlong};
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
    /// Recent documents' generic-hiding inputs, newest first (see [Blocker::page]).
    pages: Mutex<VecDeque<(String, Arc<PageHiding>)>>,
}

/// What generic hiding needs from a document's cosmetic resources: `$generichide` and the
/// `#@#.class` / `#@###id` exceptions. A page sends its classes in many batches; without this
/// each batch recomputed every cosmetic resource of the URL (about 13 times the selector lookup).
struct PageHiding {
    generichide: bool,
    exceptions: HashSet<String>,
}

/// Documents remembered per rule generation: the frames of a page and a few recent pages.
const PAGES: usize = 16;

/// Serialized [Blocker]: magic, main engine length (u64 LE), main engine, scoped rule source.
const MAGIC: &[u8; 5] = b"VIRO1";

impl Blocker {
    fn new(engine: Engine, scoped: Option<Scoped>) -> Self {
        Blocker { engine, scoped, pages: Mutex::new(VecDeque::with_capacity(PAGES)) }
    }

    /// Keeps the generic-hiding inputs of `resources`, computed for `url`.
    fn remember(&self, url: &str, resources: &UrlSpecificResources) -> Arc<PageHiding> {
        let page = Arc::new(PageHiding { generichide: resources.generichide, exceptions: resources.exceptions.clone() });
        let mut pages = self.pages.lock().unwrap_or_else(|e| e.into_inner());
        pages.retain(|(known, _)| known != url);
        pages.truncate(PAGES - 1);
        pages.push_front((url.to_owned(), Arc::clone(&page)));
        page
    }

    /// Generic-hiding inputs for `url`, from [remember] or computed once.
    fn page(&self, url: &str) -> Arc<PageHiding> {
        {
            let pages = self.pages.lock().unwrap_or_else(|e| e.into_inner());
            if let Some((_, page)) = pages.iter().find(|(known, _)| known == url) {
                return Arc::clone(page);
            }
        }
        self.remember(url, &self.engine.url_cosmetic_resources(url))
    }

    /// Writes the container part by part: the engine's bytes are not copied into a second buffer.
    fn write(&self, out: &mut impl Write) -> io::Result<()> {
        let main = self.engine.serialize();
        out.write_all(MAGIC)?;
        out.write_all(&(main.len() as u64).to_le_bytes())?;
        out.write_all(&main)?;
        out.write_all(self.scoped.as_ref().map(Scoped::source).unwrap_or("").as_bytes())?;
        out.flush()
    }

    #[cfg(test)]
    fn serialize(&self) -> Vec<u8> {
        let mut out = Vec::new();
        self.write(&mut out).unwrap();
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
        Some(Blocker::new(engine, Scoped::build(source, options, parse_resources(resources))))
    }

    /// Main blocks win; exceptions lift scoped rules unless a matching rule is important.
    fn check(&self, request: &Request, source: &str) -> BlockerResult {
        let result = self.engine.check_network_request(request);
        if result.should_block() {
            return result;
        }
        let Some(scoped) = self.scoped.as_ref().and_then(|s| s.check(request, hostname(source))) else { return result };
        if !scoped.important {
            if result.exception.is_some()
                || self.engine.check_network_request_subset(request, true, false).exception.is_some() {
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
    /// Names and aliases of the shipped resources, for [without_unknown_redirects].
    known: HashSet<String>,
}

impl Builder {
    fn new(resources: Vec<Resource>) -> Self {
        let known = resources
            .iter()
            .filter(|r| r.kind.supports_redirect())
            .flat_map(|r| std::iter::once(&r.name).chain(&r.aliases))
            .cloned()
            .collect();
        Builder { set: FilterSet::new(false), scoped: String::new(), resources, known }
    }

    fn add(&mut self, list: String, trusted: bool) {
        let list = without_unknown_redirects(list, &self.known);
        scoped::collect(&list, trusted, &mut self.scoped);
        self.set.add_filter_list(list, options(trusted));
    }

    fn finish(self) -> Blocker {
        let scoped = Scoped::build(self.scoped, options, self.resources.clone());
        let mut engine = Engine::new_with_filter_set(self.set);
        engine.use_resources(self.resources);
        Blocker::new(engine, scoped)
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
fn without_unknown_redirects(list: String, known: &HashSet<String>) -> String {
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

/// [method] is the HTTP method ("GET", "POST", "HEAD"...): rules with `$method=` match on it.
fn request(url: &str, source: &str, kind: &str, method: &str) -> Option<Request> {
    Request::new(url, source, kind, method).ok()
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

/// Restores a [Blocker] from the cache file at `path`; 0 when it is missing or unusable. The
/// file is read here, so its several megabytes never pass through the Java heap.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_reiniertutoriales_vireolumatv_adblock_RustAdblock_nativeDeserialize<'caller>(
    mut unowned: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    path: JString<'caller>,
    resources: JString<'caller>,
) -> jlong {
    unowned
        .with_env(|env| -> jni::errors::Result<jlong> {
            let Ok(bytes) = std::fs::read(text(env, &path)?) else { return Ok(0) };
            let resources = text(env, &resources)?;
            Ok(Blocker::deserialize(&bytes, &resources).map_or(0, into_handle))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

/// Writes the [Blocker] to the file at `path` (the caller's temporary file, renamed on success).
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_reiniertutoriales_vireolumatv_adblock_RustAdblock_nativeSerialize<'caller>(
    mut unowned: EnvUnowned<'caller>,
    _class: JClass<'caller>,
    handle: jlong,
    path: JString<'caller>,
) -> jboolean {
    unowned
        .with_env(|env| -> jni::errors::Result<jboolean> {
            let Some(blocker) = engine(handle) else { return Ok(false) };
            let path = text(env, &path)?;
            Ok(File::create(path).and_then(|file| blocker.write(&mut BufWriter::new(file))).is_ok())
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
            let Some(request) = request(&url, &source, &kind, "GET") else { return Ok(ALLOW) };
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
fn verdict(engine: &Blocker, url: &str, source: &str, kind: &str, method: &str) -> String {
    let Some(request) = request(url, source, kind, method) else { return String::new() };
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
    method: JString<'caller>,
) -> JString<'caller> {
    unowned
        .with_env(|env| -> jni::errors::Result<JString<'caller>> {
            let answer = match engine(handle) {
                Some(engine) => verdict(engine, &text(env, &url)?, &text(env, &source)?, &text(env, &kind)?, &text(env, &method)?),
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
                    // The page asks for its generic hiding next: keep what that needs.
                    engine.remember(&url, &resources);
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

/// Generic hiding selectors for the space-separated `classes` and `ids` found in the page at `url`.
fn hidden_selectors(engine: &Blocker, url: &str, classes: &str, ids: &str) -> Vec<String> {
    // Exceptions (`#@#.class`) and `$generichide` are per page.
    let page = engine.page(url);
    if page.generichide {
        return Vec::new();
    }
    engine.engine.hidden_class_id_selectors(classes.split_ascii_whitespace(), ids.split_ascii_whitespace(), &page.exceptions)
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
            let selectors = engine(handle).map_or_else(Vec::new, |engine| hidden_selectors(engine, &url, &classes, &ids));
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
        assert_eq!(verdict(&engine, "https://page.test/app.js", page, "script", "GET"), "");
        assert_eq!(verdict(&engine, "https://ads.test/banner.js", page, "script", "GET"), VERDICT_BLOCK);
        assert_eq!(verdict(&engine, "https://ads.test/allowed", page, "script", "GET"), "");
        let redirect = verdict(&engine, "https://cdn.test/ads/loader.js", page, "script", "GET");
        assert!(redirect.starts_with("data:application/javascript;base64,"), "{redirect}");
        assert_eq!(verdict(&engine, "not a url", page, "script", "GET"), "");
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
                assert_eq!(verdict(engine, "https://ads.test/a.js", "https://site.test/", "script", "GET") == VERDICT_BLOCK,
                    blocked, "{rules}");
                assert_eq!(verdict(engine, "https://elsewhere.test/a.js", "https://other.test/", "script", "GET"), "");
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
            let v = |url: &str, page: &str, kind: &str| verdict(engine, url, page, kind, "GET") == VERDICT_BLOCK;
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

    #[test]
    fn a_scoped_match_that_does_not_apply_does_not_hide_another_rule() {
        // Both rules match the URL; only the second allows ok.track.test. Either order.
        for rules in ["||track.test^$xhr,to=~ok.track.test\n/ads/$xhr,to=~other.test",
                      "/ads/$xhr,to=~other.test\n||track.test^$xhr,to=~ok.track.test",
                      "*$xhr,denyallow=ok.track.test,domain=site.test\n*$xhr,denyallow=cdn.test,domain=site.test"] {
            let blocker = engine_for(rules);
            let restored = Blocker::deserialize(&blocker.serialize(), NOOPJS).unwrap();
            for engine in [&blocker, &restored] {
                let page = if rules.contains("site.test") { "https://site.test/" } else { "https://any.test/" };
                assert_eq!(verdict(engine, "https://ok.track.test/ads/x", page, "xhr", "GET"), VERDICT_BLOCK, "{rules}");
            }
        }
    }

    #[test]
    fn method_rules_match_the_request_method() {
        // From the bundled lists: HEAD probes answered by a redirect, and GET-only rules.
        let engine = engine_for("||ads.test^$xhr,method=head,redirect=noopjs\n||cdn.test/v/$xhr,method=get\n||api.test/log$xhr,method=post");
        let page = "https://page.test/";
        assert!(verdict(&engine, "https://ads.test/probe", page, "xmlhttprequest", "HEAD").starts_with("data:"));
        assert_eq!(verdict(&engine, "https://ads.test/probe", page, "xmlhttprequest", "GET"), "");
        assert_eq!(verdict(&engine, "https://cdn.test/v/1", page, "xmlhttprequest", "GET"), VERDICT_BLOCK);
        assert_eq!(verdict(&engine, "https://cdn.test/v/1", page, "xmlhttprequest", "POST"), "");
        assert_eq!(verdict(&engine, "https://api.test/log", page, "xmlhttprequest", "post"), VERDICT_BLOCK);
        assert_eq!(verdict(&engine, "https://api.test/log", page, "xmlhttprequest", "GET"), "");
    }

    #[test]
    fn generic_hiding_reuses_the_page_exceptions_and_stays_bounded() {
        let engine = engine_for("##.ad\n##.promo\n###banner\nsite.test#@#.promo\n@@||quiet.test^$generichide");
        let hidden = |url: &str| {
            let mut found = hidden_selectors(&engine, url, "ad promo other", "banner");
            found.sort();
            found
        };
        // The answer for each page is the same whether it is computed or remembered.
        for _ in 0..2 {
            assert_eq!(hidden("https://site.test/"), vec!["#banner", ".ad"]);
            assert_eq!(hidden("https://other.test/"), vec!["#banner", ".ad", ".promo"]);
            assert!(hidden("https://quiet.test/").is_empty());
        }
        // The page filters request seeds the same entry.
        let url = "https://seeded.test/";
        engine.remember(url, &engine.engine.url_cosmetic_resources(url));
        assert_eq!(hidden(url), vec!["#banner", ".ad", ".promo"]);
        for i in 0..PAGES * 2 {
            hidden(&format!("https://p{i}.test/"));
        }
        assert_eq!(engine.pages.lock().unwrap().len(), PAGES);
        assert_eq!(hidden("https://site.test/"), vec!["#banner", ".ad"]);
    }
}
