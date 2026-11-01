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
assert.equal(tags('button').length, 3);
assert.equal(attribute(tags('button')[1], 'type'), 'button');
assert.equal(attribute(tags('button')[1], 'id'), 'add-bookmark');
// Edit comes last, after the saved shortcuts and the add control it acts on.
assert.equal(attribute(tags('button')[2], 'type'), 'button');
assert.equal(attribute(tags('button')[2], 'id'), 'edit-bookmarks');
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
// The page may read only its own files (favorites.json): no other site can be contacted by script.
assert.deepEqual(attribute(csp, 'content').match(/connect-src [^;]*/g), ["connect-src 'self'"]);
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

// Editing: the same code with the edit control and the built-in YouTube shortcut present.
function editable({store = {}, failWrite = false, failRead = false, favorites} = {}) {
  const nodes = [];
  let focused = null;
  function node() {
    const classes = new Set();
    return {events: {}, children: [], attrs: {}, style: {}, hidden: false, textContent: '',
      classList: {toggle(name, on) { if (on) classes.add(name); else classes.delete(name); }, contains: name => classes.has(name)},
      addEventListener(k, f) { this.events[k] = f; }, removeEventListener(k) { delete this.events[k]; },
      setAttribute(k, v) { this.attrs[k] = v; }, appendChild(child) { this.children.push(child); },
      focus() { focused = this; }};
  }
  const list = node(), add = node(), input = node(), form = node(), edit = node(), youtube = node();
  input.setCustomValidity = () => {};
  list.querySelectorAll = () => nodes.slice();
  list.removeChild = n => nodes.splice(nodes.indexOf(n), 1);
  list.insertBefore = n => nodes.push(n);
  const answers = {prompt: [], confirm: []}, alerts = [];
  const localStorage = {
    getItem(k) { if (failRead) throw Error('blocked'); return k in store ? store[k] : null; },
    setItem(k, v) { if (failWrite) throw Error('full'); store[k] = v; },
    removeItem(k) { if (failWrite) throw Error('full'); delete store[k]; },
  };
  const requests = [];
  // The browser's favorites.json: an array, a failed answer (null) or a network error ('error').
  const fetch = favorites === undefined ? undefined : (path, options) => {
    requests.push([path, options && options.cache]);
    if (favorites === 'error') return Promise.reject(Error('offline'));
    return Promise.resolve({ok: favorites !== null, json: () => Promise.resolve(favorites)});
  };
  vm.runInNewContext(source, {URL, encodeURIComponent, localStorage, fetch, Promise, document: {
    getElementById: id => ({bookmarks: list, 'add-bookmark': add, 'search-input': input,
      'edit-bookmarks': edit, 'default-youtube': youtube})[id],
    createElement: () => node(), querySelector: () => form,
  }, prompt: () => answers.prompt.shift(), confirm: () => answers.confirm.shift(), alert: m => alerts.push(m)});
  const click = target => { let prevented = false; target.events.click({preventDefault() { prevented = true; }}); return prevented; };
  return {nodes, store, alerts, edit, youtube, list, add, answers, click, requests, focused: () => focused,
    saved: () => JSON.parse(store['vireo.home.shortcuts.v1'] || '[]')};
}
const two = JSON.stringify(['https://a.test/', 'https://b.test/']);
const page = editable({store: {'vireo.home.shortcuts.v1': two}});
assert.equal(page.store['vireo.home.shortcuts.v1'], two, 'Loading never rewrites what earlier versions stored');
assert.equal(page.click(page.nodes[0]), false, 'Outside edit mode a shortcut opens normally');
assert.equal(page.click(page.youtube), false);
page.click(page.edit);
assert.equal(page.edit.textContent, 'Listo');
assert.equal(page.edit.attrs['aria-pressed'], 'true');
assert.ok(page.list.classList.contains('editing'));
page.answers.prompt.push('c.test');
assert.equal(page.click(page.nodes[0]), true);
assert.deepEqual(page.saved(), ['https://c.test/', 'https://b.test/'], 'Edited in place, order kept');
assert.equal(page.focused(), page.nodes[0], 'The remote stays on the edited shortcut');
page.answers.prompt.push('https://b.test/'); page.click(page.nodes[0]);
assert.deepEqual(page.saved(), ['https://c.test/', 'https://b.test/'], 'No duplicate by editing');
page.answers.prompt.push('javascript:alert(1)'); page.click(page.nodes[0]);
assert.deepEqual(page.saved(), ['https://c.test/', 'https://b.test/'], 'Only web addresses');
page.answers.prompt.push(null); page.click(page.nodes[0]);
assert.deepEqual(page.saved(), ['https://c.test/', 'https://b.test/'], 'Cancel changes nothing');
page.answers.prompt.push(''); page.answers.confirm.push(false); page.click(page.nodes[1]);
assert.equal(page.saved().length, 2, 'Removal needs a confirmation');
page.answers.prompt.push(' '); page.answers.confirm.push(true); page.click(page.nodes[1]);
assert.deepEqual(page.saved(), ['https://c.test/']);
assert.equal(page.focused(), page.nodes[0], 'Focus moves to the nearest remaining shortcut');
// The built-in YouTube shortcut: removed, kept removed after a restart, shown dimmed while editing, restored.
page.answers.confirm.push(false);
page.click(page.youtube);
assert.ok(!('vireo.home.youtube.hidden.v1' in page.store), 'Removing YouTube needs a confirmation');
page.answers.confirm.push(true);
assert.equal(page.click(page.youtube), true);
assert.equal(page.store['vireo.home.youtube.hidden.v1'], '1');
assert.equal(page.youtube.hidden, false, 'While editing it stays visible so it can be restored');
assert.ok(page.youtube.classList.contains('removed'));
page.click(page.edit);
assert.equal(page.youtube.hidden, true);
assert.equal(page.youtube.style.display, 'none');
const reloaded = editable({store: page.store});
assert.equal(reloaded.youtube.hidden, true, 'Removal survives a restart');
assert.deepEqual(reloaded.saved(), ['https://c.test/']);
reloaded.click(reloaded.edit);
reloaded.answers.confirm.push(true);
reloaded.click(reloaded.youtube);
assert.ok(!('vireo.home.youtube.hidden.v1' in reloaded.store));
reloaded.click(reloaded.edit);
assert.equal(reloaded.youtube.hidden, false);
assert.equal(reloaded.youtube.attrs['aria-label'], 'YouTube');
// Adding YouTube by address brings the built-in shortcut back instead of saving a copy.
const hiddenYoutube = editable({store: {'vireo.home.youtube.hidden.v1': '1'}});
hiddenYoutube.answers.prompt.push('www.youtube.com');
hiddenYoutube.click(hiddenYoutube.add);
assert.equal(hiddenYoutube.youtube.hidden, false);
assert.deepEqual(hiddenYoutube.saved(), []);
// A store that refuses writes leaves everything as it was and says so.
const readOnly = editable({store: {'vireo.home.shortcuts.v1': two}, failWrite: true});
readOnly.click(readOnly.edit);
readOnly.answers.prompt.push('c.test');
readOnly.click(readOnly.nodes[0]);
readOnly.answers.confirm.push(true);
readOnly.click(readOnly.youtube);
assert.equal(readOnly.alerts.length, 2);
assert.equal(readOnly.nodes[0].href, 'https://a.test/');
assert.ok(!readOnly.youtube.classList.contains('removed'));
// A store that cannot be read still shows YouTube, search and the controls.
const blocked = editable({failRead: true});
assert.equal(blocked.youtube.hidden, false);
assert.equal(blocked.nodes.length, 0);
console.log('Home editing passed: edit in place, confirmed removal, YouTube removal and restore across restarts, old data untouched, storage failures');

