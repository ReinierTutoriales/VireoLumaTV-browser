const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const template = fs.readFileSync('app/src/main/assets/media_policy.js', 'utf8');

function page(config, host = 'www.youtube.com') {
    const context = vm.createContext({assert, Promise, location: {hostname: host}});
    vm.runInContext(`
        window = globalThis;
        var listeners = {};
        var windowListeners = {};
        window.innerWidth = 1280; window.innerHeight = 720; window.devicePixelRatio = 1;
        window.addEventListener = function(name, fn) { windowListeners[name] = fn; };
        var player = null;
        document = {
            addEventListener(name, fn) { listeners[name] = fn; },
            getElementById(id) { return id === 'movie_player' ? player : null; }
        };
        MediaSource = { isTypeSupported(type) { return true; } };
        HTMLMediaElement = function () {};
        HTMLMediaElement.prototype.canPlayType = function () { return 'probably'; };
        var nativeResult = {supported: true, smooth: false, powerEfficient: false};
        var nativeAnswer;
        navigator = { mediaCapabilities: { decodingInfo(c) {
            return nativeAnswer = Promise.resolve(nativeResult);
        } } };
    `, context);
    vm.runInContext(template.replace('__VIREO_MEDIA_POLICY__', JSON.stringify(config)), context);
    return context;
}

