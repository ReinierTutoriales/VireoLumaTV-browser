const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const listeners = {};
let items = [];
const sandbox = {isFinite, document: {
    addEventListener: (name, fn) => { listeners['document:' + name] = fn; },
    querySelectorAll: () => items,
    querySelector: type => items.find(m => m.tag === type) || null,
}, addEventListener: (name, fn) => { listeners[name] = fn; }};
sandbox.window = sandbox;
vm.createContext(sandbox);
const source = fs.readFileSync('app/src/main/assets/generic_injects.js', 'utf8');
vm.runInContext(source, sandbox);
const ranges = values => ({length: values.length, start: i => values[i][0], end: i => values[i][1]});
const media = (overrides = {}) => ({tag: 'video', currentTime: 0, paused: false, ended: false,
    readyState: 1, seekable: ranges([[0, 120]]), plays: 0, pauses: 0,
    play() { this.plays++; return Promise.resolve(); },
    pause() { this.pauses++; this.paused = true; }, ...overrides});
(async () => {
    const buffering = media(); items = [buffering];
    sandbox.vireoLumaTVTogglePlayback();
    assert.equal(buffering.pauses, 1); assert.equal(buffering.plays, 0);
    const rejected = media({paused: true, play() { this.plays++; return Promise.reject(new Error('blocked')); }});
    items = [rejected]; sandbox.vireoLumaTVTogglePlayback();
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(rejected.plays, 1);
    const active = media({currentTime: 105, seekable: ranges([[100, 110]])});
    const inactive = media({paused: true}); items = [inactive, active];
    sandbox.vireoLumaTVRewind(); assert.equal(active.currentTime, 100); assert.equal(inactive.currentTime, 0);
    sandbox.vireoLumaTVFastForward(); assert.equal(active.currentTime, 109.95);
    sandbox.vireoLumaTVStopPlayback(); assert.equal(active.currentTime, 100); assert.equal(active.pauses, 1);
    const live = media({currentTime: 80, seekable: ranges([])}); items = [live];
    sandbox.vireoLumaTVFastForward(); sandbox.vireoLumaTVRewind();
    assert.equal(live.currentTime, 80);
    const gaps = media({seekable: ranges([[0, 10], [30, 40]])});
    sandbox.vireoLumaTVSeek(gaps, 25); assert.equal(gaps.currentTime, 30);
    sandbox.vireoLumaTVSeek(gaps, NaN); assert.equal(gaps.currentTime, 30);
    const dynamic = media({seekable: {length: 1, start() { throw Error('window moved'); }}});
    sandbox.vireoLumaTVSeek(dynamic, 10); assert.equal(dynamic.currentTime, 0);
    const preview = media({getBoundingClientRect() { return {width: 160, height: 90}; }});
    const main = media({getBoundingClientRect() { return {width: 1280, height: 720}; }});
    const backgroundAudio = media({tag: 'audio'});
    items = [backgroundAudio, preview, main];
    sandbox.vireoLumaTVTogglePlayback();
    assert.equal(main.pauses, 1); assert.equal(preview.pauses, 0); assert.equal(backgroundAudio.pauses, 0);
    const fullscreen = media();
    sandbox.document.fullscreenElement = fullscreen;
    fullscreen.matches = selector => selector === 'video, audio';
    sandbox.vireoLumaTVTogglePlayback(); assert.equal(fullscreen.pauses, 1);
    delete sandbox.document.fullscreenElement;
    const hidden = media({getBoundingClientRect() { return {width: 0, height: 0}; }});
    items = [hidden, backgroundAudio];
    sandbox.vireoLumaTVTogglePlayback(); assert.equal(backgroundAudio.pauses, 1); assert.equal(hidden.pauses, 0);
    const progressive = media({duration: 120, currentTime: 50, seekable: ranges([])});
    items = [progressive];
    sandbox.vireoLumaTVFastForward(); assert.equal(progressive.currentTime, 60);
    sandbox.vireoLumaTVSeek(progressive, 300); assert.equal(progressive.currentTime, 120);
    const unseekableLive = media({duration: Infinity, currentTime: 50, seekable: ranges([])});
    items = [unseekableLive];
    sandbox.vireoLumaTVFastForward(); assert.equal(unseekableLive.currentTime, 50);
    const links = ['ESPN', 'OP2', 'OP3', 'Disney+'].map((name, i) => ({href: 'https://example.com/' + i}));
    const script = fs.readFileSync('app/src/main/java/com/reiniertutoriales/vireolumatv/webengine/webview/PageScripts.kt', 'utf8').split('"""')[1];
    links.forEach(link => {
        const child = {isConnected: true, closest: selector => { assert.equal(selector, 'a[href]'); return link; }};
        listeners.mousedown({isTrusted: true, target: child, clientX: 12, clientY: 34});
        assert.equal(vm.runInContext(script, sandbox), link.href);
    });
    const tracked = sandbox.VIREOLUMATV_activeElement;
    listeners.mousedown({isTrusted: false, target: {}});
    assert.equal(sandbox.VIREOLUMATV_activeElement, tracked);
    listeners.touchstart({isTrusted: true, target: {isConnected: false}, touches: [{clientX: 1, clientY: 2}]});
    assert.equal(vm.runInContext(script, sandbox), null);
    const oldMouseListener = listeners.mousedown;
    vm.runInContext(source, sandbox); assert.equal(listeners.mousedown, oldMouseListener);
    console.log('WebView controls passed: buffering pause, rejected play, live/DVR/gap seeks, nested links, trusted input and reinjection');
})().catch(e => { console.error(e); process.exitCode = 1; });
