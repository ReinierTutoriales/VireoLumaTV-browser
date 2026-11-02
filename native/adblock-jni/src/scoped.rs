//! Network rules whose `$to=` / `$denyallow=` option limits the request's own hostname.
//!
//! adblock-rust 0.13 parses `$to=` but drops those filters ("parsed but ignored") and rejects
//! `$denyallow=`, so about 670 rules of the bundled uBlock lists never applied. They are kept here,
//! without the hostname option, in a small engine of their own; a match only counts when the
//! request's hostname satisfies the option (uBO semantics: `to=a|~b` needs a and not b,
//! `denyallow=a|b` is `to=~a|~b`). Main-list exceptions still apply unless the rule is `$important`.

use adblock::lists::{FilterSet, ParseOptions};
use adblock::request::Request;
use adblock::resources::{InMemoryResourceStorage, Resource, ResourceImpl, ResourceStorageBackend};
use adblock::Engine;
use std::collections::HashMap;
use std::sync::Arc;

/// One copy of the `$redirect` resources for every scoped engine.
struct Shared(Arc<InMemoryResourceStorage>);

impl ResourceStorageBackend for Shared {
    fn get_resource(&self, resource_ident: &str) -> Option<ResourceImpl> {
        self.0.get_resource(resource_ident)
    }
}

/// Hostnames from `to=` (positive and `~`negated) and `denyallow=` (negated), sorted so that
/// rules with the same option share one [Bucket].
#[derive(Default, Clone, PartialEq, Eq, Hash)]
struct Hosts {
    to: Vec<String>,
    not_to: Vec<String>,
}

impl Hosts {
    fn allows(&self, host: &str) -> bool {
        (self.to.is_empty() || self.to.iter().any(|entry| matches_host(host, entry)))
            && !self.not_to.iter().any(|entry| matches_host(host, entry))
    }
}

/// `entry` is a hostname (also matching its subdomains), a bare TLD such as `com`, or an entity
/// `name.*` matching `name` under any suffix.
fn matches_host(host: &str, entry: &str) -> bool {
    if let Some(name) = entry.strip_suffix(".*") {
        let label = format!("{name}.");
        return host.starts_with(&label) || host.contains(&format!(".{label}"));
    }
    host == entry || (host.len() > entry.len() && host.ends_with(entry) && host.as_bytes()[host.len() - entry.len() - 1] == b'.')
}

/// Values keyed by `to=`/`domain=` entry: hostnames (matching subdomains), TLDs and `name.*`
/// entities, looked up for a host without allocating.
struct Keyed<T> {
    exact: HashMap<String, T>,
    entity: HashMap<String, T>,
}

impl<T: Default> Keyed<T> {
    fn new() -> Self {
        Keyed { exact: HashMap::new(), entity: HashMap::new() }
    }

    fn entry(&mut self, key: &str) -> &mut T {
        match key.strip_suffix(".*") {
            Some(name) => self.entity.entry(name.to_owned()).or_default(),
            None => self.exact.entry(key.to_owned()).or_default(),
        }
    }

    /// Every value whose key matches [host]: its parent-domain suffixes (`a.b.com`, `b.com`,
    /// `com`) and, for entities, each run of whole labels (`a`, `a.b`, `b`).
    fn each(&self, host: &str, mut f: impl FnMut(&T)) {
        let starts = std::iter::once(0).chain(host.match_indices('.').map(|(i, _)| i + 1));
        for start in starts {
            let rest = &host[start..];
            if let Some(value) = self.exact.get(rest) {
                f(value);
            }
            if !self.entity.is_empty() {
                for (end, _) in rest.match_indices('.') {
                    if let Some(value) = self.entity.get(&rest[..end]) {
                        f(value);
                    }
                }
            }
        }
    }
}

/// Rules sharing one hostname option. Every match of [engine] is valid when [hosts] allows the
/// request hostname, which is tested before the engine runs: no match can hide another.
struct Bucket {
    hosts: Hosts,
    engine: Engine,
}

pub struct Scoped {
    /// Rules with only positive `to=` entries, one engine per entry: any match found through the
    /// request host's keys is valid. Most are regex rules (`to=com`, `to=top`) that adblock-rust
    /// cannot index, so only the engines of the host's own keys run.
    direct: Keyed<Option<Engine>>,
    buckets: Vec<Bucket>,
    /// Buckets with positive and `~`negated `to=` entries, by positive entry.
    by_host: Keyed<Vec<usize>>,
    /// `*` rules by page (`domain=`/`from=` entry): without a URL token adblock-rust would test
    /// them on every request, so only the buckets of the page host's keys run.
    by_page: Keyed<Vec<usize>>,
    /// Buckets with only `~`negated entries and a URL pattern; they allow almost every host, so
    /// they only run when [others_any], all their rules in one engine, matches the request.
    others: Vec<usize>,
    others_any: Option<Engine>,
    /// The original rules, one per line, prefixed with `T` (trusted list) or `U`: rebuilt on load.
    source: String,
}

