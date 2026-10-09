const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
// The app injects the asset compacted (AdblockPageController.compact): test exactly that text.
const compact = (text) => text.split('\n').map((l) => l.trim()).filter((l) => l && !l.startsWith('//')).join('\n');
const source = compact(fs.readFileSync('app/src/main/assets/adblock/page_filters.js', 'utf8'));
new Function(compact(fs.readFileSync('app/src/main/assets/window_open_decoy.js', 'utf8')));

/** Output format of adblock-rust's injected_script for our template resources. */
function call(name, ...args) {
    const padded = args.concat(Array.from({ length: 9 - args.length }, (_, i) => `{{${args.length + i + 1}}}`));
    return 'try {\n/*@VIREO@*/' + JSON.stringify([name, padded]) + '/*@END@*/\n} catch ( e ) { }\n';
}

function page(filters, host = 'stream.example', setup = '') {
    const timers = [];
    const context = vm.createContext({ assert, console, atob: (s) => Buffer.from(s, 'base64').toString('binary') });
    context.setTimeout = (fn) => { timers.push(fn); return timers.length; };
    context.setInterval = (fn, delay) => { context.lastInterval = { fn, delay }; return 1; };
    context.flush = () => { while (timers.length) timers.shift()(); };
    vm.runInContext(`
        window = globalThis;
        var observers = [];
        class MutationObserver { constructor(fn) { this.fn = fn; observers.push(this); } observe() {} disconnect() {} }
        class CSSStyleSheet { replaceSync(css) { this.css = css; } }
        var listeners = [];
        function EventTarget() {}
        EventTarget.prototype.addEventListener = function (type, fn) { listeners.push(type); };
        var cookieJar = [];
        document = { readyState: 'loading', adoptedStyleSheets: [], currentScript: null,
            addEventListener(type, fn) { if (type === 'DOMContentLoaded') window.__ready = fn; },
            querySelectorAll() { return []; },
            get cookie() { return cookieJar.join('; '); }, set cookie(v) { cookieJar.push(v.split(';')[0]); } };
        location = { href: 'https://${host}/watch', hostname: '${host}', pathname: '/watch' };
        localStorage = { items: {}, setItem(k, v) { this.items[k] = v; }, removeItem(k) { delete this.items[k]; } };
        window.addEventListener = function () {};
        window.open = function (url) { return null; };
        window.fetch = function () { return Promise.resolve('network'); };
        class Response { constructor(body, init) { this.body = body; this.status = init.status; } json() { return Promise.resolve(JSON.parse(this.body || 'null')); } }
        window.eval = function (code) { return 'evaluated'; };
        VireoLumaTVApp = { pageFilters(url) { assert.equal(url, location.href); return ${JSON.stringify(JSON.stringify(filters))}; } };
        ${setup}
    `, context);
    vm.runInContext(source, context);
    return context;
}

