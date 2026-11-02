// VireoLumaTV surrogate for a blocked Google Publisher Tag: every API call is an inert chain and
// queued googletag.cmd callbacks still run, so page initialisation is not left waiting.
(function () {
    'use strict';
    var chain = new Proxy(function () {}, {
        get: function (target, property) {
            if (property === 'then') return undefined;
            if (property === Symbol.toPrimitive || property === 'toString') return function () { return ''; };
            if (property === 'length') return 0;
            return chain;
        },
        apply: function () { return chain; }
    });
    var googletag = window.googletag || {};
    var queued = googletag.cmd;
    function run(fn) { if (typeof fn === 'function') { try { fn(); } catch (_) {} } }
    ['pubads', 'companionAds', 'content', 'defineSlot', 'defineOutOfPageSlot', 'display',
        'enableServices', 'destroySlots', 'sizeMapping', 'setAdIframeTitle', 'openConsole',
        'disablePublisherConsole', 'setConfig', 'getConfig', 'secureSignalProviders'].forEach(function (name) {
        googletag[name] = chain;
    });
    googletag.getVersion = function () { return ''; };
    googletag.apiReady = true;
    googletag.pubadsReady = true;
    googletag.cmd = { push: function () { for (var i = 0; i < arguments.length; i++) run(arguments[i]); return 0; } };
    window.googletag = googletag;
    if (queued && typeof queued.length === 'number') {
        for (var i = 0; i < queued.length; i++) run(queued[i]);
    }
})();
