// Narrow YouTube content filtering. No network requests, polling, media seeks or bitrate changes.
(function () {
    'use strict';
    if (location.protocol !== 'https:') return;
    var host = location.hostname.toLowerCase();
    if (!(host === 'youtube.com' || host.endsWith('.youtube.com') ||
          host === 'youtube-nocookie.com' || host.endsWith('.youtube-nocookie.com'))) return;
    if (window.__vireoYouTubeAdblock) {
        window.__vireoYouTubeAdblock.setEnabled(true);
        return;
    }
    var enabled = true;
    var originalParse = JSON.parse;
    var originalStringify = JSON.stringify;
    var style = null;
    var fields = ['playerAds', 'adPlacements', 'adSlots'];
    function player(value) {
        if (!enabled || !value || typeof value !== 'object' || Array.isArray(value)) return false;
        if (!value.playabilityStatus && !value.streamingData && !value.videoDetails) return false;
        var changed = false;
        fields.forEach(function (key) {
            if (Object.prototype.hasOwnProperty.call(value, key)) {
                try { delete value[key]; changed = true; } catch (_) {}
            }
        });
        return changed;
    }
    function prune(value) {
        if (!enabled || !value || typeof value !== 'object') return value;
        // Inspect known response envelopes only, never walk an entire recommendation tree.
        var roots = Array.isArray(value) ? value.slice(0, 16) : [value];
        roots.forEach(function (root) {
            if (!root || typeof root !== 'object') return;
            player(root);
            player(root.playerResponse);
            player(root.player_response);
            var args = root.args;
            if (args && typeof args.player_response === 'string' && args.player_response.length <= 2097152) {
                try {
                    var nested = originalParse(args.player_response);
                    if (player(nested)) args.player_response = originalStringify(nested);
                } catch (_) {}
            }
        });
        return value;
    }
    function pruneText(text) {
        if (!enabled || typeof text !== 'string' || text.length > 2097152 ||
            !fields.some(function (key) { return text.indexOf('"' + key + '"') !== -1; })) return text;
        try {
            var value = originalParse(text);
            return originalStringify(prune(value));
        } catch (_) { return text; }
    }
    function playerUrl(url) {
        try {
            var parsed = new URL(url, location.href);
            var h = parsed.hostname.toLowerCase();
            return parsed.protocol === 'https:' &&
                (h === 'youtube.com' || h.endsWith('.youtube.com') ||
                 h === 'youtube-nocookie.com' || h.endsWith('.youtube-nocookie.com')) &&
                parsed.pathname === '/youtubei/v1/player';
        } catch (_) { return false; }
    }
    JSON.parse = function () { return prune(originalParse.apply(this, arguments)); };
    // Inline bootstrap assignments bypass JSON.parse.
    ['ytInitialPlayerResponse', 'ytplayer'].forEach(function (name) {
        var descriptor = Object.getOwnPropertyDescriptor(window, name);
        if (descriptor && (!descriptor.configurable || descriptor.get || descriptor.set)) {
            try { prune(window[name]); } catch (_) {}
            return;
        }
        var value = descriptor ? prune(descriptor.value) : undefined;
        try {
            Object.defineProperty(window, name, {
                configurable: true, enumerable: descriptor ? descriptor.enumerable : true,
                get: function () { return value; },
                set: function (next) {
                    value = prune(next);
                    if (name === 'ytplayer' && value && value.config) prune(value.config);
                }
            });
        } catch (_) {}
    });
    if (typeof Response !== 'undefined') {
        ['json', 'text'].forEach(function (name) {
            var original = Response.prototype[name];
            if (typeof original !== 'function') return;
            Response.prototype[name] = function () {
                var relevant = enabled && playerUrl(this.url);
                var promise = original.apply(this, arguments);
                return relevant ? promise.then(name === 'json' ? prune : pruneText) : promise;
            };
        });
    }
    // Keep native XHR errors, response types and headers. Weak entries disappear with each XHR.
    if (typeof XMLHttpRequest !== 'undefined') {
        var textCache = new WeakMap();
        ['response', 'responseText'].forEach(function (name) {
            var descriptor = Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, name);
            if (!descriptor || !descriptor.get || !descriptor.configurable) return;
            var getter = descriptor.get;
            try {
                Object.defineProperty(XMLHttpRequest.prototype, name, Object.assign({}, descriptor, {
                    get: function () {
                        var value = getter.call(this);
                        if (!enabled || this.readyState !== 4 || !playerUrl(this.responseURL)) return value;
                        if (typeof value !== 'string') return prune(value);
                        var cached = textCache.get(this);
                        if (cached && cached.input === value) return cached.output;
                        var output = pruneText(value);
                        if (value.length <= 2097152) textCache.set(this, {input: value, output: output});
                        return output;
                    }
                }));
            } catch (_) {}
        });
    }
    function installStyle() {
        if (style || !document.documentElement) return;
        style = document.createElement('style');
        style.textContent = 'ytd-ad-slot-renderer,ytd-display-ad-renderer,' +
            'ytd-promoted-sparkles-web-renderer,ytd-promoted-video-renderer,' +
            'ytm-promoted-sparkles-web-renderer,ytm-companion-ad-renderer,' +
            '#masthead-ad,.ytp-ad-overlay-container{display:none!important}';
        style.disabled = !enabled;
        document.documentElement.appendChild(style);
    }
    window.__vireoYouTubeAdblock = {
        setEnabled: function (next) {
            enabled = next === true;
            if (style) style.disabled = !enabled;
            if (enabled) {
                prune(window.ytInitialPlayerResponse);
                if (window.ytplayer && window.ytplayer.config) prune(window.ytplayer.config);
                installStyle();
            }
        }
    };
    installStyle();
    document.addEventListener('DOMContentLoaded', installStyle, {once: true});
})();
