// When the browser blocks a popup, window.open() returns null. Players use that to claim they are
// "sandboxed" and refuse to play (embed.st: window.open('about:blank') on click, null => overlay
// cached for one hour). Return an inert window instead; no real window is opened. Same idea as
// uBlock Origin's no-window-open-if decoy.
(function () {
    'use strict';
    if (window.__vireoOpenDecoy) return;
    var original = window.open;
    if (typeof original !== 'function' || typeof Proxy !== 'function' || typeof Reflect !== 'object') return;
    // Not enumerable: detectors that list window keys must not see the browser's own marker.
    try { Object.defineProperty(window, '__vireoOpenDecoy', { value: true, configurable: true }); } catch (_) { return; }
    function noop() {}
    function decoyElement() {
        return { style: {}, setAttribute: noop, getAttribute: function () { return null; },
            appendChild: noop, removeChild: noop, addEventListener: noop, removeEventListener: noop };
    }
    function decoy(url) {
        var closed = false;
        var href = url ? String(url) : 'about:blank';
        var loc = {
            get href() { return href; },
            set href(v) { href = String(v); },
            assign: function (v) { href = String(v); },
            replace: function (v) { href = String(v); },
            reload: noop,
            toString: function () { return href; }
        };
        var doc = {
            write: noop, writeln: noop, open: function () { return doc; }, close: noop,
            createElement: decoyElement, createTextNode: function () { return {}; },
            getElementById: function () { return null; }, querySelector: function () { return null; },
            querySelectorAll: function () { return []; },
            body: decoyElement(), head: decoyElement(), documentElement: decoyElement(),
            readyState: 'complete', addEventListener: noop, removeEventListener: noop
        };
        var fake = {
            get closed() { return closed; },
            close: function () { closed = true; },
            focus: noop, blur: noop, postMessage: noop, stop: noop, print: noop,
            moveTo: noop, resizeTo: noop, scrollTo: noop,
            opener: window, name: '', length: 0, frames: [],
            navigator: window.navigator, screen: window.screen,
            history: { back: noop, forward: noop, go: noop, pushState: noop, replaceState: noop },
            get location() { return loc; },
            set location(v) { href = String(v); },
            document: doc,
            addEventListener: noop, removeEventListener: noop, dispatchEvent: function () { return true; }
        };
        fake.window = fake;
        fake.self = fake;
        fake.top = fake;
        return fake;
    }
    // A Proxy keeps the native name and length; V8 still prints a Proxy as "function () {
    // [native code] }", so Function.prototype.toString answers the original's text for it.
    var masks = typeof WeakMap === 'function' ? new WeakMap() : null;
    function cloak(proxy, of) {
        if (masks) try { masks.set(proxy, Function.prototype.toString.call(of)); } catch (_) {}
        return proxy;
    }
    try {
        if (masks) {
            var toString = Function.prototype.toString;
            Function.prototype.toString = cloak(new Proxy(toString, { apply: function (target, self, args) {
                return masks.has(self) ? masks.get(self) : Reflect.apply(target, self, args);
            } }), toString);
        }
        window.open = cloak(new Proxy(original, { apply: function (target, self, args) {
            var opened = null;
            try { opened = Reflect.apply(target, self, args); } catch (_) {}
            return opened || decoy(args[0]);
        } }), original);
    } catch (_) {}
})();