/// Network rule text without its hostname options, plus those options; None when the line is
/// not such a rule or would match everywhere (`*` pattern without a `domain=`/`from=` page).
fn split(line: &str) -> Option<(String, Hosts, Option<Vec<String>>)> {
    if line.starts_with('!') || line.starts_with('[') || line.starts_with("@@") || is_cosmetic(line) {
        return None;
    }
    if !line.contains("to=") && !line.contains("denyallow=") {
        return None;
    }
    let dollar = line.rfind('$')?;
    let (pattern, options) = (&line[..dollar], &line[dollar + 1..]);
    let mut hosts = Hosts::default();
    let mut kept = Vec::new();
    let mut scoped = false;
    let mut pages = Vec::new();
    for option in options.split(',') {
        if let Some(list) = option.strip_prefix("to=") {
            scoped = true;
            for entry in list.split('|').filter(|e| !e.is_empty()) {
                match entry.strip_prefix('~') {
                    Some(negated) => hosts.not_to.push(negated.to_ascii_lowercase()),
                    None => hosts.to.push(entry.to_ascii_lowercase()),
                }
            }
        } else if let Some(list) = option.strip_prefix("denyallow=") {
            scoped = true;
            hosts.not_to.extend(list.split('|').filter(|e| !e.is_empty()).map(str::to_ascii_lowercase));
        } else {
            if let Some(list) = option.strip_prefix("domain=").or_else(|| option.strip_prefix("from=")) {
                pages.extend(list.split('|').filter(|e| !e.is_empty() && !e.starts_with('~')).map(str::to_ascii_lowercase));
            }
            kept.push(option);
        }
    }
    let wildcard = pattern.is_empty() || pattern == "*";
    if !scoped || (wildcard && pages.is_empty()) {
        return None;
    }
    let rule = if kept.is_empty() { pattern.to_owned() } else { format!("{pattern}${}", kept.join(",")) };
    for list in [&mut hosts.to, &mut hosts.not_to] {
        list.sort();
        list.dedup();
    }
    Some((rule, hosts, wildcard.then_some(pages)))
}

/// `##`, `#@#`, `#?#`, `#$#`, `#%#` and `##+js(...)` (as FilterListPreprocessor.isCosmetic).
fn is_cosmetic(line: &str) -> bool {
    ["##", "#@#", "#?#", "#$#", "#%#"].iter().any(|marker| line.contains(marker))
}

/// Appends the hostname-scoped rules of one list to a [Scoped] source.
pub fn collect(list: &str, trusted: bool, source: &mut String) {
    if !list.contains("to=") && !list.contains("denyallow=") {
        return;
    }
    for line in list.lines().map(str::trim) {
        if split(line).is_some() {
            source.push(if trusted { 'T' } else { 'U' });
            source.push_str(line);
            source.push('\n');
        }
    }
}

impl Scoped {
    /// None when [source] has no usable rule.
    pub fn build(source: String, options: impl Fn(bool) -> ParseOptions, resources: Vec<Resource>) -> Option<Self> {
        // Rule text as [untrusted, trusted]: by positive entry (direct), by (hostname option, is
        // `*` rule) with the pages of `*` rules (buckets), and the negated-only rules together.
        let mut direct: HashMap<String, [String; 2]> = HashMap::new();
        let mut texts: HashMap<(Hosts, bool), ([String; 2], Vec<String>)> = HashMap::new();
        let mut others_text: [String; 2] = Default::default();
        let push = |text: &mut [String; 2], trusted: bool, rule: &str| {
            text[trusted as usize].push_str(rule);
            text[trusted as usize].push('\n');
        };
        for line in source.lines() {
            let (trusted, rule) = if let Some(rule) = line.strip_prefix('T') {
                (true, rule)
            } else if let Some(rule) = line.strip_prefix('U') {
                (false, rule)
            } else {
                continue;
            };
            let Some((stripped, hosts, pages)) = split(rule) else { continue };
            if pages.is_none() && hosts.not_to.is_empty() {
                for entry in &hosts.to {
                    push(direct.entry(entry.clone()).or_default(), trusted, &stripped);
                }
                continue;
            }
            if pages.is_none() && hosts.to.is_empty() {
                push(&mut others_text, trusted, &stripped);
            }
            let entry = texts.entry((hosts, pages.is_some())).or_default();
            push(&mut entry.0, trusted, &stripped);
            entry.1.extend(pages.into_iter().flatten());
        }
        if direct.is_empty() && texts.is_empty() {
            return None;
        }
        let storage = Arc::new(InMemoryResourceStorage::from_resources(resources));
        let engine = |[plain, trusted]: [String; 2]| {
            let mut set = FilterSet::new(false);
            set.add_filter_list(trusted, options(true));
            set.add_filter_list(plain, options(false));
            let mut engine = Engine::new_with_filter_set(set);
            engine.use_resource_storage(Shared(storage.clone()));
            engine
        };
        let others_any = (!others_text.iter().all(String::is_empty)).then(|| engine(others_text));
        let mut scoped = Scoped {
            direct: Keyed::new(), buckets: Vec::new(), by_host: Keyed::new(), by_page: Keyed::new(),
            others: Vec::new(), others_any, source,
        };
        for (entry, text) in direct {
            *scoped.direct.entry(&entry) = Some(engine(text));
        }
        for ((hosts, wildcard), (text, pages)) in texts {
            let index = scoped.buckets.len();
            let keys = if wildcard { pages } else { hosts.to.clone() };
            let index_map = if wildcard { &mut scoped.by_page } else { &mut scoped.by_host };
            for key in keys {
                let list = index_map.entry(&key);
                if list.last() != Some(&index) {
                    list.push(index);
                }
            }
            if !wildcard && hosts.to.is_empty() {
                scoped.others.push(index);
            }
            scoped.buckets.push(Bucket { hosts, engine: engine(text) });
        }
        Some(scoped)
    }

