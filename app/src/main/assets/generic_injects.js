(function (page) {
    'use strict';

    // Blob downloads reach the browser only as main-frame WebMessages (VireoDownload), whose replies
    // arrive asynchronously. Without that channel the frame cannot be identified: links stay WebView's.
    var replies = page.vireoLumaTVDownloadReplies;
    function channel() {
        var target = page.VireoDownload;
        return target && typeof target.postMessage === 'function' ? target : null;
    }
    if (!replies && channel() && typeof channel().addEventListener === 'function') {
        replies = page.vireoLumaTVDownloadReplies = {};
        channel().addEventListener('message', function (event) {
            var parts = String(event && event.data).split('\n');
            var key = parts[0] + '\n' + parts[1], handler = replies[key];
            if (!handler || parts.length !== 3) return;
            delete replies[key];
            handler(parts[2]);
        });
    }
    var CHUNK_BYTES = 512 * 1024;
    var transfer = channel() && replies ? {
        begin: function (address, name, done) {
            replies['begin\n' + address] = function (token) { done(token); };
            channel().postMessage('begin\n' + address + '\n' + (name || ''));
        },
        size: function (token, size, type, done) {
            replies['size\n' + token] = function (accepted) { done(accepted === '1'); };
            channel().postMessage('size\n' + token + '\n' + size + '\n' + type);
        },
        // Answered with the bytes received so far, or -1 once the browser closed the transfer.
        chunk: function (token, offset, base64, done) {
            replies['chunk\n' + token] = function (received) { done(Number(received)); };
            channel().postMessage('chunk\n' + token + '\n' + offset + '\n' + base64);
        },
        done: function (token, address) { channel().postMessage('done\n' + token + '\n' + address); },
        cancel: function (token) { channel().postMessage('cancel\n' + token); }
    } : null;

    // Native activation grants one token. Neither this listener nor a page script grants access.
    // The blob is read and sent one chunk at a time, the next only after the browser stored the
    // previous one, so neither side ever holds the whole file as text.
    function receiveBlob(address, token) {
        var settled = false;
        function cancel() {
            if (settled) return;
            settled = true;
            transfer.cancel(token);
        }
        function send(payload, offset) {
            if (settled) return;
            if (offset >= payload.size) {
                settled = true;
                transfer.done(token, address);
                return;
            }
            var conversion = new FileReader();
            conversion.onerror = conversion.onabort = cancel;
            conversion.onload = function () {
                if (settled) return;
                var result = conversion.result, comma = typeof result === 'string' ? result.indexOf(',') : -1;
                if (comma < 0 || comma === result.length - 1) { cancel(); return; }
                transfer.chunk(token, offset, result.substring(comma + 1), function (received) {
                    if (settled) return;
                    if (received < 0) { settled = true; return; }
                    if (received <= offset) { cancel(); return; }
                    try { send(payload, received); } catch (_) { cancel(); }
                });
            };
            conversion.readAsDataURL(payload.slice(offset, Math.min(payload.size, offset + CHUNK_BYTES)));
        }
        try {
            var request = new XMLHttpRequest();
            request.open('GET', address, true);
            request.responseType = 'blob';
            request.onerror = request.onabort = request.ontimeout = cancel;
            request.onload = function () {
                if (settled) return;
                try {
                    var payload = request.response;
                    if (request.status !== 200 || !payload || typeof payload.slice !== 'function') { cancel(); return; }
                    transfer.size(token, payload.size || 0, payload.type || '', function (accepted) {
                        if (settled) return;
                        if (!accepted) { cancel(); return; }
                        try { send(payload, 0); } catch (_) { cancel(); }
                    });
                } catch (_) { cancel(); }
            };
            request.send();
        } catch (_) { cancel(); }
    }

    if (!page.vireoLumaTVClicksListener) {
        page.vireoLumaTVClicksListener = function (event) {
            if (!event || event.isTrusted !== true) return;
            var anchor = event.target && typeof event.target.closest === 'function'
                ? event.target.closest('a[href]') : null;
            var address = anchor && anchor.getAttribute('href');
            if (!transfer || !address || !/^blob:/i.test(address)) return;
            transfer.begin(address, anchor.download || null, function (token) {
                if (token) receiveBlob(address, token);
            });
            event.preventDefault();
            event.stopPropagation();
        };
        document.addEventListener('click', page.vireoLumaTVClicksListener);
    }

    function playing(element) { return !!element && !element.paused && !element.ended; }

    // Prefer the fullscreen player, then the largest playing video. A small preview or
    // background audio must not steal remote commands from the main visible video.
    function currentMedia() {
        var fullscreen = document.fullscreenElement || document.webkitFullscreenElement;
        if (fullscreen) {
            if (typeof fullscreen.matches === 'function' && fullscreen.matches('video, audio')) return fullscreen;

        }
        var scope = fullscreen && typeof fullscreen.querySelectorAll === 'function' ? fullscreen : document;
        var candidates = scope.querySelectorAll('video, audio');
        var best = null, bestScore = -1;
        for (var index = 0; index < candidates.length; index++) {
            var element = candidates[index];
            var video = String(element.tagName || element.tag || '').toLowerCase() === 'video';
            var area = 0;
            if (typeof element.getBoundingClientRect === 'function') {
                var rect = element.getBoundingClientRect();
                area = Math.max(0, rect.width) * Math.max(0, rect.height);
                if (area === 0) continue;
            }
            var score = (video ? 1e12 : 0) + (playing(element) ? 1e9 : 0) + Math.min(area, 1e8);
            if (score > bestScore) { best = element; bestScore = score; }
        }
        return best;
    }

    // Each range is independent: a DVR seek must land inside a range rather than inside a gap.
    function seek(element, destination) {
        if (!element || !isFinite(destination)) return;
        try {
            var available = element.seekable;
            if (!available || available.length === 0) {
                // A finite progressive video can seek before its provider reports ranges.
                // Live streams (Infinity/unknown duration) still require an actual seekable range.
                if (element.readyState >= 1 && isFinite(element.duration) && element.duration > 0)
                    element.currentTime = Math.max(0, Math.min(element.duration, destination));
                return;
            }
            var closest, minimum = Infinity;
            for (var index = 0; index < available.length; index++) {
                var lower = available.start(index);
                var upper = Math.max(lower, available.end(index) - 0.05);
                var position = Math.min(upper, Math.max(lower, destination));
                var distance = Math.abs(destination - position);
                if (distance <= minimum) { closest = position; minimum = distance; }
            }
            element.currentTime = closest;
        } catch (_) { /* The provider can change the live window while it is being read. */ }
    }

    function command(action) {
        var element = currentMedia();
        if (!element) return;
        if (action === 'toggle') {
            if (playing(element)) element.pause();
            else {
                try {
                    var attempt = element.play();
                    if (attempt && typeof attempt.catch === 'function') attempt.catch(function () {});
                } catch (_) {}
            }
        } else if (action === 'stop') {
            element.pause();
            seek(element, 0);
        } else {
            seek(element, element.currentTime + action);
        }
    }
    page.vireoLumaTVIsPlaying = playing;
    page.vireoLumaTVMedia = currentMedia;
    page.vireoLumaTVSeek = seek;
    page.vireoLumaTVTogglePlayback = function () { command('toggle'); };
    page.vireoLumaTVStopPlayback = function () { command('stop'); };
    page.vireoLumaTVRewind = function () { command(-10); };
    page.vireoLumaTVFastForward = function () { command(10); };

    if (!page.vireoLumaTVTouchStartListener) {
        page.vireoLumaTVTouchStartListener = function (event) {
            if (!event || event.isTrusted !== true) return;
            var position = event.touches && event.touches.length ? event.touches[0] : event;
            page.VIREOLUMATV_activeElement = event.target;
            page.VIREOLUMATV_touchStartX = position.clientX;
            page.VIREOLUMATV_touchStartY = position.clientY;
        };
        page.addEventListener('touchstart', page.vireoLumaTVTouchStartListener, {passive: true});
        page.addEventListener('mousedown', page.vireoLumaTVTouchStartListener, {passive: true});
    }
})(window);
