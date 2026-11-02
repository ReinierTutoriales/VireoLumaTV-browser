// Media decoding policy for TVs. Runs at document start in every frame. It only answers capability
// queries (MediaSource/canPlayType/mediaCapabilities) so sites pick the best stream the TV decodes
// in hardware: up to the size, frame rate and bit depth its decoders handle, and not above, where
// WebView would fall back to software decoding. It never touches network requests or playback.
(function (config) {
    'use strict';
    if (!config || window.__vireoMediaPolicy) return;
    window.__vireoMediaPolicy = true;
    var blockVp8 = config.block.indexOf('vp8') !== -1;
    var blockVp9 = config.block.indexOf('vp9') !== -1;
    var blockAv1 = config.block.indexOf('av1') !== -1;
    var maxHeight = config.maxHeight | 0;
    var maxWidth = Math.round(maxHeight * 16 / 9);
    // Per codec family: highest height decoded in hardware at 30 and 60 fps, and 10-bit support.
    var hw = config.hw || {};

    function codecsOf(type) {
        var match = /codecs\s*=\s*"?([^";]*)"?/.exec(type);
        return match ? match[1] : '';
    }
    function numberParam(type, name) {
        var match = new RegExp('(?:^|;)\\s*' + name + '\\s*=\\s*"?([0-9.]+)').exec(type);
        return match ? parseFloat(match[1]) : 0;
    }
    function blockedCodec(type) {
        if (typeof type !== 'string') return false;
        var lower = type.toLowerCase();
        var codecs = codecsOf(lower);
        if (blockVp8 && /(^|[\s,])vp0?8(\.|,|\s|$)/.test(codecs)) return true;
        if (blockVp9 && /(^|[\s,])vp0?9(\.|,|\s|$)/.test(codecs)) return true;
        // A generic WebM query cannot identify a codec: reject it only when all are blocked.
        if (blockVp8 && blockVp9 && blockAv1 && lower.indexOf('video/webm') === 0 && !codecs) return true;
        if (blockAv1 && /(^|[\s,])av0?1(\.|,|\s|$)/.test(codecs)) return true;
        return false;
    }
    function overCap(width, height) {
        if (!maxHeight) return false;
        if (width && height) return width * height > maxWidth * maxHeight;
        return height > maxHeight || width > maxWidth;
    }
    function familyOf(codecs) {
        if (/(^|[\s,])(avc1|avc3)\./.test(codecs)) return 'avc';
        if (/(^|[\s,])(hev1|hvc1)\./.test(codecs)) return 'hevc';
        if (/(^|[\s,])vp0?9(\.|,|\s|$)/.test(codecs)) return 'vp9';
        if (/(^|[\s,])vp0?8(\.|,|\s|$)/.test(codecs)) return 'vp8';
        if (/(^|[\s,])av01\./.test(codecs)) return 'av1';
        return '';
    }
    // 10-bit (HDR) streams, from the codec string: VP9 profile 2/3 or depth 10+, AV1 depth 10+,
    // HEVC Main 10 (profile idc 2).
    function tenBit(family, codecs) {
        var m;
        if (family === 'vp9' && (m = /vp09\.(\d\d)\.\d\d\.(\d\d)/.exec(codecs))) return +m[1] >= 2 || +m[2] >= 10;
        if (family === 'av1' && (m = /av01\.\d\.\d\d[mh]\.(\d\d)/.exec(codecs))) return +m[1] >= 10;
        if (family === 'hevc' && (m = /(hev1|hvc1)\.[abc]?(\d+)/.exec(codecs))) return +m[2] === 2;
        return false;
    }
    // Above what the hardware decoders handle for this codec. Sizes are compared on their short
    // and long sides, so portrait videos get the same limit as landscape ones.
    function overHardware(codecs, width, height, fps) {
        var family = familyOf(codecs);
        var limits = family && hw[family];
        if (!limits) return false;
        if (tenBit(family, codecs) && !limits.ten) return true;
        if (!width && !height) return false;
        var limit = fps > 30 ? limits.h60 : limits.h30;
        if (!limit) return true;
        var short = Math.min(width || height, height || width), long = Math.max(width, height);
        return short > limit || long > Math.round(limit * 16 / 9);
    }
    function withinHardware(codecs) {
        var family = familyOf(codecs);
        return !!(family && hw[family]);
    }
    function rejectedType(type) {
        if (typeof type !== 'string') return false;
        var lower = type.toLowerCase();
        var width = numberParam(lower, 'width'), height = numberParam(lower, 'height');
        return blockedCodec(type) || overCap(width, height) ||
            overHardware(codecsOf(lower), width, height, numberParam(lower, 'framerate'));
    }

    function wrapIsTypeSupported(owner) {
        if (!owner || typeof owner.isTypeSupported !== 'function') return;
        var original = owner.isTypeSupported;
        try {
            owner.isTypeSupported = function (type) {
                return rejectedType(type) ? false : original.call(this, type);
            };
        } catch (_) {}
    }
    wrapIsTypeSupported(window.MediaSource);
    wrapIsTypeSupported(window.ManagedMediaSource);
    wrapIsTypeSupported(window.WebKitMediaSource);

    var mediaProto = window.HTMLMediaElement && window.HTMLMediaElement.prototype;
    if (mediaProto && typeof mediaProto.canPlayType === 'function') {
        var originalCanPlay = mediaProto.canPlayType;
        try {
            mediaProto.canPlayType = function (type) {
                return blockedCodec(type) || (typeof type === 'string' && overHardware(codecsOf(type.toLowerCase()), 0, 0, 0)) ?
                    '' : originalCanPlay.call(this, type);
            };
        } catch (_) {}
    }

    var capabilities = window.navigator && navigator.mediaCapabilities;
    if (capabilities && typeof capabilities.decodingInfo === 'function') {
        var originalDecodingInfo = capabilities.decodingInfo;
        try {
            capabilities.decodingInfo = function (configuration) {
                var video = configuration && configuration.video;
                var codecs = video && typeof video.contentType === 'string' ? codecsOf(video.contentType.toLowerCase()) : '';
                if (video && (blockedCodec(video.contentType) || overCap(video.width | 0, video.height | 0) ||
                        overHardware(codecs, video.width | 0, video.height | 0, +video.framerate || 0))) {
                    return Promise.resolve({supported: false, smooth: false, powerEfficient: false,
                        configuration: configuration});
                }
                var answer = originalDecodingInfo.apply(this, arguments);
                if (!video || !withinHardware(codecs) || !answer || typeof answer.then !== 'function') return answer;
                // Within the hardware decoder's stated range: playback is smooth and power efficient.
                return answer.then(function (result) {
                    if (!result || !result.supported) return result;
                    var copy = {};
                    for (var key in result) copy[key] = result[key];
                    copy.smooth = true;
                    copy.powerEfficient = true;
                    return copy;
                });
            };
        } catch (_) {}
    }

    // YouTube picks its own quality from the viewport: cap it through the player once per video.
    var host = (location.hostname || '').toLowerCase();
    var youtube = host === 'youtube.com' || /\.youtube(-nocookie)?\.com$/.test(host) ||
        host === 'youtube-nocookie.com';
    if (!maxHeight || !youtube) return;
    var levels = [[2160, 'hd2160'], [1440, 'hd1440'], [1080, 'hd1080'], [720, 'hd720'],
        [480, 'large'], [360, 'medium'], [240, 'small'], [144, 'tiny']];
    var applied = '';
    function applyCap() {
        var player = document.getElementById('movie_player');
        if (!player || typeof player.getAvailableQualityLevels !== 'function') return;
        try {
            var available = player.getAvailableQualityLevels() || [];
            var quality = null;
            for (var i = 0; i < levels.length && !quality; i++) {
                if (levels[i][0] <= maxHeight && available.indexOf(levels[i][1]) !== -1) quality = levels[i][1];
            }
            if (!quality) return;
            var data = typeof player.getVideoData === 'function' ? player.getVideoData() : null;
            var key = (data && data.video_id || '') + '|' + quality;
            if (key === applied) return;
            if (typeof player.setPlaybackQualityRange === 'function') player.setPlaybackQualityRange(quality, quality);
            if (typeof player.setPlaybackQuality === 'function') player.setPlaybackQuality(quality);
            applied = key;
        } catch (_) {}
    }
    // Media events do not bubble: listen in the capture phase.
    document.addEventListener('loadedmetadata', applyCap, true);
    document.addEventListener('playing', applyCap, true);
    document.addEventListener('yt-navigate-finish', applyCap);
})(__VIREO_MEDIA_POLICY__);
