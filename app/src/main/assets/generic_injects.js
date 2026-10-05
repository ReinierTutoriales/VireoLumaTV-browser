//download blobs support
//Authorization lives in Java: beginBlobDownload() only succeeds right after a real remote/touch
//activation and returns a single-use token generated natively. Untrusted (script) clicks are ignored.
if (!window.vireoLumaTVClicksListener) {
    window.vireoLumaTVClicksListener = function(e) {
        if (!e || e.isTrusted !== true) return;
        var target = e.target;
        if (!target || typeof target.closest !== "function") return;
        //the click can land on an element inside the link (icon, span, button...)
        var link = target.closest("a[href]");
        if (!link) return;
        var url = link.getAttribute("href");
        if (!url || !url.toLowerCase().startsWith("blob:")) return;
        var fileName = link.download || null;
        //must run synchronously inside the click so the native activation is still fresh
        var token = VireoLumaTVApp.beginBlobDownload(url, fileName);
        e.stopPropagation();
        e.preventDefault();
        if (!token) return;
        var cancel = function() { VireoLumaTVApp.cancelBlobDownload(token); };
        var xhr = new XMLHttpRequest();
        xhr.open('GET', url, true);
        xhr.responseType = 'blob';
        xhr.onerror = cancel;
        xhr.onabort = cancel;
        xhr.onload = function() {
            var blob = this.response;
            if (this.status != 200 || !blob ||
                !VireoLumaTVApp.acceptBlobSize(token, blob.size || 0, blob.type || "")) {
                cancel();
                return;
            }
            var reader = new FileReader();
            reader.onload = function() {
                if (typeof reader.result === "string") {
                    VireoLumaTVApp.takeBlobDownloadData(token, reader.result, url);
                } else {
                    cancel();
                }
            };
            reader.onerror = cancel;
            reader.onabort = cancel;
            reader.readAsDataURL(blob);
        };
        xhr.send();
    };
    document.addEventListener("click", window.vireoLumaTVClicksListener);
}

// Controls act only on user input; leave buffering, retry and bitrate to the site's player.
window.vireoLumaTVIsPlaying = function(media) {
    // Buffering (readyState 0..2) is still an active play request, including at time zero.
    return !!(media && !media.paused && !media.ended);
};

window.vireoLumaTVMedia = function() {
    var media = document.querySelectorAll('video, audio');
    for (var i = 0; i < media.length; i++) {
        if (window.vireoLumaTVIsPlaying(media[i])) return media[i];
    }
    return document.querySelector('video') || document.querySelector('audio');
};

window.vireoLumaTVSeek = function(media, target) {
    if (!media || !isFinite(target)) return;
    try {
        var ranges = media.seekable;
        if (!ranges || !ranges.length) return; // Live streams may not offer seeking.
        var nearest = ranges.start(0);
        var distance = Math.abs(target - nearest);
        for (var i = 0; i < ranges.length; i++) {
            var start = ranges.start(i), end = ranges.end(i);
            // Stay inside the available range, including a sliding live/DVR window.
            var candidate = Math.max(start, Math.min(target, Math.max(start, end - 0.05)));
            var delta = Math.abs(target - candidate);
            if (delta <= distance) { nearest = candidate; distance = delta; }
        }
        media.currentTime = nearest;
    } catch (e) { /* The live window can change between reading ranges and seeking. */ }
};

window.vireoLumaTVTogglePlayback = function() {
    var media = window.vireoLumaTVMedia();
    if (!media) return;
    if (window.vireoLumaTVIsPlaying(media)) {
        media.pause();
    } else {
        try {
            var result = media.play();
            // Autoplay policy/unsupported media can reject; never create a retry loop.
            if (result && typeof result.catch === 'function') result.catch(function() {});
        } catch (e) {}
    }
};

window.vireoLumaTVStopPlayback = function() {
    var media = window.vireoLumaTVMedia();
    if (media) {
        media.pause();
        window.vireoLumaTVSeek(media, 0);
    }
};

window.vireoLumaTVRewind = function() {
    var media = window.vireoLumaTVMedia();
    if (media) window.vireoLumaTVSeek(media, media.currentTime - 10);
};

window.vireoLumaTVFastForward = function() {
    var media = window.vireoLumaTVMedia();
    if (media) window.vireoLumaTVSeek(media, media.currentTime + 10);
};

// Context menus track both native mouse clicks and touchscreen gestures.
if (!window.vireoLumaTVTouchStartListener) {
    window.vireoLumaTVTouchStartListener = function(e) {
        if (!e || e.isTrusted !== true) return;
        window.VIREOLUMATV_activeElement = e.target;
        var point = e.touches && e.touches.length ? e.touches[0] : e;
        window.VIREOLUMATV_touchStartX = point.clientX;
        window.VIREOLUMATV_touchStartY = point.clientY;
    };
    window.addEventListener("touchstart", window.vireoLumaTVTouchStartListener, {passive: true});
    window.addEventListener("mousedown", window.vireoLumaTVTouchStartListener, {passive: true});
}