(async () => {
    // Force H.264: VP9/VP8/AV1 and codec-less WebM video are rejected, H.264 and audio are not.
    let c = page({block: ['vp8', 'vp9', 'av1'], maxHeight: 0});
    vm.runInContext(`
        var ok = t => MediaSource.isTypeSupported(t);
        assert.equal(ok('video/webm; codecs="vp9"'), false);
        assert.equal(ok('video/webm; codecs="vp09.00.51.08"'), false);
        assert.equal(ok('video/webm; codecs="vp8"'), false);
        assert.equal(ok('video/mp4; codecs="av01.0.05M.08"'), false);
        assert.equal(ok('video/webm'), false);
        assert.equal(ok('video/mp4; codecs="avc1.4d401f"'), true);
        assert.equal(ok('audio/webm; codecs="opus"'), true);
        assert.equal(ok('audio/mp4; codecs="mp4a.40.2"'), true);
        var media = new HTMLMediaElement();
        assert.equal(media.canPlayType('video/webm; codecs="vp9"'), '');
        assert.equal(media.canPlayType('video/mp4; codecs="avc1.42E01E"'), 'probably');
    `, c);
    let info = await vm.runInContext(`navigator.mediaCapabilities.decodingInfo({video: {contentType: 'video/webm; codecs="vp09.00.10.08"', width: 1280, height: 720}})`, c);
    assert.equal(info.supported, false);
    info = await vm.runInContext(`navigator.mediaCapabilities.decodingInfo({video: {contentType: 'video/mp4; codecs="avc1.4d401f"', width: 1280, height: 720}})`, c);
    assert.equal(info.supported, true);

    // Automatic mode on a device with hardware VP9 but no AV1: VP9 stays available.
    c = page({block: ['av1'], maxHeight: 0});
    vm.runInContext(`
        assert.equal(MediaSource.isTypeSupported('video/webm; codecs="vp9"'), true);
        assert.equal(MediaSource.isTypeSupported('video/mp4; codecs="av01.0.08M.08"'), false);
    `, c);

    // A TV with VP8 hardware but no VP9 must keep VP8 (and generic WebM) available.
    c = page({block: ['vp9', 'av1'], maxHeight: 0});
    vm.runInContext(`
        assert.equal(MediaSource.isTypeSupported('video/webm; codecs="vp8"'), true);
        assert.equal(MediaSource.isTypeSupported('video/webm; codecs="vp9"'), false);
        assert.equal(MediaSource.isTypeSupported('video/webm'), true);
    `, c);
    c = page({block: ['vp8'], maxHeight: 0});
    vm.runInContext(`
        assert.equal(MediaSource.isTypeSupported('video/webm; codecs="vp8"'), false);
        assert.equal(MediaSource.isTypeSupported('video/webm; codecs="vp9"'), true);
    `, c);

    // Quality cap: resolution probes above 720p fail, portrait videos are compared by area.
    c = page({block: [], maxHeight: 720});
    vm.runInContext(`
        assert.equal(MediaSource.isTypeSupported('video/mp4; codecs="avc1.640028"; width=1920; height=1080'), false);
        assert.equal(MediaSource.isTypeSupported('video/mp4; codecs="avc1.4d401f"; width=1280; height=720'), true);
        assert.equal(MediaSource.isTypeSupported('video/mp4; codecs="avc1.4d401f"; width=720; height=1280'), true);
        assert.equal(MediaSource.isTypeSupported('video/webm; codecs="vp9"'), true);
        var calls = [];
        player = {
            getAvailableQualityLevels() { return ['hd1080', 'hd720', 'large', 'medium', 'auto']; },
            getVideoData() { return {video_id: 'one'}; },
            setPlaybackQualityRange(a, b) { calls.push(a + '-' + b); },
            setPlaybackQuality(q) { calls.push(q); }
        };
        listeners.playing();
        listeners.loadedmetadata();
        assert.deepEqual(calls, ['hd720-hd720', 'hd720'], 'cap once per video');
        player.getVideoData = () => ({video_id: 'two'});
        listeners['yt-navigate-finish']();
        assert.equal(calls.length, 4);
    `, c);
    info = await vm.runInContext(`navigator.mediaCapabilities.decodingInfo({video: {contentType: 'video/mp4; codecs="avc1.640028"', width: 1920, height: 1080}})`, c);
    assert.equal(info.supported, false);

    // Automatic mode follows the window in physical pixels; it updates after a resize.
    c = page({block: ['av1'], maxHeight: 0, viewport: true});
    vm.runInContext(`
        var probe = t => MediaSource.isTypeSupported(t);
        assert.equal(probe('video/mp4; codecs="avc1.4d401f"; width=1920; height=1080'), false);
        assert.equal(probe('video/mp4; codecs="avc1.4d401f"; width=1280; height=720'), true);
        assert.equal(probe('video/mp4; codecs="av01.0.08M.08"'), false, 'codec policy remains');
        window.innerWidth = 1920; window.innerHeight = 1080;
        assert.equal(probe('video/mp4; codecs="avc1.4d401f"; width=1920; height=1080'), true);
        window.devicePixelRatio = 2;
        assert.equal(probe('video/mp4; codecs="avc1.4d401f"; width=3840; height=2160'), true);
        window.innerWidth = 0; window.innerHeight = 0;
        assert.equal(probe('video/mp4; codecs="avc1.4d401f"; width=3840; height=2160'), true, 'unknown viewport leaves provider decision');
        assert.equal(listeners.playing, undefined, 'automatic sizing never controls playback');
        assert.equal(listeners.loadedmetadata, undefined);
        assert.equal(listeners['yt-navigate-finish'], undefined);
        assert.equal(windowListeners.resize, undefined, 'capability queries read current dimensions directly');
    `, c);

    vm.runInContext('window.innerWidth = 1280; window.innerHeight = 720; window.devicePixelRatio = 1;', c);
    info = await vm.runInContext(`navigator.mediaCapabilities.decodingInfo({video: {contentType: 'video/mp4; codecs="avc1.640028"', width: 1920, height: 1080}})`, c);
    assert.equal(info.supported, false, 'automatic viewport also applies to decodingInfo');
    vm.runInContext(`
        var configuration = {video: {contentType: 'video/mp4; codecs="avc1.4d401f"', width: 1280, height: 720}};
        assert.equal(navigator.mediaCapabilities.decodingInfo(configuration), nativeAnswer,
            'automatic sizing preserves the native promise for eligible streams');
        window.devicePixelRatio = Infinity;
        assert.equal(probe('video/mp4; codecs="avc1.640028"; width=3840; height=2160'), true,
            'invalid viewport scale leaves the provider decision');
    `, c);

    // Unlimited quality keeps missing-codec blocks but never applies the viewport cap.
    c = page({block: ['av1'], maxHeight: 0});
    vm.runInContext(`
        assert.equal(MediaSource.isTypeSupported('video/mp4; codecs="avc1.640033"; width=3840; height=2160'), true);
        assert.equal(MediaSource.isTypeSupported('video/mp4; codecs="av01.0.08M.08"'), false);
        assert.equal(listeners.playing, undefined);
    `, c);

    // The YouTube player hook stays off other sites; a second injection is a no-op.
    c = page({block: [], maxHeight: 480}, 'example.com');
    vm.runInContext(`
        assert.equal(listeners.playing, undefined);
        var before = MediaSource.isTypeSupported;
    `, c);
    vm.runInContext(template.replace('__VIREO_MEDIA_POLICY__', JSON.stringify({block: ['vp9'], maxHeight: 0})), c);
    vm.runInContext(`assert.equal(MediaSource.isTypeSupported, before);`, c);

    // Automatic on a TV whose hardware decodes VP9 to 4K at 30 fps but 1080p at 60, without 10-bit,
    // and HEVC to 4K60 with 10-bit; AV1 has no hardware decoder and is blocked.
    c = page({block: ['av1'], maxHeight: 0, hw: {vp9: {h30: 2160, h60: 1080, ten: false},
        hevc: {h30: 2160, h60: 2160, ten: true}}}, 'example.com');
    vm.runInContext(`
        var ok = t => MediaSource.isTypeSupported(t);
        assert.equal(ok('video/webm; codecs="vp09.00.51.08"; width=3840; height=2160; framerate=30'), true, '4K30 in hardware');
        assert.equal(ok('video/webm; codecs="vp09.00.51.08"; width=3840; height=2160; framerate=60'), false, '4K60 would be software');
        assert.equal(ok('video/webm; codecs="vp09.00.41.08"; width=1920; height=1080; framerate=60'), true);
        assert.equal(ok('video/webm; codecs="vp09.00.51.08"; width=2160; height=3840; framerate=30'), true, 'portrait 4K');
        assert.equal(ok('video/webm; codecs="vp09.02.51.10"; width=1920; height=1080'), false, 'no 10-bit VP9');
        assert.equal(ok('video/mp4; codecs="hvc1.2.4.L153.B0"; width=3840; height=2160; framerate=60'), true, 'HEVC Main 10 4K60');
        assert.equal(ok('video/mp4; codecs="avc1.640033"; width=3840; height=2160'), true, 'no AVC entry: WebView decides');
        assert.equal(ok('video/webm; codecs="vp9"'), true, 'no size: the codec alone is fine');
        var media = new HTMLMediaElement();
        assert.equal(media.canPlayType('video/webm; codecs="vp09.02.10.10"'), '', '10-bit refused without size too');
        assert.equal(media.canPlayType('video/webm; codecs="vp09.00.10.08"'), 'probably');
    `, c);
    info = await vm.runInContext(`navigator.mediaCapabilities.decodingInfo({video: {contentType: 'video/webm; codecs="vp09.00.51.08"', width: 3840, height: 2160, framerate: 60}})`, c);
    assert.equal(info.supported, false);
    info = await vm.runInContext(`navigator.mediaCapabilities.decodingInfo({video: {contentType: 'video/webm; codecs="vp09.00.41.08"', width: 1920, height: 1080, framerate: 60}})`, c);
    assert.deepEqual([info.supported, info.smooth, info.powerEfficient], [true, false, false], 'hardware presence must not override the provider assessment');
    assert.equal(info, vm.runInContext('nativeResult', c), 'preserve the native result object');
    vm.runInContext(`
        var configuration = {video: {contentType: 'video/webm; codecs="vp09.00.41.08"',
            width: 1920, height: 1080, framerate: 60}};
        assert.equal(navigator.mediaCapabilities.decodingInfo(configuration), nativeAnswer,
            'eligible queries must return the native promise without another continuation');
        nativeResult = {supported: true, smooth: true, powerEfficient: true, keySystemAccess: {}};
    `, c);
    info = await vm.runInContext('navigator.mediaCapabilities.decodingInfo(configuration)', c);
    assert.equal(info, vm.runInContext('nativeResult', c), 'preserve positive assessments and DRM access');
    vm.runInContext('nativeResult = {supported: false, smooth: false, powerEfficient: false}', c);
    info = await vm.runInContext('navigator.mediaCapabilities.decodingInfo(configuration)', c);
    assert.equal(info.supported, false, 'a hardware entry cannot enable unsupported native configurations');
    vm.runInContext('nativeResult = {supported: true}', c);
    info = await vm.runInContext(`navigator.mediaCapabilities.decodingInfo({video: {contentType: 'video/mp4; codecs="avc1.640028"', width: 1920, height: 1080}})`, c);
    assert.equal(info.smooth, undefined, 'outside known hardware the WebView answer is untouched');

    console.log('Media policy passed: VP9/VP8/AV1 rejection, audio kept, hardware-aware mode, hardware size/rate/10-bit limits, resolution cap, YouTube cap, single injection');
})().catch(error => { console.error(error); process.exit(1); });
