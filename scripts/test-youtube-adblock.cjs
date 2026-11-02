const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('app/src/main/assets/youtube_adblock.js', 'utf8');
function page(host = 'www.youtube.com', protocol = 'https:') {
    const context = vm.createContext({assert, location: {hostname: host, protocol,
        href: protocol + '//' + host + '/watch?v=one'}, URL, console});
    vm.runInContext(`
        window = globalThis;
        var styles = [], listeners = {};
        document = {
            documentElement: {appendChild(style) { styles.push(style); }},
            createElement() { return {disabled: false}; },
            addEventListener(name, fn) { listeners[name] = fn; }
        };
        class Response {
            constructor(url, value) { this.url = url; this.value = value; }
            json() { return Promise.resolve(this.value); }
            text() { return Promise.resolve(JSON.stringify(this.value)); }
        }
        class XMLHttpRequest {
            constructor(url, value, responseType = '') {
                this.responseURL = url; this.value = value; this.readyState = 4;
                this.responseType = responseType;
            }
            get response() { return this.responseType === 'json' ? this.value : JSON.stringify(this.value); }
            get responseText() {
                if (this.responseType === 'json') throw new Error('InvalidStateError');
                return JSON.stringify(this.value);
            }
        }
        var originalParse = JSON.parse;
        var originalResponseText = Response.prototype.text;
        var payload = () => ({playabilityStatus: {status: 'OK'}, videoDetails: {videoId: 'one'},
            streamingData: {formats: [{url: 'https://video.googlevideo.com/videoplayback?token=keep'}]},
            playerAds: [1], adPlacements: [2], adSlots: [3]});
        var endpoint = 'https://www.youtube.com/youtubei/v1/player?key=abc';
    `, context);
    vm.runInContext(source, context);
    return context;
}
(async () => {
    const context = page();
    await vm.runInContext(`
        (async () => {
            var parsed = JSON.parse(JSON.stringify(payload()));
            assert.equal(parsed.playerAds, undefined);
            assert.equal(parsed.adPlacements, undefined);
            assert.equal(parsed.adSlots, undefined);
            assert.equal(parsed.videoDetails.videoId, 'one');
            assert.equal(parsed.streamingData.formats[0].url,
                'https://video.googlevideo.com/videoplayback?token=keep');
            var nested = JSON.parse(JSON.stringify({playerResponse: payload()}));
            assert.equal(nested.playerResponse.adSlots, undefined);
            var args = JSON.parse(JSON.stringify({args: {player_response: JSON.stringify(payload())}}));
            assert.equal(originalParse(args.args.player_response).playerAds, undefined);
            var unrelated = JSON.parse('{"playerAds":[1],"title":"ordinary data"}');
            assert.equal(unrelated.playerAds.length, 1);
            assert.throws(() => JSON.parse('{'), SyntaxError);
            assert.equal(JSON.parse('{"n":1}', (key, value) => key === 'n' ? 2 : value).n, 2);
            ytInitialPlayerResponse = payload();
            assert.equal(ytInitialPlayerResponse.adSlots, undefined);
            ytplayer = {config: {args: {player_response: JSON.stringify(payload())}}};
            assert.equal(originalParse(ytplayer.config.args.player_response).adPlacements, undefined);
            assert.equal((await new Response(endpoint, payload()).json()).playerAds, undefined);
            assert.equal(originalParse(await new Response(endpoint, payload()).text()).adSlots, undefined);
            var other = 'https://www.youtube.com/youtubei/v1/browse';
            assert.equal((await new Response(other, payload()).json()).playerAds.length, 1);
            assert.equal(originalParse(await new Response('https://other.test/youtubei/v1/player', payload()).text()).adSlots.length, 1);
            var xhr = new XMLHttpRequest(endpoint, payload());
            assert.equal(originalParse(xhr.responseText).playerAds, undefined);
            assert.equal(xhr.responseText, xhr.responseText);
            var xhrJson = new XMLHttpRequest(endpoint, payload(), 'json');
            assert.equal(xhrJson.response.adPlacements, undefined);
            assert.throws(() => xhrJson.responseText, /InvalidStateError/);
            assert.equal(originalParse(new XMLHttpRequest(other, payload()).responseText).adSlots.length, 1);
            assert.equal(styles.length, 1);
            __vireoYouTubeAdblock.setEnabled(false);
            assert.equal(styles[0].disabled, true);
            assert.equal(JSON.parse(JSON.stringify(payload())).adSlots.length, 1);
            assert.equal((await new Response(endpoint, payload()).json()).playerAds.length, 1);
            ytInitialPlayerResponse = payload();
            assert.equal(ytInitialPlayerResponse.playerAds.length, 1);
            __vireoYouTubeAdblock.setEnabled(true);
            assert.equal(styles[0].disabled, false);
            assert.equal(ytInitialPlayerResponse.playerAds, undefined);
        })()
    `, context);
    const patchedParse = vm.runInContext('JSON.parse', context);
    vm.runInContext(source, context);
    assert.equal(vm.runInContext('JSON.parse', context), patchedParse);
    assert.equal(vm.runInContext('styles.length', context), 1);
    for (const host of ['m.youtube.com', 'www.youtube-nocookie.com']) {
        const allowed = page(host);
        assert.equal(vm.runInContext('typeof __vireoYouTubeAdblock', allowed), 'object');
    }
    for (const [host, protocol] of [['youtube.com.evil.test', 'https:'], ['notyoutube.com', 'https:'],
            ['example.test', 'https:'], ['www.youtube.com', 'http:']]) {
        const excluded = page(host, protocol);
        assert.equal(vm.runInContext('JSON.parse === originalParse', excluded), true);
        assert.equal(vm.runInContext('Response.prototype.text === originalResponseText', excluded), true);
    }
    assert.equal(source.includes('setInterval('), false);
    assert.equal(source.includes('currentTime'), false);
    console.log('YouTube content filtering passed: bootstrap, JSON/reviver/errors, fetch, XHR, origin isolation, toggle, reinjection and preserved video URLs');
})().catch(error => { console.error(error); process.exitCode = 1; });
