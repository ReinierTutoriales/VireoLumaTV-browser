const fs = require('node:fs');
const assert = require('node:assert/strict');
const html = fs.readFileSync('app/src/main/assets/pages/home/index.html', 'utf8');
const tags = name => Array.from(html.matchAll(new RegExp(`<${name}\\b([^>]*)>`, 'gi')), match => match[1]);
const attribute = (tag, name) => tag.match(new RegExp(`\\b${name}="([^"]*)"`, 'i'))?.[1];
assert.equal(tags('form').length, 1);
const form = tags('form')[0];
assert.equal(attribute(form, 'action'), 'https://duckduckgo.com/');
assert.equal(attribute(form, 'method'), 'get');
assert.equal(attribute(form, 'target'), '_self');
assert.equal(tags('input').length, 1);
const input = tags('input')[0];
assert.equal(attribute(input, 'type'), 'search');
assert.equal(attribute(input, 'name'), 'q');
assert.equal(attribute(input, 'enterkeyhint'), 'search');
assert.ok(/\brequired\b/.test(input));
assert.equal(tags('button').length, 1);
assert.equal(attribute(tags('button')[0], 'type'), 'submit');
assert.equal(tags('a').length, 1);
assert.equal(attribute(tags('a')[0], 'href'), 'https://www.youtube.com/');
assert.equal(attribute(tags('a')[0], 'aria-label'), 'YouTube');
// Query values remain query data rather than executable markup or navigation destinations.
for (const query of ['android tv', 'café & música', 'a+b?#', '<script>alert(1)</script>', 'javascript:alert(1)']) {
  const url = new URL(attribute(form, 'action'));
  url.search = new URLSearchParams({[attribute(input, 'name')]: query}).toString();
  assert.equal(url.origin, 'https://duckduckgo.com');
  assert.equal(url.searchParams.get('q'), query);
}
// The initial page has no executable markup, remote resources or dynamic recommendation data.
assert.equal(tags('script').length, 0);
assert.equal(tags('iframe').length, 0);
assert.equal(tags('img').length, 0);
assert.ok(!/\bon\w+\s*=|@import|url\s*\(/i.test(html));
assert.ok(!/localStorage|fetch\(|XMLHttpRequest|setInterval|setTimeout|Wikipedia|reddit/i.test(html));
assert.ok(tags('link').every(tag => attribute(tag, 'href')?.startsWith('data:')));
const csp = tags('meta').find(tag => attribute(tag, 'http-equiv') === 'Content-Security-Policy');
assert.ok(attribute(csp, 'content').includes("default-src 'none'"));
assert.ok(attribute(csp, 'content').includes('form-action https://duckduckgo.com'));
assert.ok(/input:focus, button:focus, a:focus/.test(html));
console.log('Minimal home passed: DuckDuckGo form, one YouTube link, safe query encoding, visible focus and no remote startup assets');
