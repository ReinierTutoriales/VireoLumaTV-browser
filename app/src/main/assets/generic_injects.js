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

// video playback control support
//local helper instead of patching HTMLMediaElement.prototype: never clashes with the page's own code
window.vireoLumaTVIsPlaying = function(media) {
    return !!(media.currentTime > 0 && !media.paused && !media.ended && media.readyState > 2);
}

window.vireoLumaTVTogglePlayback = function() {
  var media = document.querySelector('video') || document.querySelector('audio');
  if (media) {
      if (window.vireoLumaTVIsPlaying(media)) {
        media.pause();
      } else {
        media.play();
      }
  }
}

window.vireoLumaTVStopPlayback = function() {
  var media = document.querySelector('video') || document.querySelector('audio');
  if (media) {
      media.pause();
      media.currentTime = 0;
  }
}

window.vireoLumaTVRewind = function() {
    var media = document.querySelector('video') || document.querySelector('audio');
    if (media) {
        media.currentTime -= 10;
    }
}

window.vireoLumaTVFastForward = function() {
    var media = document.querySelector('video') || document.querySelector('audio');
    if (media) {
        media.currentTime += 10;
    }
}

// context menu support
if (!window.vireoLumaTVTouchStartListener) {
    window.vireoLumaTVTouchStartListener = function(e) {
        window.VIREOLUMATV_activeElement = e.target;
        window.VIREOLUMATV_touchStartX = e.touches[0].clientX;
        window.VIREOLUMATV_touchStartY = e.touches[0].clientY;
    };
    window.addEventListener("touchstart", window.vireoLumaTVTouchStartListener);
}