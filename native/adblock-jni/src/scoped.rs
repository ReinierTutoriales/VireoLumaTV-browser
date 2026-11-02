//! Network rules whose `$to=` / `$denyallow=` option limits the request's own hostname.
//!
//! adblock-rust 0.13 parses `$to=` but drops those filters ("parsed but ignored") and rejects
//! `$denyallow=`, so about 670 rules of the bundled uBlock lists never applied. They are kept here,
//! without the hostname option, in a small engine of their own; a match only counts when the
//! request's hostname satisfies the option (uBO semantics: `to=a|~b` needs a and not b,
//! `denyallow=a|b` is `to=~a|~b`). Main-list exceptions still apply unless the rule is `$important`.

use adblock::lists::{FilterSet, ParseOptions};
use adblock::request::Request;
use adblock::resources::Resource;
use adblock::Engine;
use std::collections::{HashMap, HashSet};

/// Hostnames from `to=` (positive and `~`negated) and `denyallow=` (negated).
#[derive(Default)]
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

/// Page hostnames (`domain=`/`from=`) of the `*` rules: those rules have no URL token, so
/// adblock-rust would test each of them on every request; pages outside this set skip them.
#[derive(Default)]
struct Pages {
    hosts: HashSet<String>,
    entities: HashSet<String>,
}

impl Pages {
    fn add(&mut self, entry: &str) {
        match entry.strip_suffix(".*") {
            Some(name) => self.entities.insert(name.to_owned()),
            None => self.hosts.insert(entry.to_owned()),
        };
    }

    /// Whether [host] or a parent domain is listed, or a label run of it is a listed entity.
    fn contains(&self, host: &str) -> bool {
        let starts: Vec<usize> = std::iter::once(0).chain(host.match_indices('.').map(|(i, _)| i + 1)).collect();
        starts.iter().any(|&i| self.hosts.contains(&host[i..]))
            || (!self.entities.is_empty() && starts.iter().any(|&i| {
                host[i..].match_indices('.').any(|(j, _)| self.entities.contains(&host[i..i + j]))
            }))
    }
}

/// Rules of one `to=` entry. [direct] rules have no `~`negated entry, so any match on a host
/// under that entry satisfies them: an optimized engine without per-rule data. [keyed] rules
/// are checked against their own hostname option.
#[derive(Default)]
struct Group {
    direct: Option<Engine>,
    keyed: Option<Engine>,
}

pub struct Scoped {
    /// Rules with a URL pattern and a positive `to=` list, by `to=` entry: most are regex rules
    /// (`to=com`, `to=top`, ...) that adblock-rust cannot index, so only the engines of the
    /// request hostname's suffixes run.
    by_host: HashMap<String, Group>,
    /// Other rules with a URL pattern (only `~`negated entries, or an entity).
    patterned: Option<Engine>,
    /// `*` rules, consulted only on [pages].
    wildcard: Option<Engine>,
    pages: Pages,
    hosts: HashMap<String, Vec<Hosts>>,
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
        let mut hosts: HashMap<String, Vec<Hosts>> = HashMap::new();
        let mut pages = Pages::default();
        // Rule text by group: None (patterned), Some("") (wildcard) or Some(to= entry), with
        // whether matches need the rule's own hostname option; each as [untrusted, trusted].
        let mut texts: HashMap<(Option<String>, bool), [String; 2]> = HashMap::new();
        for line in source.lines() {
            let (trusted, rule) = if let Some(rule) = line.strip_prefix('T') {
                (true, rule)
            } else if let Some(rule) = line.strip_prefix('U') {
                (false, rule)
            } else {
                continue;
            };
            let Some((stripped, constraint, wildcard)) = split(rule) else { continue };
            let groups: Vec<Option<String>> = if let Some(list) = &wildcard {
                list.iter().for_each(|page| pages.add(page));
                vec![Some(String::new())]
            } else if !constraint.to.is_empty() && constraint.to.iter().all(|e| !e.ends_with(".*")) {
                constraint.to.iter().cloned().map(Some).collect()
            } else {
                vec![None]
            };
            let keyed = !constraint.not_to.is_empty();
            for group in groups {
                let keyed = keyed || group.as_deref().is_none_or(str::is_empty);
                let target = &mut texts.entry((group, keyed)).or_default()[trusted as usize];
                target.push_str(&stripped);
                target.push('\n');
            }
            hosts.entry(stripped).or_default().push(constraint);
        }
        if hosts.is_empty() {
            return None;
        }
        let engine = |[plain, trusted]: [String; 2], keyed: bool| {
            // Keyed: debug data keeps each rule's text, which identifies its hostname option, and
            // no optimization, which would fuse rules and lose that text.
            let mut set = FilterSet::new(keyed);
            set.add_filter_list(trusted, options(true));
            set.add_filter_list(plain, options(false));
            let mut engine = if keyed { Engine::new_with_filter_set_no_optimize(set) } else { Engine::new_with_filter_set(set) };
            engine.use_resources(resources.clone());
            engine
        };
        let mut scoped = Scoped { by_host: HashMap::new(), patterned: None, wildcard: None, pages, hosts, source };
        for ((group, keyed), text) in texts {
            let built = Some(engine(text, keyed));
            match group.as_deref() {
                None => scoped.patterned = built,
                Some("") => scoped.wildcard = built,
                Some(entry) => {
                    let slot = scoped.by_host.entry(entry.to_owned()).or_default();
                    if keyed { slot.keyed = built } else { slot.direct = built }
                }
            }
        }
        Some(scoped)
    }

    pub fn source(&self) -> &str {
        &self.source
    }

    /// The rule's answer when one matches [request] (made by a page on [page_host]) and the
    /// request hostname satisfies it.
    pub fn check(&self, request: &Request, page_host: &str) -> Option<adblock::blocker::BlockerResult> {
        let host = request.hostname.as_str();
        let direct = |engine: &Engine| Some(engine.check_network_request(request)).filter(|r| r.should_block());
        let matched = |engine: &Engine| {
            let result = engine.check_network_request(request);
            let rule = result.filter.as_ref()?.raw_line.as_deref()?;
            (result.should_block() && self.hosts.get(rule)?.iter().any(|h| h.allows(host))).then_some(result)
        };
        let suffixes = std::iter::once(0).chain(host.match_indices('.').map(|(i, _)| i + 1));
        suffixes.filter_map(|i| self.by_host.get(&host[i..]))
            .find_map(|group| group.direct.as_ref().and_then(direct).or_else(|| group.keyed.as_ref().and_then(&matched)))
            .or_else(|| self.patterned.as_ref().and_then(&matched)).or_else(|| {
            let wildcard = self.wildcard.as_ref()?;
            if self.pages.contains(&page_host.to_ascii_lowercase()) { matched(wildcard) } else { None }
        })
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
    fn pages_match_listed_hosts_their_subdomains_and_entities() {
        let mut pages = Pages::default();
        ["site.test", "japscan.*"].iter().for_each(|p| pages.add(p));
        assert!(pages.contains("site.test") && pages.contains("www.site.test"));
        assert!(!pages.contains("notsite.test"));
        assert!(pages.contains("www.japscan.foo") && pages.contains("japscan.co.uk"));
        assert!(!pages.contains("myjapscan.foo"));
    }
}
