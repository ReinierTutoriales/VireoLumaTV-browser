// VireoLumaTV surrogate for a blocked analytics.js: nothing is sent, but hitCallback still runs
// (sites often delay link navigation or form submit until it is called).
(function () {
    'use strict';
    var name = window.GoogleAnalyticsObject || 'ga';
    var previous = window[name];
    function callback(args) {
        for (var i = args.length - 1; i >= 0; i--) {
            var value = args[i];
            if (value && typeof value === 'object' && typeof value.hitCallback === 'function') {
                var fn = value.hitCallback;
                setTimeout(function () { try { fn(); } catch (_) {} }, 1);
                return;
            }
        }
    }
    var tracker = {
        get: function () { return undefined; },
        set: function () {},
        send: function () { callback(arguments); }
    };
    var ga = function () { callback(arguments); };
    ga.create = function () { return tracker; };
    ga.getByName = function () { return tracker; };
    ga.getAll = function () { return [tracker]; };
    ga.remove = function () {};
    ga.loaded = true;
    window[name] = ga;
    var queue = previous && previous.q;
    if (queue && typeof queue.length === 'number') {
        for (var i = 0; i < queue.length; i++) callback(queue[i]);
    }
})();
