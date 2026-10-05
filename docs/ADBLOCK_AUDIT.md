# Adblock reliability and low-memory audit

This change keeps the pinned native engine and dependency set. It corrects request
classification and cache handling without adding another renderer or filter engine.

## Request classification

WebResourceRequest provides request headers, not response MIME types. Main-frame
navigation is a document. Sec-Fetch-Dest and XMLHttpRequest hints take precedence
when present; otherwise known path extensions and unambiguous Accept categories
provide conservative fallbacks. Mixed HTML/XML/image Accept headers remain unknown
instead of being treated as images. Header names and extensions ignore case.
Top-level navigation uses its destination as the page context; subresources use
the containing page. The previous page must not make a new top-level navigation
appear third-party or apply the previous site's domain-scoped exceptions.

Native options use the values in truefedex/ad-block tag 0.0.4, filter.h. Its C++
object flag is octal 010 (decimal 8), whereas the Java OBJECT enum is decimal 10,
which also sets the image bit. The integer JNI overload avoids this wrapper error
and supports document, subdocument, XHR, font and media options directly.

Safe Browsing stays enabled independently of the advertising switch. Per-tab
blocking follows the requesting tab's setting rather than reading another tab's
current selection.

## Cache and update behavior

- A matching compiled cache is activated before expired-list downloads begin.
- Compiled caches include a version and SHA-256 subscription URL; changing a
  subscription cannot silently load another subscription's compiled rules.
- Custom text caches also use SHA-256 rather than collision-prone Java hashCode.
- Compiled rules serialize to a separate temporary file. Rejection or write
  failure preserves the working cache, and temporary files are removed. Text
  caches use the same unique-temporary-file path: normal/private processes cannot
  corrupt each other's staging file. A valid download is still usable if its text
  cache cannot be written.
- If all downloads fall back to cache and a matching client is active, the same
  rules are not recompiled or deserialized again.
- Failed updates retain active protection. Failed compiled-cache writes schedule
  a shorter retry instead of marking the cache good for 30 days.
- A source changed during an update cannot publish stale rules/update dates;
  it triggers another update for the selected source.
- Validator HTML detection lowercases only a short prefix, and minimum-line
  checks stop when the required count is reached. Comment/header-only lists and
  markup error responses after leading metadata cannot replace working rules.

Legacy compiled caches have no subscription identity and are intentionally not
reused. The first launch of this cache version may need downloads or reconstruction
from matching text caches. Later launches use the new scoped cache. There is no
claim of a zero-gap cold start without usable rules.

## Verification and limits

Regression tests cover mixed Accept headers, resource hints/native flags,
comment-only/markup error lists, subscription hash collisions, failed/successful cache writes, startup blocking
while a download is held open, and retaining active rules without recompilation
when a subsequent download returns HTML.

Robolectric/fake engines do not execute the Android JNI library. Physical tests
remain necessary on the 2 GB 32-bit onn device: compare the same pages with
blocking enabled/disabled, check documents/iframes/scripts/images/video/fonts,
exceptions, per-site settings and offline startup after a successful update.
Record load time and browser/renderer PSS; automated checks are not RAM benchmarks.
The engine still has no injected cosmetic hiding or scriptlet layer, and WebView
cannot intercept every request scheme or expose a reliable type for every fetch.
