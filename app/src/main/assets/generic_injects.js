//download blobs support
if (!window.vireoLumaTVClicksListener) {
    window.vireoLumaTVClicksListener = function(e) {
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
                var reader = new FileReader();
                reader.onload = function() {
                    var base64data = reader.result;
                    if (typeof base64data === "string") {
                        VireoLumaTVApp.takeBlobDownloadData(base64data, fileName, url, blob.type);
                    }
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