// Favorites saved in the browser appear on the home page, before the page's own shortcuts.
(async () => {
  const settle = () => new Promise(resolve => setImmediate(resolve));
  const withFavorites = editable({
    store: {'vireo.home.shortcuts.v1': JSON.stringify(['https://own.test/', 'https://news.test/today'])},
    favorites: [
      {title: 'Noticias', url: 'https://news.test/today'},
      {title: 'Música', url: 'music.test'},
      {title: 'Script', url: 'javascript:alert(1)'},
      {title: 'Repetido', url: 'https://music.test/'},
      {url: 'https://untitled.test/'},
      'not an object', null,
    ],
  });
  assert.deepEqual(withFavorites.requests, [['favorites.json', 'no-store']]);
  await settle();
  const labels = withFavorites.nodes.map(n => n.attrs['aria-label']);
  assert.deepEqual(labels, ['Noticias', 'Música', 'untitled.test', 'own.test'],
    'Favorites first, web addresses only, once each; a shortcut that is also a favorite shows once');
  assert.equal(withFavorites.nodes[0].href, 'https://news.test/today');
  assert.equal(withFavorites.nodes[0].attrs['data-favorite'], 'true');
  assert.equal(withFavorites.nodes[0].children[0].src, 'favicon://news.test');
  assert.deepEqual(withFavorites.nodes.map(n => n.children[1].textContent), ['Noticias', 'Música', 'untitled.test', 'own.test'],
    'Each shortcut is named, so they stay apart when the icons are not cached');
  assert.equal(withFavorites.click(withFavorites.nodes[0]), false, 'A favorite opens like any shortcut');
  assert.deepEqual(JSON.parse(withFavorites.store['vireo.home.shortcuts.v1']), ['https://own.test/', 'https://news.test/today'],
    'Showing favorites never rewrites the page\'s own shortcuts');
  // While editing, a favorite is not changed from the page: the Favorites panel manages it.
  withFavorites.click(withFavorites.edit);
  assert.equal(withFavorites.click(withFavorites.nodes[0]), true);
  assert.equal(withFavorites.alerts.length, 1);
  assert.equal(withFavorites.nodes.length, 4);
  // Many favorites are bounded; no favorites, a failed answer or no network leave the page as it was.
  const many = editable({favorites: Array.from({length: 60}, (_, i) => ({title: 't' + i, url: 'https://s' + i + '.test/'}))});
  await settle();
  assert.equal(many.nodes.length, 48);
  for (const answer of [[], null, 'error', {not: 'a list'}]) {
    const page = editable({store: {'vireo.home.shortcuts.v1': JSON.stringify(['https://own.test/'])}, favorites: answer});
    await settle();
    assert.deepEqual(page.nodes.map(n => n.attrs['aria-label']), ['own.test']);
    assert.equal(page.youtube.hidden, false);
  }
  console.log('Home favorites passed: browser favorites shown first and bounded, no duplicates, managed in the Favorites panel, failures harmless');
})().catch(error => { console.error(error); process.exit(1); });
