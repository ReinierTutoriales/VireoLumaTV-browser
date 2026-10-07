// VireoLumaTV surrogate for a blocked adsbygoogle.js: the page sees a loaded, inert queue.
(function () {
    'use strict';
    var noop = function () {};
    var queue = window.adsbygoogle;
    if (Array.isArray(queue)) {
        queue.loaded = true;
        queue.push = noop;
    } else {
        window.adsbygoogle = { loaded: true, push: noop, length: 0 };
    }
})();
