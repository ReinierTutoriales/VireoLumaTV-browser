// When the browser blocks a popup, window.open() returns null. Players use that to claim they are
// "sandboxed" and refuse to play (embed.st: window.open('about:blank') on click, null => overlay
// cached for one hour). Return an inert window instead; no real window is opened. Same idea as
// uBlock Origin's no-window-open-if decoy.
(function () {
    'use strict';
    if (window.__vireoOpenDecoy) return;
    var original = window.open;
    if (typeof original !== 'function') return;
    window.__vireoOpenDecoy = true;
    function noop() {}
    function decoy(url) {
        var closed = false;
        var href = url ? String(url) : 'about:blank';
        var fake = {
            get closed() { return closed; },
            close: function () { closed = true; },
            focus: noop, blur: noop, postMessage: noop, stop: noop, print: noop,
            moveTo: noop, resizeTo: noop, scrollTo: noop,
            opener: window, name: '', length: 0, frames: [],
            location: { href: href, assign: noop, replace: noop, reload: noop, toString: function () { return href; } },
            document: { write: noop, writeln: noop, open: noop, close: noop, body: null,
                addEventListener: noop, removeEventListener: noop },
            addEventListener: noop, removeEventListener: noop
        };
        fake.window = fake;
        fake.self = fake;
        fake.top = fake;
        return fake;
    }
    var wrapped = function open(url) {
        var opened = null;
        try { opened = original.apply(this, arguments); } catch (_) {}
        return opened || decoy(url);
    };
    try {
        Object.defineProperty(wrapped, 'toString', { value: function () { return original.toString(); } });
    } catch (_) {}
    try { window.open = wrapped; } catch (_) {}
})();
