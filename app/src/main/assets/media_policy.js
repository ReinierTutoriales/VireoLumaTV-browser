// Media decoding policy for low-end TVs. Runs at document start in every frame. It only answers
// capability queries (MediaSource/canPlayType/mediaCapabilities) so sites pick a stream the TV
// decodes in hardware; it never touches network requests or playback state.
(function (config) {
    'use strict';
    if (!config || window.__vireoMediaPolicy) return;
    window.__vireoMediaPolicy = true;
    var blockVp9 = config.block.indexOf('vp9') !== -1;
    var blockAv1 = config.block.indexOf('av1') !== -1;
    var maxHeight = config.maxHeight | 0;
    var maxWidth = Math.round(maxHeight * 16 / 9);

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
        if (blockVp9) {
            if (/(^|[\s,])(vp0?9|vp0?8)(\.|,|\s|$)/.test(codecs)) return true;
            // A WebM video query without codecs means VP8/VP9/AV1.
            if (lower.indexOf('video/webm') === 0 && !codecs) return true;
        }
        if (blockAv1 && /(^|[\s,])av0?1(\.|,|\s|$)/.test(codecs)) return true;
        return false;
    }
    function overCap(width, height) {
        if (!maxHeight) return false;
        if (width && height) return width * height > maxWidth * maxHeight;
        return height > maxHeight || width > maxWidth;
    }
    function rejectedType(type) {
        return blockedCodec(type) ||
            (typeof type === 'string' && overCap(numberParam(type.toLowerCase(), 'width'),
                numberParam(type.toLowerCase(), 'height')));
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
                return blockedCodec(type) ? '' : originalCanPlay.call(this, type);
            };
        } catch (_) {}
    }

    var capabilities = window.navigator && navigator.mediaCapabilities;
    if (capabilities && typeof capabilities.decodingInfo === 'function') {
        var originalDecodingInfo = capabilities.decodingInfo;
        try {
            capabilities.decodingInfo = function (configuration) {
                var video = configuration && configuration.video;
                if (video && (blockedCodec(video.contentType) || overCap(video.width | 0, video.height | 0))) {
                    return Promise.resolve({supported: false, smooth: false, powerEfficient: false,
                        configuration: configuration});
                }
                return originalDecodingInfo.apply(this, arguments);
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
