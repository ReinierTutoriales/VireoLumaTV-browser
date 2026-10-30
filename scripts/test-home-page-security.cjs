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
assert.equal(tags('button').length, 2);
assert.equal(attribute(tags('button')[1], 'type'), 'button');
assert.equal(attribute(tags('button')[1], 'id'), 'add-bookmark');
assert.equal(attribute(tags('button')[0], 'type'), 'submit');
assert.equal(tags('a').length, 1);
assert.equal(attribute(tags('a')[0], 'href'), 'https://m.youtube.com/');
assert.equal(attribute(tags('a')[0], 'aria-label'), 'YouTube');
// Query values remain query data rather than executable markup or navigation destinations.
for (const query of ['android tv', 'café & música', 'a+b?#', '<script>alert(1)</script>', 'javascript:alert(1)']) {
  const url = new URL(attribute(form, 'action'));
  url.search = new URLSearchParams({[attribute(input, 'name')]: query}).toString();
  assert.equal(url.origin, 'https://duckduckgo.com');
  assert.equal(url.searchParams.get('q'), query);
}
// The initial page has no executable markup, remote resources or dynamic recommendation data.
assert.equal(tags('script').length, 1);
assert.equal(attribute(tags('script')[0], 'src'), 'home.js');
assert.equal(tags('iframe').length, 0);
assert.equal(tags('img').length, 0);
assert.ok(!/\bon\w+\s*=|@import|url\s*\(/i.test(html));
assert.ok(!/fetch\(|XMLHttpRequest|setInterval|setTimeout|Wikipedia|reddit/i.test(html));
assert.ok(tags('link').every(tag => attribute(tag, 'href')?.startsWith('data:')));
const csp = tags('meta').find(tag => attribute(tag, 'http-equiv') === 'Content-Security-Policy');
assert.ok(attribute(csp, 'content').includes("default-src 'none'"));
assert.ok(attribute(csp, 'content').includes('form-action https://duckduckgo.com'));
assert.ok(/input:focus, button:focus, a:focus/.test(html));
// App-owned pages remain dark even with a light system setting. Verify actual CSS
// foreground/backdrop pairs rather than relying on a color-scheme declaration alone.
function luminance(hex) {
  let digits = hex.slice(1);
  if (digits.length === 3) digits = digits.split('').map(c => c + c).join('');
  return [0.2126, 0.7152, 0.0722].reduce((sum, weight, index) => {
    const value = parseInt(digits.slice(index * 2, index * 2 + 2), 16) / 255;
    return sum + weight * (value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4);
  }, 0);
}
function contrast(a, b) {
  const values = [luminance(a), luminance(b)].sort((a, b) => b - a);
  return (values[0] + 0.05) / (values[1] + 0.05);
}
function cssRule(page, selector) {
  const rules = Array.from(page.matchAll(/([^{}]+)\{([^{}]*)\}/g));
  return rules.find(match => match[1].trim() === selector)?.[2] ?? '';
}
function cssColor(rule, property) {
  return rule.match(new RegExp(`(?:^|;)\\s*${property}:\\s*(#[a-f0-9]+)`, 'i'))?.[1];
}
for (const page of [html, fs.readFileSync('app/src/main/assets/pages/warning-certificate.html', 'utf8')]) {
  assert.ok(/name="color-scheme" content="dark"/.test(page));
  assert.ok(!/prefers-color-scheme|color-scheme[^;<>]*light/.test(page));
  const body = cssRule(page, 'body');
  const background = cssColor(body, 'background');
  assert.equal(luminance(background), 0);
  assert.ok(contrast(cssColor(body, 'color'), background) >= 7);
  assert.ok(/sans-serif/.test(body));
}
const field = cssColor(cssRule(html, 'input'), 'background');
assert.ok(contrast(cssColor(cssRule(html, 'body'), 'color'), field) >= 7);
assert.ok(contrast(cssColor(cssRule(html, 'input::placeholder'), 'color'), field) >= 4.5);
assert.ok(contrast(cssRule(html, 'input:focus, button:focus, a:focus').match(/#[a-f0-9]+/i)[0], field) >= 3);
console.log('Home passed: DuckDuckGo form, one YouTube link, safe query encoding, visible focus and no remote startup assets');

// Exercise actual bookmark code with a small DOM: URL validation, persistence and storage errors.
const vm = require('node:vm');
const source = fs.readFileSync('app/src/main/assets/pages/home/home.js', 'utf8');
function setup(saved = '[]', failWrite = false) {
  const nodes = [];
  function node() { return {events: {}, children: [], attrs: {}, addEventListener(k, f) { this.events[k] = f; },
    removeEventListener(k) { delete this.events[k]; }, setAttribute(k, v) { this.attrs[k] = v; },
    appendChild(child) { this.children.push(child); }, focus() { this.focused = true; }}; }
  const list = node(), add = node(), input = node(), form = node();
  input.value = ''; input.setCustomValidity = v => { input.error = v; };
  list.querySelectorAll = () => nodes.slice();
  list.removeChild = n => nodes.splice(nodes.indexOf(n), 1);
  list.insertBefore = n => nodes.push(n);
  let stored = saved, next = null;
  const context = {URL, encodeURIComponent, document: {
    getElementById(id) { return {bookmarks: list, 'add-bookmark': add, 'search-input': input}[id]; },
    createElement: () => node(), querySelector: () => form,
  }, localStorage: {getItem: () => stored, setItem(k, v) { if (failWrite) throw Error('full'); stored = v; }},
  prompt: () => next, alert() {}};
  vm.runInNewContext(source, context);
  return {nodes, input, form, add(value) { next = value; add.events.click(); }, saved: () => JSON.parse(stored)};
}
let home = setup();
for (const value of [null, '', 'javascript:alert(1)', 'data:text/html,evil', 'https://user:secret@example.com']) home.add(value);
assert.equal(home.nodes.length, 0);
home.add('example.com');
assert.deepEqual(home.saved(), ['https://example.com/']);
assert.equal(home.nodes[0].attrs['aria-label'], 'example.com');
assert.equal(home.nodes[0].children[0].src, 'favicon://example.com');
home.nodes[0].children[0].events.error();
assert.ok(home.nodes[0].children[0].src.startsWith('data:image/svg+xml,'));
home.add('https://example.com/');
assert.equal(home.nodes.length, 1);
assert.equal(setup(JSON.stringify(home.saved())).nodes.length, 1);
assert.equal(setup('{broken').nodes.length, 0);
assert.equal(setup(JSON.stringify(['https://example.com/' + 'a'.repeat(70000)])).nodes.length, 0);
let full = setup('[]', true); full.add('https://example.com/'); assert.equal(full.nodes.length, 0);
home.input.value = '   '; let prevented = false;
home.form.events.submit({preventDefault() { prevented = true; }}); assert.ok(prevented);
home.input.events.input(); assert.equal(home.input.error, '');
home.input.value = '  televisión & música  '; home.form.events.submit({preventDefault() { assert.fail(); }});
assert.equal(home.input.value, 'televisión & música');
for (let i = 0; i < 30; i++) home.add('https://example.com/' + i);
assert.equal(home.nodes.length, 24);
console.log('Home shortcuts passed: safe URLs, duplicates, icon fallback, reload, bounded storage and query trimming');