    pub fn source(&self) -> &str {
        &self.source
    }

    /// The answer of a rule matching [request] (made by a page on [page_host]) whose hostname
    /// option allows the request; an `$important` one when any matches.
    pub fn check(&self, request: &Request, page_host: &str) -> Option<adblock::blocker::BlockerResult> {
        let host = request.hostname.as_str();
        let mut first: Option<adblock::blocker::BlockerResult> = None;
        let mut important = None;
        let mut consider = |result: adblock::blocker::BlockerResult| {
            if important.is_some() || !result.should_block() {
                return;
            }
            // A normal match in one engine must not hide an important rule in another.
            if result.important {
                important = Some(result);
            } else if first.is_none() {
                first = Some(result);
            }
        };
        self.direct.each(host, |engine| {
            if let Some(engine) = engine { consider(engine.check_network_request(request)) }
        });
        let mut candidates = Vec::new();
        self.by_host.each(host, |list| candidates.extend_from_slice(list));
        if self.others_any.as_ref().is_some_and(|any| any.check_network_request(request).should_block()) {
            candidates.extend_from_slice(&self.others);
        }
        if !self.by_page.exact.is_empty() || !self.by_page.entity.is_empty() {
            self.by_page.each(&page_host.to_ascii_lowercase(), |list| candidates.extend_from_slice(list));
        }
        candidates.sort_unstable();
        candidates.dedup();
        for bucket in candidates.into_iter().map(|i| &self.buckets[i]) {
            if bucket.hosts.allows(host) {
                consider(bucket.engine.check_network_request(request));
            }
        }
        important.or(first)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn hostname_entries_match_subdomains_tlds_and_entities() {
        assert!(matches_host("cdn.jsdelivr.net", "cdn.jsdelivr.net"));
        assert!(matches_host("a.cdn.jsdelivr.net", "jsdelivr.net"));
        assert!(!matches_host("notjsdelivr.net", "jsdelivr.net"));
        assert!(matches_host("ads.example.com", "com"));
        assert!(!matches_host("ads.example.co", "com"));
        assert!(matches_host("www.google.co.uk", "google.*"));
        assert!(!matches_host("notgoogle.com", "google.*"));
    }

    #[test]
    fn only_hostname_scoped_network_rules_with_a_page_are_kept() {
        assert!(split("*$script,3p,denyallow=a.com|b.com,domain=site.test").is_some());
        assert!(split("/^https:\\/\\/x\\.example\\//$script,to=com").is_some());
        assert!(split("*$script,3p,denyallow=a.com").is_none(), "would block every site");
        assert!(split("*$script,denyallow=a.com,domain=~site.test").is_none());
        assert!(split("@@*$script,to=a.com,domain=site.test").is_none());
        assert!(split("site.test##.ad").is_none());
        assert!(split("||ads.example^$script").is_none());
        let (rule, hosts, pages) = split("*$xhr,redirect=noop.txt,from=tunein.com,to=event.tunein.com|~x.tunein.com").unwrap();
        assert_eq!(rule, "*$xhr,redirect=noop.txt,from=tunein.com");
        assert_eq!((hosts.to, hosts.not_to), (vec!["event.tunein.com".to_owned()], vec!["x.tunein.com".to_owned()]));
        assert_eq!(pages, Some(vec!["tunein.com".to_owned()]));
        assert_eq!(split("||x.test^$script,to=com,domain=a.test").unwrap().2, None);
    }

    #[test]
    fn keys_match_parent_domains_and_entities() {
        let mut keyed: Keyed<Vec<&str>> = Keyed::new();
        for key in ["www.japscan.co.uk", "japscan.co.uk", "co.uk", "uk", "japscan.*", "www.japscan.*", "japscan.co.*", "japscan.uk", "pan.*"] {
            keyed.entry(key).push(key);
        }
        let mut found = Vec::new();
        keyed.each("www.japscan.co.uk", |keys| found.extend(keys.iter().copied()));
        found.sort();
        assert_eq!(found, ["co.uk", "japscan.*", "japscan.co.*", "japscan.co.uk", "uk", "www.japscan.*", "www.japscan.co.uk"]);
        found.clear();
        keyed.each("myjapscan.foo", |keys| found.extend(keys.iter().copied()));
        assert!(found.is_empty());
    }
}
