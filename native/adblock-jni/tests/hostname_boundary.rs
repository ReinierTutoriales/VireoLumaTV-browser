use adblock::lists::{FilterSet, ParseOptions};
use adblock::request::Request;
use adblock::Engine;

fn blocks(filter: &str, host: &str) -> bool {
    let mut rules = FilterSet::new(false);
    rules.add_filter_list(filter.to_owned(), ParseOptions::default());
    let engine = Engine::new_with_filter_set(rules);
    let request = Request::new(&format!("https://{host}/asset.js"), "https://page.test/", "script", "GET").unwrap();
    engine.check_network_request(&request).should_block()
}

#[test]
fn infix_match_checks_the_boundary_after_the_matched_label() {
    assert!(blocks("||foo.bar", "a.foo.bar.baz"));
}

#[test]
fn infix_match_does_not_use_a_dot_inside_the_filter_as_the_end_boundary() {
    assert!(!blocks("||foo.bar", "abc.foo.barx.test"));
    assert!(blocks("||foo.bar^", "abc.foo.bar"));
    assert!(!blocks("||foo.bar^", "abc.notfoo.bar"));
}

#[test]
fn old_v6_cache_is_rejected_and_current_rules_round_trip() {
    let legacy = include_bytes!("../../../app/src/test/resources/adblock/0.13.3.dat");
    assert_eq!(legacy[4], 6);
    assert!(Engine::default().deserialize(legacy).is_err());
    let mut filters = FilterSet::new(false);
    filters.add_filter_list("||old-cache.test^".to_owned(), ParseOptions::default());
    let serialized = Engine::new_with_filter_set(filters).serialize();
    assert_eq!(serialized[4], 8);
    let mut restored = Engine::default();
    restored.deserialize(&serialized).unwrap();
    let request = Request::new("https://old-cache.test/banner.js", "https://page.test/", "script", "GET").unwrap();
    assert!(restored.check_network_request(&request).should_block());
}
