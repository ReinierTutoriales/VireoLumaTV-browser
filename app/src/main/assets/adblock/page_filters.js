// VireoLumaTV page filters: element hiding and scriptlets for the current frame.
//
// Registered at document start in every frame. The native engine (adblock-rust) decides which
// rules apply to this URL; scriptlet invocations come back as JSON markers produced by our own
// resource templates, so nothing here uses eval() and page CSP cannot block it. The scriptlets
// below are written for VireoLumaTV from the behaviour documented in the uBlock Origin wiki
// ("Resources Library"); they accept the same names and arguments as the filter lists.
(function () {
    'use strict';
    if (window.__vireoPageFilters) return;
    window.__vireoPageFilters = true;
    var bridge = window.VireoLumaTVApp;
    if (!bridge || typeof bridge.pageFilters !== 'function') return;
    var href = String(location.href);
    if (!/^https?:/.test(href)) return;
    var data;
    try { data = JSON.parse(bridge.pageFilters(href) || '{}'); } catch (_) { return; }
    if (!data || typeof data !== 'object') return;

    var Reflect_ = window.Reflect, Proxy_ = window.Proxy;
    var defineProperty = Object.defineProperty, getOwnPropertyDescriptor = Object.getOwnPropertyDescriptor;
    var magic = 'vireo' + Math.floor(Math.random() * 1e9).toString(36);
    var noopFunc = function () {};

    // ---- helpers -------------------------------------------------------------------------

    function escapeRegex(text) { return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }

    /** '' matches everything; /re/flags is a regex; anything else is a literal substring. */
    function toRegex(arg, extraFlags) {
        if (arg === undefined || arg === null || arg === '') return /^/;
        var match = /^\/(.+)\/([dgimsuy]*)$/.exec(arg);
        if (match) {
            try { return new RegExp(match[1], (match[2].replace('g', '') + (extraFlags || ''))); } catch (_) { return /(?!)/; }
        }
        return new RegExp(escapeRegex(String(arg)), extraFlags || '');
    }

    /** Like toRegex, with uBO's leading "!" negation. */
    function matcher(arg) {
        var not = false;
        if (typeof arg === 'string' && arg.charAt(0) === '!') { not = true; arg = arg.slice(1); }
        var re = toRegex(arg);
        return function (text) { return re.test(String(text)) !== not; };
    }

    function wrapFunction(owner, name, handler) {
        var original = owner && owner[name];
        if (typeof original !== 'function' || !Proxy_ || !Reflect_) return;
        try { owner[name] = new Proxy_(original, { apply: handler }); } catch (_) {}
    }

    function stringOf(value) {
        try { return typeof value === 'function' ? Function.prototype.toString.call(value) : String(value); } catch (_) { return ''; }
    }

    var errorFilterInstalled = false;
    function abort() {
        if (!errorFilterInstalled && typeof window.addEventListener === 'function') {
            errorFilterInstalled = true;
            window.addEventListener('error', function (event) {
                if (event && typeof event.message === 'string' && event.message.indexOf(magic) !== -1) {
                    event.preventDefault();
                    event.stopImmediatePropagation();
                }
            }, true);
        }
        throw new ReferenceError(magic);
    }

    function normalizeChain(chain) { return String(chain || '').replace(/^window\./, ''); }

    /** Objects and functions from any frame (instanceof fails across realms). */
    function isObjectLike(value) {
        return value !== null && (typeof value === 'object' || typeof value === 'function');
    }

    /**
     * Applies [leaf] to the last property of [chain], waiting for missing intermediate objects
     * to be assigned (scripts often create them later).
     */
    function trapChain(owner, chain, leaf) {
        var dot = chain.indexOf('.');
        if (dot === -1) { leaf(owner, chain); return; }
        var prop = chain.slice(0, dot), rest = chain.slice(dot + 1);
        var current = owner[prop];
        if (isObjectLike(current)) {
            trapChain(current, rest, leaf);
            return;
        }
        var descriptor = getOwnPropertyDescriptor(owner, prop);
        if (descriptor && descriptor.configurable === false) return;
        try {
            defineProperty(owner, prop, {
                configurable: true,
                get: function () { return current; },
                set: function (value) {
                    current = value;
                    if (isObjectLike(value)) trapChain(value, rest, leaf);
                }
            });
        } catch (_) {}
    }

    var constants = {
        'undefined': function () { return undefined; },
        'false': function () { return false; },
        'true': function () { return true; },
        'null': function () { return null; },
        "''": function () { return ''; },
        'emptyStr': function () { return ''; },
        'emptyArr': function () { return []; },
        'emptyObj': function () { return {}; },
        'noopFunc': function () { return function () {}; },
        'trueFunc': function () { return function () { return true; }; },
        'falseFunc': function () { return function () { return false; }; },
        'throwFunc': function () { return function () { throw new Error(); }; },
        'noopCallbackFunc': function () { return function () { return function () {}; }; },
        'noopPromiseResolve': function () { return function () { return Promise.resolve(); }; },
        'noopPromiseReject': function () { return function () { return Promise.reject(); }; },
        'yes': function () { return 'yes'; }, 'no': function () { return 'no'; },
        'on': function () { return 'on'; }, 'off': function () { return 'off'; },
        'accept': function () { return 'accept'; }, 'reject': function () { return 'reject'; },
        'ok': function () { return 'ok'; }
    };

    /** uBO set-constant values; returns {value} or null for an unsupported literal. */
    function constant(raw) {
        if (raw === undefined) return null;
        if (Object.prototype.hasOwnProperty.call(constants, raw)) return { value: constants[raw]() };
        if (/^-?\d+$/.test(raw)) {
            var n = parseInt(raw, 10);
            if (Math.abs(n) <= 0x7FFF) return { value: n };
        }
        return null;
    }

    function scriptText(element) {
        if (!element) return '';
        var text = element.textContent || '';
        if (text.trim() !== '') return text;
        var src = element.src || '';
        var match = /^data:[^,]*,(.+)$/.exec(src.trim());
        if (!match) return '';
        try { return /;base64,/.test(src) ? atob(match[1]) : decodeURIComponent(match[1]); } catch (_) { return ''; }
    }

    function onReady(fn) {
        if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', fn, { once: true });
        else fn();
    }

    // ---- scriptlets ----------------------------------------------------------------------

    var scriptlets = {};

    scriptlets['set-constant'] = function (chain, rawValue) {
        chain = normalizeChain(chain);
        var parsed = constant(rawValue);
        if (!chain || !parsed) return;
        var value = parsed.value;
        function mismatched(v) {
            return v !== undefined && v !== null && value !== undefined && value !== null && typeof v !== typeof value;
        }
        trapChain(window, chain, function (owner, prop) {
            var descriptor = getOwnPropertyDescriptor(owner, prop);
            if (descriptor && 'value' in descriptor && mismatched(descriptor.value)) return;
            if (descriptor && descriptor.configurable === false) {
                try { owner[prop] = value; } catch (_) {}
                return;
            }
            var getter = descriptor && descriptor.get, setter = descriptor && descriptor.set;
            try {
                defineProperty(owner, prop, {
                    configurable: true,
                    get: function () { if (getter) { try { getter.call(this); } catch (_) {} } return value; },
                    set: function (v) { if (setter) { try { setter.call(this, v); } catch (_) {} } }
                });
            } catch (_) {}
        });
    };

    scriptlets['abort-on-property-read'] = function (chain) {
        chain = normalizeChain(chain);
        if (!chain) return;
        trapChain(window, chain, function (owner, prop) {
            try { defineProperty(owner, prop, { configurable: true, get: abort, set: noopFunc }); } catch (_) {}
        });
    };

    scriptlets['abort-on-property-write'] = function (chain) {
        chain = normalizeChain(chain);
        if (!chain) return;
        trapChain(window, chain, function (owner, prop) {
            var value = owner[prop];
            try {
                defineProperty(owner, prop, {
                    configurable: true,
                    get: function () { return value; },
                    set: function () { abort(); }
                });
            } catch (_) {}
        });
    };

    scriptlets['abort-current-script'] = function (chain, needle, context) {
        chain = normalizeChain(chain);
        if (!chain) return;
        var reNeedle = toRegex(needle), reContext = toRegex(context);
        var thisScript = document.currentScript;
        function check() {
            var element = document.currentScript;
            if (!element || element === thisScript || element.tagName !== 'SCRIPT') return;
            if (context && !reContext.test(element.src || '')) return;
            if (!reNeedle.test(scriptText(element))) return;
            abort();
        }
        trapChain(window, chain, function (owner, prop) {
            var descriptor = getOwnPropertyDescriptor(owner, prop);
            if (descriptor && descriptor.configurable === false) return;
            var value = owner[prop];
            var getter = descriptor && descriptor.get, setter = descriptor && descriptor.set;
            try {
                defineProperty(owner, prop, {
                    configurable: true,
                    get: function () { check(); return getter ? getter.call(this) : value; },
                    set: function (v) { check(); if (setter) setter.call(this, v); else value = v; }
                });
            } catch (_) {}
        });
    };

    scriptlets['abort-on-stack-trace'] = function (chain, needle) {
        chain = normalizeChain(chain);
        if (!chain) return;
        var re;
        if (needle === 'inlineScript') re = new RegExp(escapeRegex(href.split('#')[0]) + ':\\d+:\\d+');
        else if (needle === 'injectedScript') re = /<anonymous>/;
        else re = toRegex(needle);
        function check() {
            var stack = '';
            try { stack = String(new Error().stack); } catch (_) {}
            if (re.test(stack.split('\n').slice(2).join('\n'))) abort();
        }
        trapChain(window, chain, function (owner, prop) {
            var value = owner[prop];
            try {
                defineProperty(owner, prop, {
                    configurable: true,
                    get: function () { check(); return value; },
                    set: function (v) { check(); value = v; }
                });
            } catch (_) {}
        });
    };

    function timerDefuser(name) {
        return function (needle, delay) {
            var matches = matcher(needle);
            var delayNot = typeof delay === 'string' && delay.charAt(0) === '!';
            var wanted = delay ? parseInt(delayNot ? delay.slice(1) : delay, 10) : NaN;
            wrapFunction(window, name, function (target, self, args) {
                var callback = stringOf(args[0]);
                var actualDelay = args[1] | 0;
                var delayOk = isNaN(wanted) || ((actualDelay === wanted) !== delayNot);
                if (delayOk && matches(callback)) args[0] = noopFunc;
                return Reflect_.apply(target, self, args);
            });
        };
    }
    scriptlets['no-setTimeout-if'] = timerDefuser('setTimeout');
    scriptlets['no-setInterval-if'] = timerDefuser('setInterval');

    function timerBooster(name) {
        return function (needle, delay, boost) {
            var re = toRegex(needle);
            var wanted = delay === '*' ? -1 : (delay ? parseInt(delay, 10) : 1000);
            var factor = parseFloat(boost);
            if (isNaN(factor)) factor = 0.05;
            factor = Math.min(50, Math.max(0.001, factor));
            wrapFunction(window, name, function (target, self, args) {
                if ((wanted === -1 || (args[1] | 0) === wanted) && re.test(stringOf(args[0]))) {
                    args[1] = (args[1] | 0) * factor;
                }
                return Reflect_.apply(target, self, args);
            });
        };
    }
    scriptlets['nano-setInterval-booster'] = timerBooster('setInterval');
    scriptlets['nano-setTimeout-booster'] = timerBooster('setTimeout');

    scriptlets['addEventListener-defuser'] = function (type, pattern) {
        var reType = toRegex(type), reHandler = toRegex(pattern);
        var proto = window.EventTarget && EventTarget.prototype;
        wrapFunction(proto, 'addEventListener', function (target, self, args) {
            var handler = args[1];
            var text = stringOf(handler && typeof handler === 'object' ? handler.handleEvent : handler);
            if (reType.test(String(args[0])) && reHandler.test(text)) return undefined;
            return Reflect_.apply(target, self, args);
        });
    };

    function decoyWindow(url) {
        var closed = false, href2 = url ? String(url) : 'about:blank';
        var fake = {
            get closed() { return closed; },
            close: function () { closed = true; },
            focus: noopFunc, blur: noopFunc, postMessage: noopFunc, opener: window, name: '',
            location: { href: href2, assign: noopFunc, replace: noopFunc, reload: noopFunc },
            document: { write: noopFunc, writeln: noopFunc, open: noopFunc, close: noopFunc, body: null },
            addEventListener: noopFunc, removeEventListener: noopFunc
        };
        fake.window = fake; fake.self = fake;
        return fake;
    }

    scriptlets['no-window-open-if'] = function (pattern) {
        var matches = matcher(pattern);
        wrapFunction(window, 'open', function (target, self, args) {
            if (matches(args[0] === undefined ? '' : args[0])) return decoyWindow(args[0]);
            return Reflect_.apply(target, self, args);
        });
    };

    function nodeTextRewriter(nodeName, pattern, replacement) {
        var name = String(nodeName || '').toLowerCase();
        if (!name) return;
        var re = toRegex(pattern), global = toRegex(pattern, 'g');
        function handle(node) {
            if (!node || String(node.nodeName).toLowerCase() !== name) return;
            var text = node.textContent;
            if (!text || !re.test(text)) return;
            node.textContent = replacement === null ? '' : text.replace(global, replacement);
        }
        if (typeof MutationObserver !== 'function') return;
        new MutationObserver(function (mutations) {
            for (var i = 0; i < mutations.length; i++) {
                var added = mutations[i].addedNodes;
                for (var j = 0; j < added.length; j++) handle(added[j]);
            }
        }).observe(document, { childList: true, subtree: true });
    }
    scriptlets['remove-node-text'] = function (nodeName, pattern) { nodeTextRewriter(nodeName, pattern, null); };
    scriptlets['replace-node-text'] = function (nodeName, pattern, replacement) {
        nodeTextRewriter(nodeName, pattern, replacement === undefined ? '' : String(replacement));
    };

    /** uBO propsToMatch: "url:/re/ method:POST" or a bare URL pattern. */
    function requestMatcher(props) {
        if (props === undefined || props === '' || props === '*') return function () { return true; };
        var tests = [];
        String(props).split(/\s+/).forEach(function (token) {
            if (!token) return;
            var colon = token.indexOf(':');
            var key = 'url', value = token;
            if (colon > 0 && /^[a-zA-Z]+$/.test(token.slice(0, colon))) { key = token.slice(0, colon); value = token.slice(colon + 1); }
            tests.push({ key: key, re: toRegex(value) });
        });
        return function (details) {
            for (var i = 0; i < tests.length; i++) {
                if (!tests[i].re.test(String(details[tests[i].key] === undefined ? '' : details[tests[i].key]))) return false;
            }
            return true;
        };
    }

    function responseBody(kind) {
        if (kind === 'emptyObj') return '{}';
        if (kind === 'emptyArr') return '[]';
        return '';
    }

    scriptlets['no-fetch-if'] = function (props, body) {
        var matches = requestMatcher(props);
        wrapFunction(window, 'fetch', function (target, self, args) {
            var input = args[0], init = args[1] || {};
            var details = { url: '', method: 'GET' };
            try {
                if (input && typeof input === 'object' && 'url' in input) { details.url = input.url; details.method = input.method || 'GET'; }
                else details.url = String(input);
                if (init.method) details.method = String(init.method);
                if (init.body !== undefined) details.body = String(init.body);
            } catch (_) {}
            if (matches(details) && typeof Response === 'function') {
                return Promise.resolve(new Response(responseBody(body), { status: 200, statusText: 'OK' }));
            }
            return Reflect_.apply(target, self, args);
        });
    };

    scriptlets['no-xhr-if'] = function (props, body) {
        var matches = requestMatcher(props);
        var proto = window.XMLHttpRequest && XMLHttpRequest.prototype;
        if (!proto) return;
        var pending = new WeakMap();
        wrapFunction(proto, 'open', function (target, self, args) {
            var details = { method: String(args[0] || 'GET'), url: String(args[1] || '') };
            if (matches(details)) pending.set(self, true); else pending.delete(self);
            return Reflect_.apply(target, self, args);
        });
        wrapFunction(proto, 'send', function (target, self, args) {
            if (!pending.has(self)) return Reflect_.apply(target, self, args);
            var text = responseBody(body);
            try {
                defineProperty(self, 'readyState', { value: 4, configurable: true });
                defineProperty(self, 'status', { value: 200, configurable: true });
                defineProperty(self, 'statusText', { value: 'OK', configurable: true });
                defineProperty(self, 'responseText', { value: text, configurable: true });
                defineProperty(self, 'response', { value: text, configurable: true });
            } catch (_) {}
            setTimeout(function () {
                ['readystatechange', 'load', 'loadend'].forEach(function (type) {
                    try { self.dispatchEvent(new Event(type)); } catch (_) {}
                });
            }, 1);
            return undefined;
        });
    };

    function evalDefuser(needle) {
        var re = toRegex(needle);
        wrapFunction(window, 'eval', function (target, self, args) {
            if (re.test(String(args[0]))) return undefined;
            return Reflect_.apply(target, self, args);
        });
    }
    scriptlets['noeval-if'] = evalDefuser;
    scriptlets['noeval'] = function () { evalDefuser(''); };

    /** Dot paths with "[]" or "*" for any array element / property. */
    function walkPath(root, path, remove) {
        var parts = path.split('.');
        function walk(node, index) {
            if (node === null || typeof node !== 'object') return false;
            var key = parts[index], last = index === parts.length - 1, found = false;
            if (key === '[]' || key === '*') {
                var keys = Object.keys(node);
                for (var i = 0; i < keys.length; i++) {
                    if (last) { found = true; if (remove) delete node[keys[i]]; }
                    else if (walk(node[keys[i]], index + 1)) found = true;
                }
                return found;
            }
            if (!Object.prototype.hasOwnProperty.call(node, key)) return false;
            if (last) { if (remove) delete node[key]; return true; }
            return walk(node[key], index + 1);
        }
        return walk(root, 0);
    }

    function pruner(prunePaths, requiredPaths) {
        var prune = String(prunePaths || '').split(/\s+/).filter(Boolean);
        var required = String(requiredPaths || '').split(/\s+/).filter(Boolean);
        return function (value) {
            if (!prune.length || value === null || typeof value !== 'object') return value;
            for (var i = 0; i < required.length; i++) if (!walkPath(value, required[i], false)) return value;
            for (var j = 0; j < prune.length; j++) walkPath(value, prune[j], true);
            return value;
        };
    }

    scriptlets['json-prune'] = function (prunePaths, requiredPaths) {
        var prune = pruner(prunePaths, requiredPaths);
        wrapFunction(JSON, 'parse', function (target, self, args) {
            return prune(Reflect_.apply(target, self, args));
        });
        if (window.Response) {
            wrapFunction(Response.prototype, 'json', function (target, self, args) {
                return Reflect_.apply(target, self, args).then(prune);
            });
        }
    };

    function domCleaner(attribute, fn) {
        return function (names, selector, behavior) {
            var list = String(names || '').split('|').map(function (s) { return s.trim(); }).filter(Boolean);
            if (!list.length) return;
            var query = selector || list.map(function (n) { return attribute ? '[' + n + ']' : '.' + n; }).join(',');
            function run() {
                var nodes;
                try { nodes = document.querySelectorAll(query); } catch (_) { return; }
                for (var i = 0; i < nodes.length; i++) for (var k = 0; k < list.length; k++) fn(nodes[i], list[k]);
            }
            var stay = String(behavior || '').indexOf('stay') !== -1;
            if (String(behavior || '').indexOf('asap') !== -1) run();
            onReady(function () {
                run();
                if (stay && typeof MutationObserver === 'function') {
                    var queued = false;
                    new MutationObserver(function () {
                        if (queued) return;
                        queued = true;
                        setTimeout(function () { queued = false; run(); }, 50);
                    }).observe(document.documentElement, { childList: true, subtree: true, attributes: true });
                }
            });
        };
    }
    scriptlets['remove-attr'] = domCleaner(true, function (node, name) { node.removeAttribute(name); });
    scriptlets['remove-class'] = domCleaner(false, function (node, name) { node.classList.remove(name); });

    scriptlets['nowebrtc'] = function () {
        var Fake = function () {};
        Fake.prototype = {
            close: noopFunc, createDataChannel: function () { return { close: noopFunc, send: noopFunc }; },
            createOffer: function () { return Promise.reject(new Error('WebRTC disabled')); },
            addEventListener: noopFunc, removeEventListener: noopFunc, setLocalDescription: noopFunc,
            setRemoteDescription: noopFunc
        };
        ['RTCPeerConnection', 'webkitRTCPeerConnection'].forEach(function (name) {
            if (window[name]) { try { window[name] = Fake; } catch (_) {} }
        });
    };

    var safeValues = /^(?:true|false|yes|no|y|n|on|off|ok|accept|accepted|reject|rejected|allow|allowed|deny|denied|necessary|required|hide|hidden|essential|nonessential|checked|unchecked|forbidden|forever|-?\d{1,5}|)$/i;

    scriptlets['set-cookie'] = function (name, value, path) {
        if (!name || value === undefined || !safeValues.test(value)) return;
        var cookie = encodeURIComponent(name) + '=' + encodeURIComponent(value);
        if (document.cookie.split(/;\s*/).indexOf(cookie) !== -1) return;
        document.cookie = cookie + '; path=' + (path === 'none' ? location.pathname : '/') + '; max-age=31536000';
    };

    scriptlets['set-local-storage-item'] = function (key, value) {
        if (!key || value === undefined) return;
        try {
            if (value === '$remove$') { localStorage.removeItem(key); return; }
            var mapped = { 'emptyArr': '[]', 'emptyObj': '{}', 'undefined': 'undefined', 'null': 'null', "''": '' };
            var text = Object.prototype.hasOwnProperty.call(mapped, value) ? mapped[value] : value;
            if (!safeValues.test(text) && text !== '[]' && text !== '{}' && text !== 'undefined' && text !== 'null') return;
            localStorage.setItem(key, text);
        } catch (_) {}
    };

    scriptlets['popads-dummy'] = function () {
        try { delete window.PopAds; delete window.popns; } catch (_) {}
        try { defineProperty(window, 'PopAds', { value: {} }); defineProperty(window, 'popns', { value: {} }); } catch (_) {}
    };

    /** FuckAdBlock / BlockAdBlock detectors always report "not detected". */
    function detectorStub() {
        var stub = {
            setOption: function () { return stub; }, check: function () { return false; },
            emitEvent: function () { return stub; }, clearEvent: noopFunc,
            onDetected: function () { return stub; },
            onNotDetected: function (fn) { if (typeof fn === 'function') setTimeout(fn, 1); return stub; }
        };
        return stub;
    }
    scriptlets['nofab'] = function () {
        var Ctor = function () { return detectorStub(); };
        ['FuckAdBlock', 'BlockAdBlock', 'SniffAdBlock'].forEach(function (name) {
            try { defineProperty(window, name, { value: Ctor, configurable: true }); } catch (_) {}
        });
        ['fuckAdBlock', 'blockAdBlock', 'sniffAdBlock'].forEach(function (name) {
            try { defineProperty(window, name, { value: detectorStub(), configurable: true }); } catch (_) {}
        });
    };
    scriptlets['nobab'] = scriptlets['nofab'];

    // ---- apply ---------------------------------------------------------------------------

    var hide = Array.isArray(data.hide) ? data.hide : [];
    if (hide.length) {
        // One rule per selector: an unsupported selector must not drop the whole group.
        var css = '';
        for (var h = 0; h < hide.length && h < 2048; h++) css += hide[h] + '{display:none!important}\n';
        var adopted = false;
        try {
            if (typeof CSSStyleSheet === 'function' && 'adoptedStyleSheets' in document) {
                // Constructed sheets are not inline styles: page CSP cannot block them.
                var sheet = new CSSStyleSheet();
                sheet.replaceSync(css);
                document.adoptedStyleSheets = document.adoptedStyleSheets.concat([sheet]);
                adopted = true;
            }
        } catch (_) {}
        if (!adopted) {
            var add = function () {
                var root = document.head || document.documentElement;
                if (!root) return false;
                var style = document.createElement('style');
                style.textContent = css;
                root.appendChild(style);
                return true;
            };
            if (!add() && typeof MutationObserver === 'function') {
                var waiter = new MutationObserver(function () { if (add()) waiter.disconnect(); });
                waiter.observe(document, { childList: true, subtree: true });
            }
        }
    }

    // YouTube has its own dedicated filter (youtube_adblock.js); list scriptlets for it rely on
    // trusted resources we do not ship and could fight with it.
    var host = String(location.hostname).toLowerCase();
    if (/(^|\.)youtube(-nocookie)?\.com$/.test(host)) return;

    var script = typeof data.script === 'string' ? data.script : '';
    var marker = /\/\*@VIREO@\*\/(\[[\s\S]*?\])\/\*@END@\*\//g, found;
    while ((found = marker.exec(script)) !== null) {
        try {
            var call = JSON.parse(found[1]);
            var fn = scriptlets[call[0]];
            if (typeof fn !== 'function') continue;
            var args = (call[1] || []).filter(function (arg) { return !/^\{\{\d+\}\}$/.test(arg); });
            fn.apply(null, args);
        } catch (_) {}
    }
})();
