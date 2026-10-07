// VireoLumaTV surrogate for a blocked Google Tag Manager / gtag.js: events are not sent, but
// eventCallback / event_callback still run so links and forms that wait for them keep working.
(function () {
    'use strict';
    var layer = window.dataLayer = window.dataLayer || [];
    function later(fn) { setTimeout(function () { try { fn(); } catch (_) {} }, 1); }
    function fire(entry) {
        if (!entry || typeof entry !== 'object') return;
        if (typeof entry.eventCallback === 'function') later(entry.eventCallback);
        // gtag('event', name, {event_callback: fn}) pushes the arguments object.
        var params = entry[2];
        if (params && typeof params === 'object' && typeof params.event_callback === 'function') {
            later(params.event_callback);
        }
    }
    for (var i = 0; i < layer.length; i++) fire(layer[i]);
    var push = layer.push;
    layer.push = function () {
        for (var j = 0; j < arguments.length; j++) fire(arguments[j]);
        return push.apply(layer, arguments);
    };
})();