(async () => {
    // Element hiding through a constructed stylesheet (not blocked by page CSP).
    let c = page({ hide: ['.ad-box', '#banner'], script: '' });
    vm.runInContext(`
        assert.equal(document.adoptedStyleSheets.length, 1);
        assert.match(document.adoptedStyleSheets[0].css, /\\.ad-box\\{display:none!important\\}\\n#banner\\{display:none!important\\}/);
    `, c);

    // Procedural/action filters: pure CSS ones become stylesheet rules; uBO-only pseudo-classes
    // never reach the stylesheet; DOM ones run on DOMContentLoaded.
    c = page({ hide: ['.ad', 'div:has-text(Sponsored)'], script: '', procedural: [
        JSON.stringify({ selector: [{ type: 'css-selector', arg: '.player' }], action: { type: 'style', arg: 'visibility: visible !important' } }),
        JSON.stringify({ selector: [{ type: 'css-selector', arg: '.overlay' }] }),
        JSON.stringify({ selector: [{ type: 'css-selector', arg: '.box' }, { type: 'has-text', arg: 'adblock' }], action: { type: 'remove' } })
    ] }, 'stream.example', `
        var removed = [];
        var box = { textContent: 'Please disable adblock', parentNode: { removeChild(n) { removed.push(n); } } };
        var keep = { textContent: 'Video', parentNode: { removeChild(n) { removed.push(n); } } };
        document.documentElement = {};
        document.querySelectorAll = function (q) { return q === '.box' ? [box, keep] : []; };
    `);
    vm.runInContext(`
        var css = document.adoptedStyleSheets[0].css;
        assert.match(css, /\.ad\{display:none!important\}/);
        assert.doesNotMatch(css, /has-text/);
        assert.match(css, /\.player\{visibility: visible !important\}/);
        assert.match(css, /\.overlay\{display:none!important\}/);
        window.__ready();
        assert.deepEqual(removed, [box]);
    `, c);

    // Media playback is reported once per frame, so popups cannot replace a playing player.
    c = page({ script: '' }, 'stream.example', `
        var played = 0, playingListener = null;
        VireoLumaTVApp.mediaStarted = function () { played++; };
        document.addEventListener = function (type, fn) { if (type === 'DOMContentLoaded') window.__ready = fn; if (type === 'playing') playingListener = fn; };
        document.removeEventListener = function (type, fn) { if (type === 'playing' && fn === playingListener) playingListener = null; };
    `);
    vm.runInContext(`
        assert.equal(typeof playingListener, 'function');
        playingListener({ target: { paused: false, muted: true, volume: 1, duration: 600 } });
        playingListener({ target: { paused: false, muted: false, volume: 1, duration: 15 } });
        assert.equal(played, 0, 'muted autoplay and short clips are not the user watching');
        playingListener({ target: { paused: false, muted: false, volume: 1, duration: Infinity } });
        assert.equal(played, 1);
        assert.equal(playingListener, null);
    `, c);

    // Trusted values, storage, argument replacement, frame callbacks, window close.
    c = page({ script: call('trusted-set-constant', 'cfg.ads', 'json:{"on":false}') + call('set-session-storage-item', 'seen', 'true') +
        call('set-session-storage-item', 'evil', '<x>') + call('trusted-set-local-storage-item', 'ts', '$now$') +
        call('trusted-replace-argument', 'decide', '0', 'false', 'condition', 'adblock') +
        call('no-requestAnimationFrame-if', 'detect') + call('window-close-if', '/watch') + call('alert-buster') },
        'stream.example', `
        sessionStorage = { items: {}, setItem(k, v) { this.items[k] = v; }, removeItem(k) { delete this.items[k]; } };
        window.decide = function (v) { return v; };
        var frames = [];
        window.requestAnimationFrame = function (fn) { frames.push(fn); return 1; };
        var closed = 0; window.close = function () { closed++; };
        window.alert = function () { throw new Error('alert'); };
    `);
    vm.runInContext(`
        window.cfg = {};
        assert.equal(JSON.stringify(cfg.ads), '{"on":false}');
        assert.equal(sessionStorage.items.seen, 'true');
        assert.equal(sessionStorage.items.evil, undefined);
        assert.match(localStorage.items.ts, /^\\d{13}$/);
        assert.equal(decide('adblock on'), false);
        assert.equal(decide('normal'), 'normal');
        requestAnimationFrame(function detectAdblock() { throw new Error('ran'); });
        frames[0]();
        assert.equal(closed, 1);
        alert('x');
    `, c);

    // set-constant on existing and later-created chains; type mismatch leaves the property alone.
    c = page({ script: call('set-constant', 'adblock.detected', 'false') + call('set-constant', 'canRunAds', 'true') + call('set-constant', 'title', '0') },
        'stream.example', 'window.title = "keep";');
    vm.runInContext(`
        assert.equal(canRunAds, true);
        canRunAds = false; assert.equal(canRunAds, true);
        window.adblock = { detected: true };
        assert.equal(adblock.detected, false);
        assert.equal(title, 'keep');
    `, c);

    // abort-on-property-read / abort-current-script / abort-on-property-write.
    c = page({ script: call('abort-on-property-read', 'popunder.init') + call('abort-current-script', 'detector', 'adblock') + call('abort-on-property-write', 'ads') });
    vm.runInContext(`
        window.popunder = {};
        assert.throws(() => popunder.init, ReferenceError);
        window.detector = 1;
        document.currentScript = { tagName: 'SCRIPT', textContent: 'if (adblock) detector()', src: '' };
        assert.throws(() => detector, ReferenceError);
        document.currentScript = { tagName: 'SCRIPT', textContent: 'harmless()', src: '' };
        assert.equal(detector, 1);
        assert.throws(() => { window.ads = 1; }, ReferenceError);
    `, c);

    // Timers, listeners, window.open, eval.
    c = page({ script: call('no-setTimeout-if', 'adblock') + call('nano-setInterval-booster', 'countdown', '*', '0.02') +
        call('addEventListener-defuser', 'click', 'popMagic') + call('no-window-open-if') + call('noeval-if', 'antiAdblock') });
    vm.runInContext(`
        var fired = 0;
        setTimeout(function () { /* adblock check */ fired++; }, 10);
        setTimeout(function () { fired += 10; }, 10);
        flush();
        assert.equal(fired, 10);
        setInterval(function countdown() {}, 1000);
        assert.equal(lastInterval.delay, 20);
        var target = new EventTarget();
        target.addEventListener('click', function () { popMagic.go(); });
        target.addEventListener('click', function () { play(); });
        assert.deepEqual(listeners, ['click']);
        var w = window.open('https://ads.example/pop');
        assert.notEqual(w, null); w.close(); assert.equal(w.closed, true);
        assert.equal(eval('antiAdblock()'), undefined);
        assert.equal(eval('player()'), 'evaluated');
    `, c);

    // Network stubs and JSON pruning.
    c = page({ script: call('no-fetch-if', 'url:/ads/ method:POST') + call('json-prune', 'playerAds adPlacements', '') });
    const blocked = await vm.runInContext(`fetch('https://x.example/ads/track', { method: 'POST' })`, c);
    assert.equal(blocked.status, 200);
    assert.equal(await vm.runInContext(`fetch('https://x.example/video.m3u8')`, c), 'network');
    vm.runInContext(`
        var parsed = JSON.parse('{"playerAds":[1],"adPlacements":[2],"video":{"id":"x"}}');
        assert.equal(parsed.playerAds, undefined);
        assert.equal(parsed.video.id, 'x');
    `, c);

    // remove-node-text acts on scripts added later; cookies/storage only take safe values.
    c = page({ script: call('remove-node-text', 'script', 'adsbygoogle') + call('set-cookie', 'consent', 'accept') +
        call('set-cookie', 'evil', '<script>') + call('set-local-storage-item', 'adblock_seen', 'true') });
    vm.runInContext(`
        var node = { nodeName: 'SCRIPT', textContent: '(adsbygoogle = window.adsbygoogle || []).push({})' };
        var other = { nodeName: 'SCRIPT', textContent: 'player.load()' };
        observers[0].fn([{ addedNodes: [node, other] }]);
        assert.equal(node.textContent, '');
        assert.equal(other.textContent, 'player.load()');
        assert.match(document.cookie, /consent=accept/);
        assert.doesNotMatch(document.cookie, /evil/);
        assert.equal(localStorage.items.adblock_seen, 'true');
    `, c);

    // Unknown scriptlets are ignored; YouTube keeps only element hiding (own dedicated filter).
    c = page({ hide: ['ytd-ad-slot-renderer'], script: call('trusted-replace-xhr-response', 'a', 'b') + call('set-constant', 'ytAds', 'false') }, 'www.youtube.com');
    vm.runInContext(`
        assert.equal(document.adoptedStyleSheets.length, 1);
        assert.equal(typeof window.ytAds, 'undefined');
    `, c);

    // Single injection per frame.
    vm.runInContext(source, c);
    vm.runInContext(`assert.equal(document.adoptedStyleSheets.length, 1);`, c);

    console.log('Page filters passed: trusted values/storage/arguments, constructed stylesheet, procedural/action filters, media report, set-constant, aopr/acs/aopw, timers, listeners, nowoif, noeval-if, no-fetch-if, json-prune, rmnt, cookies/storage, YouTube exclusion');
})().catch((error) => { console.error(error); process.exit(1); });
