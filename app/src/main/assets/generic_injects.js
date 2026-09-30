//download blobs support
if (!window.tvBroClicksListener) {
    window.tvBroClicksListener = function(e) {
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
                        TVBro.takeBlobDownloadData(base64data, fileName, url, blob.type);
                    }
                };
                reader.readAsDataURL(blob);
            }
        };
        xhr.send();
        e.stopPropagation();
        e.preventDefault();
    };
    document.addEventListener("click", window.tvBroClicksListener);
}

// video playback control support
//local helper instead of patching HTMLMediaElement.prototype: never clashes with the page's own code
window.tvBroIsPlaying = function(media) {
    return !!(media.currentTime > 0 && !media.paused && !media.ended && media.readyState > 2);
}

window.tvBroTogglePlayback = function() {
  var media = document.querySelector('video') || document.querySelector('audio');
  if (media) {
      if (window.tvBroIsPlaying(media)) {
        media.pause();
      } else {
        media.play();
      }
  }
}

window.tvBroStopPlayback = function() {
  var media = document.querySelector('video') || document.querySelector('audio');
  if (media) {
      media.pause();
      media.currentTime = 0;
  }
}

window.tvBroRewind = function() {
    var media = document.querySelector('video') || document.querySelector('audio');
    if (media) {
        media.currentTime -= 10;
    }
}

window.tvBroFastForward = function() {
    var media = document.querySelector('video') || document.querySelector('audio');
    if (media) {
        media.currentTime += 10;
    }
}

// context menu support
if (!window.tvBroTouchStartListener) {
    window.tvBroTouchStartListener = function(e) {
        window.TVBRO_activeElement = e.target;
        window.TVBRO_touchStartX = e.touches[0].clientX;
        window.TVBRO_touchStartY = e.touches[0].clientY;
    };
    window.addEventListener("touchstart", window.tvBroTouchStartListener);
}