`0.13.3.dat` is a real DAT v6 cache containing the project-owned rule
`||old-cache.test^`. It was generated with `export_cache_fixture.rs` while
Cargo.toml and Cargo.lock still matched `7e8995a0` (adblock 0.13.3), before
updating the engine. It is intentionally kept in the old format.

SHA-256: `58a326bc2b3337ac0c3e4ce7518cb9def4030dc151b553bd2b3db8fabc2466bc`.

The recovery test rejects it with the new engine, rebuilds from the saved rule
text after an HTTP 503, then restores the replacement cache without recompiling.
