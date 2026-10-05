//download blobs support
if (!window.vireoLumaTVClicksListener) {
    window.vireoLumaTVCreateBlobToken = function() {
        var alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-";
        var bytes = new Uint8Array(32);
        if (window.crypto && typeof window.crypto.getRandomValues === "function") {
            window.crypto.getRandomValues(bytes);
        } else {
            for (var i = 0; i < bytes.length; i++) {
                bytes[i] = Math.floor(Math.random() * 256);
            }
        }
        var token = "";
        for (var j = 0; j < bytes.length; j++) {
            token += alphabet.charAt(bytes[j] % alphabet.length);
        }
        return token;
    };

    window.vireoLumaTVClicksListener = function(e) {
        if (e && e.isTrusted === false) return;
        var target = e.target;
        if (!target || typeof target.closest !== "function") return;
        //the click can land on an element inside the link (icon, span, button...)
        var link = target.closest("a[href]");
        if (!link) return;
        var url = link.getAttribute("href");
        if (!url || !url.toLowerCase().startsWith("blob:")) return;
        var fileName = link.download || null;
        var xhr = new XMLHttpRequest();
        xhr.open('GET', url, true);
        xhr.responseType = 'blob';
        xhr.onload = function() {
            if (this.status == 200) {
                var blob = this.response;
                if (!blob) return;
                var mimetype = blob.type || "";
                var token = window.vireoLumaTVCreateBlobToken();
                if (!VireoLumaTVApp.beginBlobDownload(token, url, fileName, mimetype, blob.size || 0)) {
                    return;
                }
                var reader = new FileReader();
                reader.onload = function() {
                    var base64data = reader.result;
                    if (typeof base64data === "string") {
                        VireoLumaTVApp.takeBlobDownloadData(token, base64data, fileName, url, mimetype);
                    } else {
                        VireoLumaTVApp.cancelBlobDownload(token);
                    }
                };
                reader.onerror = function() {
                    VireoLumaTVApp.cancelBlobDownload(token);
                };
                reader.readAsDataURL(blob);
            }
        };
        xhr.send();
        e.stopPropagation();
        e.preventDefault();
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