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
    // A cold engine must not permanently mark a frame as filtered before rules arrive.
    let delayed = page({}, 'stream.example', `
        var filterReads = 0;
        VireoLumaTVApp.pageFilters = function () {
            filterReads++;
            return JSON.stringify(filterReads === 1 ? { pending: true } : { hide: ['.late-ad'], script: '' });
        };
    `);
    vm.runInContext(`assert.equal(window.__vireoPageFilters, undefined);`, delayed);
    vm.runInContext(source, delayed); // Reinjection while waiting must not create another timer.
    delayed.flush();
    vm.runInContext(`
        assert.equal(filterReads, 2);
        assert.equal('VireoLumaTVApp' in window, false);
        assert.equal(window.__vireoPageFilters, true);
        assert.match(document.adoptedStyleSheets[0].css, /late-ad/);
    `, delayed);
    let waiting = page({}, 'stream.example', `
        var filterReads = 0;
        VireoLumaTVApp.pageFilters = function () { filterReads++; return '{"pending":true}'; };
    `);
    waiting.flush();
    vm.runInContext(`assert.equal(filterReads, 13); assert.equal(window.__vireoPageFilters, undefined);`, waiting);
    let empty = page({}, 'stream.example', `var filterReads = 0; VireoLumaTVApp.pageFilters = function () { filterReads++; return '{}'; };`);
    empty.flush();
    vm.runInContext(`assert.equal(filterReads, 1);`, empty);

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
    c = page({ script: call('set-constant', 'adblock.detected', 'false') + call('set-constant', 'canRunAds', 'true') +
        call('set-constant', 'emptyConfig', '{}') + call('set-constant', 'emptyList', '[]') + call('set-constant', 'title', '0') },
        'stream.example', 'window.title = "keep";');
    vm.runInContext(`
        assert.equal(canRunAds, true);
        canRunAds = false; assert.equal(canRunAds, true);
        window.adblock = { detected: true };
        assert.equal(adblock.detected, false);
        assert.deepEqual(emptyConfig, {});
        assert.deepEqual(emptyList, []);
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
        assert.equal(Object.prototype.hasOwnProperty.call(target, 'addEventListener'), false);
        var w = window.open('https://ads.example/pop');
        assert.notEqual(w, null); w.close(); assert.equal(w.closed, true);
        assert.equal(eval('antiAdblock()'), undefined);
        assert.equal(eval('player()'), 'evaluated');
    `, c);

    // no-window-open-if matches joined arguments (such as target '_blank') and auto-closes after delay.
    c = page({ script: call('no-window-open-if', '_blank', '1') });
    vm.runInContext(`
        var w = window.open('https://dynamic.example/path', '_blank', 'width=400');
        assert.notEqual(w, null);
        assert.equal(w.closed, false);
        w.document.body.appendChild(w.document.createElement('div'));
        w.location = 'https://popunder.example/';
        assert.equal(w.location.href, 'https://popunder.example/');
        flush();
        assert.equal(w.closed, true);
        assert.equal(window.open('https://allowed.example/path', '_self'), null);
    `, c);

    // Network stubs and JSON pruning.
    c = page({ script: call('no-fetch-if', 'url:/ads/ method:POST mode:no-cors', 'length:10') +
        call('no-fetch-if', 'url:/vast/', 'war:noop-vast3.xml') +
        call('trusted-prevent-fetch', 'url:/custom-stub/', '["4117"]') +
        call('json-prune', 'playerAds adPlacements', '') });
    const blocked = await vm.runInContext(`fetch('https://x.example/ads/track', { method: 'POST', mode: 'no-cors' })`, c);
    assert.equal(blocked.status, 200);
    assert.equal(blocked.body.length, 10);
    const vastStub = await vm.runInContext(`fetch('https://x.example/vast/ad.xml')`, c);
    assert.match(vastStub.body, /<VAST version="3\.0"><\/VAST>/);
    const customStub = await vm.runInContext(`fetch('https://x.example/custom-stub/check')`, c);
    assert.equal(customStub.body, '["4117"]');
    assert.equal(await vm.runInContext(`fetch('https://x.example/video.m3u8')`, c), 'network');
    vm.runInContext(`
        var parsed = JSON.parse('{"playerAds":[1],"adPlacements":[2],"video":{"id":"x"}}');
        assert.equal(parsed.playerAds, undefined);
        assert.equal(parsed.video.id, 'x');
    `, c);

    // remove-node-text acts on scripts added later and honours excludes; addEventListener-defuser honours elements.
    c = page({ script: call('remove-node-text', 'script', 'adsbygoogle', 'excludes', 'self.__next') +
        call('addEventListener-defuser', 'click', '', 'elements', '#get-link-button') +
        call('set-cookie', 'consent', 'accept') +
        call('set-cookie', 'evil', '<script>') + call('set-local-storage-item', 'adblock_seen', 'true') },
        'stream.example', 'delete window.addEventListener; Object.setPrototypeOf(window, EventTarget.prototype);');
    vm.runInContext(`
        var node = { nodeName: 'SCRIPT', textContent: '(adsbygoogle = window.adsbygoogle || []).push({})' };
        var excluded = { nodeName: 'SCRIPT', textContent: 'self.__next_f.push([1,"adsbygoogle"])' };
        var other = { nodeName: 'SCRIPT', textContent: 'player.load()' };
        observers[0].fn([{ addedNodes: [node, excluded, other] }]);
        assert.equal(node.textContent, '');
        assert.equal(excluded.textContent, 'self.__next_f.push([1,"adsbygoogle"])');
        assert.equal(other.textContent, 'player.load()');
        var blockedBtn = new EventTarget();
        blockedBtn.matches = function (sel) { return sel === '#get-link-button'; };
        var allowedBtn = new EventTarget();
        allowedBtn.matches = function () { return false; };
        blockedBtn.addEventListener('click', function () {});
        allowedBtn.addEventListener('click', function () {});
        assert.deepEqual(listeners, ['click']);
        assert.equal(Object.prototype.hasOwnProperty.call(window, 'addEventListener'), false);
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

    // A mutation burst must scan a queued subtree once, including nested attribute changes.
    let batched = page({hide: [], script: ''}, 'stream.example', `
        var scans = 0, leafScans = 0, lookups = [];
        var root = {nodeType: 1, id: '', classList: [], querySelectorAll() { return []; }};
        var child = {nodeType: 1, id: 'panel', classList: ['ad-box'], parentElement: root,
            querySelectorAll() { scans++; return [leaf]; }};
        var leaf = {nodeType: 1, id: 'sponsor', classList: ['sponsored'], parentElement: child,
            querySelectorAll() { leafScans++; return []; }};
        document.documentElement = root;
        document.readyState = 'complete';
        VireoLumaTVApp.hiddenSelectors = function (url, classes, ids) { lookups.push({classes, ids}); return '[]'; };
    `);
    batched.flush();
    vm.runInContext(`
        for (var i = 0; i < 100; i++) observers[0].fn([
            {type: 'attributes', target: child}, {type: 'childList', addedNodes: [child, leaf]}
        ]);
    `, batched);
    batched.flush();
    vm.runInContext(`
        assert.equal(scans, 1);
        assert.equal(leafScans, 0);
        assert.equal(lookups.length, 1);
        assert.match(lookups[0].ids, /panel/);
        assert.match(lookups[0].ids, /sponsor/);
        child.classList.push('new-ad');
        observers[0].fn([{type: 'attributes', target: child}]);
    `, batched);
    batched.flush();
    vm.runInContext(`assert.equal(lookups.length, 2); assert.equal(lookups[1].classes, 'new-ad');`, batched);

    // Class or id changes on many elements are read directly and never force a whole-document walk.
    let churn = page({hide: [], script: ''}, 'stream.example', `
        var walks = 0, names = [];
        var root = {nodeType: 1, id: '', classList: []};
        var nodes = Array.from({length: 300}, (_, i) => ({nodeType: 1, id: '', classList: ['c' + i], parentElement: root}));
        document.documentElement = root;
        document.readyState = 'complete';
        document.createNodeIterator = function (node) {
            if (node === root) walks++;
            var done = false;
            return {nextNode() { if (done) return null; done = true; return node; }};
        };
        VireoLumaTVApp.hiddenSelectors = function (url, classes) { names.push(...classes.split(' ').filter(Boolean)); return '[]'; };
    `);
    churn.flush();
    vm.runInContext(`
        assert.equal(walks, 1);
        observers[0].fn(nodes.map(function (n) { return {type: 'attributes', target: n}; }));
    `, churn);
    churn.flush();
    vm.runInContext(`assert.equal(walks, 1); assert.equal(new Set(names).size, 300);`, churn);

    // A large real-DOM traversal yields and never materializes the entire subtree as a NodeList.
    let sliced = page({hide: [], script: ''}, 'stream.example', `
        var visited = 0, names = [];
        var root = {nodeType: 1, id: '', classList: [], querySelectorAll() { throw Error('Full subtree scan'); }};
        var children = Array.from({length: 1000}, (_, i) => ({nodeType: 1, id: '', classList: ['slot-' + i]}));
        document.documentElement = root;
        document.readyState = 'complete';
        document.createNodeIterator = function () {
            var index = 0;
            return {nextNode() { visited++; return children[index++]; }};
        };
        VireoLumaTVApp.hiddenSelectors = function (url, classes) { names.push(...classes.split(' ')); return '[]'; };
    `);
    vm.runInContext(`assert.ok(visited <= 256); assert.ok(names.length < 1000);`, sliced);
    sliced.flush();
    vm.runInContext(`assert.equal(new Set(names).size, 1000);`, sliced);

    // Model live DOM traversal, including NodeIterator's pre-removal adjustment.
    let removal = page({hide: [], script: ''}, 'stream.example', `
        var names = [], iterators = [];
        var root = {nodeType: 1, id: '', classList: []};
        var children = Array.from({length: 1000}, (_, i) => ({nodeType: 1, id: '', classList: ['slot-' + i], parentElement: root}));
        document.documentElement = root; document.readyState = 'complete';
        function after(reference) {
            if (reference === root) return children[0] || null;
            return reference.parentElement === root ? children[children.indexOf(reference) + 1] || null : null;
        }
        document.createTreeWalker = function () {
            var reference = root;
            return {nextNode() { return reference = after(reference); }};
        };
        document.createNodeIterator = function () {
            var iterator = {reference: root, before: true,
                nextNode() {
                    if (this.before) { this.before = false; return this.reference; }
                    var next = after(this.reference);
                    if (next) this.reference = next;
                    return next;
                }};
            iterators.push(iterator); return iterator;
        };
        function removeCurrent() {
            var current = iterators.length ? iterators[0].reference : children[254];
            var index = children.indexOf(current);
            assert.ok(index >= 0);
            iterators.forEach(function (iterator) {
                if (iterator.reference === current) { iterator.reference = children[index - 1] || root; iterator.before = false; }
            });
            children.splice(index, 1); current.parentElement = null;
            observers[0].fn([{type: 'childList', addedNodes: [], removedNodes: [current]}]);
        }
        VireoLumaTVApp.hiddenSelectors = function (url, classes) { names.push(...classes.split(' ')); return '[]'; };
    `);
    vm.runInContext(`assert.ok(names.length < 1000); removeCurrent();`, removal);
    removal.flush();
    vm.runInContext(`assert.equal(new Set(names).size, 1000, 'removing current node must not drop later siblings');`, removal);

    // Every procedural rule still runs, but a batch yields after at most sixteen rules.
    let proceduralBatch = page({procedural: Array.from({length: 40}, (_, i) => JSON.stringify({
        selector: [{type: 'css-selector', arg: '.slot-' + i}, {type: 'has-text', arg: 'ad'}]
    }))}, 'stream.example', `
        var queries = [];
        document.documentElement = {};
        document.querySelectorAll = function (query) { queries.push(query); return []; };
    `);
    vm.runInContext(`__ready(); assert.ok(queries.length > 0 && queries.length <= 16);`, proceduralBatch);
    proceduralBatch.flush();
    vm.runInContext(`assert.equal(queries.length, 40); assert.equal(new Set(queries).size, 40);`, proceduralBatch);

    // Frames inserted by the named method get this window's wrapped functions (fetch, and
    // XMLHttpRequest for ".prototype"); cross-origin frames and other nodes are left alone.
    let multi = page({ script: call('trusted-prevent-dom-bypass', 'Node.prototype.appendChild', 'fetch JSON.parse') }, 'stream.example', `
        function Node() {}
        Node.prototype.appendChild = function appendChild(node) { return node; };
        var parsePristine = function () {}, fetchPristine = function () {};
        var frame = { nodeType: 1, nodeName: 'IFRAME', contentWindow: { location: { href: location.href },
            fetch: fetchPristine, JSON: { parse: parsePristine } } };
    `);
    vm.runInContext(`
        new Node().appendChild(frame);
        assert.equal(frame.contentWindow.fetch, window.fetch);
        assert.equal(frame.contentWindow.JSON.parse, JSON.parse);
    `, multi);

    let bypass = page({ script: call('trusted-prevent-dom-bypass', 'Node.prototype.appendChild', 'fetch') +
        call('trusted-prevent-dom-bypass', 'Node.prototype.appendChild', 'XMLHttpRequest.prototype') +
        call('no-fetch-if', 'ads') }, 'stream.example', `
        function Node() {}
        Node.prototype.appendChild = function appendChild(node) { return node; };
        window.XMLHttpRequest = function XMLHttpRequest() {};
        var fetchText = Function.prototype.toString.call(window.fetch);
        var pristine = function () { return Promise.resolve('pristine'); };
        var blank = { href: 'about:blank' };
        var frame = { nodeType: 1, nodeName: 'IFRAME', contentWindow: { location: blank, fetch: pristine, XMLHttpRequest: function () {} } };
        var nested = { nodeType: 1, nodeName: 'IFRAME', contentWindow: { location: blank, fetch: pristine } };
        var box = { nodeType: 1, nodeName: 'DIV', getElementsByTagName(tag) { return tag === 'iframe' ? [nested] : []; } };
        // A same-origin frame showing its own page keeps its functions (URLs resolve against it).
        var page = { nodeType: 1, nodeName: 'IFRAME', contentWindow: { location: { href: 'https://stream.example/chat' }, fetch: pristine } };
        var foreign = { nodeType: 1, nodeName: 'IFRAME', get contentWindow() { throw new Error('cross-origin'); } };
    `);
    vm.runInContext(`
        var parent = new Node();
        assert.equal(parent.appendChild(frame), frame);
        assert.equal(frame.contentWindow.fetch, window.fetch);
        assert.notEqual(window.fetch, pristine);
        assert.equal(frame.contentWindow.XMLHttpRequest, window.XMLHttpRequest);
        parent.appendChild(box);
        assert.equal(nested.contentWindow.fetch, window.fetch);
        assert.equal(parent.appendChild(foreign), foreign);
        parent.appendChild(page);
        assert.equal(page.contentWindow.fetch, pristine);
        // Wrapped functions print as the original, and so does the masked toString itself.
        assert.equal(Function.prototype.toString.call(window.fetch), fetchText);
        assert.equal(Function.prototype.toString.call(Node.prototype.appendChild), 'function appendChild(node) { return node; }');
        assert.equal(Function.prototype.toString.call(Function.prototype.toString), 'function toString() { [native code] }');
        // Browser markers are readable by the browser but not listed among window keys.
        assert.equal(window.__vireoPageFilters, true);
        assert.deepEqual(Object.keys(window).filter(function (key) { return key.indexOf('__vireo') === 0; }), []);
    `, bypass);

    // Inserting a real DocumentFragment consumes its children before appendChild returns.
    const fragmentBypass = page({script: call('trusted-prevent-dom-bypass', 'Node.prototype.appendChild', 'fetch')},
        'stream.example', `
        function Node() {}
        Node.prototype.appendChild = function (node) {
            if (node.nodeType === 11) node.children = [];
            return node;
        };
        var pristine = function () {};
        var blankFrame = {nodeType: 1, nodeName: 'IFRAME', contentWindow: {
            location: {href: 'about:blank'}, fetch: pristine}};
        var pageFrame = {nodeType: 1, nodeName: 'IFRAME', contentWindow: {
            location: {href: 'https://stream.example/chat'}, fetch: pristine}};
        var foreignFrame = {nodeType: 1, nodeName: 'IFRAME', get contentWindow() { throw Error('cross-origin'); }};
        var fragment = {nodeType: 11, children: [blankFrame, pageFrame, foreignFrame],
            querySelectorAll(selector) { assert.equal(selector, 'iframe'); return this.children.slice(); }};
    `);
    vm.runInContext(`
        assert.equal(new Node().appendChild(fragment), fragment);
        assert.equal(fragment.children.length, 0);
        assert.equal(blankFrame.contentWindow.fetch, window.fetch, 'protect frames moved out of a consumed fragment');
        assert.equal(pageFrame.contentWindow.fetch, pristine, 'keep a legitimate frame independent');
    `, fragmentBypass);

    // json-edit queries taken from the bundled uBlock filters. Untrusted names only remove.
    let edit = page({ script: call('json-edit', '..nodes.*[?.sponsored_data]') + call('json-edit', '.showAds=false') +
        call('trusted-json-edit', '[?.type=="ads"].visibility.status="hidden"') +
        call('trusted-json-edit', '.lact="${now}"') +
        call('trusted-json-edit', '.title=repl({"regex":"Ad ","flags":"g","replacement":""})') +
        call('json-edit', '.broken[?.x==') + call('trusted-json-edit', '.x=call(["${obj}","f"])') });
    vm.runInContext(`
        var feed = JSON.parse('{"data":{"nodes":[{"id":1},{"id":2,"sponsored_data":{}},{"id":3}]},"showAds":true}');
        assert.deepEqual(feed.data.nodes.map(function (n) { return n.id; }), [1, 3]);
        assert.equal(feed.showAds, true);
        var cards = JSON.parse('[{"type":"ads","visibility":{"status":"shown"}},{"type":"text","visibility":{"status":"shown"}}]');
        assert.equal(cards[0].visibility.status, 'hidden');
        assert.equal(cards[1].visibility.status, 'shown');
        assert.equal(JSON.parse('{"title":"Ad one Ad two"}').title, 'one two');
        assert.equal(JSON.parse('7'), 7);
        assert.match(JSON.parse('{"lact":"0"}').lact, /^[0-9]{13}$/);
    `, edit);

    // Explicit array indices may arrive unsorted or repeated; edits refer to the original array.
    for (const [query, expected] of [['.items[2,0]', [1, 3]], ['.items[0,2]', [1, 3]],
            ['.items[2,0,2]', [1, 3]], ['.items[-1,0]', [1, 2]]]) {
        const arrays = page({script: call('json-edit', query)});
        vm.runInContext(`assert.deepEqual(JSON.parse('{"items":[0,1,2,3]}').items, ${JSON.stringify(expected)});`, arrays);
    }
    const inherited = page({script: call('json-edit', '.__proto__.vireoSentinel')}, 'stream.example', `
        Object.prototype.vireoSentinel = 'keep';
    `);
    vm.runInContext(`
        assert.deepEqual(JSON.parse('{"id":1}'), {id: 1});
        assert.equal(Object.prototype.vireoSentinel, 'keep', 'JSON edits must not traverse inherited properties');
    `, inherited);
    const ownProto = page({script: call('json-edit', '.__proto__.ad')});
    vm.runInContext(`
        var value = JSON.parse('{"__proto__":{"ad":true,"keep":1}}');
        assert.equal(Object.hasOwn(value, '__proto__'), true);
        assert.deepEqual(value.__proto__, {keep: 1}, 'an own JSON property named __proto__ is still data');
    `, ownProto);

    // Responses: fetch (propsToMatch) and JSON Lines over XHR; other URLs are untouched.
    let responses = page({ script: call('trusted-json-edit-fetch-response', '.result.PlayAds=false', 'propsToMatch', '/api/get-urls') +
        call('jsonl-edit-xhr-response', '..node[?.*.__typename=="SponsoredData"]', 'propsToMatch', '/graphql') }, 'stream.example', `
        Response = class { constructor(body, init) { this.body = body; this.status = init && init.status;
            this.headers = { get(name) { return name === 'content-type' ? 'application/json' : null; } }; }
            clone() { return this; } text() { return Promise.resolve(this.body); } };
        window.fetch = function () { return Promise.resolve(new Response('{"result":{"PlayAds":true,"url":"v"}}', { status: 200 })); };
        function XMLHttpRequest() { this.readyState = 4; this.responseType = ''; }
        XMLHttpRequest.prototype.open = function () {};
        Object.defineProperty(XMLHttpRequest.prototype, 'responseText', { configurable: true, get() { return this.body; } });
        Object.defineProperty(XMLHttpRequest.prototype, 'response', { configurable: true, get() { return this.body; } });
        window.XMLHttpRequest = XMLHttpRequest;
    `);
    const edited = await vm.runInContext(`fetch('https://stream.example/api/get-urls').then(function (r) { return r.text(); })`, responses);
    assert.deepEqual(JSON.parse(edited), { result: { PlayAds: false, url: 'v' } });
    const untouched = await vm.runInContext(`fetch('https://stream.example/other').then(function (r) { return r.text(); })`, responses);
    assert.equal(untouched, '{"result":{"PlayAds":true,"url":"v"}}');
    vm.runInContext(`
        var xhr = new XMLHttpRequest();
        xhr.open('POST', 'https://stream.example/api/graphql');
        xhr.body = '{"node":{"a":{"__typename":"SponsoredData"}},"id":1}\\n{"node":{"a":{"__typename":"Story"}},"id":2}';
        var lines = xhr.responseText.split('\\n').map(function (l) { return JSON.parse(l); });
        assert.deepEqual(lines[0], { id: 1 });
        assert.equal(lines[1].node.a.__typename, 'Story');
    `, responses);

    // Inbound copies, setters with v2 regex keys, outbound text, navigation and clipboard.
    let misc = page({ script: call('trusted-edit-inbound-object', 'JSON.stringify', '0',
            '[?.attestationRequest][?.context.client.userAgent=/yahi|instream/]+={"params":"YAHI"}') +
        call('edit-object-on-setter', 'flashvars', 'v2:$./^adv_/') +
        call('trusted-replace-outbound-text', 'makeText', '/ad/g', 'x', 'condition', 'keep') +
        call('trusted-replace-outbound-text', 'encoded', 'ad', 'ok', 'encoding', 'base64') +
        call('prevent-navigation', '/werbeblocker/i') + call('prevent-clipboard-write', '/^powershell /') +
        call('multiup') }, 'stream.example', `
        btoa = function (s) { return { 'ad here': 'YWQgaGVyZQ==', 'ok here': 'b2sgaGVyZQ==' }[s]; };
        window.makeText = function () { return this.text; };
        window.encoded = function () { return btoa('ad here'); };
        var navigation = { addEventListener(type, fn) { this.handler = fn; } };
        window.navigation = navigation;
        var copied = [];
        function Clipboard() {}
        Clipboard.prototype.writeText = function (text) { copied.push(text); return Promise.resolve(); };
        window.Clipboard = Clipboard;
        var commands = [], selected = '';
        document.execCommand = function execCommand(command) { commands.push(command); return true; };
        document.getSelection = function () { return { toString() { return selected; } }; };
        var clickHandler = null;
        document.addEventListener = function (type, fn) { if (type === 'click') clickHandler = fn; };
    `);
    vm.runInContext(`
        var request = { attestationRequest: 1, context: { client: { userAgent: 'x-yahi' } } };
        var sent = JSON.parse(JSON.stringify(request));
        assert.equal(sent.params, 'YAHI');
        assert.equal(request.params, undefined);
        assert.equal(JSON.parse(JSON.stringify({ attestationRequest: 1, context: { client: { userAgent: 'web' } } })).params, undefined);
        window.flashvars = { adv_pre: 'u', adv_post: 'v', video: 'w' };
        assert.deepEqual(Object.keys(flashvars), ['video']);
        assert.equal(makeText.call({ text: 'keep ad ad' }), 'keep x x');
        assert.equal(makeText.call({ text: 'other ad' }), 'other ad');
        assert.equal(atob(encoded()), 'ok here');
        var event = { userInitiated: false, cancelable: true, destination: { url: 'https://s.example/Werbeblocker' },
            preventDefault() { this.prevented = true; } };
        navigation.handler(event);
        assert.equal(event.prevented, true);
        var clicked = { userInitiated: true, cancelable: true, destination: { url: 'https://s.example/werbeblocker' },
            preventDefault() { this.prevented = true; } };
        navigation.handler(clicked);
        assert.equal(clicked.prevented, undefined);
        new Clipboard().writeText('powershell -w hidden');
        new Clipboard().writeText('hello');
        new Clipboard().writeText('  powershell -enc AAA  ');
        assert.deepEqual(copied, ['hello']);
        // The legacy copy command copies the selection: a matching lure is dropped, others copy.
        selected = '   powershell -w hidden -c iwr  ';
        assert.equal(document.execCommand('copy'), true);
        selected = 'plain text';
        document.execCommand('copy');
        document.execCommand('bold');
        assert.deepEqual(commands, ['copy', 'bold']);
        var form = {};
        var button = { parentElement: form, matches(s) { return s === 'button[link]'; }, closest() { return form; },
            getAttribute() { return ' https://multiup.example/file '; } };
        var stopped = 0;
        clickHandler({ target: button, preventDefault() { stopped++; }, stopPropagation() { stopped++; } });
        assert.equal(location.href, 'https://multiup.example/file');
        assert.equal(stopped, 2);
    `, misc);

    // A text control's selected value is independent of Document.getSelection().
    const controlClipboard = page({script: call('prevent-clipboard-write', '/^powershell /',
        'excludeMatches', 'allowed')}, 'stream.example', `
        var copiedCommands = [], selected = '';
        document.execCommand = function (command) { copiedCommands.push(command); return true; };
        document.getSelection = function () { return {toString() { return selected; }}; };
        document.activeElement = null;
    `);
    vm.runInContext(`
        var value = 'prefix powershell blocked suffix';
        for (var localName of ['input', 'textarea']) {
            document.activeElement = {localName: localName, value: value,
                selectionStart: 7, selectionEnd: 25};
            assert.equal(document.execCommand('copy'), true);
            assert.equal(document.execCommand('cut'), true);
        }
        assert.deepEqual(copiedCommands, [], 'block matching selected text from inputs and textareas');
        // Copy only the selected slice, never test the entire value or a stale page selection.
        document.activeElement.selectionStart = 0;
        document.activeElement.selectionEnd = 6;
        selected = 'powershell blocked';
        document.execCommand('copy');
        document.activeElement = {localName: 'input', value: 'powershell allowed', selectionStart: 0, selectionEnd: 18};
        document.execCommand('copy');
        document.activeElement = {localName: 'input', value: 'plain', selectionStart: null, selectionEnd: null};
        selected = 'plain';
        document.execCommand('copy');
        document.execCommand('bold');
        document.activeElement = {localName: 'div'};
        selected = 'powershell blocked';
        document.execCommand('copy');
        assert.deepEqual(copiedCommands, ['copy', 'copy', 'copy', 'bold']);
    `, controlClipboard);

    // Single injection per frame.
    vm.runInContext(source, c);
    vm.runInContext(`assert.equal(document.adoptedStyleSheets.length, 1);`, c);

    console.log('Page filters passed: trusted values/storage/arguments, constructed stylesheet, procedural/action filters, media report, DOM bypass, toString masking, json-edit queries/responses/JSONL, inbound/setter edits, outbound text, navigation, clipboard, multiup, hidden markers, set-constant, aopr/acs/aopw, timers, listeners, nowoif, noeval-if, no-fetch-if, json-prune, rmnt, cookies/storage, YouTube exclusion');
})().catch((error) => { console.error(error); process.exit(1); });
