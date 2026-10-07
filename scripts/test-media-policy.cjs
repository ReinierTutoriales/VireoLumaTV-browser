const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const template = fs.readFileSync('app/src/main/assets/media_policy.js', 'utf8');

function page(config, host = 'www.youtube.com') {
    const context = vm.createContext({assert, Promise, location: {hostname: host}});
    vm.runInContext(`
        window = globalThis;
        var listeners = {};
        var player = null;
        document = {
            addEventListener(name, fn) { listeners[name] = fn; },
            getElementById(id) { return id === 'movie_player' ? player : null; }
        };
        MediaSource = { isTypeSupported(type) { return true; } };
        HTMLMediaElement = function () {};
        HTMLMediaElement.prototype.canPlayType = function () { return 'probably'; };
        navigator = { mediaCapabilities: { decodingInfo(c) { return Promise.resolve({supported: true}); } } };
    `, context);
    vm.runInContext(template.replace('__VIREO_MEDIA_POLICY__', JSON.stringify(config)), context);
    return context;
}

(async () => {
    // Force H.264: VP9/VP8/AV1 and codec-less WebM video are rejected, H.264 and audio are not.
    let c = page({block: ['vp9', 'av1'], maxHeight: 0});
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

    // The YouTube player hook stays off other sites; a second injection is a no-op.
    c = page({block: [], maxHeight: 480}, 'example.com');
    vm.runInContext(`
        assert.equal(listeners.playing, undefined);
        var before = MediaSource.isTypeSupported;
    `, c);
    vm.runInContext(template.replace('__VIREO_MEDIA_POLICY__', JSON.stringify({block: ['vp9'], maxHeight: 0})), c);
    vm.runInContext(`assert.equal(MediaSource.isTypeSupported, before);`, c);

    console.log('Media policy passed: VP9/VP8/AV1 rejection, audio kept, hardware-aware mode, resolution cap, YouTube cap, single injection');
})().catch(error => { console.error(error); process.exit(1); });
