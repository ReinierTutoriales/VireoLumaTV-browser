(function (page) {
    'use strict';

    // Native activation grants one token. Neither this listener nor a page script grants access.
    function receiveBlob(address, token) {
        var settled = false;
        function cancel() {
            if (settled) return;
            settled = true;
            page.VireoLumaTVApp.cancelBlobDownload(token);
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
                    if (request.status !== 200 || !payload ||
                        !page.VireoLumaTVApp.acceptBlobSize(token, payload.size || 0, payload.type || '')) {
                        cancel();
                        return;
                    }
                    var conversion = new FileReader();
                    conversion.onerror = conversion.onabort = cancel;
                    conversion.onload = function () {
                        if (settled) return;
                        if (typeof conversion.result !== 'string') { cancel(); return; }
                        try {
                            page.VireoLumaTVApp.takeBlobDownloadData(token, conversion.result, address);
                            settled = true;
                        } catch (_) { cancel(); }
                    };
                    conversion.readAsDataURL(payload);
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
            if (!address || !/^blob:/i.test(address)) return;
            var token = page.VireoLumaTVApp.beginBlobDownload(address, anchor.download || null);
            event.preventDefault();
            event.stopPropagation();
            if (token) receiveBlob(address, token);
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
