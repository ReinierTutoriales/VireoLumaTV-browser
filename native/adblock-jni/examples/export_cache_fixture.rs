use adblock::lists::{FilterSet, ParseOptions};
use adblock::Engine;

fn main() {
    let destination = std::env::args().nth(1).expect("cache destination");
    let mut filters = FilterSet::new(false);
    filters.add_filter_list("||old-cache.test^".to_owned(), ParseOptions::default());
    let engine = Engine::new_with_filter_set(filters);
    std::fs::write(destination, engine.serialize()).unwrap();
}
